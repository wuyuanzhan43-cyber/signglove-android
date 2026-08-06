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

    /** 同步调用 (放后台线程)。失败返回 null。 */
    fun combine(words: List<String>, key: String, model: String, url: String, prompt: String): String? {
        if (key.isBlank()) return null
        val messages = JSONArray()
            .put(JSONObject().put("role", "system").put("content", prompt))
            .put(JSONObject().put("role", "user").put("content", "手势词序列: " + words.joinToString(" ")))
        return chat(messages, 0.3, key, model, url)
    }

    /**
     * 一次生成 count 个语义明显不同的候选句 (多候选模式用)。
     * 每句单独一行返回; 失败或解析不出返回 null。
     */
    fun combineAll(words: List<String>, count: Int, key: String, model: String, url: String): List<String>? {
        if (key.isBlank() || count <= 1) return null
        val sys = "你是专业的中文手语语义补全与翻译助手。用户输入的是手语设备按识别顺序得到的中文关键词，内容可能缺少主语、谓语、宾语、助词或必要上下文。" +
            "请给出 $count 个语义明显不同、但都符合常见表达的中文句子，补全缺失成分、调整语序并添加标点。" +
            "必须保留每个输入关键词的核心含义，不得遗漏、替换或曲解；不得凭空添加具体人名、地点、时间、数字、疾病名称。" +
            "输入中出现数字时只能保留原有数字，绝对不能产生输入中不存在的数字。若包含求救、帮助、疼痛或SOS等紧急含义，必须保留紧急表达。" +
            "每个候选句子单独占一行。不要编号、不要解释、不要引号、不要其他内容。"
        val messages = JSONArray()
            .put(JSONObject().put("role", "system").put("content", sys))
            .put(JSONObject().put("role", "user").put("content", "手势词序列: ${words.joinToString(" ")}\n请生成 $count 个语义不同的中文句子，每句一行。"))
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
