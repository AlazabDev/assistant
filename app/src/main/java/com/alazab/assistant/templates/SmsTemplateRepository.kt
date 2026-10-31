package com.alazab.assistant.templates

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Repository to provide and persist built-in and user-customized SMS templates.
 */
class SmsTemplateRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences("azab_sms_templates_prefs", Context.MODE_PRIVATE)
    private val PREF_CUSTOM_TEMPLATES_KEY = "custom_sms_templates_json"

    private val _templates = MutableStateFlow<List<SmsTemplate>>(emptyList())
    val templates: StateFlow<List<SmsTemplate>> = _templates.asStateFlow()

    init {
        loadTemplates()
    }

    fun loadTemplates() {
        val builtIn = SmsTemplateEngine.getDefaultEnterpriseTemplates()
        val custom = loadCustomTemplatesFromPrefs()
        _templates.value = builtIn + custom
    }

    private fun loadCustomTemplatesFromPrefs(): List<SmsTemplate> {
        val jsonString = prefs.getString(PREF_CUSTOM_TEMPLATES_KEY, null) ?: return emptyList()
        val list = mutableListOf<SmsTemplate>()
        try {
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val id = obj.getString("id")
                val title = obj.getString("title")
                val categoryName = obj.optString("category", SmsTemplateCategory.ALL.name)
                val category = try {
                    SmsTemplateCategory.valueOf(categoryName)
                } catch (e: Exception) {
                    SmsTemplateCategory.ALL
                }
                val rawBody = obj.getString("rawBody")
                val entityId = obj.optString("entityIdDefault", "CUSTOM_SMS")
                val variables = SmsTemplateEngine.extractVariables(rawBody)

                list.add(
                    SmsTemplate(
                        id = id,
                        title = title,
                        category = category,
                        rawBody = rawBody,
                        entityIdDefault = entityId,
                        isBuiltIn = false,
                        variables = variables
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    private fun saveCustomTemplatesToPrefs(customList: List<SmsTemplate>) {
        val jsonArray = JSONArray()
        for (item in customList) {
            val obj = JSONObject().apply {
                put("id", item.id)
                put("title", item.title)
                put("category", item.category.name)
                put("rawBody", item.rawBody)
                put("entityIdDefault", item.entityIdDefault)
            }
            jsonArray.put(obj)
        }
        prefs.edit().putString(PREF_CUSTOM_TEMPLATES_KEY, jsonArray.toString()).apply()
    }

    fun addCustomTemplate(
        title: String,
        category: SmsTemplateCategory,
        rawBody: String,
        entityId: String
    ): SmsTemplate {
        val newId = "custom_${System.currentTimeMillis()}"
        val template = SmsTemplate(
            id = newId,
            title = title.trim(),
            category = category,
            rawBody = rawBody.trim(),
            entityIdDefault = if (entityId.isNotBlank()) entityId.trim() else "CUSTOM_SMS",
            isBuiltIn = false,
            variables = SmsTemplateEngine.extractVariables(rawBody)
        )

        val currentCustom = loadCustomTemplatesFromPrefs().toMutableList()
        currentCustom.add(template)
        saveCustomTemplatesToPrefs(currentCustom)

        loadTemplates()
        return template
    }

    fun updateCustomTemplate(template: SmsTemplate) {
        val currentCustom = loadCustomTemplatesFromPrefs().toMutableList()
        val index = currentCustom.indexOfFirst { it.id == template.id }
        if (index != -1) {
            val updated = template.copy(
                variables = SmsTemplateEngine.extractVariables(template.rawBody)
            )
            currentCustom[index] = updated
            saveCustomTemplatesToPrefs(currentCustom)
            loadTemplates()
        }
    }

    fun deleteCustomTemplate(templateId: String) {
        val currentCustom = loadCustomTemplatesFromPrefs().toMutableList()
        val filtered = currentCustom.filter { it.id != templateId }
        saveCustomTemplatesToPrefs(filtered)
        loadTemplates()
    }

    fun resetToDefaults() {
        prefs.edit().remove(PREF_CUSTOM_TEMPLATES_KEY).apply()
        loadTemplates()
    }
}
