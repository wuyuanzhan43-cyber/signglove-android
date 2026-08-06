package com.signglove

import android.os.Handler
import android.os.Looper
import kotlin.concurrent.thread

/**
 * 停顿断句 + 组句。收到手势词进缓冲, 停顿超过 pauseSec 触发组句:
 * DeepSeek 已开启且配置 key → 调云端；否则本地直拼。结果回主线程。
 * 多候选模式(且 ≥2 词)下, 云端一次生成多个候选, 进入"候选等待态"等使用者选择
 * (数字手势由 MainActivity 拦截后调 selectCandidate, 或点击候选), 选中才播报;
 * 等待态收到新词视为开始新一句(先放弃候选)。
 */
class SentenceComposer(
    private val settings: Settings,
    private val onWord: (String) -> Unit,                 // 每个手势词(累积小字)
    private val onComposing: () -> Unit,                  // 停顿触发组句(清词流, 显示"组句中")
    private val onCandidates: (List<String>?) -> Unit,    // 候选列表: 非null=展示候选进等待态; null=清候选
    private val onSentence: (String, String) -> Unit      // (句子, 来源: deepseek/local/fallback/deepseek_multi)
) {
    private val main = Handler(Looper.getMainLooper())
    private val buffer = mutableListOf<String>()
    private var pending: Runnable? = null
    @Volatile private var generation = 0L
    /** 候选句列表; 非 null 即处于"候选等待态"(此态 pending 恒为 null)。主线程读写。 */
    private var candidates: List<String>? = null

    /** 收到一个手势词(已映射的中文词)。 */
    fun feed(word: String) {
        if (candidates != null) abandon()                 // 等待态来了新词 → 放弃候选, 开始新一句
        buffer.add(word)
        main.post { onWord(word) }
        pending?.let { main.removeCallbacks(it) }
        val r = Runnable { fire() }
        pending = r
        main.postDelayed(r, (settings.pauseSec * 1000).toLong())
    }

    /** 关闭手势识别时丢弃尚未组句的词，并屏蔽已经发出的异步结果。 */
    fun clear() {
        generation++
        buffer.clear()
        pending?.let { main.removeCallbacks(it) }
        pending = null
        if (candidates != null) {
            candidates = null
            onCandidates(null)
        }
    }

    /** 是否处于候选等待态(供 MainActivity 拦截数字选择手势)。主线程调用。 */
    fun hasCandidates(): Boolean = candidates != null

    /** 语义选择演示: 无需云端, 直接展示一组内置候选并进入等待态(演示选择交互)。主线程调用。 */
    fun demoCandidates() {
        if (candidates != null) abandon()
        val list = listOf("我想喝水", "我要去接点水", "我渴了想喝东西")
        candidates = list
        onCandidates(list)
    }

    /** 选择第 index 个候选并播报 (数字手势/屏幕点选入口)。越界忽略。主线程调用。 */
    fun selectCandidate(index: Int) {
        val list = candidates ?: return
        if (index < 1 || index > list.size) return
        candidates = null
        onCandidates(null)
        onSentence(list[index - 1], "deepseek_multi")
    }

    /** 放弃当前候选, 回到积词态。调用方需保证在主线程。 */
    private fun abandon() {
        candidates = null
        onCandidates(null)
    }

    private fun fire() {
        pending = null
        if (buffer.isEmpty()) return
        val requestGeneration = generation
        val words = buffer.toList()
        buffer.clear()
        main.post {
            if (generation == requestGeneration) onComposing()
        }   // 清词流, 进入"组句中"
        // DeepSeek 仅用于“连词成句”。单个词无需改写，直接保留设备识别后的
        // 中文词，避免“下午好”等正确结果被云端错误替换成数字或其他词。
        if (!CompositionPolicy.shouldUseDeepSeek(words)) {
            deliver(words.single(), "local_single", requestGeneration)
            return
        }
        if (!settings.deepseekEnabled) {
            deliver(words.joinToString(" "), "local_disabled", requestGeneration)
            return
        }
        val key = settings.deepseekKey
        if (key.isBlank()) {
            deliver(words.joinToString(" "), "local_no_key", requestGeneration)
            return
        }
        val useMulti = settings.multiMode && settings.candidateCount > 1
        thread {
            if (useMulti) {
                val list = DeepSeek.combineAll(
                    words, settings.candidateCount, key, settings.deepseekModel, settings.deepseekUrl
                )
                main.post {
                    if (generation != requestGeneration) return@post
                    // 过滤掉凭空生成数字等不合规候选, 保留至少一句才进候选等待态
                    val accepted = list
                        ?.filter { CompositionPolicy.acceptsDeepSeekResult(words, it) }
                        ?.distinct()
                    when {
                        accepted.isNullOrEmpty() -> onSentence(words.joinToString(" "), "fallback")
                        accepted.size <= 1 || buffer.isNotEmpty() ->
                            // 只剩一句(无可选性) 或 组句期间已开始新词 → 直接播首句
                            onSentence(accepted.first(), "deepseek_multi")
                        else -> { candidates = accepted; onCandidates(accepted) }
                    }
                }
            } else {
                val s = DeepSeek.combine(
                    words,
                    key,
                    settings.deepseekModel,
                    settings.deepseekUrl,
                    settings.deepseekPrompt
                )
                val accepted = s?.takeIf { CompositionPolicy.acceptsDeepSeekResult(words, it) }
                val text = accepted ?: words.joinToString(" ")
                val src = if (accepted != null) "deepseek" else "fallback"
                deliver(text, src, requestGeneration)
            }
        }
    }

    private fun deliver(text: String, source: String, requestGeneration: Long) {
        main.post {
            if (generation == requestGeneration) onSentence(text, source)
        }
    }
}
