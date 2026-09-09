package com.devil1716.bluetoothmanet.update

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.devil1716.bluetoothmanet.BuildConfig
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

data class GithubRelease(
    val tagName: String,
    val versionName: String,
    val apkUrl: String
)

fun interface UpdateLog {
    fun log(message: String)
}

object AppUpdater {
    private const val RELEASE_API =
        "https://api.github.com/repos/Devil1716/bluetooth-manet-android/releases/latest"
    private const val FALLBACK_APK_URL =
        "https://github.com/Devil1716/bluetooth-manet-android/releases/latest/download/app-debug.apk"
    private val userAgent = "BluetoothManet/${BuildConfig.VERSION_NAME} (Android)"
    private val executor = Executors.newSingleThreadExecutor()
    private val busy = AtomicBoolean(false)
    @Volatile private var pendingApk: File? = null

    @JvmStatic
    fun checkAndInstall(activity: Activity, currentVersion: String, log: UpdateLog) {
        if (!busy.compareAndSet(false, true)) {
            log.log("Update already in progress.")
            return
        }
        executor.execute {
            try {
                val pending = pendingApk
                if (pending != null && pending.exists() && pending.length() > 0L) {
                    activity.runOnUiThread { installOrRequestPermission(activity, pending, log) }
                    return@execute
                }
                log.log("Checking GitHub for the latest APK...")
                val release = runCatching { fetchLatest() }.getOrElse { error ->
                    log.log("GitHub API failed (${error.message}). Trying direct APK URL...")
                    GithubRelease("latest", currentVersion, FALLBACK_APK_URL)
                }
                if (release.tagName != "latest" && !AppVersion.isNewer(release.versionName, currentVersion)) {
                    log.log("Already on the latest version (${release.tagName}).")
                    return@execute
                }
                log.log("Downloading ${release.tagName}...")
                val apk = File(activity.cacheDir, "mesh-update.apk")
                downloadApk(release.apkUrl, apk) { copied, total ->
                    if (total > 0L && copied % (512 * 1024) < 16 * 1024) {
                        val percent = (copied * 100L / total).toInt()
                        log.log("Downloading update… $percent%")
                    }
                }
                pendingApk = apk
                log.log("Download complete. Opening installer for ${release.tagName}.")
                activity.runOnUiThread { installOrRequestPermission(activity, apk, log) }
            } catch (error: Exception) {
                log.log("Update failed: ${error.message}")
            } finally {
                busy.set(false)
            }
        }
    }

    @JvmStatic
    fun installPendingIfReady(activity: Activity, log: UpdateLog) {
        val apk = pendingApk ?: return
        if (!apk.exists()) return
        if (needsInstallPermission(activity)) return
        installApk(activity, apk)
        log.log("Opening installer...")
    }

    internal fun parseRelease(payload: String): GithubRelease {
        val release = JSONObject(payload)
        val tagName = release.optString("tag_name", "latest")
        val assets = release.optJSONArray("assets")
        var apkUrl: String? = null
        if (assets != null) {
            for (index in 0 until assets.length()) {
                val asset = assets.getJSONObject(index)
                val name = asset.optString("name")
                val url = asset.optString("browser_download_url")
                if (name == "app-debug.apk" && url.isNotBlank()) {
                    apkUrl = url
                    break
                }
                if (apkUrl == null && name.endsWith(".apk", ignoreCase = true) && url.isNotBlank()) {
                    apkUrl = url
                }
            }
        }
        return GithubRelease(
            tagName = tagName,
            versionName = AppVersion.normalize(tagName),
            apkUrl = apkUrl?.takeIf { it.isNotBlank() } ?: FALLBACK_APK_URL
        )
    }

    private fun fetchLatest(): GithubRelease {
        val connection = openFollowingRedirects(RELEASE_API, "application/vnd.github+json")
        try {
            if (connection.responseCode !in 200..299) {
                throw IOException("GitHub API returned ${connection.responseCode}")
            }
            val payload = connection.inputStream.bufferedReader().use { it.readText() }
            return parseRelease(payload)
        } finally {
            connection.disconnect()
        }
    }

    private fun downloadApk(url: String, destination: File, onProgress: (Long, Long) -> Unit) {
        val connection = openFollowingRedirects(url, "*/*")
        try {
            if (connection.responseCode !in 200..299) {
                throw IOException("APK download returned ${connection.responseCode}")
            }
            val total = connection.contentLength.toLong()
            destination.parentFile?.mkdirs()
            connection.inputStream.use { input ->
                destination.outputStream().use { output ->
                    val buffer = ByteArray(16 * 1024)
                    var copied = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        copied += read
                        onProgress(copied, total)
                    }
                    if (copied <= 0L) throw IOException("Downloaded APK was empty")
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun openFollowingRedirects(url: String, accept: String): HttpURLConnection {
        var current = url
        repeat(8) {
            val connection = (URL(current).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                requestMethod = "GET"
                setRequestProperty("User-Agent", userAgent)
                setRequestProperty("Accept", accept)
                connectTimeout = 15_000
                readTimeout = 30_000
            }
            val code = connection.responseCode
            if (code in 300..399) {
                val location = connection.getHeaderField("Location")
                    ?: throw IOException("Redirect without Location from $current")
                connection.disconnect()
                current = if (location.startsWith("http", ignoreCase = true)) {
                    location
                } else {
                    URL(URL(current), location).toString()
                }
            } else {
                return connection
            }
        }
        throw IOException("Too many redirects for $url")
    }

    private fun installOrRequestPermission(activity: Activity, apk: File, log: UpdateLog) {
        if (needsInstallPermission(activity)) {
            log.log("Allow Mesh to install updates, then return to the app.")
            activity.startActivity(installPermissionIntent(activity))
            return
        }
        installApk(activity, apk)
    }

    private fun needsInstallPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()

    private fun installPermissionIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private fun installApk(activity: Activity, apk: File) {
        val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.files", apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            clipData = ClipData.newRawUri("update", uri)
        }
        activity.startActivity(intent)
    }
}
