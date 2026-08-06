package com.signglove

import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.signglove.databinding.ActivitySettingsBinding

/** 设置界面: 读写 SharedPreferences；提示词编辑器默认隐藏，空白处连续点击 8 次显示。 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var b: ActivitySettingsBinding
    private lateinit var settings: Settings
    private var secretTapCount = 0
    private var lastSecretTapAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(b.root)
        supportActionBar?.title = getString(R.string.settings_title)

        settings = Settings(this)
        // 填入现值
        b.etServerchan.setText(settings.serverchan)
        b.etWebhook.setText(settings.webhook)
        b.etName.setText(settings.userName)
        b.etDsKey.setText(settings.deepseekKey)
        b.etDeepseekPrompt.setText(settings.deepseekPrompt)
        b.swPromptEditorVisible.isChecked = settings.deepseekPromptVisible
        b.swMultiMode.isChecked = settings.multiMode
        b.etCandidateCount.setText(settings.candidateCount.toString())
        b.promptEditorSection.visibility =
            if (settings.deepseekPromptVisible) View.VISIBLE else View.GONE

        b.settingsRoot.setOnClickListener { registerSecretTap() }
        b.swPromptEditorVisible.setOnCheckedChangeListener { _, visibleByDefault ->
            settings.deepseekPromptVisible = visibleByDefault
            if (!visibleByDefault) b.promptEditorSection.visibility = View.GONE
            Toast.makeText(
                this,
                if (visibleByDefault) "以后将默认显示提示词编辑器"
                else "提示词编辑器已隐藏，连续点击空白处 8 次可再次显示",
                Toast.LENGTH_SHORT
            ).show()
        }

        b.btnSave.setOnClickListener {
            settings.serverchan = b.etServerchan.text.toString().trim()
            settings.webhook = b.etWebhook.text.toString().trim()
            settings.userName = b.etName.text.toString().trim().ifBlank { "手语手套用户" }
            settings.deepseekKey = b.etDsKey.text.toString().trim()
            settings.deepseekPrompt = b.etDeepseekPrompt.text.toString().trim()
            settings.multiMode = b.swMultiMode.isChecked
            settings.candidateCount = b.etCandidateCount.text.toString().trim().toIntOrNull()?.coerceIn(1, 5) ?: 3
            Toast.makeText(this, "✓ 设置已保存", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun registerSecretTap() {
        val now = SystemClock.elapsedRealtime()
        secretTapCount = if (now - lastSecretTapAt <= 1200L) secretTapCount + 1 else 1
        lastSecretTapAt = now
        if (secretTapCount < 8) return

        secretTapCount = 0
        b.promptEditorSection.visibility = View.VISIBLE
        Toast.makeText(this, "已显示云端提示词编辑器", Toast.LENGTH_SHORT).show()
    }
}
