package com.example.twicopy.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.regex.Pattern

class TweetFetcher {

    companion object {
        private val TWEET_URL_PATTERN = Pattern.compile(
            "https?://(?:(?:mobile|www|m)\\.)?(?:twitter\\.com|x\\.com)/(?:#!/)?([a-zA-Z0-9_]+)/status(?:es)?/(\\d+)",
            Pattern.CASE_INSENSITIVE
        )
        private val TWEET_URL_PATTERN_I = Pattern.compile(
            "https?://(?:(?:mobile|www|m)\\.)?(?:twitter\\.com|x\\.com)/i/web/status/(\\d+)",
            Pattern.CASE_INSENSITIVE
        )
        private val SHORT_URL_PATTERN = Pattern.compile(
            "https?://t\\.co/([a-zA-Z0-9]+)",
            Pattern.CASE_INSENSITIVE
        )
        private val GENERAL_URL_PATTERN = Pattern.compile(
            "https?://[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]+",
            Pattern.CASE_INSENSITIVE
        )
    }

    /**
     * テキスト内からTwitter/XのURLまたはツイートIDを抽出する
     */
    suspend fun extractTweetUrl(input: String): String? = withContext(Dispatchers.IO) {
        val trimmed = input.trim()

        // 1. 直マッチ: twitter.com / x.com の status URL
        var matcher = TWEET_URL_PATTERN.matcher(trimmed)
        if (matcher.find()) {
            return@withContext matcher.group(0)
        }

        matcher = TWEET_URL_PATTERN_I.matcher(trimmed)
        if (matcher.find()) {
            return@withContext matcher.group(0)
        }

        // 2. 短縮URL (t.co) の場合、リダイレクト解決
        val shortMatcher = SHORT_URL_PATTERN.matcher(trimmed)
        if (shortMatcher.find()) {
            val tcoUrl = shortMatcher.group(0) ?: return@withContext null
            val resolved = resolveRedirect(tcoUrl)
            val resMatcher = TWEET_URL_PATTERN.matcher(resolved)
            if (resMatcher.find()) {
                return@withContext resMatcher.group(0)
            }
        }

        // 3. テキスト全体から任意のURLを探してリダイレクト解決
        val genMatcher = GENERAL_URL_PATTERN.matcher(trimmed)
        while (genMatcher.find()) {
            val foundUrl = genMatcher.group(0) ?: continue
            val resolved = resolveRedirect(foundUrl)
            val resMatcher = TWEET_URL_PATTERN.matcher(resolved)
            if (resMatcher.find()) {
                return@withContext resMatcher.group(0)
            }
        }

        // 4. 数字のみ（IDのみ）の場合
        if (trimmed.matches(Regex("^\\d{1,25}$"))) {
            return@withContext "https://x.com/i/status/$trimmed"
        }

        null
    }

    fun extractTweetId(url: String): String? {
        var matcher = TWEET_URL_PATTERN.matcher(url)
        if (matcher.find()) {
            return matcher.group(2)
        }
        matcher = TWEET_URL_PATTERN_I.matcher(url)
        if (matcher.find()) {
            return matcher.group(1)
        }
        if (url.matches(Regex("^\\d+$"))) {
            return url
        }
        return null
    }

    fun extractScreenName(url: String): String? {
        val matcher = TWEET_URL_PATTERN.matcher(url)
        if (matcher.find()) {
            val name = matcher.group(1)
            if (name != null && !name.equals("i", ignoreCase = true)) {
                return name
            }
        }
        return null
    }

