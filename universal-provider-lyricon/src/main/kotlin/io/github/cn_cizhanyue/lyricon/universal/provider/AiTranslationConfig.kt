package io.github.cn_cizhanyue.lyricon.universal.provider

import org.json.JSONObject

internal data class AiTranslationConfig(
    val enabled: Boolean,
    val endpoint: String,
    val apiKey: String,
    val model: String,
    val target: String,
) {
    fun toJson(): String = JSONObject().apply {
        put("enabled", enabled)
        put("endpoint", endpoint)
        put("apiKey", apiKey)
        put("model", model)
        put("target", target)
    }.toString()

    companion object {
        val DEFAULT = AiTranslationConfig(
            enabled = false,
            endpoint = "https://api.openai.com/v1",
            apiKey = "",
            model = "gpt-4o-mini",
            target = "中文",
        )

        fun fromJson(raw: String): AiTranslationConfig? = runCatching {
            val json = JSONObject(raw)
            AiTranslationConfig(
                enabled = json.optBoolean("enabled", false),
                endpoint = json.optString("endpoint").ifBlank { DEFAULT.endpoint },
                apiKey = json.optString("apiKey"),
                model = json.optString("model").ifBlank { DEFAULT.model },
                target = json.optString("target").ifBlank { DEFAULT.target },
            )
        }.getOrNull()
    }
}
