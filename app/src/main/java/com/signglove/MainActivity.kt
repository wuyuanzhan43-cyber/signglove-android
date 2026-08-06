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
import android.view.KeyEvent
import android.view.View
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import com.signglove.databinding.ActivityMainBinding
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding
    private lateinit var settings: Settings
    private lateinit var bt: BluetoothBle
    private lateinit var composer: SentenceComposer
    private lateinit var vitals: Vitals
    private lateinit var sos: Sos
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var ttsWarned = false

    private var pairedDevices = listOf<Pair<String, String>>()
    private var deviceMacs = listOf<String>()
    private var connected = false
    private var targetDevice: Pair<String, String>? = null
    private val flow = StringBuilder()
    private val history = StringBuilder()
    private val main = Handler(Looper.getMainLooper())
    private var sosDialog: AlertDialog? = null

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()) { refreshDevices() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        settings = Settings(this)
        b.tvTitle.text = "🧤 手语手套 · 智能监测  v2.7"
        initTts()

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
            onLine = { line -> GestureMap.parseGesture(line)?.let { name -> handleGestureName(name) } },
            onState = { c -> updateBluetoothState(c) })

        wireControls()
        updateVitalsConnectionState()
        requestPerms()
        vitals.startSim()   // 生命体征模拟(同 PC)
    }

    private fun wireControls() {
        b.btnSettings.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }

        b.btnConnect.setOnClickListener {
            if (connected || targetDevice != null) {
                targetDevice = null
                bt.disconnect()
            }
            else {
                val pos = b.spDevices.selectedItemPosition
                if (pos < 0 || pos >= pairedDevices.size) { toast("请先在系统设置配对 JDY-18, 再刷新选择"); return@setOnClickListener }
                if (!hasBt()) { requestPerms(); return@setOnClickListener }
                targetDevice = pairedDevices[pos]
                settings.lastBluetoothMac = pairedDevices[pos].second
                b.btnConnect.text = "取消连接"
                bt.connect(pairedDevices[pos].second); toast("连接中…")
            }
        }

        b.swGestureRecognition.isChecked = settings.gestureRecognitionEnabled
        b.swGestureSos.isEnabled = settings.gestureRecognitionEnabled
        b.swGestureRecognition.setOnCheckedChangeListener { _, on ->
            settings.gestureRecognitionEnabled = on
            b.swGestureSos.isEnabled = on
            if (on) {
                b.tvGesture.text = "等待手势…"
                b.tvStatus.text = if (connected) "手势识别已开启" else "请先连接蓝牙手套"
            } else {
                composer.clear()
                flow.clear()
                b.tvFlow.text = ""
                b.tvGesture.text = "手势识别已关闭"
                b.tvStatus.text = "蓝牙保持连接，不处理手势数据"
            }
            toast("手势识别 " + if (on) "开" else "关")
        }
        if (!settings.gestureRecognitionEnabled) {
            b.tvGesture.text = "手势识别已关闭"
            b.tvStatus.text = "蓝牙保持连接，不处理手势数据"
        }

        b.swDeepseekEnabled.isChecked = settings.deepseekEnabled
        b.etPause.setText(formatPause(settings.pauseSec))
        b.etPause.isEnabled = settings.deepseekEnabled
        b.swDeepseekEnabled.setOnCheckedChangeListener { _, on ->
            settings.deepseekEnabled = on
            b.etPause.isEnabled = on
            composer.clear()
            flow.clear()
            b.tvFlow.text = ""
            if (settings.gestureRecognitionEnabled) {
                b.tvStatus.text = if (on) {
                    "云端组句已开启，停顿 ${formatPause(settings.pauseSec)} 秒后成句"
                } else {
                    "云端组句已关闭，手势即时识别"
                }
            }
            toast(if (on) "云端语义补全已开启" else "已切换为即时手势识别")
        }
        b.etPause.doAfterTextChanged { text ->
            text?.toString()?.toFloatOrNull()?.takeIf { it in 0.5f..30f }?.let {
                settings.pauseSec = it
            }
        }
        b.etPause.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) normalizePauseInput()
        }

        b.swAutoSos.isChecked = settings.autoSos
        b.swAutoSos.setOnCheckedChangeListener { _, on -> settings.autoSos = on
            toast("自动报警 " + if (on) "开" else "关") }

        b.swGestureSos.isChecked = settings.gestureSosEnabled
        b.swGestureSos.setOnCheckedChangeListener { _, on ->
            settings.gestureSosEnabled = on
            toast("求救手势自动报警 " + if (on) "开" else "关")
        }

        b.btnSosTest.setOnClickListener {
            if (!settings.autoSos) { toast("请先打开自动报警开关"); return@setOnClickListener }
            toast("注入异常生命体征…"); vitals.injectDanger()
        }

        // 语义选择演示（设置页隐藏开关开启后可见）: 无需云端/硬件, 演示多候选选择流程
        b.btnDemo.visibility = if (settings.demoMode) View.VISIBLE else View.GONE
        b.btnDemo.setOnClickListener { composer.demoCandidates() }
    }

    private fun handleGestureName(name: String) {
        if (!settings.gestureRecognitionEnabled) return
        if (GestureMap.isSos(name) && settings.gestureSosEnabled) {
            triggerGestureSosNow("SOS 手势触发求救")
            return
        }
        if (GestureMap.isIdle(name)) return
        // 多候选等待态: 数字手势 one/two/three/four = 选择对应候选
        if (settings.multiMode && composer.hasCandidates()) {
            val idx = GestureMap.selectIndex(name)
            if (idx != null) { composer.selectCandidate(idx); return }
            // 非数字新手势 → 交给 feed(放弃候选并开始新一句)
        }
        val word = GestureMap.word(name)
        if (word == null) {
            b.tvStatus.text = "未配置手势词：$name"
            return
        }
        // 识别后立即更新大字区域，避免停顿组句期间仍显示上一次结果（例如旧的“9”）。
        b.tvGesture.text = word
        if (settings.deepseekEnabled) {
            composer.feed(word)
        } else {
            // 云端关闭时是普通即时识别模式，不等待停顿断句。
            onSentence(word, "local_realtime")
        }
    }

    private fun triggerGestureSosNow(reason: String) {
        flow.clear()
        b.tvFlow.text = ""
        b.tvGesture.text = "求救"
        b.tvStatus.text = "正在触发紧急告警"
        sos.triggerNow(reason, vitals.latest())
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
            "local_realtime" -> "[即时识别]"
            "local_single" -> "[单词直显]"
            "local_disabled" -> "[直拼·DeepSeek已关闭]"
            "local_no_key" -> "[直拼·未配置Key]"
            "fallback" -> "[回退·DeepSeek失败]"
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
                setPadding(dp(12), dp(10), dp(12), dp(10))
                setOnClickListener { composer.selectCandidate(i + 1) }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(4) }
            }
            b.llCandidates.addView(tv)
        }
    }

    /** dp → px。 */
    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun showVitals(v: VitalsData) {
        if (!connected) {
            showVitalsPlaceholder()
            return
        }
        b.tvHr.text = "❤️\n${v.hr}\nBPM"
        b.tvSpo2.text = "🩸\n${v.spo2}\n%"
        b.tvTemp.text = "🌡️\n${v.temp}\n℃"
    }

    private fun updateVitalsConnectionState() {
        if (!connected) showVitalsPlaceholder()
    }

    private fun updateBluetoothState(isConnected: Boolean) {
        connected = isConnected
        if (isConnected) {
            lockDeviceListToConnectedDevice()
            b.tvBle.text = "蓝牙: 已连接 · ${targetDevice?.first ?: "当前设备"}"
            b.btnConnect.text = "⏏ 断开"
            if (settings.gestureRecognitionEnabled) {
                b.tvStatus.text = if (settings.deepseekEnabled) {
                    "手势识别已开启 · 云端组句"
                } else {
                    "手势识别已开启 · 即时模式"
                }
            }
        } else {
            b.tvBle.text = if (targetDevice != null) "蓝牙: 正在连接…" else "蓝牙: 未连接"
            b.btnConnect.text = if (targetDevice != null) "取消连接" else "🔌 连接"
            refreshDevices()
        }
        updateVitalsConnectionState()
    }

    /** 连接成功后列表只显示当前设备，断开后 refreshDevices() 才恢复完整列表。 */
    private fun lockDeviceListToConnectedDevice() {
        val device = targetDevice ?: return
        deviceMacs = listOf(device.second)
        val label = listOf("✓ ${device.first}  ${device.second}（已连接）")
        b.spDevices.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, label)
        b.spDevices.setSelection(0)
        b.spDevices.isEnabled = false
    }

    private fun normalizePauseInput() {
        val pause = b.etPause.text.toString().toFloatOrNull()
            ?.coerceIn(0.5f, 30f) ?: settings.pauseSec
        settings.pauseSec = pause
        val normalized = formatPause(pause)
        if (b.etPause.text.toString() != normalized) b.etPause.setText(normalized)
    }

    private fun formatPause(seconds: Float): String =
        if (seconds % 1f == 0f) seconds.toInt().toString() else seconds.toString()

    private fun showVitalsPlaceholder() {
        b.tvHr.text = "❤️\n--\nBPM"
        b.tvSpo2.text = "🩸\n--\n%"
        b.tvTemp.text = "🌡️\n--\n℃"
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

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            if (sosDialog?.isShowing == true) {
                sos.fireNow()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun initTts() {
        tts = TextToSpeech(this) { status ->
            main.post {
                val engine = tts
                if (status != TextToSpeech.SUCCESS || engine == null) {
                    ttsReady = false
                    toastOnceForTts("语音播报初始化失败，请检查手机 TTS 引擎")
                    return@post
                }

                val zh = engine.setLanguage(Locale.CHINA)
                ttsReady = zh != TextToSpeech.LANG_MISSING_DATA && zh != TextToSpeech.LANG_NOT_SUPPORTED
                if (!ttsReady) {
                    val fallback = engine.setLanguage(Locale.getDefault())
                    ttsReady = fallback != TextToSpeech.LANG_MISSING_DATA && fallback != TextToSpeech.LANG_NOT_SUPPORTED
                    if (!ttsReady) {
                        toastOnceForTts("手机未安装可用语音包，请在系统设置安装文字转语音引擎")
                    } else {
                        toastOnceForTts("未找到中文语音包，已使用系统默认语音")
                    }
                }
            }
        }
    }

    private fun speak(text: String) {
        val engine = tts
        if (engine == null) {
            toastOnceForTts("语音播报尚未初始化")
            return
        }
        if (!ttsReady) {
            main.postDelayed({ if (ttsReady) speak(text) else toastOnceForTts("语音播报不可用，请检查手机 TTS 设置") }, 500L)
            return
        }
        val result = engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "signglove-tts")
        if (result == TextToSpeech.ERROR) toastOnceForTts("语音播报失败，请检查媒体音量和 TTS 引擎")
    }

    private fun toastOnceForTts(message: String) {
        if (ttsWarned) return
        ttsWarned = true
        toast(message)
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    // ---- 权限 / 设备 ----
    private fun hasBt(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    private fun requestPerms() {
        val ask = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)
                ask.add(Manifest.permission.BLUETOOTH_CONNECT)
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED)
                ask.add(Manifest.permission.BLUETOOTH_SCAN)

            // Android 12+ 请求精确定位时，必须把精确与大致定位放在同一批次申请。
            val fineGranted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            val coarseGranted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            if (!fineGranted || !coarseGranted) {
                // 即使其中一个已授权，也保留两项，避免系统忽略单独的精确定位升级请求。
                ask.add(Manifest.permission.ACCESS_FINE_LOCATION)
                ask.add(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
                ask.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) ask.add(Manifest.permission.POST_NOTIFICATIONS)
        if (ask.isNotEmpty()) permLauncher.launch(ask.toTypedArray()) else refreshDevices()
    }

    private fun refreshDevices() {
        if (connected) {
            lockDeviceListToConnectedDevice()
            return
        }
        if (!bt.isAvailable()) { b.tvBle.text = "蓝牙: 设备不支持"; return }
        if (!bt.isEnabled()) { b.tvBle.text = "蓝牙: 未开启(请打开手机蓝牙)" }
        val list = bt.bondedDevices()
        pairedDevices = list
        deviceMacs = list.map { it.second }
        val labels = if (list.isEmpty()) listOf("无已配对设备(先配对 JDY-18)")
                     else list.map { "${it.first}  ${it.second}" }
        b.spDevices.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, labels)
        b.spDevices.isEnabled = list.isNotEmpty()
        val preferredMac = targetDevice?.second ?: settings.lastBluetoothMac
        val preferredIndex = deviceMacs.indexOf(preferredMac)
        if (preferredIndex >= 0) b.spDevices.setSelection(preferredIndex)
    }

    override fun onResume() {
        super.onResume()
        b.btnDemo.visibility = if (settings.demoMode) View.VISIBLE else View.GONE
        if (hasBt()) {
            if (connected) lockDeviceListToConnectedDevice() else refreshDevices()
        }
    }

    override fun onDestroy() {
        bt.disconnect(); vitals.stopSim()
        tts?.shutdown()
        super.onDestroy()
    }
}
