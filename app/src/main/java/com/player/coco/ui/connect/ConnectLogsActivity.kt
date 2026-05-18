package com.player.coco.ui.connect

import com.player.coco.logging.CocoLog
import com.player.coco.R
import com.player.coco.ui.dp
import com.player.coco.ui.getColorCompat
import com.player.coco.ui.getDrawableCompat
import com.player.coco.ui.widget.CocoSelectField

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.graphics.Typeface
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.ListView
import android.widget.PopupWindow
import android.widget.TextView
import android.widget.Toast

class ConnectLogsActivity : Activity() {
    private lateinit var logVerbositySelect: CocoSelectField
    private lateinit var searchInput: EditText
    private lateinit var clearSearchButton: View
    private lateinit var matchCountText: TextView
    private lateinit var emptyText: TextView
    private lateinit var logsList: ListView
    private lateinit var adapter: LogsAdapter
    private var rawLines = emptyList<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_logs)

        logVerbositySelect = CocoSelectField.bind(
            field = findViewById(R.id.log_verbosity_select),
            options = resources.getStringArray(R.array.log_verbosity_options).toList(),
            initialValue = CocoLog.displayFilterLevel(this),
            onChanged = { selectedLevel ->
                CocoLog.setDisplayFilterLevel(this, selectedLevel)
                renderLogs(autoScroll = false)
            },
        )
        searchInput = findViewById(R.id.search_input)
        clearSearchButton = findViewById(R.id.clear_search_button)
        matchCountText = findViewById(R.id.match_count_text)
        emptyText = findViewById(R.id.empty_logs_text)
        logsList = findViewById(R.id.logs_list)
        adapter = LogsAdapter()
        logsList.adapter = adapter
        logsList.setOnItemClickListener { _, anchor, position, _ ->
            showLogLineMenu(anchor, adapter.getItem(position))
        }

        findViewById<View>(R.id.back_button).setOnClickListener { finish() }
        findViewById<View>(R.id.refresh_button).setOnClickListener { loadLogs() }
        findViewById<TextView>(R.id.clear_button).setOnClickListener {
            CocoLog.clear(this)
            loadLogs()
        }
        clearSearchButton.setOnClickListener {
            searchInput.text?.clear()
            searchInput.requestFocus()
        }

        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                renderLogs(autoScroll = false)
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
    }

    override fun onResume() {
        super.onResume()
        loadLogs()
    }

    private fun loadLogs() {
        rawLines = CocoLog.readRecentLines(this)
        renderLogs(autoScroll = true)
    }

    private fun renderLogs(autoScroll: Boolean) {
        val query = searchInput.text?.toString().orEmpty().trim()
        val selectedLevel = logVerbositySelect.value
        val visibleLines = rawLines
            .filter { line -> query.isBlank() || line.contains(query, ignoreCase = true) }
            .filter { line -> isVisibleAtLevel(line, selectedLevel) }

        adapter.submit(visibleLines)
        matchCountText.text = if (query.isBlank()) {
            getString(R.string.logs_line_count, rawLines.size)
        } else {
            getString(R.string.logs_match_count, visibleLines.size)
        }

        emptyText.text = if (rawLines.isEmpty()) {
            getString(R.string.logs_empty)
        } else {
            getString(R.string.logs_no_matches)
        }
        emptyText.visibility = if (visibleLines.isEmpty()) View.VISIBLE else View.GONE
        logsList.visibility = if (visibleLines.isEmpty()) View.GONE else View.VISIBLE
        clearSearchButton.visibility = if (query.isBlank()) View.GONE else View.VISIBLE

        if (autoScroll && adapter.count > 0) {
            logsList.post { logsList.setSelection(adapter.count - 1) }
        }
    }

    private fun isVisibleAtLevel(line: String, selectedLevel: String): Boolean {
        val selectedIndex = LOG_LEVELS.indexOf(selectedLevel).takeIf { it >= 0 } ?: LOG_LEVELS.indexOf(CocoLog.LEVEL_WARNING)
        val lowerLine = line.lowercase()
        val lineLevel = when {
            "/E/" in line || " E/" in line || "[error]" in lowerLine -> CocoLog.LEVEL_ERROR
            "/W/" in line || " W/" in line || "[warning]" in lowerLine -> CocoLog.LEVEL_WARNING
            "/I/" in line || " I/" in line || "[info]" in lowerLine -> CocoLog.LEVEL_INFO
            "/D/" in line || " D/" in line || "[debug]" in lowerLine || "debug" in lowerLine -> CocoLog.LEVEL_DEBUG
            else -> CocoLog.LEVEL_INFO
        }
        val lineIndex = LOG_LEVELS.indexOf(lineLevel).takeIf { it >= 0 } ?: LOG_LEVELS.indexOf(CocoLog.LEVEL_INFO)
        return lineIndex <= selectedIndex
    }

    private fun showLogLineMenu(anchor: View, line: String) {
        val menuItem = TextView(this).apply {
            background = getDrawableCompat(R.drawable.bg_popup)
            isClickable = true
            isFocusable = true
            minWidth = dp(168)
            setPadding(dp(18), 0, dp(18), 0)
            height = dp(48)
            gravity = android.view.Gravity.CENTER_VERTICAL
            includeFontPadding = false
            setTextColor(getColorCompat(R.color.coco_title))
            textSize = 15f
            text = getString(R.string.action_copy_log_line)
        }
        val popup = PopupWindow(menuItem, ViewGroup.LayoutParams.WRAP_CONTENT, dp(48), true).apply {
            isOutsideTouchable = true
            elevation = dp(8).toFloat()
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
        }
        menuItem.setOnClickListener {
            copyLine(line)
            popup.dismiss()
        }
        popup.showAsDropDown(anchor, dp(12), -anchor.height)
    }

    private fun copyLine(line: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.clip_label_log_line), line))
        Toast.makeText(this, R.string.log_line_copied, Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val MAX_RENDERED_LINE_CHARS = 4000
        private val LOG_LEVELS = listOf(
            CocoLog.LEVEL_ERROR,
            CocoLog.LEVEL_WARNING,
            CocoLog.LEVEL_INFO,
            CocoLog.LEVEL_DEBUG,
        )
    }

    private inner class LogsAdapter : BaseAdapter() {
        private var lines = emptyList<String>()

        fun submit(nextLines: List<String>) {
            lines = nextLines
            notifyDataSetChanged()
        }

        override fun getCount(): Int = lines.size

        override fun getItem(position: Int): String = lines[position]

        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val textView = (convertView as? TextView) ?: TextView(this@ConnectLogsActivity).apply {
                typeface = Typeface.MONOSPACE
                includeFontPadding = false
                setTextColor(getColorCompat(R.color.coco_body))
                textSize = 12f
                setPadding(dp(12), dp(4), dp(12), dp(4))
                setTextIsSelectable(false)
            }
            val line = getItem(position)
            textView.text = if (line.length > MAX_RENDERED_LINE_CHARS) {
                line.take(MAX_RENDERED_LINE_CHARS) + " ...[truncated]"
            } else {
                line
            }
            return textView
        }
    }

}
