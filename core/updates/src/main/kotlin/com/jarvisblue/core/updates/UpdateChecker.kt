package com.jarvisblue.core.updates

import java.security.MessageDigest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The update feed that the release script writes.
 *
 * The same shape is produced by `scripts/create_update_manifest.py` in the
 * Windows repository, so there is one format in the ecosystem.
 */
@Serializable
data class UpdateManifest(
    val packageId: String,
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val sha256: String,
    val notes: String = "",
)

/** Why an update could not be used. */
sealed class UpdateResult {
    /** The app is already on this version or a newer one. */
    data class UpToDate(val versionName: String) : UpdateResult()

    /** A newer version is there and the file behind it matches the digest. */
    data class Available(
        val manifest: UpdateManifest,
        val bytes: ByteArray,
    ) : UpdateResult() {
        override fun equals(other: Any?): Boolean =
            other is Available && other.manifest == manifest && other.bytes.contentEquals(bytes)

        override fun hashCode(): Int = 31 * manifest.hashCode() + bytes.contentHashCode()
    }

    /** The feed says a newer version is there, but the file does not match. */
    data class DigestMismatch(
        val expected: String,
        val actual: String,
    ) : UpdateResult()

    /** The feed is not for this app, or is not usable. */
    data class Refused(val reason: String) : UpdateResult()

    /** The feed could not be read. */
    data class Unreachable(val reason: String) : UpdateResult()
}

/**
 * The update checker.
 *
 * It reads the feed, and it only reports an update when the file behind it
 * matches the SHA-256 that the feed carries. A file that does not match is
 * refused, and nothing is ever installed by this class: the owner decides,
 * and the install needs the system installer.
 */
class UpdateChecker(
    private val packageId: String,
    private val currentVersionCode: Int,
    private val httpClient: okhttp3.OkHttpClient = defaultClient(),
    private val downloader: suspend (String) -> ByteArray = { url -> download(url, httpClient) },
) {

    /** Reads the feed and the file behind it. */
    suspend fun check(feedUrl: String): UpdateResult {
        if (!feedUrl.startsWith("https://")) {
            return UpdateResult.Refused("The update feed address must use HTTPS.")
        }
        val body = try {
            httpClient.newCall(okhttp3.Request.Builder().url(feedUrl).get().build()).execute()
                .use { response ->
                    if (!response.isSuccessful) {
                        return UpdateResult.Unreachable("The feed answered HTTP ${response.code}.")
                    }
                    response.body?.string().orEmpty()
                }
        } catch (error: Exception) {
            return UpdateResult.Unreachable("The feed could not be read: ${error.message}")
        }
        val manifest = try {
            json.decodeFromString(UpdateManifest.serializer(), body)
        } catch (error: Exception) {
            return UpdateResult.Refused("The feed could not be understood: ${error.message}")
        }
        if (manifest.packageId != packageId) {
            return UpdateResult.Refused(
                "The feed is for ${manifest.packageId} and this app is $packageId.",
            )
        }
        if (!manifest.apkUrl.startsWith("https://")) {
            return UpdateResult.Refused("The download address in the feed is not HTTPS.")
        }
        if (manifest.versionCode <= currentVersionCode) {
            return UpdateResult.UpToDate(manifest.versionName)
        }
        val bytes = try {
            downloader(manifest.apkUrl)
        } catch (error: Exception) {
            return UpdateResult.Unreachable("The file could not be read: ${error.message}")
        }
        val actual = sha256(bytes)
        if (!actual.equals(manifest.sha256, ignoreCase = true)) {
            return UpdateResult.DigestMismatch(manifest.sha256.lowercase(), actual)
        }
        return UpdateResult.Available(manifest, bytes)
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        /** The SHA-256 of a file, as lowercase hex. */
        fun sha256(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(bytes)
                .joinToString("") { "%02x".format(it) }

        fun defaultClient(): okhttp3.OkHttpClient = okhttp3.OkHttpClient.Builder()
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        /** The bytes behind an address. */
        fun download(url: String, client: okhttp3.OkHttpClient): ByteArray =
            client.newCall(okhttp3.Request.Builder().url(url).get().build()).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("HTTP ${response.code}")
                }
                response.body?.bytes() ?: ByteArray(0)
            }
    }
}
