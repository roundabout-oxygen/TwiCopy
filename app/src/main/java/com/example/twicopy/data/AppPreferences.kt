package com.example.twicopy.data

import android.content.Context
import android.content.SharedPreferences

class AppPreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("twicopy_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_OUTPUT_MODE = "output_mode"
        private const val KEY_EMOJI_LEVEL = "emoji_removal_level"
        private const val KEY_AUTO_REMOVE_BLANKS = "auto_remove_blanks"
        private const val KEY_INCLUDE_QUOTE = "include_quote"
        private const val KEY_SWAP_QUOTE = "swap_quote"
        private const val KEY_DATE_FORMAT = "date_format"
    }

    fun loadPreferences(): UserPreferences {
        val outputModeName = prefs.getString(KEY_OUTPUT_MODE, OutputMode.NORMAL.name) ?: OutputMode.NORMAL.name
        val emojiLevelName = prefs.getString(KEY_EMOJI_LEVEL, EmojiRemovalLevel.NONE.name) ?: EmojiRemovalLevel.NONE.name
        val autoRemoveBlanks = prefs.getBoolean(KEY_AUTO_REMOVE_BLANKS, false)
        val includeQuote = prefs.getBoolean(KEY_INCLUDE_QUOTE, false)
        val swapQuote = prefs.getBoolean(KEY_SWAP_QUOTE, false)
        val dateFormat = prefs.getString(KEY_DATE_FORMAT, "yyyy/MM/dd HH:mm:ss") ?: "yyyy/MM/dd HH:mm:ss"

        return UserPreferences(
            outputMode = runCatching { OutputMode.valueOf(outputModeName) }.getOrDefault(OutputMode.NORMAL),
            emojiRemovalLevel = runCatching { EmojiRemovalLevel.valueOf(emojiLevelName) }.getOrDefault(EmojiRemovalLevel.NONE),
            autoRemoveBlankLines = autoRemoveBlanks,
            includeQuoteTweet = includeQuote,
            swapQuotePosition = swapQuote,
            dateFormat = dateFormat
        )
    }

    fun savePreferences(preferences: UserPreferences) {
        prefs.edit().apply {
            putString(KEY_OUTPUT_MODE, preferences.outputMode.name)
            putString(KEY_EMOJI_LEVEL, preferences.emojiRemovalLevel.name)
            putBoolean(KEY_AUTO_REMOVE_BLANKS, preferences.autoRemoveBlankLines)
            putBoolean(KEY_INCLUDE_QUOTE, preferences.includeQuoteTweet)
            putBoolean(KEY_SWAP_QUOTE, preferences.swapQuotePosition)
            putString(KEY_DATE_FORMAT, preferences.dateFormat)
            apply()
        }
    }
}
