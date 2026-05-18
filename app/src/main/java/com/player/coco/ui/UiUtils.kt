package com.player.coco.ui

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.View

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
