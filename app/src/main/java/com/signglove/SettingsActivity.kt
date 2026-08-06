package com.signglove

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.signglove.databinding.ActivitySettingsBinding

/** 设置界面: 读写 SharedPreferences (Server酱/企微/DeepSeek/称呼/停顿)。 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var b: ActivitySettingsBinding
    private lateinit var settings: Settings

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
        b.etPause.setText(settings.pauseSec.toString())
        b.swMultiMode.isChecked = settings.multiMode
        b.etCandidateCount.setText(settings.candidateCount.toString())

        b.btnSave.setOnClickListener {
            settings.serverchan = b.etServerchan.text.toString().trim()
            settings.webhook = b.etWebhook.text.toString().trim()
            settings.userName = b.etName.text.toString().trim().ifBlank { "手语手套用户" }
            settings.deepseekKey = b.etDsKey.text.toString().trim()
            settings.pauseSec = b.etPause.text.toString().trim().toFloatOrNull() ?: 2.5f
            settings.multiMode = b.swMultiMode.isChecked
            settings.candidateCount = b.etCandidateCount.text.toString().trim().toIntOrNull()?.coerceIn(1, 5) ?: 3
            Toast.makeText(this, "✓ 设置已保存", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
