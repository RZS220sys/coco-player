package com.player.coco.ui

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.PopupWindow

fun Context.dp(value: Int): Int {
    return (value * resources.displayMetrics.density).toInt()
}

fun View.dp(value: Int): Int {
    return context.dp(value)
}

fun Context.getColorCompat(colorRes: Int): Int {
    return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
        getColor(colorRes)
    } else {
        resources.getColor(colorRes)
    }
}

fun Context.getDrawableCompat(drawableRes: Int): Drawable? {
    return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
        getDrawable(drawableRes)
    } else {
        resources.getDrawable(drawableRes)
    }
}

fun Context.obtainSelectableItemBackground(): Drawable? {
    val attrs = intArrayOf(android.R.attr.selectableItemBackground)
    val typedArray = obtainStyledAttributes(attrs)
    return try {
        typedArray.getDrawable(0)
    } finally {
        typedArray.recycle()
    }
}

fun View.hideKeyboard() {
    clearFocus()
    val inputMethodManager = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
    inputMethodManager?.hideSoftInputFromWindow(windowToken, 0)
}

fun PopupWindow.showAnchoredTo(anchor: View, content: View, widthPx: Int, marginPx: Int = anchor.dp(8)) {
    val root = anchor.rootView
    if (root.width <= 0 || root.height <= 0) {
        showAsDropDown(anchor, -widthPx + anchor.width, -anchor.height)
        return
    }

    content.measure(
        View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
    )
    val popupHeight = content.measuredHeight.takeIf { it > 0 } ?: anchor.height
    val anchorLocation = IntArray(2)
    val rootLocation = IntArray(2)
    anchor.getLocationInWindow(anchorLocation)
    root.getLocationInWindow(rootLocation)

    val anchorLeft = anchorLocation[0] - rootLocation[0]
    val anchorTop = anchorLocation[1] - rootLocation[1]
    val maxX = (root.width - widthPx - marginPx).coerceAtLeast(marginPx)
    val x = (anchorLeft + anchor.width - widthPx).coerceIn(marginPx, maxX)
    val topAlignedY = anchorTop
    val aboveAlignedY = anchorTop + anchor.height - popupHeight
    val roomBelow = root.height - topAlignedY - marginPx
    val roomAbove = anchorTop + anchor.height - marginPx
    val y = if (popupHeight <= roomBelow || roomBelow >= roomAbove) {
        topAlignedY.coerceAtMost((root.height - popupHeight - marginPx).coerceAtLeast(marginPx))
    } else {
        aboveAlignedY.coerceAtLeast(marginPx)
    }

    showAtLocation(root, Gravity.START or Gravity.TOP, x, y)
}
