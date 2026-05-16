package com.player.coco.ui

import com.player.coco.data.ChainLinkConfig
import com.player.coco.data.ChainLinkStore
import com.player.coco.data.GlobalSettingsStore
import com.player.coco.logging.CocoLog
import com.player.coco.R
import com.player.coco.share.ConfigShareCodecs
import com.player.coco.share.DecodedChainLinkDraft
import com.player.coco.xray.runtime.XrayConnectionState
import com.player.coco.xray.runtime.XrayCoreRuntime
import com.player.coco.xray.runtime.XrayServiceActions

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.ColorDrawable
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var drawerScrim: View
    private lateinit var drawerPanel: View
    private lateinit var chainList: LinearLayout
    private lateinit var deleteUndoBar: View
    private lateinit var connectionStateText: TextView
    private lateinit var connectButton: ImageButton
    private lateinit var store: ChainLinkStore
    private lateinit var settingsStore: GlobalSettingsStore
    private var pendingConfigId = 0L
    private var pendingDeleteConfigId = 0L
    private val deleteHandler = Handler(Looper.getMainLooper())
    private var pendingDeleteRunnable: Runnable? = null
    private val connectionStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == XrayConnectionState.ACTION_CHANGED) {
                renderConnectionState()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        store = ChainLinkStore(filesDir)
        settingsStore = GlobalSettingsStore(filesDir)
        drawerScrim = findViewById(R.id.drawer_scrim)
        drawerPanel = findViewById(R.id.drawer_panel)
        chainList = findViewById(R.id.chain_list)
        deleteUndoBar = findViewById(R.id.delete_undo_bar)
        connectionStateText = findViewById(R.id.connection_state_text)
        connectButton = findViewById(R.id.connect_button)
        CocoLog.trimToBudget(this)

        bindTopBar()
        bindDrawer()
        bindDeleteUndoBar()
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter(XrayConnectionState.ACTION_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(connectionStateReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(connectionStateReceiver, filter)
        }
    }

    override fun onStop() {
        unregisterReceiver(connectionStateReceiver)
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        renderChainLinks()
        renderConnectionState()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_VPN_PERMISSION) {
            return
        }

        if (resultCode == RESULT_OK) {
            startVpnService()
        } else {
            XrayConnectionState.publishDisconnected(this)
            Toast.makeText(this, R.string.connect_vpn_permission_denied, Toast.LENGTH_SHORT).show()
            renderConnectionState()
        }
    }

    private fun bindTopBar() {
        findViewById<ImageButton>(R.id.menu_button).setOnClickListener {
            showDrawer()
        }
        findViewById<ImageButton>(R.id.filter_button).setOnClickListener {
            showPlaceholder(getString(R.string.action_filter))
        }
        findViewById<ImageButton>(R.id.add_button).setOnClickListener { anchor ->
            showPopup(anchor, R.layout.popup_add_menu, R.dimen.popup_width_add) { popupView, popup ->
                bindPopupItem(popupView, R.id.import_qrcode, R.string.menu_import_qrcode)
                bindPopupItem(popupView, R.id.import_local_storage, R.string.menu_import_local_storage)
                bindPopupItem(popupView, R.id.add_single_link, R.string.menu_add_single_link)
                popupView.findViewById<View>(R.id.import_clipboard).setOnClickListener {
                    popup.dismiss()
                    importConfigsFromClipboard()
                }
                popupView.findViewById<View>(R.id.add_chain_link).setOnClickListener {
                    popup.dismiss()
                    startActivity(Intent(this, ChainLinkFormActivity::class.java))
                }
            }
        }
        findViewById<ImageButton>(R.id.more_button).setOnClickListener { anchor ->
            showPopup(anchor, R.layout.popup_overflow_menu, R.dimen.popup_width_overflow) { popupView, popup ->
                bindPopupItem(popupView, R.id.sort_chain_links, R.string.menu_sort_chain_links)
                bindPopupItem(popupView, R.id.refresh_list, R.string.menu_refresh_list)
                popupView.findViewById<View>(R.id.open_logs).setOnClickListener {
                    popup.dismiss()
                    openLogs()
                }
            }
        }
        connectButton.setOnClickListener {
            toggleConnection()
        }
    }

    private fun bindDrawer() {
        drawerScrim.setOnClickListener { hideDrawer() }

        findViewById<View>(R.id.drawer_per_app_row).setOnClickListener {
            hideDrawer()
            startActivity(Intent(this, PerAppSettingsActivity::class.java))
        }
        bindDrawerRow(R.id.drawer_routing_row, R.string.drawer_routing)
        bindDrawerRow(R.id.drawer_assets_row, R.string.drawer_asset_files)
        findViewById<View>(R.id.drawer_settings_row).setOnClickListener {
            hideDrawer()
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        findViewById<View>(R.id.drawer_logs_row).setOnClickListener {
            hideDrawer()
            openLogs()
        }
        bindDrawerRow(R.id.drawer_updates_row, R.string.drawer_updates)
        bindDrawerRow(R.id.drawer_backup_row, R.string.drawer_backup_restore)
        bindDrawerRow(R.id.drawer_about_row, R.string.drawer_about)
    }

    private fun bindDrawerRow(rowId: Int, labelRes: Int) {
        findViewById<View>(rowId).setOnClickListener {
            hideDrawer()
            showPlaceholder(getString(labelRes))
        }
    }

    private fun bindDeleteUndoBar() {
        findViewById<View>(R.id.delete_undo_button).setOnClickListener {
            cancelPendingDelete()
        }
    }

    private fun renderChainLinks() {
        val inflater = LayoutInflater.from(this)
        chainList.removeAllViews()
        val configs = store.loadAll()

        if (configs.isEmpty()) {
            chainList.addView(inflater.inflate(R.layout.item_empty_chain_links, chainList, false))
            return
        }

        val activeConfigId = activeConfigId(configs)
        configs.forEach { item ->
            val row = inflater.inflate(R.layout.item_config, chainList, false)
            row.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                configRowHeight()
            )
            row.findViewById<View>(R.id.selected_indicator).visibility =
                if (item.id == activeConfigId) View.VISIBLE else View.GONE
            row.findViewById<TextView>(R.id.config_name).text = item.name
            row.findViewById<TextView>(R.id.config_endpoint).text = item.endpoint
            row.findViewById<TextView>(R.id.config_type).text = configTypeLabel(item)
            row.findViewById<TextView>(R.id.config_latency).text = "-"
            renderPendingDeleteState(row, item.id == pendingDeleteConfigId)

            row.setOnClickListener {
                if (item.id != pendingDeleteConfigId) {
                    selectConfig(item.id)
                }
            }
            row.findViewById<View>(R.id.config_more_button).setOnClickListener { anchor ->
                if (item.id != pendingDeleteConfigId) {
                    showConfigActions(anchor, item)
                }
            }
            row.findViewById<View>(R.id.edit_button).setOnClickListener {
                if (item.id != pendingDeleteConfigId) {
                    startActivity(
                        Intent(this, ChainLinkFormActivity::class.java)
                            .putExtra(ChainLinkFormActivity.EXTRA_CONFIG_ID, item.id)
                    )
                }
            }
            row.findViewById<View>(R.id.delete_button).setOnClickListener {
                queueConfigDelete(item.id)
            }

            chainList.addView(row)
        }
    }

    private fun renderPendingDeleteState(row: View, pendingDelete: Boolean) {
        row.findViewById<View>(R.id.config_item_content).alpha =
            if (pendingDelete) PENDING_DELETE_ALPHA else 1.0f
        row.findViewById<View>(R.id.selected_indicator).alpha =
            if (pendingDelete) PENDING_DELETE_ALPHA else 1.0f
        row.findViewById<View>(R.id.config_more_button).isEnabled = !pendingDelete
        row.findViewById<View>(R.id.edit_button).isEnabled = !pendingDelete
        row.findViewById<View>(R.id.delete_button).isEnabled = !pendingDelete
    }

    private fun toggleConnection() {
        if (isConnectionActive()) {
            XrayConnectionState.publishDisconnected(this)
            XrayServiceActions.stopAll(this)
            renderConnectionState()
            return
        }

        val selectedConfig = activeConfig(store.loadAll().filter { it.id != pendingDeleteConfigId })
        if (selectedConfig == null) {
            Toast.makeText(this, R.string.connect_no_config, Toast.LENGTH_SHORT).show()
            return
        }

        pendingConfigId = selectedConfig.id
        if (settingsStore.appMode() == GlobalSettingsStore.APP_MODE_PROXY_ONLY) {
            XrayConnectionState.publishConnecting(this, XrayServiceActions.MODE_PROXY_ONLY, selectedConfig.id)
            XrayServiceActions.startProxyOnly(this, selectedConfig.id)
            renderConnectionState()
        } else {
            val permissionIntent = VpnService.prepare(this)
            if (permissionIntent == null) {
                startVpnService()
            } else {
                startActivityForResult(permissionIntent, REQUEST_VPN_PERMISSION)
            }
        }
    }

    private fun startVpnService() {
        XrayConnectionState.publishConnecting(this, XrayServiceActions.MODE_VPN, pendingConfigId)
        XrayServiceActions.startVpn(this, pendingConfigId)
        renderConnectionState()
    }

    private fun renderConnectingState() {
        connectionStateText.text = getString(R.string.status_connecting)
        connectionStateText.setTextColor(getColorCompat(R.color.coco_muted))
        connectButton.setBackgroundResource(R.drawable.bg_connect_button)
    }

    private fun renderConnectionState() {
        val snapshot = XrayConnectionState.snapshot()
        if (snapshot.state == XrayConnectionState.STATE_CONNECTING) {
            renderConnectingState()
        } else if (snapshot.state == XrayConnectionState.STATE_CONNECTED) {
            val statusRes = if (snapshot.mode == XrayServiceActions.MODE_VPN) {
                R.string.status_connected_vpn
            } else {
                R.string.status_connected_proxy
            }
            connectionStateText.text = getString(statusRes)
            connectionStateText.setTextColor(getColorCompat(R.color.coco_primary))
            connectButton.setBackgroundResource(R.drawable.bg_connect_button_active)
        } else {
            connectionStateText.text = getString(R.string.status_not_connected)
            connectionStateText.setTextColor(getColorCompat(R.color.coco_muted))
            connectButton.setBackgroundResource(R.drawable.bg_connect_button)
        }
    }

    private fun isConnectionActive(): Boolean {
        val snapshot = XrayConnectionState.snapshot()
        return snapshot.state == XrayConnectionState.STATE_CONNECTING ||
            snapshot.state == XrayConnectionState.STATE_CONNECTED ||
            XrayCoreRuntime.isRunning()
    }

    private fun showPopup(
        anchor: View,
        layoutRes: Int,
        widthRes: Int,
        bindContent: (View, PopupWindow) -> Unit,
    ) {
        val content = LayoutInflater.from(this).inflate(layoutRes, null, false)
        val width = resources.getDimensionPixelSize(widthRes)
        val popup = PopupWindow(content, width, LinearLayout.LayoutParams.WRAP_CONTENT, true).apply {
            isOutsideTouchable = true
            elevation = dp(8).toFloat()
            setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
        }

        bindContent(content, popup)
        popup.showAsDropDown(anchor, -width + anchor.width, -anchor.height)
    }

    private fun bindPopupItem(parent: View, itemId: Int, labelRes: Int) {
        parent.findViewById<View>(itemId).setOnClickListener {
            showPlaceholder(getString(labelRes))
        }
    }

    private fun showConfigActions(anchor: View, config: ChainLinkConfig) {
        showPopup(anchor, R.layout.popup_config_actions, R.dimen.popup_width_config_actions) { popupView, popup ->
            bindConfigActionItem(popupView, R.id.test_real_delay, R.string.menu_test_real_delay, popup)
            bindConfigActionItem(popupView, R.id.test_tcping, R.string.menu_test_tcping, popup)
            bindConfigActionItem(popupView, R.id.show_qrcode, R.string.menu_qrcode, popup)
            bindConfigActionItem(popupView, R.id.copy_full_config, R.string.menu_copy_full_config, popup)
            popupView.findViewById<View>(R.id.copy_config).setOnClickListener {
                popup.dismiss()
                copyConfig(config)
            }
        }
    }

    private fun bindConfigActionItem(parent: View, itemId: Int, labelRes: Int, popup: PopupWindow) {
        parent.findViewById<View>(itemId).setOnClickListener {
            popup.dismiss()
            showPlaceholder(getString(labelRes))
        }
    }

    private fun copyConfig(config: ChainLinkConfig) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val link = ConfigShareCodecs.encode(config)
        if (link == null) {
            Toast.makeText(
                this,
                getString(R.string.config_copy_not_supported, configTypeLabel(config)),
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.clip_label_chain_link), link))
        Toast.makeText(this, R.string.chain_link_copied, Toast.LENGTH_SHORT).show()
    }

    private fun queueConfigDelete(configId: Long) {
        if (pendingDeleteConfigId != 0L) {
            commitPendingDelete(renderAfterDelete = false)
        }

        pendingDeleteConfigId = configId
        pendingDeleteRunnable = Runnable {
            commitPendingDelete(renderAfterDelete = true)
        }
        deleteHandler.postDelayed(pendingDeleteRunnable!!, DELETE_UNDO_WINDOW_MILLIS)
        deleteUndoBar.visibility = View.VISIBLE
        renderChainLinks()
    }

    private fun cancelPendingDelete() {
        pendingDeleteRunnable?.let { deleteHandler.removeCallbacks(it) }
        pendingDeleteRunnable = null
        pendingDeleteConfigId = 0L
        deleteUndoBar.visibility = View.GONE
        renderChainLinks()
    }

    private fun commitPendingDelete(renderAfterDelete: Boolean) {
        val configId = pendingDeleteConfigId
        if (configId == 0L) {
            return
        }

        pendingDeleteRunnable?.let { deleteHandler.removeCallbacks(it) }
        pendingDeleteRunnable = null
        pendingDeleteConfigId = 0L
        deleteUndoBar.visibility = View.GONE
        store.delete(configId)
        if (renderAfterDelete) {
            renderChainLinks()
        }
    }

    private fun selectConfig(configId: Long) {
        settingsStore.saveActiveConfigId(configId)
        renderChainLinks()
    }

    private fun activeConfig(configs: List<ChainLinkConfig>): ChainLinkConfig? {
        val activeId = activeConfigId(configs)
        return configs.firstOrNull { it.id == activeId } ?: configs.firstOrNull()
    }

    private fun activeConfigId(configs: List<ChainLinkConfig>): Long {
        if (configs.isEmpty()) {
            return 0L
        }

        val savedConfigId = settingsStore.activeConfigId()
        return if (configs.any { it.id == savedConfigId }) {
            savedConfigId
        } else {
            configs.first().id
        }
    }

    private fun importConfigsFromClipboard() {
        val tokens = clipboardText()
            .split(Regex("\\s+"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
        var imported = 0
        var failed = 0
        var skipped = 0

        tokens.forEach { token ->
            val codec = ConfigShareCodecs.codecForLink(token)
            if (codec == null) {
                skipped += 1
                return@forEach
            }

            val decoded = codec.decode(token)
            if (decoded == null) {
                failed += 1
                return@forEach
            }

            try {
                when (decoded) {
                    is DecodedChainLinkDraft -> {
                        store.saveNew(decoded.chainLink)
                        imported += 1
                    }
                }
            } catch (_: Exception) {
                failed += 1
            }
        }

        if (imported > 0) {
            renderChainLinks()
        }
        Toast.makeText(
            this,
            getString(R.string.clipboard_import_result, imported, failed, skipped),
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun clipboardText(): String {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip ?: return ""
        val items = mutableListOf<String>()
        for (index in 0 until clip.itemCount) {
            items += clip.getItemAt(index).coerceToText(this)?.toString().orEmpty()
        }
        return items.joinToString(" ")
    }

    private fun openLogs() {
        startActivity(Intent(this, LogsActivity::class.java))
    }

    private fun showDrawer() {
        drawerScrim.visibility = View.VISIBLE
        drawerPanel.visibility = View.VISIBLE
    }

    private fun hideDrawer() {
        drawerPanel.visibility = View.GONE
        drawerScrim.visibility = View.GONE
    }

    private fun showPlaceholder(label: String) {
        Toast.makeText(
            this,
            getString(R.string.placeholder_wired_later, label),
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun getColorCompat(colorRes: Int): Int {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            getColor(colorRes)
        } else {
            resources.getColor(colorRes)
        }
    }

    private fun configTypeLabel(config: ChainLinkConfig): String {
        return when (config.type) {
            ChainLinkStore.TYPE_CHAIN_LINK -> getString(R.string.chain_link_type)
            else -> config.type.uppercase()
        }
    }

    private fun configRowHeight(): Int {
        val screenSixth = resources.displayMetrics.heightPixels / 6
        return screenSixth.coerceIn(dp(124), dp(168))
    }

    companion object {
        private const val REQUEST_VPN_PERMISSION = 100
        private const val DELETE_UNDO_WINDOW_MILLIS = 3_000L
        private const val PENDING_DELETE_ALPHA = 0.38f
    }

}
