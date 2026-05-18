package com.player.coco.ui.music

import com.player.coco.R
import com.player.coco.data.ConnectCryptoSession
import com.player.coco.data.MusicSettingsStore

import android.app.Activity
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast

class MusicHomeController(
    private val activity: Activity,
    root: View,
    private val musicSettingsStore: MusicSettingsStore,
    private val onUnlockRequested: () -> Unit,
) {
    private val searchInput: EditText = root.findViewById(R.id.music_search_input)
    private val searchButton: ImageButton = root.findViewById(R.id.music_search_button)
    private val resultText: TextView = root.findViewById(R.id.music_result_text)
    private var unlocked = false

    init {
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                renderSearchState(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        searchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                submitSearch()
                true
            } else {
                false
            }
        }
        searchButton.setOnClickListener {
            submitSearch()
        }
    }

    fun onResume() {
        unlocked = false
        resultText.text = activity.getString(R.string.music_recent_tracks)
    }

    private fun submitSearch() {
        if (unlocked) {
            return
        }

        val value = searchInput.text?.toString().orEmpty()
        if (
            musicSettingsStore.hasCocoConnectAuth() &&
            ConnectCryptoSession.unlockWithPass(musicSettingsStore, value.trim())
        ) {
            unlocked = true
            searchInput.setText("")
            onUnlockRequested()
            return
        }

        showSearchPlaceholder()
    }

    private fun renderSearchState(value: String) {
        resultText.text = if (value.isBlank()) {
            activity.getString(R.string.music_recent_tracks)
        } else {
            activity.getString(R.string.music_searching)
        }
    }

    private fun showSearchPlaceholder() {
        Toast.makeText(activity, R.string.music_search_unavailable, Toast.LENGTH_SHORT).show()
    }
}
