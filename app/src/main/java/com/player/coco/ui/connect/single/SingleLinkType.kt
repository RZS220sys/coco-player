package com.player.coco.ui.connect.single

data class SingleLinkType(
    val id: String,
    val sections: List<SingleLinkSection>,
)

data class SingleLinkSection(
    val id: String,
    val title: String,
    val fields: List<SingleLinkField>,
    val visibleWhen: FormCondition = FormCondition.Always,
)

sealed interface FormCondition {
    object Always : FormCondition
    data class Equals(val sectionId: String, val fieldKey: String, val value: String) : FormCondition
    data class In(val sectionId: String, val fieldKey: String, val values: Set<String>) : FormCondition
}

sealed class SingleLinkField(
    val key: String,
    val label: String,
    val defaultValue: String = "",
    val required: Boolean = false,
) {
    class Text(
        key: String,
        label: String,
        defaultValue: String = "",
        required: Boolean = false,
        val input: TextInput = TextInput.TEXT,
    ) : SingleLinkField(key, label, defaultValue, required)

    class Select(
        key: String,
        label: String,
        val options: List<String>,
        defaultValue: String = options.firstOrNull().orEmpty(),
        required: Boolean = false,
    ) : SingleLinkField(key, label, defaultValue, required)

    class Switch(
        key: String,
        label: String,
        val defaultChecked: Boolean = false,
    ) : SingleLinkField(key, label, defaultChecked.toString(), required = false)
}

enum class TextInput {
    TEXT,
    NUMBER,
    PASSWORD,
}
