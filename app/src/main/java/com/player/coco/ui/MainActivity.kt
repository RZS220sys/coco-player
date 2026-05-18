package com.player.coco.ui

import com.player.coco.R
import com.player.coco.data.ConnectCryptoSession
import com.player.coco.data.ConnectDataMigration
import com.player.coco.data.MusicSettingsStore
import com.player.coco.ui.connect.CocoConnectHome
import com.player.coco.ui.music.MusicHomeController

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout

class MainActivity : Activity() {
    private lateinit var musicSettingsStore: MusicSettingsStore
    private lateinit var musicSurface: View
    private lateinit var musicHomeController: MusicHomeController
    private lateinit var connectHome: CocoConnectHome
    private var connectUnlocked = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        musicSettingsStore = MusicSettingsStore(filesDir)
        musicSurface = findViewById(R.id.music_surface)
        connectHome = CocoConnectHome(this, findViewById(R.id.root_frame))
        musicHomeController = MusicHomeController(
            activity = this,
            root = musicSurface,
            musicSettingsStore = musicSettingsStore,
            onUnlockRequested = { showConnectHome() },
        )

        if (musicSettingsStore.hasCocoConnectAuth()) {
            showMusicHome()
        } else {
            ConnectCryptoSession.unlockWithoutAuth()
            showConnectHome()
            if (!musicSettingsStore.displayedConnectPassDialog()) {
                showConnectPassDialog()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        connectHome.onStart()
    }

    override fun onStop() {
        connectHome.onStop()
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        musicHomeController.onResume()

        if (connectUnlocked) {
            connectHome.onResume()
        } else if (!musicSettingsStore.hasCocoConnectAuth()) {
            ConnectCryptoSession.unlockWithoutAuth()
            showConnectHome()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        connectHome.onActivityResult(requestCode, resultCode)
    }

    private fun showMusicHome() {
        connectUnlocked = false
        musicSurface.visibility = View.VISIBLE
    }

    private fun showConnectHome() {
        connectUnlocked = true
        musicSurface.visibility = View.GONE
        connectHome.onResume()
    }

    private fun showConnectPassDialog() {
        val passInput = connectPassInput(R.string.connect_pass_input_hint)
        val repeatInput = connectPassInput(R.string.connect_pass_repeat_hint)
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val padding = dp(20)
            setPadding(padding, dp(8), padding, 0)
            addView(passInput)
            addView(repeatInput)
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.connect_pass_dialog_title)
            .setMessage(R.string.connect_pass_dialog_description)
            .setView(form)
            .setPositiveButton(R.string.action_ok, null)
            .setNegativeButton(R.string.action_no_thanks) { _, _ ->
                musicSettingsStore.saveCocoConnectPass(null)
                ConnectCryptoSession.unlockWithoutAuth()
                showConnectHome()
            }
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val pass = passInput.text?.toString().orEmpty()
                val repeat = repeatInput.text?.toString().orEmpty()
                if (pass != repeat) {
                    repeatInput.error = getString(R.string.connect_pass_mismatch)
                    repeatInput.requestFocus()
                    return@setOnClickListener
                }

                musicSettingsStore.saveCocoConnectPass(pass)
                if (pass.isBlank()) {
                    ConnectCryptoSession.unlockWithoutAuth()
                    showConnectHome()
                } else {
                    check(ConnectCryptoSession.unlockWithPass(musicSettingsStore, pass))
                    ConnectDataMigration.rewriteConnectData(filesDir)
                    showMusicHome()
                }
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun connectPassInput(hintRes: Int): EditText {
        return EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setSingleLine(true)
            hint = getString(hintRes)
        }
    }
}
