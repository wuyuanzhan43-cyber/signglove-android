package com.signglove

import android.content.Context

/** 设置持久化 (SharedPreferences)。对应 PC 版 settings.json。 */
class Settings(ctx: Context) {
    companion object {
        private const val LEGACY_DEEPSEEK_PROMPT =
            "你是专业的手语翻译助手。用户会按识别顺序输入一串以空格分隔的中文手势词。" +
            "请结合手语语序，将这些词连成一句自然、通顺、口语化的中文句子；可以调整语序、补充必要虚词并添加恰当标点，" +
            "但不得改变原意、遗漏关键信息、把文字词替换成数字或凭空添加事实。若包含“求救”或“SOS”等紧急信息，必须保留其紧急含义。" +
            "只输出最终中文句子，不要解释、引号、前缀或其他内容。"

        const val DEFAULT_DEEPSEEK_PROMPT =
            "你是专业的中文手语语义补全与翻译助手。用户输入的是手语设备按识别顺序得到的中文关键词，内容可能缺少主语、谓语、宾语、助词或必要上下文。" +
            "你的任务不是简单拼接关键词，而是理解用户意图，将关键词补全为一句自然、完整、通顺、适合日常交流的中文句子。" +
            "必须保留每个输入关键词的核心含义，不得遗漏、替换或曲解；允许根据最常见的日常表达补充缺失成分、调整语序并添加标点。" +
            "当存在多种解释时，选择最常见、最保守的表达；不得凭空添加具体人名、地点、时间、数字、疾病名称或其他无法合理推断的事实。" +
            "输入中出现数字时只能保留原有数字，绝对不能产生输入中不存在的数字。若包含求救、帮助、疼痛或SOS等紧急含义，必须生成明确、紧迫的求助表达。" +
            "示例：我 喝水→我想喝水。；我 厕所→我想去厕所。；你 哪里→你要去哪里？；老师 过来→老师，请过来。；我 疼痛 帮助→我很疼，请帮助我。" +
            "只输出一句最终补全后的中文句子，不要输出分析、解释、引号、标题、前缀或候选答案。"
    }

    private val sp = ctx.getSharedPreferences("signglove", Context.MODE_PRIVATE)

    var serverchan: String
        get() = sp.getString("sos_serverchan", "") ?: ""
        set(v) { sp.edit().putString("sos_serverchan", v).apply() }

    var webhook: String
        get() = sp.getString("sos_webhook", "") ?: ""
        set(v) { sp.edit().putString("sos_webhook", v).apply() }

    var userName: String
        get() = sp.getString("sos_name", "手语手套用户") ?: "手语手套用户"
        set(v) { sp.edit().putString("sos_name", v).apply() }

    var deepseekKey: String
        get() = sp.getString("deepseek_key", "") ?: ""
        set(v) { sp.edit().putString("deepseek_key", v).apply() }

    var deepseekEnabled: Boolean
        get() = sp.getBoolean("deepseek_enabled", true)
        set(v) { sp.edit().putBoolean("deepseek_enabled", v).apply() }

    var deepseekPrompt: String
        get() {
            val saved = sp.getString("deepseek_prompt", DEFAULT_DEEPSEEK_PROMPT).orEmpty()
            return if (saved.isBlank() || saved == LEGACY_DEEPSEEK_PROMPT) DEFAULT_DEEPSEEK_PROMPT else saved
        }
        set(v) { sp.edit().putString("deepseek_prompt", v.ifBlank { DEFAULT_DEEPSEEK_PROMPT }).apply() }

    var deepseekPromptVisible: Boolean
        get() = sp.getBoolean("deepseek_prompt_visible", false)
        set(v) { sp.edit().putBoolean("deepseek_prompt_visible", v).apply() }

    var deepseekModel: String
        get() = sp.getString("deepseek_model", "deepseek-chat") ?: "deepseek-chat"
        set(v) { sp.edit().putString("deepseek_model", v).apply() }

    var deepseekUrl: String
        get() = sp.getString("deepseek_url", "https://api.deepseek.com/chat/completions")
            ?: "https://api.deepseek.com/chat/completions"
        set(v) { sp.edit().putString("deepseek_url", v).apply() }

    var pauseSec: Float
        get() = sp.getFloat("pause_sec", 2.5f)
        set(v) { sp.edit().putFloat("pause_sec", v).apply() }

    var autoSos: Boolean
        get() = sp.getBoolean("auto_sos", true)
        set(v) { sp.edit().putBoolean("auto_sos", v).apply() }

    var gestureSosEnabled: Boolean
        get() = sp.getBoolean("gesture_sos_enabled", true)
        set(v) { sp.edit().putBoolean("gesture_sos_enabled", v).apply() }

    var gestureRecognitionEnabled: Boolean
        get() = sp.getBoolean("gesture_recognition_enabled", true)
        set(v) { sp.edit().putBoolean("gesture_recognition_enabled", v).apply() }

    var lastBluetoothMac: String
        get() = sp.getString("last_bluetooth_mac", "") ?: ""
        set(v) { sp.edit().putString("last_bluetooth_mac", v).apply() }

    /** 多候选语义模式: 停顿后列出多个候选句, 数字手势/点击选择; false=单句直接播报。 */
    var multiMode: Boolean
        get() = sp.getBoolean("multi_mode", true)
        set(v) { sp.edit().putBoolean("multi_mode", v).apply() }

    /** 候选句数量 (1~5, 默认 3)。仅 multiMode 时生效。 */
    var candidateCount: Int
        get() = sp.getInt("candidate_count", 3)
        set(v) { sp.edit().putInt("candidate_count", v.coerceIn(1, 5)).apply() }
}
