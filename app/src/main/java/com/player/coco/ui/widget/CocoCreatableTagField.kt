package com.player.coco.ui.widget

import com.player.coco.R
import com.player.coco.ui.dp
import com.player.coco.ui.getColorCompat

import android.app.Dialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class CocoCreatableTagField private constructor(
    private val container: LinearLayout,
    suggestions: List<String>?,
    hint: String?,
) {
    private var suggestions = suggestions.cleanTags()
    private val fieldHint = hint
    private val selectedValues = linkedSetOf<String>()
    private val input = buildInput()
    private var sheetDialog: Dialog? = null
    private var sheetSearch: EditText? = null
    private var sheetSelectedContainer: LinearLayout? = null
    private var sheetSuggestionContainer: LinearLayout? = null
    private var renderingText = false

    val values: List<String>
        get() = selectedValues.toList()

    init {
        container.orientation = LinearLayout.VERTICAL
        container.isClickable = true
        container.setOnClickListener { showPickerSheet() }
        renderField()
    }

    fun setValues(nextValues: List<String>) {
        selectedValues.clear()
        nextValues.cleanTags().forEach { addTag(it, render = false) }
        renderField()
        renderSheetContent()
    }

    fun setSuggestions(nextSuggestions: List<String>) {
        suggestions = nextSuggestions.cleanTags()
        renderSheetContent()
    }

    private fun buildInput(): EditText {
        return EditText(container.context).apply {
            background = ColorDrawable(Color.TRANSPARENT)
            hint = fieldHint
            imeOptions = EditorInfo.IME_ACTION_NONE
            inputType = InputType.TYPE_NULL
            includeFontPadding = false
            isCursorVisible = false
            isFocusable = false
            isFocusableInTouchMode = false
            minWidth = container.dp(96)
            setPadding(container.dp(8), 0, container.dp(8), 0)
            setSingleLine(true)
            setTextColor(container.context.getColorCompat(R.color.coco_title))
            setHintTextColor(container.context.getColorCompat(R.color.coco_muted))
            textSize = 15f
            setOnClickListener { showPickerSheet() }
        }
    }

    private fun showPickerSheet() {
        val existing = sheetDialog
        if (existing?.isShowing == true) {
            focusSheetSearch()
            return
        }

        val context = container.context
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setCanceledOnTouchOutside(true)
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = context.getDrawable(R.drawable.bg_form_section)
            setPadding(container.dp(16), container.dp(14), container.dp(16), container.dp(12))
        }

        root.addView(
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply {
                    bottomMargin = container.dp(10)
                }

                addView(
                    TextView(context).apply {
                        includeFontPadding = false
                        setTextColor(context.getColorCompat(R.color.coco_title))
                        text = sheetTitle()
                        textSize = 16f
                        typeface = Typeface.DEFAULT_BOLD
                    },
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                )
                addView(
                    ImageButton(context).apply {
                        background = ColorDrawable(Color.TRANSPARENT)
                        contentDescription = "Close"
                        setImageResource(R.drawable.ic_close_24)
                        setPadding(container.dp(8), container.dp(8), container.dp(8), container.dp(8))
                        setOnClickListener { dialog.dismiss() }
                    },
                    LinearLayout.LayoutParams(container.dp(40), container.dp(40))
                )
            }
        )

        val search = buildSheetSearchInput()
        sheetSearch = search
        root.addView(
            search,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                container.dp(48),
            ).apply {
                bottomMargin = container.dp(10)
            }
        )

        sheetSelectedContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                bottomMargin = container.dp(8)
            }
        }
        root.addView(sheetSelectedContainer)

        sheetSuggestionContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        root.addView(
            ScrollView(context).apply {
                isFillViewport = false
                overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
                addView(sheetSuggestionContainer)
            },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                container.dp(300),
            )
        )

        dialog.setContentView(root)
        dialog.setOnDismissListener {
            sheetDialog = null
            sheetSearch = null
            sheetSelectedContainer = null
            sheetSuggestionContainer = null
        }
        sheetDialog = dialog
        dialog.show()
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setGravity(Gravity.BOTTOM)
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        renderSheetContent()
        focusSheetSearch()
    }

    private fun buildSheetSearchInput(): EditText {
        return EditText(container.context).apply {
            background = container.context.getDrawable(R.drawable.bg_form_field)
            hint = fieldHint
            imeOptions = EditorInfo.IME_ACTION_DONE
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            includeFontPadding = false
            setSingleLine(true)
            setTextColor(container.context.getColorCompat(R.color.coco_title))
            setHintTextColor(container.context.getColorCompat(R.color.coco_muted))
            textSize = 15f
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    if (!renderingText) {
                        handleSheetTypedText(this@apply, s?.toString().orEmpty())
                    }
                }
                override fun afterTextChanged(s: Editable?) = Unit
            })
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    addSheetQuery(this)
                    true
                } else {
                    false
                }
            }
            setOnKeyListener { _, keyCode, event ->
                if (event.action == KeyEvent.ACTION_DOWN &&
                    (keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_COMMA)
                ) {
                    addSheetQuery(this)
                    true
                } else {
                    false
                }
            }
        }
    }

    private fun handleSheetTypedText(search: EditText, text: String) {
        if (!text.contains(",") && !text.contains("\n")) {
            renderSheetSuggestions()
            return
        }

        val endsWithSeparator = text.endsWith(",") || text.endsWith("\n")
        val pieces = text.split(',', '\n')
        val completed = if (endsWithSeparator) pieces else pieces.dropLast(1)
        completed.forEach { addTag(it, render = false) }
        setSearchText(search, if (endsWithSeparator) "" else pieces.lastOrNull().orEmpty())
        renderField()
        renderSheetContent()
    }

    private fun addSheetQuery(search: EditText) {
        addTag(search.text?.toString().orEmpty())
        setSearchText(search, "")
        renderSheetContent()
        focusSheetSearch()
    }

    private fun addTag(rawValue: String, render: Boolean = true) {
        val value = canonicalTag(rawValue) ?: return
        if (selectedValues.none { it.equals(value, ignoreCase = true) }) {
            selectedValues.add(value)
        }
        if (render) {
            renderField()
            renderSheetContent()
        }
    }

    private fun removeTag(value: String) {
        selectedValues.removeAll { it.equals(value, ignoreCase = true) }
        renderField()
        renderSheetContent()
        focusSheetSearch()
    }

    private fun canonicalTag(rawValue: String): String? {
        val cleanValue = rawValue.trim()
        if (cleanValue.isBlank()) {
            return null
        }
        return suggestions.firstOrNull { it.equals(cleanValue, ignoreCase = true) } ?: cleanValue
    }

    private fun renderField() {
        container.post {
            container.removeAllViews()
            val availableWidth = container.width
                .takeIf { it > 0 }
                ?.minus(container.paddingStart + container.paddingEnd)
                ?: 0
            var row = newRow()
            var rowWidth = 0
            selectedValues.forEach { value ->
                val chip = buildChip(value)
                chip.measure(
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                )
                val chipWidth = chip.measuredWidth + container.dp(8)
                if (row.childCount > 0 && availableWidth > 0 && rowWidth + chipWidth > availableWidth) {
                    container.addView(row)
                    row = newRow()
                    rowWidth = 0
                }
                row.addView(chip)
                rowWidth += chipWidth
            }

            if (input.parent != null) {
                (input.parent as ViewGroup).removeView(input)
            }
            row.addView(
                input,
                LinearLayout.LayoutParams(0, container.dp(36), 1f).apply {
                    topMargin = container.dp(6)
                    bottomMargin = container.dp(6)
                },
            )
            container.addView(row)
        }
    }

    private fun renderSheetContent() {
        renderSheetSelectedValues()
        renderSheetSuggestions()
    }

    private fun renderSheetSelectedValues() {
        val selectedContainer = sheetSelectedContainer ?: return
        selectedContainer.post {
            selectedContainer.removeAllViews()
            if (selectedValues.isEmpty()) {
                return@post
            }

            val availableWidth = selectedContainer.width
                .takeIf { it > 0 }
                ?.minus(selectedContainer.paddingStart + selectedContainer.paddingEnd)
                ?: 0
            var row = newRow()
            var rowWidth = 0
            selectedValues.forEach { value ->
                val chip = buildChip(value)
                chip.measure(
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                )
                val chipWidth = chip.measuredWidth + container.dp(8)
                if (row.childCount > 0 && availableWidth > 0 && rowWidth + chipWidth > availableWidth) {
                    selectedContainer.addView(row)
                    row = newRow()
                    rowWidth = 0
                }
                row.addView(chip)
                rowWidth += chipWidth
            }
            selectedContainer.addView(row)
        }
    }

    private fun renderSheetSuggestions() {
        val suggestionContainer = sheetSuggestionContainer ?: return
        suggestionContainer.removeAllViews()
        suggestionRows(sheetSearch?.text?.toString().orEmpty()).forEach { row ->
            suggestionContainer.addView(buildSuggestionRow(row))
        }
    }

    private fun newRow(): LinearLayout {
        return LinearLayout(container.context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            gravity = Gravity.CENTER_VERTICAL
            orientation = LinearLayout.HORIZONTAL
        }
    }

    private fun buildChip(value: String): TextView {
        return TextView(container.context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                container.dp(36),
            ).apply {
                topMargin = container.dp(6)
                marginEnd = container.dp(8)
                bottomMargin = container.dp(6)
            }
            background = container.context.getDrawable(R.drawable.bg_choice_chip_selected)
            gravity = Gravity.CENTER
            includeFontPadding = false
            setPadding(container.dp(12), 0, container.dp(10), 0)
            setTextColor(container.context.getColorCompat(R.color.coco_title))
            text = "$value  \u00d7"
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setOnClickListener { removeTag(value) }
        }
    }

    private fun suggestionRows(rawQuery: String): List<SuggestionRow> {
        val query = rawQuery.trim()
        val filtered = suggestions
            .filter { query.isBlank() || it.contains(query, ignoreCase = true) }
            .sortedWith(compareBy<String> { !it.startsWith(query, ignoreCase = true) }.thenBy { it.lowercase() })
            .take(MAX_SUGGESTIONS)
            .map { SuggestionRow.Suggestion(it) }
            .toMutableList<SuggestionRow>()

        if (query.isNotBlank() &&
            suggestions.none { it.equals(query, ignoreCase = true) } &&
            selectedValues.none { it.equals(query, ignoreCase = true) }
        ) {
            filtered.add(0, SuggestionRow.Add(query))
        }
        return filtered
    }

    private fun buildSuggestionRow(row: SuggestionRow): TextView {
        return TextView(container.context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                container.dp(44),
            )
            gravity = Gravity.CENTER_VERTICAL
            includeFontPadding = false
            setPadding(container.dp(16), 0, container.dp(16), 0)
            setTextColor(container.context.getColorCompat(R.color.coco_title))
            textSize = 15f
            when (row) {
                is SuggestionRow.Add -> {
                    text = "Add \"${row.value}\""
                    setOnClickListener {
                        addTag(row.value)
                        sheetSearch?.let { setSearchText(it, "") }
                        renderSheetContent()
                        focusSheetSearch()
                    }
                }
                is SuggestionRow.Suggestion -> {
                    val selected = selectedValues.any { it.equals(row.value, ignoreCase = true) }
                    text = if (selected) "\u2713 ${row.value}" else row.value
                    setTextColor(
                        container.context.getColorCompat(
                            if (selected) R.color.coco_primary else R.color.coco_title
                        )
                    )
                    setOnClickListener {
                        if (selected) {
                            removeTag(row.value)
                        } else {
                            addTag(row.value)
                        }
                        sheetSearch?.let { setSearchText(it, "") }
                        renderSheetContent()
                        focusSheetSearch()
                    }
                }
            }
        }
    }

    private fun setSearchText(search: EditText, value: String) {
        renderingText = true
        search.setText(value)
        search.setSelection(search.text?.length ?: 0)
        renderingText = false
    }

    private fun focusSheetSearch() {
        val search = sheetSearch ?: return
        search.post {
            search.requestFocus()
            search.setSelection(search.text?.length ?: 0)
            val inputManager = container.context.getSystemService(InputMethodManager::class.java)
            inputManager?.showSoftInput(search, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun sheetTitle(): String {
        val label = fieldHint?.trim()
        return if (label == null || label.isEmpty()) "Destinations" else label
    }

    private fun List<String>?.cleanTags(): List<String> {
        return orEmpty()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
    }

    private sealed class SuggestionRow {
        data class Suggestion(val value: String) : SuggestionRow()
        data class Add(val value: String) : SuggestionRow()
    }

    companion object {
        private const val MAX_SUGGESTIONS = 48

        fun bind(
            container: LinearLayout,
            suggestions: List<String>?,
            hint: String? = "",
        ): CocoCreatableTagField {
            return CocoCreatableTagField(container, suggestions, hint)
        }
    }
}
