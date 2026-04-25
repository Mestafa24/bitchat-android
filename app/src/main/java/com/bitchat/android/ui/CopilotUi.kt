package com.bitchat.android.ui

import org.json.JSONArray
import org.json.JSONObject

const val COPILOT_PREFIX: String = "[COPILOT]"

data class CopilotEnvelope(
    val kind: String,
    val stage: String,
    val text: String,
    val choices: List<String> = emptyList(),
    val severity: String? = null,
    val incidentId: Int? = null
)

fun parseCopilotEnvelope(raw: String): CopilotEnvelope? {
    if (raw.startsWith(COPILOT_PREFIX)) {
        return try {
            val obj = JSONObject(raw.removePrefix(COPILOT_PREFIX))
            val choices = mutableListOf<String>()
            val arr = obj.optJSONArray("choices") ?: JSONArray()
            for (i in 0 until arr.length()) {
                choices.add(arr.optString(i))
            }
            CopilotEnvelope(
                kind = obj.optString("kind", "message"),
                stage = obj.optString("stage", "unknown"),
                text = obj.optString("text", raw),
                choices = choices.filter { it.isNotBlank() },
                severity = obj.optString("severity").takeIf { it.isNotBlank() },
                incidentId = if (obj.has("incident_id")) obj.optInt("incident_id") else null
            )
        } catch (_: Exception) {
            null
        }
    }

    if (raw.startsWith("Safety Copilot:", ignoreCase = true)) {
        val choices = extractLegacyChoices(raw)
        return CopilotEnvelope(
            kind = "legacy",
            stage = "unknown",
            text = raw,
            choices = choices
        )
    }

    return null
}

fun isCopilotMessage(raw: String): Boolean = parseCopilotEnvelope(raw) != null

private fun extractLegacyChoices(text: String): List<String> {
    val lower = text.lowercase()
    val marker = "reply with one word:"
    val idx = lower.indexOf(marker)
    if (idx == -1) return emptyList()
    val part = text.substring(idx + marker.length)
    return part.split("/", ",")
        .map { it.trim() }
        .filter { it.isNotBlank() }
}
