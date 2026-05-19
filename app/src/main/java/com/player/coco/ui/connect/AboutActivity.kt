package com.player.coco.ui.connect

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.player.coco.BuildConfig
import com.player.coco.R
import com.player.coco.ui.dp
import com.player.coco.ui.getColorCompat

class AboutActivity : Activity() {
    private lateinit var sections: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)

        sections = findViewById(R.id.about_sections)
        findViewById<View>(R.id.back_button).setOnClickListener {
            finish()
        }

        renderSections()
    }

    private fun renderSections() {
        sections.removeAllViews()
        addSection(
            title = getString(R.string.about_section_app),
            rows = listOf(
                getString(R.string.about_version_label) to "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                getString(R.string.about_build_date_label) to BuildConfig.BUILD_DATE,
            ),
        )
        addSection(
            title = getString(R.string.about_license_section),
            rows = listOf(
                getString(R.string.about_license_name) to getString(R.string.about_license_text),
            ),
        )
        addSection(
            title = getString(R.string.about_more_section),
            rows = listOf(
                "" to getString(R.string.about_more_text),
            ),
        )
    }

    private fun addSection(title: String, rows: List<Pair<String, String>>) {
        val section = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = getDrawable(R.drawable.bg_form_section)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                setMargins(dp(14), dp(12), dp(14), 0)
            }
        }

        section.addView(sectionTitle(title))
        rows.forEach { (label, value) ->
            section.addView(infoRow(label, value))
        }
        sections.addView(section)
    }

    private fun sectionTitle(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            includeFontPadding = false
            setTextColor(getColorCompat(R.color.coco_title))
            textSize = 18f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, dp(10))
        }
    }

    private fun infoRow(label: String, value: String): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(6), 0, dp(8))
            if (label.isNotBlank()) {
                addView(TextView(context).apply {
                    text = label
                    includeFontPadding = false
                    setTextColor(getColorCompat(R.color.coco_muted))
                    textSize = 12f
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                })
            }
            addView(TextView(context).apply {
                text = value
                includeFontPadding = false
                setTextColor(getColorCompat(R.color.coco_body))
                textSize = 14f
                setLineSpacing(dp(2).toFloat(), 1.0f)
            })
        }
    }
}
