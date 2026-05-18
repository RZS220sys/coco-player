package com.player.coco.ui.connect

import android.app.Activity
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import com.player.coco.R
import com.player.coco.data.config.ConnectConfigStore
import com.player.coco.data.config.ConnectConfigTypes
import com.player.coco.data.config.singlelink.SingleLinkConfigDataMapper
import com.player.coco.data.config.singlelink.SingleLinkDraft
import com.player.coco.ui.dp
import com.player.coco.ui.connect.single.SingleLinkField
import com.player.coco.ui.connect.single.SingleLinkSection
import com.player.coco.ui.connect.single.SingleLinkType
import com.player.coco.ui.connect.single.SingleLinkTypeRegistry
import com.player.coco.ui.connect.single.TextInput
import com.player.coco.ui.getColorCompat
import com.player.coco.ui.widget.CocoSelectField
import com.player.coco.xray.single.SingleLinkEndpoint
import com.player.coco.xray.single.SingleLinkOutboundBuilder
import org.json.JSONObject

class SingleLinkFormActivity : Activity() {
    private lateinit var store: ConnectConfigStore
    private lateinit var nameInput: EditText
    private lateinit var typeSelect: CocoSelectField
    private lateinit var fieldsContainer: LinearLayout
    private val inputsByKey = linkedMapOf<String, View>()
    private val selectsByKey = linkedMapOf<String, CocoSelectField>()
    private var configId = NO_CONFIG_ID
    private var loading = false
    private var editSettings = JSONObject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_single_link_form)

        store = ConnectConfigStore(filesDir)
        configId = intent.getLongExtra(EXTRA_CONFIG_ID, NO_CONFIG_ID)
        nameInput = findViewById(R.id.config_name_input)
        fieldsContainer = findViewById(R.id.protocol_fields_container)
        typeSelect = CocoSelectField.bind(
            field = findViewById(R.id.config_type_select),
            options = SingleLinkTypeRegistry.ids(),
            onChanged = {
                if (!loading) {
                    renderProtocolFields(collectValues(validate = false) ?: JSONObject())
                }
            }
        )

        findViewById<ImageButton>(R.id.back_button).setOnClickListener {
            finish()
        }
        findViewById<TextView>(R.id.save_button).setOnClickListener {
            saveSingleLink()
        }
        renderTitle()

        if (isEditMode()) {
            loadExistingSingleLink()
        } else {
            renderProtocolFields(JSONObject())
        }
    }

    private fun loadExistingSingleLink() {
        val config = SingleLinkConfigDataMapper.fromContainer(store.load(configId))
        if (config == null) {
            Toast.makeText(this, getString(R.string.config_not_found), Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        loading = true
        nameInput.setText(config.name)
        typeSelect.setValue(config.protocol.ifBlank { SingleLinkTypeRegistry.ids().first() })
        editSettings = config.settings
        renderProtocolFields(config.values)
        loading = false
    }

    private fun saveSingleLink() {
        val draft = buildDraftOrNull() ?: return
        if (isEditMode()) {
            store.saveExisting(configId, ConnectConfigTypes.SINGLE_LINK, SingleLinkConfigDataMapper.toJson(draft, existingCreatedAtMillis()))
            Toast.makeText(this, getString(R.string.single_link_updated), Toast.LENGTH_SHORT).show()
        } else {
            store.saveNew(ConnectConfigTypes.SINGLE_LINK, SingleLinkConfigDataMapper.toJson(draft))
            Toast.makeText(this, getString(R.string.single_link_saved), Toast.LENGTH_SHORT).show()
        }
        finish()
    }

    private fun buildDraftOrNull(): SingleLinkDraft? {
        val name = nameInput.text?.toString().orEmpty().trim()
        if (name.isBlank()) {
            nameInput.error = getString(R.string.error_config_name_required)
            nameInput.requestFocus()
            return null
        }
        nameInput.error = null

        val protocol = typeSelect.value
        val values = collectValues(validate = true) ?: return null
        try {
            SingleLinkOutboundBuilder.build(protocol, values)
        } catch (error: Exception) {
            Toast.makeText(this, error.message.orEmpty().ifBlank { "Invalid single link." }, Toast.LENGTH_SHORT).show()
            return null
        }

        return SingleLinkDraft(
            name = name,
            protocol = protocol,
            values = values,
            endpoint = SingleLinkEndpoint.fromValues(values),
            settings = editSettings,
        )
    }

    private fun renderProtocolFields(seedValues: JSONObject) {
        val type = SingleLinkTypeRegistry.byId(typeSelect.value)
        val values = seedValues
        inputsByKey.clear()
        selectsByKey.clear()
        fieldsContainer.removeAllViews()

        SingleLinkTypeRegistry.visibleSections(type, values).forEach { section ->
            fieldsContainer.addView(buildSection(section, values))
        }
    }

    private fun buildSection(section: SingleLinkSection, values: JSONObject): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(dp(14), dp(12), dp(14), 0)
            }
            setBackgroundResource(R.drawable.bg_form_section)
            setPadding(dp(14), dp(14), dp(14), dp(14))
            addView(buildSectionTitle(section.title))
            section.fields.forEach { field ->
                if (field !is SingleLinkField.Switch) {
                    addView(buildFieldLabel(field.label))
                }
                addView(buildFieldView(section, field, fieldValue(values, section, field)))
            }
        }
    }

    private fun buildSectionTitle(title: String): TextView {
        return TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            includeFontPadding = false
            setPadding(0, 0, 0, dp(10))
            setTextColor(getColorCompat(R.color.coco_title))
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            text = title
        }
    }

    private fun buildFieldLabel(label: String): TextView {
        return TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            includeFontPadding = false
            setPadding(0, dp(8), 0, dp(6))
            setTextColor(getColorCompat(R.color.coco_body))
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            text = label
        }
    }

    private fun buildFieldView(section: SingleLinkSection, field: SingleLinkField, value: String): View {
        return when (field) {
            is SingleLinkField.Text -> buildTextField(field, value)
            is SingleLinkField.Select -> buildSelectField(section, field, value)
            is SingleLinkField.Switch -> buildSwitchField(field, value)
        }.also { view ->
            inputsByKey[fieldId(section, field)] = view
        }
    }

    private fun buildTextField(field: SingleLinkField.Text, value: String): EditText {
        return EditText(this).apply {
            applyFormFieldChrome()
            setText(value)
            inputType = when (field.input) {
                TextInput.NUMBER -> InputType.TYPE_CLASS_NUMBER
                TextInput.PASSWORD -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                TextInput.TEXT -> InputType.TYPE_CLASS_TEXT
            }
        }
    }

    private fun buildSelectField(section: SingleLinkSection, field: SingleLinkField.Select, value: String): TextView {
        return TextView(this).also { view ->
            view.applyFormFieldChrome()
            view.gravity = Gravity.CENTER_VERTICAL
            view.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_arrow_drop_down_24, 0)
            view.compoundDrawablePadding = dp(8)
            val select = CocoSelectField.bind(view, field.options) {
                if (!loading) {
                    renderProtocolFields(collectValues(validate = false) ?: JSONObject())
                }
            }
            select.setValue(value)
            selectsByKey[fieldId(section, field)] = select
        }
    }

    private fun buildSwitchField(field: SingleLinkField.Switch, value: String): Switch {
        return Switch(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(48)
            )
            setPadding(0, dp(6), 0, 0)
            setTextColor(getColorCompat(R.color.coco_body))
            textSize = 14f
            text = field.label
            isChecked = value.toBooleanStrictOrNull() ?: field.defaultChecked
            setOnCheckedChangeListener { _, _ ->
                if (!loading) {
                    renderProtocolFields(collectValues(validate = false) ?: JSONObject())
                }
            }
        }
    }

    private fun TextView.applyFormFieldChrome() {
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(48)
        )
        setBackgroundResource(R.drawable.bg_form_field)
        includeFontPadding = false
        setPadding(dp(12), 0, dp(12), 0)
        setTextColor(getColorCompat(R.color.coco_title))
        setHintTextColor(getColorCompat(R.color.coco_muted))
        textSize = 15f
        gravity = Gravity.CENTER_VERTICAL
        isSingleLine = true
    }

    private fun collectValues(validate: Boolean): JSONObject? {
        val type = SingleLinkTypeRegistry.byId(typeSelect.value)
        val values = JSONObject()
        var firstInvalid: EditText? = null

        type.sections.filter { section -> isSectionRendered(section) }.forEach { section ->
            val sectionValues = JSONObject()
            section.fields.forEach { field ->
                when (val view = inputsByKey[fieldId(section, field)]) {
                    is EditText -> {
                        val text = view.text?.toString().orEmpty().trim()
                        if (validate && field.required && text.isBlank()) {
                            view.error = "${field.label} is required."
                            if (firstInvalid == null) {
                                firstInvalid = view
                            }
                        } else {
                            view.error = null
                        }
                        if (text.isNotBlank()) {
                            sectionValues.put(field.key, text)
                        }
                    }
                    is Switch -> sectionValues.put(field.key, view.isChecked)
                    is TextView -> selectsByKey[fieldId(section, field)]?.value?.let { value ->
                        if (value.isNotBlank()) {
                            sectionValues.put(field.key, value)
                        }
                    }
                }
            }
            if (sectionValues.length() > 0) {
                values.put(section.id, sectionValues)
            }
        }

        firstInvalid?.let {
            it.requestFocus()
            return null
        }
        return values
    }

    private fun fieldValue(values: JSONObject, section: SingleLinkSection, field: SingleLinkField): String {
        val sectionValues = values.optJSONObject(section.id)
        if (sectionValues != null && sectionValues.has(field.key)) {
            return sectionValues.opt(field.key)?.toString().orEmpty()
        }
        return field.defaultValue
    }

    private fun isSectionRendered(section: SingleLinkSection): Boolean {
        return section.fields.any { field -> inputsByKey.containsKey(fieldId(section, field)) }
    }

    private fun fieldId(section: SingleLinkSection, field: SingleLinkField): String {
        return "${section.id}.${field.key}"
    }

    private fun renderTitle() {
        findViewById<TextView>(R.id.title_text).text = getString(
            if (isEditMode()) R.string.screen_edit_single_link_title
            else R.string.screen_add_single_link_title
        )
    }

    private fun isEditMode(): Boolean = configId != NO_CONFIG_ID

    private fun existingCreatedAtMillis(): Long {
        return SingleLinkConfigDataMapper.fromContainer(store.load(configId))?.createdAtMillis
            ?: System.currentTimeMillis()
    }

    companion object {
        const val EXTRA_CONFIG_ID = "com.player.coco.EXTRA_CONFIG_ID"
        private const val NO_CONFIG_ID = -1L
    }
}
