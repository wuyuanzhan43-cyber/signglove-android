package com.signglove

import java.util.Locale

/**
 * 手势名 → 显示词 映射 (与固件 train/vocab.json 25 类保持一致)。
 * null = 忽略(如 idle 静止)。
 */
object GestureMap {

    /** 固件词表: 当前主工程 fusion_deploy_0707/train/vocab.json 的 25 类。 */
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
        "one" to "1",
        "two" to "2",
        "three" to "3",
        "four" to "4",
        "judge" to "评委",
        "teacher" to "老师",
        "good_afternoon" to "下午好",
        "contest" to "嵌赛",
    )

    /** 兼容早期 6 类固件、37 类数字别名以及中文标签直传。 */
    private val map: Map<String, String?> = buildMap {
        putAll(firmwareVocabulary)
        firmwareVocabulary.values.filterNotNull().forEach { chinese -> put(chinese, chinese) }
        // 37 类固件数字别名
        put("num_1", "1"); put("num_2", "2"); put("num_4", "4")
        // 早期 6 类: fist/open/point/victory/ok 及其中文别名
        put("fist", "我"); put("握拳", "我"); put("拳头", "我")
        put("open", "你好"); put("张开", "你好"); put("张手", "你好")
        put("point", "这个"); put("指向", "这个"); put("这个", "这个")
        put("victory", "需要"); put("胜利", "需要"); put("剪刀手", "需要"); put("需要", "需要")
        put("ok", "好的"); put("确认", "好的"); put("好的", "好的")
        put("静止", null); put("静息", null)
    }

    /** 解析一行: "GESTURE:fist" → "fist"; 容错纯文本也当手势名。 */
    fun parseGesture(line: String): String? {
        val s = line.trim()
        if (s.isEmpty()) return null
        return if (s.uppercase().startsWith("GESTURE:")) s.substringAfter(":").trim() else s
    }

    /** 手势名 → 词。未知英文标签忽略(过滤垃圾); 未知中文透传。 */
    fun word(name: String): String? {
        val key = normalize(name)
        if (map.containsKey(key)) return map[key]
        return name.trim().takeIf { value -> value.any { it.code in 0x3400..0x9FFF } }
    }

    /**
     * 解析"选择候选句"的手势序号: 数字手势 one/two/three/four、
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
