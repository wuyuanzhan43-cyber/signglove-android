package com.signglove

import android.content.Context

/** 设置持久化 (SharedPreferences)。对应 PC 版 settings.json。 */
class Settings(ctx: Context) {
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

    /** 多候选语义模式: 停顿后列出多个候选句, 手势/点击选择; false=单句直接播报。 */
    var multiMode: Boolean
        get() = sp.getBoolean("multi_mode", true)
        set(v) { sp.edit().putBoolean("multi_mode", v).apply() }

    /** 候选句数量 (1~5, 默认 3)。仅 multiMode 时生效。 */
    var candidateCount: Int
        get() = sp.getInt("candidate_count", 3)
        set(v) { sp.edit().putInt("candidate_count", v.coerceIn(1, 5)).apply() }
}
