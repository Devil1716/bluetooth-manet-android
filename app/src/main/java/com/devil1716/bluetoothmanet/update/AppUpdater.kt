package com.devil1716.bluetoothmanet.update

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
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

enum class UpdatePhase {
    IDLE,
    CHECKING,
    UP_TO_DATE,
    AVAILABLE,
    DOWNLOADING,
    READY,
    INSTALLING,
    NEEDS_PERMISSION,
    SIGNATURE_CONFLICT,
    FAILED
}

data class UpdateUi(
    val phase: UpdatePhase = UpdatePhase.IDLE,
    val currentVersion: String = "",
    val availableVersion: String = "",
    val progress: Float = 0f,
    val message: String = "",
    val dismissed: Boolean = false
) {
    val busy: Boolean
        get() = phase == UpdatePhase.CHECKING ||
            phase == UpdatePhase.DOWNLOADING ||
            phase == UpdatePhase.INSTALLING

    val bannerVisible: Boolean
        get() = !dismissed && phase in BANNER_PHASES

    val primaryLabel: String
        get() = when (phase) {
            UpdatePhase.AVAILABLE -> "Update"
            UpdatePhase.DOWNLOADING -> "Downloading"
            UpdatePhase.READY, UpdatePhase.INSTALLING -> "Install"
            UpdatePhase.NEEDS_PERMISSION -> "Allow"
            UpdatePhase.SIGNATURE_CONFLICT -> "Uninstall old app"
            UpdatePhase.FAILED -> "Retry"
            else -> "Check"
        }
}

private val BANNER_PHASES = setOf(
    UpdatePhase.AVAILABLE,
    UpdatePhase.DOWNLOADING,
    UpdatePhase.READY,
    UpdatePhase.INSTALLING,
    UpdatePhase.NEEDS_PERMISSION,
    UpdatePhase.SIGNATURE_CONFLICT,
    UpdatePhase.FAILED
)

fun interface UpdateLog {
    fun log(message: String)
}

fun interface UpdateListener {
    fun onUpdate(state: UpdateUi)
}

object AppUpdater {
    const val ACTION_INSTALL_RESULT = "com.devil1716.bluetoothmanet.INSTALL_UPDATE_RESULT"

    private const val RELEASE_API =
        "https://api.github.com/repos/Devil1716/bluetooth-manet-android/releases/latest"
    private const val FALLBACK_APK_URL =
        "https://github.com/Devil1716/bluetooth-manet-android/releases/latest/download/app-debug.apk"
    private val userAgent = "BluetoothManet/${com.devil1716.bluetoothmanet.BuildConfig.VERSION_NAME} (Android)"
    private val executor = Executors.newSingleThreadExecutor()
    private val busy = AtomicBoolean(false)

    @Volatile private var latestRelease: GithubRelease? = null
    @Volatile private var pendingApk: File? = null
    @Volatile private var lastState: UpdateUi = UpdateUi()

    @JvmStatic
    fun checkAndInstall(activity: Activity, currentVersion: String, log: UpdateLog) {
        start(
            activity = activity,
            currentVersion = currentVersion,
            downloadIfAvailable = true,
            installWhenReady = true,
            listener = logAdapter(currentVersion, log)
        )
    }

    @JvmStatic
    fun checkQuietly(context: Context, currentVersion: String, listener: UpdateListener) {
        start(
            activity = context,
            currentVersion = currentVersion,
            downloadIfAvailable = true,
            installWhenReady = false,
            listener = listener
        )
    }

    @JvmStatic
    fun startFromBanner(activity: Activity, currentVersion: String, listener: UpdateListener) {
        when (lastState.phase) {
            UpdatePhase.READY, UpdatePhase.NEEDS_PERMISSION, UpdatePhase.INSTALLING ->
                continueInstall(activity, listener)
            UpdatePhase.SIGNATURE_CONFLICT -> listener.onUpdate(lastState)
            else -> start(
                activity = activity,
                currentVersion = currentVersion,
                downloadIfAvailable = true,
                installWhenReady = true,
                listener = listener
            )
        }
    }

    @JvmStatic
    fun installPendingIfReady(activity: Activity, listener: UpdateListener) {
        if (lastState.phase == UpdatePhase.NEEDS_PERMISSION && !needsInstallPermission(activity)) {
            continueInstall(activity, listener)
        }
    }

    @JvmStatic
    fun continueInstall(activity: Activity, listener: UpdateListener) {
        val apk = pendingApk ?: apkFile(activity).takeIf { it.exists() && it.length() > 0L }
        if (apk == null) {
            emit(listener, lastState.copy(phase = UpdatePhase.FAILED, message = "Update file missing. Tap Retry."))
            return
        }
        pendingApk = apk
        installOrRequestPermission(activity, apk, listener)
    }

