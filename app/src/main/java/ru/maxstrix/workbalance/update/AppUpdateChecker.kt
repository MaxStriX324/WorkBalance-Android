package ru.maxstrix.workbalance.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

data class AppRelease(
    val version: String,
    val pageUrl: String,
    val notes: String
)

object AppUpdateChecker {
    private const val RELEASES_API =
        "https://api.github.com/repos/MaxStriX324/WorkBalance-Android/releases?per_page=20"

    suspend fun latestRelease(currentVersion: String): AppRelease = withContext(Dispatchers.IO) {
        val connection = URL(RELEASES_API).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 7_000
            connection.readTimeout = 10_000
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            connection.setRequestProperty("User-Agent", "WorkBalance-Android/$currentVersion")

            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                error("GitHub вернул код $responseCode")
            }
            val json = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            selectLatestRelease(json) ?: error("Опубликованные версии не найдены")
        } finally {
            connection.disconnect()
        }
    }

    fun isNewer(latestVersion: String, currentVersion: String): Boolean {
        val latest = parseVersion(latestVersion) ?: return false
        val current = parseVersion(currentVersion) ?: return false
        return compareVersionParts(latest, current) > 0
    }

    internal fun selectLatestRelease(json: String): AppRelease? {
        val releases = JSONArray(json)
        var best: Pair<List<Int>, AppRelease>? = null
        for (index in 0 until releases.length()) {
            val item = releases.getJSONObject(index)
            if (item.optBoolean("draft") || item.optBoolean("prerelease")) continue

            val tag = item.optString("tag_name")
            val versionParts = parseVersion(tag) ?: continue
            val version = versionParts.joinToString(".")
            val release = AppRelease(
                version = version,
                pageUrl = item.optString(
                    "html_url",
                    "https://github.com/MaxStriX324/WorkBalance-Android/releases"
                ),
                notes = item.optString("body")
            )
            if (best == null || compareVersionParts(versionParts, best.first) > 0) {
                best = versionParts to release
            }
        }
        return best?.second
    }

    private fun parseVersion(value: String): List<Int>? {
        val match = Regex("^v?(\\d+)\\.(\\d+)\\.(\\d+)$").matchEntire(value.trim()) ?: return null
        return match.groupValues.drop(1).map { it.toInt() }
    }

    private fun compareVersionParts(left: List<Int>, right: List<Int>): Int {
        for (index in 0 until maxOf(left.size, right.size)) {
            val difference = left.getOrElse(index) { 0 }.compareTo(right.getOrElse(index) { 0 })
            if (difference != 0) return difference
        }
        return 0
    }
}
