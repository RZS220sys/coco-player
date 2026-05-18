package com.player.coco.ui.widget

import com.player.coco.R
import com.player.coco.ui.dp
import com.player.coco.ui.getColorCompat

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView

class CocoMultiChoiceField private constructor(
    private val container: LinearLayout,
    private val options: List<String>,
) {
    private val chips = linkedMapOf<String, TextView>()
    private val selectedValues = linkedSetOf<String>()

    val values: List<String>
        get() = selectedValues.toList()

    init {
        container.orientation = LinearLayout.VERTICAL
        renderChips(options)
    }

    fun setValues(nextValues: List<String>) {
        val cleanValues = nextValues.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        val unknownValues = cleanValues.filter { it !in chips }
        if (unknownValues.isNotEmpty()) {
            renderChips(options + unknownValues)
        }

        selectedValues.clear()
        selectedValues.addAll(cleanValues)
        renderChipStates()
    }

    private fun renderChips(values: List<String>) {
        container.post {
            container.removeAllViews()
            chips.clear()
            val availableWidth = container.width
                .takeIf { it > 0 }
                ?.minus(container.paddingStart + container.paddingEnd)
                ?: ViewGroup.LayoutParams.WRAP_CONTENT
            var row = newRow()
            var rowWidth = 0
            values.distinct().forEach { value ->
                val cleanValue = value.trim()
                if (cleanValue.isBlank()) {
                    return@forEach
                }

                val chip = buildChip(cleanValue)
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
                chips[cleanValue] = chip
                rowWidth += chipWidth
            }
            if (row.childCount > 0) {
                container.addView(row)
            }
            renderChipStates()
        }
    }

    private fun newRow(): LinearLayout {
        return LinearLayout(container.context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
    }

    private fun buildChip(value: String): TextView {
        return TextView(container.context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                container.dp(36),
            ).apply {
                topMargin = container.dp(4)
                marginEnd = container.dp(8)
                bottomMargin = container.dp(8)
            }
            isClickable = true
            isFocusable = true
            gravity = Gravity.CENTER_VERTICAL
            includeFontPadding = false
            minWidth = container.dp(58)
            setPadding(container.dp(12), 0, container.dp(12), 0)
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            textSize = 15f
            setOnClickListener {
                if (!selectedValues.add(value)) {
                    selectedValues.remove(value)
                }
                renderChipStates()
            }
        }
    }

    private fun renderChipStates() {
        chips.forEach { (value, chip) ->
            val selected = value in selectedValues
            chip.setBackgroundResource(if (selected) R.drawable.bg_choice_chip_selected else R.drawable.bg_choice_chip)
            chip.setTextColor(container.context.getColorCompat(if (selected) R.color.coco_title else R.color.coco_title))
            chip.text = if (selected) "\u2713 $value" else value
        }
    }

    companion object {
        fun bind(container: LinearLayout, options: List<String>): CocoMultiChoiceField {
            return CocoMultiChoiceField(container, options)
        }
    }
}
