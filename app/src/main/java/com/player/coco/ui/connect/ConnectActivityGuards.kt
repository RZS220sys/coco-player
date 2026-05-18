package com.player.coco.ui.connect

import com.player.coco.R
import com.player.coco.data.ConnectCryptoSession
import com.player.coco.data.MusicSettingsStore
import com.player.coco.ui.MainActivity

import android.app.Activity
import android.content.Intent
import android.widget.Toast

fun Activity.finishIfConnectDataLocked(): Boolean {
    if (!MusicSettingsStore(filesDir).hasCocoConnectAuth() || ConnectCryptoSession.keyOrNull() != null) {
        return false
    }

    Toast.makeText(this, R.string.connect_data_locked, Toast.LENGTH_SHORT).show()
    startActivity(
        Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    )
    finish()
    return true
}
