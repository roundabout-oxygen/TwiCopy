package com.example.twicopy

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.example.twicopy.data.AppPreferences
import com.example.twicopy.data.EmojiRemovalLevel
import com.example.twicopy.data.OutputMode
import com.example.twicopy.data.UserPreferences
import com.example.twicopy.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var appPreferences: AppPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        appPreferences = AppPreferences(this)
        loadSettings()
        registerShareShortcut()

        binding.btnSave.setOnClickListener {
            saveSettings()
            Toast.makeText(this, "設定を保存しました", Toast.LENGTH_SHORT).show()
        }

        binding.btnExit.setOnClickListener {
            saveSettings()
            finishAffinity()
        }
    }

    private fun registerShareShortcut() {
        try {
            val shareIntent = Intent(this, ShareHandlerActivity::class.java).apply {
                action = Intent.ACTION_SEND
                type = "text/plain"
            }

            val shortcut = ShortcutInfoCompat.Builder(this, "twicopy_share_target")
                .setShortLabel("TwiCopy")
                .setLongLabel("TwiCopy でコピー")
                .setIcon(IconCompat.createWithResource(this, R.mipmap.ic_launcher))
                .setIntent(shareIntent)
                .setCategories(setOf("com.example.twicopy.category.TEXT_SHARE_TARGET"))
                .build()

            ShortcutManagerCompat.pushDynamicShortcut(this, shortcut)
        } catch (_: Exception) {}
    }

    private fun loadSettings() {
        val prefs = appPreferences.loadPreferences()

        // 出力モード
        when (prefs.outputMode) {
            OutputMode.NORMAL -> binding.rbModeNormal.isChecked = true
            OutputMode.IMAGE_ONLY -> binding.rbModeImageOnly.isChecked = true
            OutputMode.NO_IMAGE -> binding.rbModeNoImage.isChecked = true
        }

        // 絵文字レベル
        when (prefs.emojiRemovalLevel) {
            EmojiRemovalLevel.NONE -> binding.rbEmojiNone.isChecked = true
            EmojiRemovalLevel.COMPOSITE -> binding.rbEmojiComposite.isChecked = true
            EmojiRemovalLevel.FULL_FILTER -> binding.rbEmojiFull.isChecked = true
        }

        // スイッチ
        binding.swIncludeQuote.isChecked = prefs.includeQuoteTweet
        binding.swSwapQuote.isChecked = prefs.swapQuotePosition
        binding.swRemoveBlankLines.isChecked = prefs.autoRemoveBlankLines

        // 日時フォーマット
        binding.etDateFormat.setText(prefs.dateFormat)
    }

    private fun saveSettings() {
        val outputMode = when (binding.rgOutputMode.checkedRadioButtonId) {
            binding.rbModeImageOnly.id -> OutputMode.IMAGE_ONLY
            binding.rbModeNoImage.id -> OutputMode.NO_IMAGE
            else -> OutputMode.NORMAL
        }

        val emojiLevel = when (binding.rgEmojiLevel.checkedRadioButtonId) {
            binding.rbEmojiComposite.id -> EmojiRemovalLevel.COMPOSITE
            binding.rbEmojiFull.id -> EmojiRemovalLevel.FULL_FILTER
            else -> EmojiRemovalLevel.NONE
        }

        val dateFormat = binding.etDateFormat.text?.toString()?.trim()
            ?.ifBlank { "yyyy/MM/dd HH:mm:ss" } ?: "yyyy/MM/dd HH:mm:ss"

        val updated = UserPreferences(
            outputMode = outputMode,
            emojiRemovalLevel = emojiLevel,
            autoRemoveBlankLines = binding.swRemoveBlankLines.isChecked,
            includeQuoteTweet = binding.swIncludeQuote.isChecked,
            swapQuotePosition = binding.swSwapQuote.isChecked,
            dateFormat = dateFormat
        )

        appPreferences.savePreferences(updated)
    }
}
