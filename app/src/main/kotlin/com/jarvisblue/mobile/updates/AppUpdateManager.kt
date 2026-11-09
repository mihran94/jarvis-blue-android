package com.jarvisblue.mobile.updates

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import com.jarvisblue.mobile.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.ByteArrayOutputStream
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Private APK update channel, opt-in per device.
 * A feed is a HTTPS JSON document published by the project maintainer.
 * There is no unattended install or Play Protect bypass.
 */
internal class AppUpdateManager(private val context: Context) {
    private val prefs = context.getSharedPreferences("verified_app_updates", Context.MODE_PRIVATE)

    val currentVersionName: String get() = BuildConfig.VERSION_NAME
    val currentVersionCode: Long get() = BuildConfig.VERSION_CODE.toLong()
    val packageId: String get() = context.packageName
    val feedUrl: String get() = prefs.getString(
        "update_feed_url",
        if (packageId == "com.jarvisblue.mobile") OFFICIAL_RELEASE_FEED else "",
    ).orEmpty()
    /** Checks and notifications are disabled until the user explicitly opts in. */
    val autoCheckEnabled: Boolean get() = prefs.getBoolean("auto_check_enabled", false)
    val wifiOnly: Boolean get() = prefs.getBoolean("wifi_only", true)
    val lastAutoCheckMessage: String get() = prefs.getString("last_background_check", "Not checked yet").orEmpty()
    val lastAvailableVersion: String get() = prefs.getString("last_available_version", "").orEmpty()

    fun setAutoCheckEnabled(enabled: Boolean) {
        require(!enabled || feedUrl.isNotBlank()) { "Configure a trusted update feed first." }
        prefs.edit().putBoolean("auto_check_enabled", enabled).apply()
    }

    fun setWifiOnly(enabled: Boolean) { prefs.edit().putBoolean("wifi_only", enabled).apply() }

    fun saveCheckStatus(message: String, version: String = "") {
        prefs.edit().putString("last_background_check", message.take(250))
            .putString("last_available_version", version.take(70)).apply()
    }

    fun saveFeedUrl(value: String) {
        val trimmed = value.trim()
        if (trimmed.isNotBlank()) requireHttps(trimmed)
        prefs.edit().putString("update_feed_url", trimmed).apply()
        clearDownloads()
    }

    suspend fun check(): UpdateRelease = withContext(Dispatchers.IO) {
        val feed = feedUrl
        require(feed.isNotBlank()) { "Add your trusted HTTPS update feed URL first." }
        val connection = openHttps(feed)
        try {
            if (connection.responseCode != 200) error("Update feed HTTP ${connection.responseCode}")
            val length = connection.contentLengthLong
            require(length == -1L || length <= MAX_FEED_BYTES) { "Update feed is too large" }
            val bytes = connection.inputStream.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(4096)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    require(output.size() + count <= MAX_FEED_BYTES) { "Update feed is too large" }
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
            require(bytes.size <= MAX_FEED_BYTES) { "Update feed is too large" }
            val json = JSONObject(String(bytes, Charsets.UTF_8))
            val id = json.getString("packageId")
            require(id == packageId) {
                "This feed is for $id, but your installed app is $packageId. Use the matching debug/release feed."
            }
            val code = json.getLong("versionCode")
            val name = json.getString("versionName").trim()
            val apkUrl = json.getString("apkUrl").trim()
            val hash = json.getString("sha256").trim().lowercase()
            require(code > 0 && name.isNotBlank()) { "Invalid update version" }
            require(Regex("[a-f0-9]{64}").matches(hash)) { "Invalid APK SHA-256 in update feed" }
            requireHttps(apkUrl)
            UpdateRelease(id, code, name, apkUrl, hash, json.optString("notes").take(2_000))
        } finally {
            connection.disconnect()
        }
    }

