package com.signglove

import android.os.Handler
import android.os.Looper
import kotlin.concurrent.thread

/**
 * 停顿断句 + 组句。收到手势词进缓冲, 停顿超过 pauseSec 触发组句:
 * - 单句模式(或未配 key / 候选<=1): 调云端组一句直接播报 (有 key) 或直拼;
 * - 多候选模式: 云端一次生成多个候选, 进入"候选等待态"等使用者选择(数字手势或屏幕点选),
 *   选中才播报; 打非数字新手势视为开始新一句(放弃候选)。
 * 所有回调都回主线程。
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
    /** 候选句列表; 非 null 即处于"候选等待态"(此态 pending 恒为 null)。 */
    private var candidates: List<String>? = null

    /**
     * 收到一个手势名(原始名, 未映射)。
     * 多候选等待态下, 数字手势 one/two/three/four 解析为"选择第N个候选";
     * 其他手势视为开始新一句(先放弃当前候选)。
     */
    fun feed(name: String) {
        val idx = GestureMap.selectIndex(name)
        if (idx != null) {
            when {
                !settings.multiMode -> enqueueWord(GestureMap.word(name) ?: return)  // 单句: 数字当普通词
                candidates != null -> select(idx)                                    // 等待态: 手势选择候选
                // 多候选非等待态: 忽略数字手势(保留给选候选用)
            }
            return
        }
        val w = GestureMap.word(name) ?: return
        enqueueWord(w)
    }

    private fun enqueueWord(w: String) {
        if (candidates != null) abandon()                 // 等待态来了新词 → 放弃候选
        buffer.add(w)
        main.post { onWord(w) }
        pending?.let { main.removeCallbacks(it) }
        val r = Runnable { fire() }
        pending = r
        main.postDelayed(r, (settings.pauseSec * 1000).toLong())
    }

    /** UI 点击候选入口 (主线程调用)。 */
    fun selectCandidate(index: Int) = select(index)

    /** 是否处于候选等待态(供模拟数据/UI 查询)。 */
    fun hasCandidates(): Boolean = candidates != null

    /** 选择第 index 个候选并播报; 越界忽略。主线程调用。 */
    private fun select(index: Int) {
        val list = candidates ?: return
        if (index < 1 || index > list.size) return
        candidates = null
        onCandidates(null)
        onSentence(list[index - 1], "deepseek_multi")
    }

    /** 放弃当前候选, 回到积词态。主线程调用。 */
    private fun abandon() {
        candidates = null
        onCandidates(null)
    }

    private fun fire() {
        pending = null
        if (buffer.isEmpty()) return
        val words = buffer.toList()
        buffer.clear()
        main.post { onComposing() }   // 清词流, 进入"组句中"
        val key = settings.deepseekKey
        if (key.isBlank()) {
            main.post { onSentence(words.joinToString(" "), "local") }
            return
        }
        val useMulti = settings.multiMode && settings.candidateCount > 1
        thread {
            if (useMulti) {
                val list = DeepSeek.combineAll(words, settings.candidateCount, key, settings.deepseekModel, settings.deepseekUrl)
                main.post {
                    when {
                        list.isNullOrEmpty() -> onSentence(words.joinToString(" "), "fallback")
                        list.size <= 1 || buffer.isNotEmpty() ->
                            // 只剩一句(无可选性) 或 组句期间已开始新词 → 直接播首句
                            onSentence(list.first(), "deepseek_multi")
                        else -> { candidates = list; onCandidates(list) }
                    }
                }
            } else {
                val s = DeepSeek.combine(words, key, settings.deepseekModel, settings.deepseekUrl)
                val text = s ?: words.joinToString(" ")
                val src = if (s != null) "deepseek" else "fallback"
                main.post { onSentence(text, src) }
            }
        }
    }
}
