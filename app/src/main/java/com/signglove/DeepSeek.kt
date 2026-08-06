package com.signglove

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** 调 DeepSeek 把手势词序列组合成通顺中文句子 (同 PC server.py:deepseek_combine)。 */
object DeepSeek {
    private val client = OkHttpClient.Builder()
        .callTimeout(12, TimeUnit.SECONDS)
        .build()

    private const val SYS =
        "你是手语翻译助手。用户给一串手语识别出的手势词(空格分隔)。" +
        "请组合成一句通顺、自然、口语化的中文句子, 补全虚词、时态和标点。" +
        "只输出句子本身, 不要解释、不要引号、不要多余内容。"

    /** 同步调用 (放后台线程)。失败返回 null。 */
    fun combine(words: List<String>, key: String, model: String, url: String): String? {
        if (key.isBlank()) return null
        val messages = JSONArray()
            .put(JSONObject().put("role", "system").put("content", SYS))
            .put(JSONObject().put("role", "user").put("content", "手势词序列: " + words.joinToString(" ")))
        return chat(messages, 0.3, key, model, url)
    }

    /**
     * 一次生成 count 个语义不同的候选句 (多候选模式用)。
     * 每句单独一行返回; 失败或解析不出返回 null。
     */
    fun combineAll(words: List<String>, count: Int, key: String, model: String, url: String): List<String>? {
        if (key.isBlank() || count <= 1) return null
        val sys = "你是手语翻译助手。用户给一串手语识别出的手势词(空格分隔)。" +
            "请给出 $count 个语义明显不同的通顺、自然、口语化中文句子, 补全虚词、时态和标点。" +
            "每个句子单独占一行。不要编号、不要解释、不要引号、不要多余内容。"
        val messages = JSONArray()
            .put(JSONObject().put("role", "system").put("content", sys))
            .put(JSONObject().put("role", "user").put("content", "手势词序列: ${words.joinToString(" ")}\n请生成 $count 个语义不同的中文句子, 每句一行。"))
        val raw = chat(messages, 0.7, key, model, url) ?: return null
        val list = raw.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { stripNumberPrefix(it) }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(count)
        return list.ifEmpty { null }
    }

    /** 剥离行首编号 "1."/"2、" 之类, 保留句子正文。 */
    private fun stripNumberPrefix(line: String): String =
        line.replaceFirst(Regex("^\\s*\\d+[.、)．]\\s*"), "")

    private fun chat(messages: JSONArray, temperature: Double, key: String, model: String, url: String): String? {
        return try {
            val payload = JSONObject()
                .put("model", model)
                .put("messages", messages)
                .put("temperature", temperature)
                .put("stream", false)
            val req = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $key")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body?.string() ?: return null
                JSONObject(body).getJSONArray("choices").getJSONObject(0)
                    .getJSONObject("message").getString("content").trim()
            }
        } catch (e: Exception) {
            null
        }
    }
}
