package com.example.twicopy.domain

import com.example.twicopy.data.EmojiRemovalLevel
import com.example.twicopy.data.OutputMode
import com.example.twicopy.data.TweetData
import com.example.twicopy.data.UserPreferences
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object TweetFormatter {

    fun format(tweet: TweetData, prefs: UserPreferences): String {
        return when (prefs.outputMode) {
            OutputMode.IMAGE_ONLY -> formatImagesOnly(tweet, prefs)
            OutputMode.NO_IMAGE -> formatStandard(tweet, prefs, includeImages = false)
            OutputMode.NORMAL -> formatStandard(tweet, prefs, includeImages = true)
        }
    }

    private fun formatImagesOnly(tweet: TweetData, prefs: UserPreferences): String {
        val allImages = mutableListOf<String>()
        allImages.addAll(tweet.mediaUrls)
        if (prefs.includeQuoteTweet && tweet.quote != null) {
            allImages.addAll(tweet.quote.mediaUrls)
        }
        return allImages.joinToString("\n")
    }

    private fun formatStandard(tweet: TweetData, prefs: UserPreferences, includeImages: Boolean): String {
        val mainFormatted = formatSingleTweet(tweet, prefs, includeImages = includeImages, isQuote = false)

        var result = if (prefs.includeQuoteTweet && tweet.quote != null) {
            val quoteFormatted = formatSingleTweet(tweet.quote, prefs, includeImages = includeImages, isQuote = true)
            if (prefs.swapQuotePosition) {
                "$quoteFormatted\n\n$mainFormatted\n${tweet.url}"
            } else {
                "$mainFormatted\n\n$quoteFormatted\n\n${tweet.url}"
            }
        } else {
            // 画像URLとTwitter URLの間は改行1つ（空行なし）
            "$mainFormatted\n${tweet.url}"
        }

        result = applyEmojiFilter(result, prefs.emojiRemovalLevel)

        if (prefs.autoRemoveBlankLines) {
            result = removeBlankLines(result)
        }

        return result.trim()
    }

    private fun formatSingleTweet(
        tweet: TweetData,
        prefs: UserPreferences,
        includeImages: Boolean,
        isQuote: Boolean
    ): String {
        val formattedDate = formatDate(tweet.dateEpoch, tweet.dateString, prefs.dateFormat)
        val header = if (isQuote) {
            "[引用元] ${tweet.userName} [@${tweet.userScreenName}] ($formattedDate)"
        } else {
            "${tweet.userName} [@${tweet.userScreenName}] ($formattedDate)"
        }

        val cleanText = cleanTweetText(tweet.text)

        val parts = mutableListOf<String>()
        parts.add(header)
        if (cleanText.isNotBlank()) {
            parts.add(cleanText)
        }

        if (includeImages && tweet.mediaUrls.isNotEmpty()) {
            parts.addAll(tweet.mediaUrls)
        }

        if (isQuote && tweet.url.isNotBlank()) {
            parts.add(tweet.url)
        }

        return parts.joinToString("\n")
    }

    fun formatDate(epochSeconds: Long?, rawDateString: String?, pattern: String): String {
        val sdf = SimpleDateFormat(pattern, Locale.JAPAN).apply {
            timeZone = TimeZone.getTimeZone("Asia/Tokyo")
        }

        if (epochSeconds != null && epochSeconds > 0) {
            return sdf.format(Date(epochSeconds * 1000L))
        }

        if (!rawDateString.isNullOrBlank()) {
            try {
                val inputFormat = SimpleDateFormat("EEE MMM dd HH:mm:ss Z yyyy", Locale.ENGLISH)
                val parsed = inputFormat.parse(rawDateString)
                if (parsed != null) {
                    return sdf.format(parsed)
                }
            } catch (_: Exception) {}

            try {
                val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ENGLISH)
                val parsed = isoFormat.parse(rawDateString.substringBefore("."))
                if (parsed != null) {
                    return sdf.format(parsed)
                }
            } catch (_: Exception) {}
        }

        return sdf.format(Date())
    }

    private fun cleanTweetText(text: String): String {
        return text.replace(Regex("https?://pic\\.twitter\\.com/\\w+"), "").trim()
    }

    fun removeBlankLines(input: String): String {
        return input.lines().map { it.trim() }.filter { it.isNotBlank() }.joinToString("\n")
    }

    fun applyEmojiFilter(input: String, level: EmojiRemovalLevel): String {
        return when (level) {
            EmojiRemovalLevel.NONE -> input
            EmojiRemovalLevel.COMPOSITE -> input.replace(Regex("\\u200D[♂♀]?|[\\uFE0E\\uFE0F]"), "")
            EmojiRemovalLevel.FULL_FILTER -> filter5ch(input)
        }
    }

    fun filter5ch(str: String): String {
        var replaced = str
        val replacePairs = listOf(
            "\\u200D[♂♀]?" to "",
            "[⋯┈]" to "…",
            "〜" to "～",
            "‼️" to "!!",
            "[◦˙]" to "･",
            "[❗️❕]" to "!",
            "⁉" to "!?",
            "💤" to "zzz",
            "[🎵🎶]" to "♪",
            "[⇩👇⇓⇣]" to "↓",
            "🔞" to "(18)",
            "[➡️👉☞⇢➠]" to "→",
            "👈" to "←",
            "[▷▶️▶]" to "|>",
            "↳" to "└",
            "[✞✟]" to "†",
            "[✲❁✴︎]" to "＊",
            "✨" to "☆",
            "[🌟✡✰]" to "☆",
            "[🔻🔽]" to "▼",
            "[⊿🔺🔼]" to "▲",
            "❔" to "？",
            "✧" to "◇",
            "❮" to "<",
            "❯" to ">",
            "🆕" to "[NEW]",
            "🈲" to "[禁]",
            "㊗" to "(祝)",
            "㊙" to "(秘)",
            "🆙" to "[UP]",
            "🆓" to "[FREE]",
            "🆖" to "[NG]",
            "🆗" to "[OK]",
            "⸜" to "\\",
            "⸝" to "/",
            "￤" to "｜",
            "〖" to "【",
            "〗" to "】",
            "✖" to "×",
            "™" to "(TM)",
            "👍" to "ｂ",
            "[\\uFE0E\\uFE0F]" to ""
        )

        for ((pat, rep) in replacePairs) {
            replaced = replaced.replace(Regex(pat), rep)
        }

        val sb = StringBuilder()
        var i = 0
        while (i < replaced.length) {
            val codePoint = replaced.codePointAt(i)
            val charCount = Character.charCount(codePoint)

            val isAscii = codePoint in 0x0020..0x007E || codePoint == 0x000A || codePoint == 0x000D
            val isJapanese = codePoint in 0x3000..0x303F ||
                    codePoint in 0x3040..0x309F ||
                    codePoint in 0x30A0..0x30FF ||
                    codePoint in 0x4E00..0x9FFF ||
                    codePoint in 0xFF00..0xFFEF

            if (isAscii || isJapanese) {
                sb.appendCodePoint(codePoint)
            } else {
                if (charCount == 1) {
                    sb.appendCodePoint(codePoint)
                }
            }
            i += charCount
        }

        return sb.toString().replace(Regex("\\n{3,}"), "\n\n").trim()
    }
}
