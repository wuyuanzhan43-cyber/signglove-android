package com.signglove

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.util.TypedValue
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.signglove.databinding.ActivityMainBinding
import java.util.Locale
import kotlin.random.Random

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding
    private lateinit var settings: Settings
    private lateinit var bt: BluetoothBle
    private lateinit var composer: SentenceComposer
    private lateinit var vitals: Vitals
    private lateinit var sos: Sos
    private var tts: TextToSpeech? = null

    private var deviceMacs = listOf<String>()
    private var connected = false
    private val flow = StringBuilder()
    private val history = StringBuilder()
    private val main = Handler(Looper.getMainLooper())
    private var sosDialog: AlertDialog? = null

    // 手势模拟: 发一组(2~4)词 → 停顿(>pauseSec)触发组句 → 再发下一组;
    // 多候选模式下若已出候选, 则模拟打数字手势 1~4 选择(走手势选择路径)。
    private var simRunning = false
    private var simWordsLeft = 0
    private val simNames = listOf("fist", "open", "point", "victory", "ok")
    private val simSelNames = listOf("one", "two", "three", "four")
    private val simTick = object : Runnable {
        override fun run() {
            if (!simRunning) return
            if (settings.multiMode && composer.hasCandidates()) {
                composer.feed(simSelNames[Random.nextInt(simSelNames.size)])  // 模拟打数字手势选候选
                simWordsLeft = 0
                main.postDelayed(this, (settings.pauseSec * 1000).toLong() + 1200)
                return
            }
            composer.feed(simNames[Random.nextInt(simNames.size)])
            simWordsLeft--
            if (simWordsLeft > 0) {
                main.postDelayed(this, (600 + Random.nextInt(400)).toLong())  // 词间 0.6~1.0s
            } else {
                simWordsLeft = 2 + Random.nextInt(3)                           // 下一组 2~4 词
                main.postDelayed(this, (settings.pauseSec * 1000).toLong() + 1200) // 停顿>阈值, 触发组句
            }
        }
    }

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()) { refreshDevices() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        settings = Settings(this)
        b.tvTitle.text = "🧤 手语手套 · 智能监测  v1.5"
        tts = TextToSpeech(this) { if (it == TextToSpeech.SUCCESS) tts?.language = Locale.CHINA }

        composer = SentenceComposer(settings,
            onWord = { w -> flow.append(if (flow.isEmpty()) "" else " ").append(w)
                b.tvFlow.text = "手势词: $flow"; b.tvStatus.text = "… 组句中" },
            onComposing = { flow.clear(); b.tvFlow.text = ""; b.tvStatus.text = "… 组句中" },
            onCandidates = { list -> onCandidates(list) },
            onSentence = { text, src -> onSentence(text, src) })

        vitals = Vitals(
            onUpdate = { v -> showVitals(v) },
            onDanger = { reason, v -> if (settings.autoSos) sos.trigger(reason, v) })

        sos = Sos(this, settings,
            onCountdown = { sec, reason -> updateCountdown(sec, reason) },
            onPushed = { ok, detail -> onPushed(ok, detail) })

        bt = BluetoothBle(
            ctx = this,
            onLine = { line -> GestureMap.parseGesture(line)?.let { composer.feed(it) } },
            onState = { c -> connected = c
                b.tvBle.text = if (c) "蓝牙: 已连接" else "蓝牙: 未连接"
                b.btnConnect.text = if (c) "⏏ 断开" else "🔌 连接" })

        wireControls()
        requestPerms()
        vitals.startSim()   // 生命体征模拟(同 PC)
    }

    private fun wireControls() {
        b.btnSettings.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }

        b.btnConnect.setOnClickListener {
            if (connected) { bt.disconnect() }
            else {
                val pos = b.spDevices.selectedItemPosition
                if (pos < 0 || pos >= deviceMacs.size) { toast("请先在系统设置配对 JDY-18, 再刷新选择"); return@setOnClickListener }
                if (!hasBt()) { requestPerms(); return@setOnClickListener }
                bt.connect(deviceMacs[pos]); toast("连接中…")
            }
        }

        b.swSim.setOnCheckedChangeListener { _, on ->
            simRunning = on
            if (on) { simWordsLeft = 2 + Random.nextInt(3); main.post(simTick) }
            else main.removeCallbacks(simTick)
        }
        b.swAutoSos.isChecked = settings.autoSos
        b.swAutoSos.setOnCheckedChangeListener { _, on -> settings.autoSos = on
            toast("自动报警 " + if (on) "开" else "关") }

        b.btnSosTest.setOnClickListener {
            if (!settings.autoSos) { toast("请先打开自动报警开关"); return@setOnClickListener }
            toast("注入异常生命体征…"); vitals.injectDanger()
        }
        b.btnDemo.setOnClickListener {
            val demo = listOf("你好", "谢谢", "请问需要帮助吗", "我肚子饿了想吃饭")
            onSentence(demo[Random.nextInt(demo.size)], "demo")
        }
    }

    private fun onSentence(text: String, src: String) {
        b.tvGesture.text = text      // 句子持久展示在大字区
        b.tvStatus.text = ""         // 清"组句中"状态
        b.tvFlow.text = ""
        flow.clear()
        speak(text)
        val tag = when (src) {
            "deepseek" -> "[☁DeepSeek]"
            "deepseek_multi" -> "[☁DeepSeek·多候选]"
            "local" -> "[直拼·未配Key]"
            "fallback" -> "[回退·DeepSeek失败]"
            "demo" -> "[演示]"
            else -> ""
        }
        history.insert(0, "• $text  $tag\n")
        b.tvHistory.text = history.toString()
    }

    /** 多候选语义: 展示可点击候选列表并进入等待态; null 清空列表。 */
    private fun onCandidates(list: List<String>?) {
        b.llCandidates.removeAllViews()
        if (list == null) return
        b.tvGesture.text = "✋ 请选择语义"
        b.tvStatus.text = "打数字手势 1~${list.size} 或点击下方选项；打其他手势开始新句子"
        b.tvFlow.text = ""
        list.forEachIndexed { i, s ->
            val tv = TextView(this).apply {
                text = "${i + 1}. $s"
                textSize = 16f
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.txt))
                setBackgroundColor(0x0D000000)
                isClickable = true
                val outValue = TypedValue()
                theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
                foreground = getDrawable(outValue.resourceId)
                setPadding(12.dp, 10.dp, 12.dp, 10.dp)
                setOnClickListener { composer.selectCandidate(i + 1) }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = 4.dp }
            }
            b.llCandidates.addView(tv)
        }
    }

    private fun Int.dp() = (this * resources.displayMetrics.density).toInt()

    private fun showVitals(v: VitalsData) {
        b.tvHr.text = "❤️\n${v.hr}\nBPM"
        b.tvSpo2.text = "🩸\n${v.spo2}\n%"
        b.tvTemp.text = "🌡️\n${v.temp}\n℃"
        b.tvGsr.text = "⚡\n${v.gsr}\nμS"
    }

    private fun updateCountdown(sec: Int, reason: String) {
        if (sec <= 0) { sosDialog?.dismiss(); sosDialog = null; return }
        if (sosDialog == null) {
            sosDialog = AlertDialog.Builder(this)
                .setCancelable(false)
                .setTitle("🆘 检测到生命体征异常")
                .setPositiveButton("立即求助") { _, _ -> sos.fireNow() }
                .setNegativeButton("我没事，取消") { _, _ -> sos.cancel() }
                .create()
            sosDialog?.show()
        }
        sosDialog?.setMessage("$reason\n\n$sec 秒后自动向家人发送求助与定位")
    }

    private fun onPushed(ok: Boolean, detail: String) {
        toast(if (ok) "✓ 已向家人发送求助" else "推送失败: $detail")
        history.insert(0, (if (ok) "🆘 已向家人发送求助\n" else "🆘 求助未送达: $detail\n"))
        b.tvHistory.text = history.toString()
    }

    private fun speak(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "u")
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    // ---- 权限 / 设备 ----
    private fun hasBt(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    private fun requestPerms() {
        val need = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            need.add(Manifest.permission.BLUETOOTH_CONNECT)
            need.add(Manifest.permission.BLUETOOTH_SCAN)
        }
        need.add(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            need.add(Manifest.permission.POST_NOTIFICATIONS)
        val ask = need.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (ask.isNotEmpty()) permLauncher.launch(ask.toTypedArray()) else refreshDevices()
    }

    private fun refreshDevices() {
        if (!bt.isAvailable()) { b.tvBle.text = "蓝牙: 设备不支持"; return }
        if (!bt.isEnabled()) { b.tvBle.text = "蓝牙: 未开启(请打开手机蓝牙)" }
        val list = bt.bondedDevices()
        deviceMacs = list.map { it.second }
        val labels = if (list.isEmpty()) listOf("无已配对设备(先配对 JDY-18)")
                     else list.map { "${it.first}  ${it.second}" }
        b.spDevices.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, labels)
    }

    override fun onResume() { super.onResume(); if (hasBt()) refreshDevices() }

    override fun onDestroy() {
        bt.disconnect(); vitals.stopSim(); simRunning = false
        tts?.shutdown()
        super.onDestroy()
    }
}
