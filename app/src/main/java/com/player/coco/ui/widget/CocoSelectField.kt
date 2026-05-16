package com.player.coco.ui.widget

import android.graphics.drawable.ColorDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.TextView
import com.player.coco.R

class CocoSelectField private constructor(
    private val field: TextView,
    private val options: List<String>,
    initialValue: String,
    var onChanged: (String) -> Unit,
) {
    var value: String = normalizeValue(initialValue)
        private set
    private var popup: PopupWindow? = null

    init {
        renderValue()
        field.isClickable = true
        field.isFocusable = true
        field.setOnClickListener {
            showPopup()
        }
    }

    fun setValue(nextValue: String, notify: Boolean = false) {
        val normalized = normalizeValue(nextValue)
        if (value == normalized) {
            renderValue()
            return
        }

        value = normalized
        renderValue()
        if (notify) {
            onChanged(value)
        }
    }

    private fun showPopup() {
        if (options.isEmpty()) {
            return
        }

        popup?.dismiss()
        val content = ScrollView(field.context).apply {
            isFillViewport = false
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            background = field.context.getDrawableCompat(R.drawable.bg_config_action_panel)
            addView(buildRows())
        }
        val width = field.width.coerceAtLeast(dp(180))
        val height = ((options.size * dp(48)) + dp(12)).coerceAtMost(dp(260))
        popup = PopupWindow(content, width, height, true).apply {
            isOutsideTouchable = true
            elevation = dp(8).toFloat()
            setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
            showAsDropDown(field, 0, -dp(2))
        }
    }

    private fun buildRows(): LinearLayout {
        return LinearLayout(field.context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(6), 0, dp(6))
            options.forEach { option ->
                addView(buildRow(option))
            }
        }
    }

    private fun buildRow(option: String): TextView {
        return TextView(field.context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(48)
            )
            background = field.context.obtainSelectableItemBackground()
            gravity = android.view.Gravity.CENTER_VERTICAL
            includeFontPadding = false
            setPadding(dp(16), 0, dp(16), 0)
            setTextColor(field.context.getColorCompat(if (option == value) R.color.coco_primary else R.color.coco_title))
            text = option
            textSize = 15f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setOnClickListener {
                popup?.dismiss()
                popup = null
                setValue(option, notify = true)
            }
        }
    }

    private fun renderValue() {
        field.text = value
    }

    private fun normalizeValue(nextValue: String): String {
        return if (nextValue in options) {
            nextValue
        } else {
            options.firstOrNull().orEmpty()
        }
    }

    private fun dp(value: Int): Int = (value * field.resources.displayMetrics.density).toInt()

    companion object {
        fun bind(
            field: TextView,
            options: List<String>,
            initialValue: String = options.firstOrNull().orEmpty(),
            onChanged: (String) -> Unit = {},
        ): CocoSelectField {
            return CocoSelectField(field, options, initialValue, onChanged)
        }
    }
}

private fun android.content.Context.getColorCompat(colorRes: Int): Int {
    return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
        getColor(colorRes)
    } else {
        resources.getColor(colorRes)
    }
}

private fun android.content.Context.getDrawableCompat(drawableRes: Int): android.graphics.drawable.Drawable? {
    return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
        getDrawable(drawableRes)
    } else {
        resources.getDrawable(drawableRes)
    }
}

private fun android.content.Context.obtainSelectableItemBackground(): android.graphics.drawable.Drawable? {
    val attrs = intArrayOf(android.R.attr.selectableItemBackground)
    val typedArray = obtainStyledAttributes(attrs)
    return try {
        typedArray.getDrawable(0)
    } finally {
        typedArray.recycle()
    }
}