    @JvmStatic
    fun handleInstallResult(context: Context, intent: Intent, listener: UpdateListener) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty()
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                emit(
                    listener,
                    lastState.copy(
                        phase = UpdatePhase.INSTALLING,
                        message = "Confirm the system update prompt."
                    )
                )
                if (confirm != null && context is Activity) {
                    context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }
            PackageInstaller.STATUS_SUCCESS -> {
                pendingApk = null
                emit(
                    listener,
                    lastState.copy(
                        phase = UpdatePhase.UP_TO_DATE,
                        dismissed = true,
                        progress = 1f,
                        message = "Updated to ${lastState.availableVersion}."
                    )
                )
            }
            PackageInstaller.STATUS_FAILURE_CONFLICT,
            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> emit(
                listener,
                lastState.copy(
                    phase = UpdatePhase.SIGNATURE_CONFLICT,
                    message = signatureConflictMessage(lastState.availableVersion)
                )
            )
            PackageInstaller.STATUS_FAILURE_ABORTED -> emit(
                listener,
                lastState.copy(phase = UpdatePhase.READY, message = "Install cancelled. Tap Install to try again.")
            )
            else -> emit(
                listener,
                lastState.copy(
                    phase = UpdatePhase.FAILED,
                    message = "Install failed${if (message.isBlank()) "" else ": $message"}"
                )
            )
        }
    }

    @JvmStatic
    fun unknownSourcesIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")
        )

    @JvmStatic
    fun uninstallIntent(context: Context): Intent =
        Intent(Intent.ACTION_DELETE, Uri.parse("package:${context.packageName}"))

    @JvmStatic
    fun latestApkPage(): String = "https://github.com/Devil1716/bluetooth-manet-android/releases/latest"

    @JvmStatic
    fun signatureConflictMessage(version: String): String =
        "Android blocked this update because an older Mesh APK used a different signing key. " +
            "Copy your node ID, uninstall Mesh, then install $version. Later updates will replace in place."

    internal fun parseRelease(payload: String): GithubRelease {
        val tagName = jsonString(payload, "tag_name") ?: "latest"
        var debugApk: String? = null
        var anyApk: String? = null
        val assetPattern = Regex(
            "\"name\"\\s*:\\s*\"([^\"]+)\"\\s*,\\s*[\\s\\S]*?\"browser_download_url\"\\s*:\\s*\"([^\"]+)\""
        )
        for (match in assetPattern.findAll(payload)) {
            val name = match.groupValues[1]
            val url = match.groupValues[2]
            if (name == "app-debug.apk" && url.isNotBlank()) {
                debugApk = url
                break
            }
            if (anyApk == null && name.endsWith(".apk", ignoreCase = true) && url.isNotBlank()) {
                anyApk = url
            }
        }
        return GithubRelease(
            tagName = tagName,
            versionName = AppVersion.normalize(tagName),
            apkUrl = debugApk ?: anyApk ?: FALLBACK_APK_URL
        )
    }

    private fun jsonString(payload: String, key: String): String? {
        val match = Regex("\"${Regex.escape(key)}\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"").find(payload)
        return match?.groupValues?.get(1)
    }

    internal fun installerFailurePhase(status: Int): UpdatePhase = when (status) {
        PackageInstaller.STATUS_FAILURE_CONFLICT, PackageInstaller.STATUS_FAILURE_INCOMPATIBLE ->
            UpdatePhase.SIGNATURE_CONFLICT
        PackageInstaller.STATUS_FAILURE_ABORTED -> UpdatePhase.READY
        PackageInstaller.STATUS_SUCCESS -> UpdatePhase.UP_TO_DATE
        else -> UpdatePhase.FAILED
    }

    private fun start(
        activity: Context,
        currentVersion: String,
        downloadIfAvailable: Boolean,
        installWhenReady: Boolean,
        listener: UpdateListener
    ) {
        if (!busy.compareAndSet(false, true)) {
            emit(listener, lastState.copy(message = lastState.message.ifBlank { "Update already in progress." }))
            return
        }
        executor.execute {
            try {
                emit(
                    listener,
                    UpdateUi(
                        phase = UpdatePhase.CHECKING,
                        currentVersion = currentVersion,
                        availableVersion = lastState.availableVersion,
                        message = "Checking for a new Mesh version…"
                    )
                )
                val release = runCatching { fetchLatest() }.getOrElse { error ->
                    throw IOException("Could not reach GitHub (${error.message})")
                }
                latestRelease = release
                if (!AppVersion.isNewer(release.versionName, currentVersion)) {
                    pendingApk = null
                    emit(
                        listener,
                        UpdateUi(
                            phase = UpdatePhase.UP_TO_DATE,
                            currentVersion = currentVersion,
                            availableVersion = release.versionName,
                            dismissed = true,
                            message = "You're on the latest Mesh (${release.tagName})."
                        )
                    )
                    return@execute
                }
                val apk = apkFile(activity)
                val marker = versionMarker(activity)
                val alreadyDownloaded = apk.exists() &&
                    apk.length() > 0L &&
                    marker.takeIf { it.exists() }?.readText()?.trim() == release.versionName
                if (alreadyDownloaded) {
                    pendingApk = apk
                    val ready = UpdateUi(
                        phase = if (installWhenReady) UpdatePhase.INSTALLING else UpdatePhase.READY,
                        currentVersion = currentVersion,
                        availableVersion = release.versionName,
                        progress = 1f,
                        message = "Mesh ${release.tagName} is ready to install."
                    )
                    emit(listener, ready)
                    if (installWhenReady && activity is Activity) {
                        installOrRequestPermission(activity, apk, listener)
                    }
                    return@execute
                }
                emit(
                    listener,
                    UpdateUi(
                        phase = if (downloadIfAvailable) UpdatePhase.DOWNLOADING else UpdatePhase.AVAILABLE,
                        currentVersion = currentVersion,
                        availableVersion = release.versionName,
                        message = "Mesh ${release.tagName} is available."
                    )
                )
                if (!downloadIfAvailable) return@execute
                emit(
                    listener,
                    UpdateUi(
                        phase = UpdatePhase.DOWNLOADING,
                        currentVersion = currentVersion,
                        availableVersion = release.versionName,
                        message = "Downloading Mesh ${release.tagName}…"
                    )
                )
                downloadApk(release.apkUrl, apk) { copied, total ->
                    val progress = if (total > 0L) (copied.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f
                    emit(
                        listener,
                        UpdateUi(
                            phase = UpdatePhase.DOWNLOADING,
                            currentVersion = currentVersion,
                            availableVersion = release.versionName,
                            progress = progress,
                            message = if (total > 0L) {
                                "Downloading Mesh ${release.tagName}… ${(progress * 100).toInt()}%"
                            } else {
                                "Downloading Mesh ${release.tagName}…"
                            }
                        )
                    )
                }
                marker.parentFile?.mkdirs()
                marker.writeText(release.versionName)
                pendingApk = apk
                emit(
                    listener,
                    UpdateUi(
                        phase = UpdatePhase.READY,
                        currentVersion = currentVersion,
                        availableVersion = release.versionName,
                        progress = 1f,
                        message = "Mesh ${release.tagName} downloaded. Tap Install."
                    )
                )
                if (installWhenReady && activity is Activity) {
                    installOrRequestPermission(activity, apk, listener)
                }
            } catch (error: Exception) {
                emit(
                    listener,
                    UpdateUi(
                        phase = UpdatePhase.FAILED,
                        currentVersion = currentVersion,
                        availableVersion = lastState.availableVersion,
                        message = "Update failed: ${error.message}"
                    )
                )
            } finally {
                busy.set(false)
            }
        }
    }

    private fun installOrRequestPermission(activity: Activity, apk: File, listener: UpdateListener) {
        if (needsInstallPermission(activity)) {
            emit(
                listener,
                lastState.copy(
                    phase = UpdatePhase.NEEDS_PERMISSION,
                    message = "Allow Mesh to install updates, then return here."
                )
            )
            return
        }
        emit(
            listener,
            lastState.copy(phase = UpdatePhase.INSTALLING, message = "Installing Mesh ${lastState.availableVersion}…")
        )
        try {
            commitSession(activity, apk)
        } catch (error: Exception) {
            emit(
                listener,
                lastState.copy(phase = UpdatePhase.FAILED, message = "Could not start installer: ${error.message}")
            )
        }
    }

    private fun commitSession(activity: Activity, apk: File) {
        val installer = activity.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(activity.packageName)
        params.setSize(apk.length())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val sessionId = installer.createSession(params)
        val session = installer.openSession(sessionId)
        try {
            session.openWrite("mesh-update", 0, apk.length()).use { out ->
                apk.inputStream().use { input -> input.copyTo(out) }
                session.fsync(out)
            }
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val pending = PendingIntent.getBroadcast(
                activity,
                sessionId,
                Intent(ACTION_INSTALL_RESULT).setPackage(activity.packageName),
                flags
            )
            session.commit(pending.intentSender)
        } catch (error: Exception) {
            session.abandon()
            throw error
        }
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
            val total = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                connection.contentLengthLong
            } else {
                connection.contentLength.toLong()
            }
            destination.parentFile?.mkdirs()
            val temp = File(destination.parentFile, "${destination.name}.part")
            connection.inputStream.use { input ->
                temp.outputStream().use { output ->
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
            if (destination.exists()) destination.delete()
            if (!temp.renameTo(destination)) {
                temp.copyTo(destination, overwrite = true)
                temp.delete()
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
                readTimeout = 60_000
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

    private fun needsInstallPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()

    private fun apkFile(context: Context): File = File(File(context.filesDir, "updates"), "mesh-update.apk")

    private fun versionMarker(context: Context): File = File(File(context.filesDir, "updates"), "mesh-update.version")

    private fun emit(listener: UpdateListener, state: UpdateUi) {
        lastState = state
        listener.onUpdate(state)
    }

    private fun logAdapter(currentVersion: String, log: UpdateLog) = UpdateListener { state ->
        lastState = state.copy(currentVersion = currentVersion)
        log.log(state.message)
    }
}
