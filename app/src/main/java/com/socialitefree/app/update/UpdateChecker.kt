package com.socialitefree.app.update

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.socialitefree.app.BuildConfig
import com.socialitefree.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

data class AppUpdateInfo(
    val versionName: String,
    val buildCode: Int,
    val title: String,
    val releaseNotes: String,
    val apkDownloadUrl: String,
    val releasePageUrl: String
)

object UpdateChecker {

    private const val TAG = "UpdateChecker"
    private const val CHANNEL_ID = "sociallite_app_updates"
    private const val NOTIFICATION_ID = 4040

    // Checks GitHub Releases API automatically; when hosting your own website later,
    // point this URL to your hosted JSON endpoint (e.g. https://yoursite.com/version.json).
    private const val GITHUB_LATEST_RELEASE_API =
        "https://api.github.com/repos/aryamaddy/SocialLiteFree/releases/latest"

    suspend fun checkForUpdate(): AppUpdateInfo? = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(GITHUB_LATEST_RELEASE_API).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "SocialLite-Android/${BuildConfig.VERSION_NAME}")
            }

            if (conn.responseCode != 200) {
                Log.w(TAG, "Update check returned HTTP ${conn.responseCode}")
                return@withContext null
            }

            val body = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
            val json = JSONObject(body)

            val tagName = json.optString("tag_name", "") // e.g. v1.0.0-11
            val remoteBuild = parseBuildNumber(tagName)
            if (remoteBuild <= BuildConfig.VERSION_CODE) {
                return@withContext null
            }

            val title = json.optString("name", "SocialLite Update")
            val releaseNotes = json.optString("body", "Bug fixes and new features available.")
            val releasePageUrl = json.optString("html_url", "https://github.com/aryamaddy/SocialLiteFree/releases/latest")

            // Match the exact APK asset for the user's installed edition
            val desiredApkToken = if (BuildConfig.SUPPORTS_NATIVE_BLOCKER) "OriginalAppBlocker" else "WebsiteClean"
            var downloadUrl = releasePageUrl

            val assets = json.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.optJSONObject(i) ?: continue
                    val name = asset.optString("name", "")
                    val url = asset.optString("browser_download_url", "")
                    if (name.contains(desiredApkToken, ignoreCase = true) && url.isNotBlank()) {
                        downloadUrl = url
                        break
                    }
                    if (name.endsWith(".apk", ignoreCase = true) && url.isNotBlank() && downloadUrl == releasePageUrl) {
                        downloadUrl = url
                    }
                }
            }

            AppUpdateInfo(
                versionName = tagName.ifBlank { "Build $remoteBuild" },
                buildCode = remoteBuild,
                title = title,
                releaseNotes = releaseNotes,
                apkDownloadUrl = downloadUrl,
                releasePageUrl = releasePageUrl
            )
        } catch (e: Exception) {
            Log.w(TAG, "Update check failed: ${e.message}")
            null
        } finally {
            conn?.disconnect()
        }
    }

    private fun parseBuildNumber(tag: String): Int {
        // Parses "v1.0.0-11" or "v1.4.0-10" -> 11 or 10
        val dashPart = tag.substringAfterLast('-', "")
        dashPart.toIntOrNull()?.let { return it }
        val digits = Regex("\\d+$").find(tag)?.value
        return digits?.toIntOrNull() ?: 0
    }

    fun showUpdateNotification(context: Context, info: AppUpdateInfo) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "App Updates",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifies you when a new SocialLite version with new features & bug fixes is available."
            }
            manager.createNotificationChannel(channel)
        }

        val downloadIntent = Intent(Intent.ACTION_VIEW, Uri.parse(info.apkDownloadUrl)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            info.buildCode,
            downloadIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("New Update Ready: ${info.title}")
            .setContentText("Tap to update SocialLite for new features & bug fixes!")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("SocialLite ${info.versionName} is out! Update now to get the latest bug fixes and features.")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        manager.notify(NOTIFICATION_ID, notification)
    }

    fun openUpdateLink(context: Context, url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
