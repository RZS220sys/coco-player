package com.player.coco.ui.connect.single

import com.player.coco.ui.connect.single.protocol.HttpSingleLinkType
import com.player.coco.ui.connect.single.protocol.ShadowsocksSingleLinkType
import com.player.coco.ui.connect.single.protocol.SocksSingleLinkType
import com.player.coco.ui.connect.single.protocol.TrojanSingleLinkType
import com.player.coco.ui.connect.single.protocol.VlessSingleLinkType
import com.player.coco.ui.connect.single.protocol.VmessSingleLinkType
import org.json.JSONObject

object SingleLinkTypeRegistry {
    val all: List<SingleLinkType> = listOf(
        VlessSingleLinkType.type,
        VmessSingleLinkType.type,
        TrojanSingleLinkType.type,
        ShadowsocksSingleLinkType.type,
        SocksSingleLinkType.type,
        HttpSingleLinkType.type,
    )

    fun ids(): List<String> = all.map { it.id }

    fun byId(id: String): SingleLinkType {
        return all.firstOrNull { it.id == id } ?: all.first()
    }

    fun allFields(type: SingleLinkType): List<SingleLinkField> {
        return type.sections.flatMap { it.fields }
    }

    fun visibleSections(type: SingleLinkType, values: JSONObject): List<SingleLinkSection> {
        return type.sections.filter { section -> section.visibleWhen.matches(type, values) }
    }

    fun fieldDefault(type: SingleLinkType, sectionId: String, fieldKey: String): String {
        return type.sections
            .firstOrNull { it.id == sectionId }
            ?.fields
            ?.firstOrNull { it.key == fieldKey }
            ?.defaultValue
            .orEmpty()
    }

    private fun FormCondition.matches(type: SingleLinkType, values: JSONObject): Boolean {
        return when (this) {
            FormCondition.Always -> true
            is FormCondition.Equals -> valueAt(type, values, sectionId, fieldKey) == value
            is FormCondition.In -> valueAt(type, values, sectionId, fieldKey) in this.values
        }
    }

    private fun valueAt(type: SingleLinkType, values: JSONObject, sectionId: String, fieldKey: String): String {
        val section = values.optJSONObject(sectionId)
        if (section != null && section.has(fieldKey)) {
            return section.optString(fieldKey)
        }
        return fieldDefault(type, sectionId, fieldKey)
    }
}