    /** Downloads to app-private cache and verifies hash + package + signer BEFORE offering install. */
    suspend fun downloadAndVerify(release: UpdateRelease, onProgress: (Int) -> Unit): File =
        withContext(Dispatchers.IO) {
            require(release.packageId == packageId) { "Update package ID mismatch" }
            require(release.versionCode > currentVersionCode) { "No newer version is available" }
            requireHttps(release.apkUrl)
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            val temp = File(dir, "download.part.apk")
            val verified = File(dir, "verified-update.apk")
            temp.delete()
            verified.delete()
            val connection = openHttps(release.apkUrl)
            try {
                require(connection.responseCode == 200) { "APK download HTTP ${connection.responseCode}" }
                val advertised = connection.contentLengthLong
                require(advertised == -1L || advertised <= MAX_APK_BYTES) { "APK exceeds size limit" }
                val digest = MessageDigest.getInstance("SHA-256")
                var total = 0L
                connection.inputStream.use { input ->
                    temp.outputStream().buffered().use { output ->
                        val chunk = ByteArray(32 * 1024)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val count = input.read(chunk)
                            if (count < 0) break
                            total += count
                            require(total <= MAX_APK_BYTES) { "APK exceeds size limit" }
                            digest.update(chunk, 0, count)
                            output.write(chunk, 0, count)
                            if (advertised > 0) onProgress((total * 100 / advertised).toInt().coerceIn(0, 100))
                        }
                    }
                }
                require(total >= 1024L) { "Downloaded APK is incomplete" }
                val actual = digest.digest().joinToString("") { "%02x".format(it) }
                require(actual.equals(release.sha256, ignoreCase = true)) { "APK SHA-256 mismatch — update rejected" }
                verifyArchive(temp, release)
                require(temp.renameTo(verified)) { "Cannot prepare verified APK" }
                onProgress(100)
                verified
            } catch (e: Exception) {
                temp.delete()
                verified.delete()
                throw e
            } finally {
                connection.disconnect()
            }
        }

    /** Check the file once more and show Android's normal installer, not a silent install. */
    suspend fun installVerified(file: File, release: UpdateRelease): InstallResult {
        withContext(Dispatchers.IO) {
            require(file.parentFile?.canonicalFile == File(context.cacheDir, "updates").canonicalFile &&
                file.name == "verified-update.apk") { "Untrusted update file location" }
            verifyFileSha256(file, release.sha256)
            verifyArchive(file, release)
        }
        return withContext(Dispatchers.Main) {
            if (!context.packageManager.canRequestPackageInstalls()) {
                val request = Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}"),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(request)
                return@withContext InstallResult.PermissionNeeded
            }
            val contentUri = FileProvider.getUriForFile(
                context, "${context.packageName}.updates.files", file,
            )
            val intent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
                data = contentUri
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            InstallResult.InstallerOpened
        }
    }

    private fun verifyFileSha256(file: File, expected: String) {
        require(file.isFile && file.length() in 1024..MAX_APK_BYTES) { "Verified APK missing" }
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(32 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        require(digest.digest().joinToString("") { "%02x".format(it) } == expected) {
            "Verified APK was modified; download again"
        }
    }

    @Suppress("DEPRECATION")
    private fun verifyArchive(file: File, release: UpdateRelease) {
        val pm = context.packageManager
        val flags = PackageManager.GET_SIGNING_CERTIFICATES
        val archive = pm.getPackageArchiveInfo(file.absolutePath, flags)
            ?: error("Downloaded file is not a valid APK")
        require(archive.packageName == release.packageId) { "APK has the wrong package ID" }
        require(archive.longVersionCode == release.versionCode) { "APK version does not match update feed" }
        require(archive.longVersionCode > currentVersionCode) { "Update is not newer" }
        val installed = pm.getPackageInfo(packageId, flags)
        val known = installed.signingInfo?.apkContentsSigners.orEmpty().map { it.toByteArray() }
        val incoming = archive.signingInfo?.apkContentsSigners.orEmpty().map { it.toByteArray() }
        require(known.isNotEmpty() && incoming.isNotEmpty() &&
            known.any { a -> incoming.any { b -> MessageDigest.isEqual(a, b) } }) {
            "APK signer differs from installed app — update rejected (keep original signing key)"
        }
    }

    private fun clearDownloads() {
        File(context.cacheDir, "updates").listFiles()?.forEach { it.delete() }
    }

    private fun requireHttps(value: String) {
        val url = URL(value)
        require(url.protocol.equals("https", ignoreCase = true) &&
            !url.host.isNullOrBlank() && url.userInfo == null && url.port in listOf(-1, 443)) {
            "Use a trusted HTTPS URL without credentials or custom ports"
        }
    }

    private fun openHttps(value: String): HttpURLConnection {
        var next = value
        repeat(6) {
            requireHttps(next)
            val connection = (URL(next).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 15_000
                readTimeout = 30_000
                setRequestProperty("Accept", "application/json, application/vnd.android.package-archive, */*")
                setRequestProperty("User-Agent", "JARVIS-BLUE-Update-Client/1")
            }
            val status = connection.responseCode
            if (status in listOf(301, 302, 303, 307, 308)) {
                val location = connection.getHeaderField("Location")
                    ?: error("Update redirect has no destination")
                next = URL(URL(next), location).toString()
                connection.disconnect()
            } else {
                return connection
            }
        }
        error("Too many redirects when checking updates")
    }

    private companion object {
        const val OFFICIAL_RELEASE_FEED =
            "https://raw.githubusercontent.com/mihran94/JARVIS-Mobile-Beta/main/update.json"
        const val MAX_FEED_BYTES = 32 * 1024
        const val MAX_APK_BYTES = 300L * 1024 * 1024
    }
}

internal data class UpdateRelease(
    val packageId: String,
    val versionCode: Long,
    val versionName: String,
    val apkUrl: String,
    val sha256: String,
    val notes: String,
)

internal enum class InstallResult { InstallerOpened, PermissionNeeded }