    /**
     * ツイート情報をフェッチ
     */
    suspend fun fetchTweet(rawInput: String): Result<TweetData> = withContext(Dispatchers.IO) {
        try {
            val tweetUrl = extractTweetUrl(rawInput)
                ?: return@withContext Result.failure(IllegalArgumentException("有効なTwitter/XのURLが見つかりませんでした"))

            val tweetId = extractTweetId(tweetUrl)
                ?: return@withContext Result.failure(IllegalArgumentException("ツイートIDを抽出できませんでした"))

            val screenName = extractScreenName(tweetUrl) ?: "i"

            // 1. vxTwitter API
            val vxResult = fetchFromVxTwitter(screenName, tweetId)
            if (vxResult.isSuccess) {
                return@withContext vxResult
            }

            // 2. FxTwitter API (フォールバック)
            val fxResult = fetchFromFxTwitter(tweetId)
            if (fxResult.isSuccess) {
                return@withContext fxResult
            }

            Result.failure(vxResult.exceptionOrNull() ?: fxResult.exceptionOrNull() ?: Exception("ツイート取得に失敗しました"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun fetchFromVxTwitter(screenName: String, tweetId: String): Result<TweetData> {
        return try {
            val apiUrl = "https://api.vxtwitter.com/$screenName/status/$tweetId"
            val responseBody = httpGet(apiUrl)
            val json = JSONObject(responseBody)
            val tweet = parseVxTweetJson(json, tweetId)
            Result.success(tweet)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseVxTweetJson(json: JSONObject, fallbackId: String): TweetData {
        val id = json.optString("tweetID", fallbackId)
        val userName = json.optString("user_name", "")
        val userScreenName = json.optString("user_screen_name", "")
        val text = json.optString("text", "")
        val tweetUrl = json.optString("tweetURL", "https://x.com/$userScreenName/status/$id")
        val userProfileImageUrl = if (json.has("user_profile_image_url") && !json.isNull("user_profile_image_url")) {
            json.getString("user_profile_image_url")
        } else null

        val dateEpoch = if (json.has("date_epoch") && !json.isNull("date_epoch")) json.getLong("date_epoch") else null
        val dateString = if (json.has("date") && !json.isNull("date")) json.getString("date") else null

        val mediaList = mutableListOf<String>()
        if (json.has("mediaURLs")) {
            val mediaArray = json.optJSONArray("mediaURLs")
            if (mediaArray != null) {
                for (i in 0 until mediaArray.length()) {
                    val raw = mediaArray.optString(i)
                    if (raw.isNotBlank()) {
                        mediaList.add(normalizeImageUrl(raw))
                    }
                }
            }
        }

        if (mediaList.isEmpty() && json.has("media_extended")) {
            val extArray = json.optJSONArray("media_extended")
            if (extArray != null) {
                for (i in 0 until extArray.length()) {
                    val obj = extArray.optJSONObject(i)
                    val url = obj?.optString("url", "")
                    if (!url.isNullOrBlank()) {
                        mediaList.add(normalizeImageUrl(url))
                    }
                }
            }
        }

        val quoteData: TweetData? = if (json.has("qrt") && !json.isNull("qrt")) {
            val qrtJson = json.optJSONObject("qrt")
            if (qrtJson != null) parseVxTweetJson(qrtJson, "") else null
        } else null

        return TweetData(
            id = id,
            url = tweetUrl,
            userName = userName,
            userScreenName = userScreenName,
            userProfileImageUrl = userProfileImageUrl,
            text = text,
            dateEpoch = dateEpoch,
            dateString = dateString,
            mediaUrls = mediaList,
            quote = quoteData
        )
    }

    private fun fetchFromFxTwitter(tweetId: String): Result<TweetData> {
        return try {
            val apiUrl = "https://api.fxtwitter.com/i/status/$tweetId"
            val responseBody = httpGet(apiUrl)
            val json = JSONObject(responseBody)
            val tweetObj = json.optJSONObject("tweet") ?: return Result.failure(Exception("tweet object missing"))
            val tweet = parseFxTweetJson(tweetObj, tweetId)
            Result.success(tweet)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseFxTweetJson(json: JSONObject, fallbackId: String): TweetData {
        val id = json.optString("id", fallbackId)
        val url = json.optString("url", "https://x.com/i/status/$id")
        val text = json.optString("text", "")
        val dateEpoch = if (json.has("created_timestamp") && !json.isNull("created_timestamp")) json.getLong("created_timestamp") else null
        val dateString = if (json.has("created_at") && !json.isNull("created_at")) json.getString("created_at") else null

        val author = json.optJSONObject("author")
        val userName = author?.optString("name", "") ?: ""
        val userScreenName = author?.optString("screen_name", "") ?: ""
        val avatarUrl = if (author != null && author.has("avatar_url") && !author.isNull("avatar_url")) {
            author.getString("avatar_url")
        } else null

        val mediaList = mutableListOf<String>()
        val media = json.optJSONObject("media")
        if (media != null) {
            val photos = media.optJSONArray("photos")
            if (photos != null) {
                for (i in 0 until photos.length()) {
                    val p = photos.optJSONObject(i)
                    val pUrl = p?.optString("url", "")
                    if (!pUrl.isNullOrBlank()) {
                        mediaList.add(normalizeImageUrl(pUrl))
                    }
                }
            }
            val videos = media.optJSONArray("videos")
            if (videos != null) {
                for (i in 0 until videos.length()) {
                    val v = videos.optJSONObject(i)
                    val thumbUrl = v?.optString("thumbnail_url", "")
                    if (!thumbUrl.isNullOrBlank()) {
                        mediaList.add(normalizeImageUrl(thumbUrl))
                    }
                }
            }
        }

        val quoteData: TweetData? = if (json.has("quote") && !json.isNull("quote")) {
            val quoteObj = json.optJSONObject("quote")
            if (quoteObj != null) parseFxTweetJson(quoteObj, "") else null
        } else null

        return TweetData(
            id = id,
            url = url,
            userName = userName,
            userScreenName = userScreenName,
            userProfileImageUrl = avatarUrl,
            text = text,
            dateEpoch = dateEpoch,
            dateString = dateString,
            mediaUrls = mediaList,
            quote = quoteData
        )
    }

    /**
     * 画像URLを直接開ける形式（.jpg 等）に正規化
     */
    fun normalizeImageUrl(url: String): String {
        val clean = url.trim()
        if (clean.contains("pbs.twimg.com/media/")) {
            if (clean.contains("format=")) {
                val base = clean.substringBefore("?")
                val formatMatch = Regex("[?&]format=([a-zA-Z0-9]+)").find(clean)
                val format = formatMatch?.groupValues?.get(1) ?: "jpg"
                return "$base.$format"
            }

            val withoutQuery = clean.substringBefore("?")
            val mediaPath = withoutQuery.substringAfter("pbs.twimg.com/media/")

            if (mediaPath.contains(":")) {
                val basePart = withoutQuery.substringBeforeLast(":")
                val extension = basePart.substringAfterLast(".", "")
                if (extension.isEmpty() || extension.length > 4) {
                    return "$basePart.jpg"
                }
                return basePart
            }

            val ext = withoutQuery.substringAfterLast(".", "")
            if (ext.isEmpty() || ext.contains("/") || ext.length > 4) {
                return "$withoutQuery.jpg"
            }
            return withoutQuery
        }
        return clean
    }

    private fun httpGet(urlString: String): String {
        val url = URL(urlString)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 10000
        conn.readTimeout = 10000
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (compatible; TwiCopy/1.0)")

        val responseCode = conn.responseCode
        if (responseCode !in 200..299) {
            throw Exception("HTTP Error: $responseCode")
        }

        val reader = BufferedReader(InputStreamReader(conn.inputStream, "UTF-8"))
        val sb = StringBuilder()
        var line: String?
        while (reader.readLine().also { line = it } != null) {
            sb.append(line)
        }
        reader.close()
        return sb.toString()
    }

    private fun resolveRedirect(urlString: String): String {
        return try {
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = false
            connection.requestMethod = "HEAD"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.setRequestProperty("User-Agent", "Mozilla/5.0")
            connection.connect()

            val responseCode = connection.responseCode
            if (responseCode in 300..399) {
                val location = connection.getHeaderField("Location")
                if (!location.isNullOrBlank()) {
                    return resolveRedirect(location)
                }
            }
            urlString
        } catch (e: Exception) {
            urlString
        }
    }
}
