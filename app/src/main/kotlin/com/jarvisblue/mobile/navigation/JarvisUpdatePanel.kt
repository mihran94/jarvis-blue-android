package com.jarvisblue.mobile.navigation

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/** Checks published stable releases. Never silently installs an APK or uses CI debug artifacts. */
@Composable
fun JarvisUpdatePanel() {
    val context = LocalContext.current
    var latest by remember { mutableStateOf<ReleaseInfo?>(null) }
    var message by remember { mutableStateOf("Checking for updates…") }
    var updateAvailable by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        try {
            latest = fetchLatestJarvisRelease()
            val current = context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
            message = if (latest == null) "No published stable release yet"
                else if (latest!!.version == current) "You're up to date"
                else "Available: ${latest!!.version} (installed: $current)"
        } catch (_: Exception) {
            message = "Could not check updates. Check internet connection."
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("App updates", style = MaterialTheme.typography.titleMedium)
        Text(message, style = MaterialTheme.typography.bodySmall)
        if (updateAvailable && latest != null) {
            Button(onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(latest!!.url)))
            }, modifier = Modifier.fillMaxWidth()) {
                Text("View verified release on GitHub")
            }
            Text("Only install releases signed with the same trusted app signing key. " +
                "Android will ask before installation.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

private data class ReleaseInfo(val version: String, val url: String)

private suspend fun fetchLatestJarvisRelease(): ReleaseInfo? = withContext(Dispatchers.IO) {
    val request = Request.Builder()
        .url("https://api.github.com/repos/mihran94/jarvis-blue-android/releases/latest")
        .header("Accept", "application/vnd.github+json").build()
    OkHttpClient().newCall(request).execute().use { response ->
        if (response.code == 404) return@withContext null
        if (!response.isSuccessful) error("GitHub HTTP ${response.code}")
        val json = JSONObject(response.body?.string().orEmpty())
        if (json.optBoolean("draft") || json.optBoolean("prerelease")) return@withContext null
        val url = json.optString("html_url")
        if (!url.startsWith("https://github.com/mihran94/jarvis-blue-android/releases/")) return@withContext null
        ReleaseInfo(json.optString("tag_name"), url)
    }
}


/** Compare stable numeric version segments, ignoring local -debug/-diagnostic suffixes. */
private fun isNewerRelease(tag: String, installed: String): Boolean {
    fun parts(value: String): List<Int>? {
        val cleaned = value.trim().removePrefix("v").substringBefore("-")
        if (!Regex("[0-9]+(\\.[0-9]+)*").matches(cleaned)) return null
        return cleaned.split(".").map { it.toIntOrNull() ?: return null }
    }
    val remote = parts(tag) ?: return false
    val local = parts(installed) ?: return false
    for (index in 0 until maxOf(remote.size, local.size)) {
        val a = remote.getOrElse(index) { 0 }
        val b = local.getOrElse(index) { 0 }
        if (a != b) return a > b
    }
    return false
}
