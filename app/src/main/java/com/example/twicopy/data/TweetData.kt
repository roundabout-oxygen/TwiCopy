package com.example.twicopy.data

/**
 * ツイートの構造化データ
 */
data class TweetData(
    val id: String,
    val url: String,
    val userName: String,
    val userScreenName: String,
    val userProfileImageUrl: String? = null,
    val text: String,
    val dateEpoch: Long? = null,
    val dateString: String? = null,
    val mediaUrls: List<String> = emptyList(),
    val quote: TweetData? = null
)

/**
 * 出力モード
 */
enum class OutputMode(val label: String) {
    NORMAL("通常（本文＋画像URL）"),
    IMAGE_ONLY("画像URLのみ"),
    NO_IMAGE("画像URLなし")
}

/**
 * 絵文字除去レベル
 */
enum class EmojiRemovalLevel(val label: String) {
    NONE("0 - すべての絵文字を残す"),
    COMPOSITE("1 - 合成絵文字を取り除く"),
    FULL_FILTER("2 - 絵文字・特殊文字を全消去（掲示板互換）")
}

/**
 * ユーザー設定
 */
data class UserPreferences(
    val outputMode: OutputMode = OutputMode.NORMAL,
    val emojiRemovalLevel: EmojiRemovalLevel = EmojiRemovalLevel.NONE,
    val autoRemoveBlankLines: Boolean = false,
    val includeQuoteTweet: Boolean = false,
    val swapQuotePosition: Boolean = false,
    val dateFormat: String = "yyyy/MM/dd HH:mm:ss"
)
