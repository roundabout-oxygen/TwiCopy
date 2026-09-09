package com.example.twicopy

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import com.example.twicopy.data.AppPreferences
import com.example.twicopy.data.TweetFetcher
import com.example.twicopy.domain.TweetFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ShareHandlerActivity : Activity() {

    private val fetcher = TweetFetcher()
    private lateinit var appPreferences: AppPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appPreferences = AppPreferences(this)

        val intent = intent
        val action = intent.action
        val type = intent.type

        val sharedText = if (Intent.ACTION_SEND == action && type != null && type.startsWith("text/")) {
            intent.getStringExtra(Intent.EXTRA_TEXT)
                ?: intent.getStringExtra(Intent.EXTRA_SUBJECT)
                ?: intent.dataString
        } else if (Intent.ACTION_VIEW == action) {
            intent.dataString
        } else null

        if (sharedText.isNullOrBlank()) {
            Toast.makeText(this, "TwiCopy: URLが見つかりませんでした", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        processSharedText(sharedText)
    }

    private fun processSharedText(sharedText: String) {
        val prefs = appPreferences.loadPreferences()
        Toast.makeText(this, "TwiCopy: ツイート取得中...", Toast.LENGTH_SHORT).show()

        CoroutineScope(Dispatchers.Main).launch {
            val result = fetcher.fetchTweet(sharedText)
            if (result.isSuccess) {
                val tweet = result.getOrNull()
                if (tweet != null) {
                    val formatted = TweetFormatter.format(tweet, prefs)
                    copyToClipboard(formatted)
                    Toast.makeText(this@ShareHandlerActivity, "TwiCopy: コピーしました！", Toast.LENGTH_SHORT).show()
                    finish()
                    return@launch
                }
            }

            val errorMsg = result.exceptionOrNull()?.localizedMessage ?: "取得エラー"
            Toast.makeText(this@ShareHandlerActivity, "TwiCopy: $errorMsg", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Tweet", text)
        clipboard.setPrimaryClip(clip)
    }
}
