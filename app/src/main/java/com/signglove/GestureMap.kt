package com.signglove

import java.util.Locale

/** 设备手势标签 → 中文手势词。37 类主词表与固件 train/vocab.json 保持一致。 */
object GestureMap {
    private val firmwareVocabulary: Map<String, String?> = linkedMapOf(
        "idle" to null,
        "hello" to "你好",
        "thanks" to "谢谢",
        "bye" to "再见",
        "me" to "我",
        "you" to "你",
        "want" to "要",
        "water" to "喝水",
        "eat" to "吃饭",
        "toilet" to "厕所",
        "help" to "帮助",
        "pain" to "疼痛",
        "home" to "家",
        "sos" to "求救",
        "go" to "走",
        "come" to "过来",
        "stop" to "停",
        "sorry" to "对不起",
        "please" to "请",
        "you_all" to "你们",
        "why" to "为什么",
        "where" to "哪里",
        "how" to "怎么样",
        "teacher" to "老师",
        "go_to" to "去",
        "look" to "看",
        "judge" to "评委",
        "num_0" to "0",
        "num_1" to "1",
        "num_4" to "4",
        "num_7" to "7",
        "num_8" to "8",
        "num_9" to "9",
        "welcome" to "欢迎",
        "good_afternoon" to "下午好",
        "very" to "很",
        "think" to "想",
    )

    /** 兼容早期 6 类固件以及中文标签直传。 */
    private val map: Map<String, String?> = buildMap {
        putAll(firmwareVocabulary)
        firmwareVocabulary.values.filterNotNull().forEach { chinese -> put(chinese, chinese) }
        put("静止", null)
        put("静息", null)
        put("fist", "我")
        put("握拳", "我")
        put("拳头", "我")
        put("open", "你好")
        put("张开", "你好")
        put("张手", "你好")
        put("point", "这个")
        put("指向", "这个")
        put("这个", "这个")
        put("victory", "需要")
        put("胜利", "需要")
        put("剪刀手", "需要")
        put("需要", "需要")
        put("ok", "好的")
        put("确认", "好的")
        put("好的", "好的")
        put("rescue", "求救")
        put("emergency", "求救")
    }

    /** 解析一行: "GESTURE:hello" → "hello"; 容错纯文本也当手势名。 */
    fun parseGesture(line: String): String? {
        val s = line.trim()
        if (s.isEmpty()) return null
        return if (s.startsWith("GESTURE:", ignoreCase = true)) s.substringAfter(":").trim() else s
    }

    /** SOS 标签大小写不敏感，中文“求救”也能直接识别。 */
    fun isSos(name: String): Boolean =
        normalize(name) in setOf("sos", "rescue", "emergency", "求救")

    /** idle/静息只表示没有动作，不进入组句，也不显示未知词提示。 */
    fun isIdle(name: String): Boolean = normalize(name) in setOf("idle", "静止", "静息")

    /** 手势名 → 中文词。未知英文标签不再原样显示，中文词可直接透传。 */
    fun word(name: String): String? {
        val key = normalize(name)
        if (map.containsKey(key)) return map[key]
        return name.trim().takeIf { value -> value.any { it.code in 0x3400..0x9FFF } }
    }

    /**
     * 解析"选择候选句"的手势序号: 当前固件数字手势 one/two/three/four、
     * 37 类别名 num_1..num_4、半角/全角/中文数字 → 1~4; 其他返回 null。
     */
    fun selectIndex(name: String): Int? = when (normalize(name)) {
        "one", "num_1", "1", "一", "１" -> 1
        "two", "num_2", "2", "二", "２" -> 2
        "three", "num_3", "3", "三", "３" -> 3
        "four", "num_4", "4", "四", "４" -> 4
        else -> null
    }

    private fun normalize(name: String): String = name.trim().lowercase(Locale.ROOT)
}
