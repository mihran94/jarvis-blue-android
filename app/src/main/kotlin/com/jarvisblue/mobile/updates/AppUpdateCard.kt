package com.jarvisblue.mobile.updates

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Alignment
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.jarvisblue.core.ui.components.GlowCard
import com.jarvisblue.core.ui.theme.CyanAccent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.io.File

/** In-app update flow. No APK is installed without an explicit user action and system confirmation. */
@Composable
fun AppUpdateCard() {
    val context = LocalContext.current.applicationContext
    val updater = remember(context) { AppUpdateManager(context) }
    val scope = rememberCoroutineScope()

    var feed by remember { mutableStateOf(updater.feedUrl) }
    var feedDraft by remember { mutableStateOf(feed) }
    var automatic by remember { mutableStateOf(updater.autoCheckEnabled) }
    var unmeteredOnly by remember { mutableStateOf(updater.wifiOnly) }
    var status by remember { mutableStateOf(if (feed.isEmpty()) "Add your trusted update feed to enable in-app updates." else "Checking for updates…") }
    var checking by remember { mutableStateOf(false) }
    var downloading by remember { mutableStateOf(false) }
    var installing by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(0) }
    var available by remember { mutableStateOf<UpdateRelease?>(null) }
    var verifiedFile by remember { mutableStateOf<File?>(null) }

    // Checks once whenever the user opens Settings or changes the feed.
    LaunchedEffect(feed) {
        if (feed.isNotBlank()) {
            checking = true
            try {
                val candidate = updater.check()
                available = candidate.takeIf { it.versionCode > updater.currentVersionCode }
                verifiedFile = null
                status = if (available != null) "Version ${candidate.versionName} is available." else "You're using the latest published version."
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Exception) {
                status = "Update check failed: ${error.message ?: "Network error"}"
                available = null
            } finally {
                checking = false
            }
        }
    }

    GlowCard(
        title = "App updates",
        subtitle = "Installed: ${updater.currentVersionName} (${updater.currentVersionCode}) · ${updater.packageId}",
        accent = CyanAccent,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(status, style = MaterialTheme.typography.bodyMedium)
            OutlinedTextField(
                value = feedDraft,
                onValueChange = { feedDraft = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Trusted HTTPS update feed URL") },
                supportingText = {
                    Text("Production uses JARVIS-Mobile-Beta GitHub Releases. " +
                        "Debug builds require a matching debug feed and signer.")
                },
            )
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val narrow = maxWidth < 355.dp
                @Composable fun SaveFeedButton() {
                    OutlinedButton(
                        enabled = !checking && !downloading && !installing,
                        onClick = {
                            try {
                                updater.saveFeedUrl(feedDraft)
                                feed = updater.feedUrl
                                UpdateCheckScheduler.configure(context)
                                available = null
                                verifiedFile = null
                                status = if (feed.isEmpty()) "Update feed cleared." else "Update feed saved; checking…"
                            } catch (error: Exception) {
                                status = "Cannot save feed: ${error.message}"
                            }
                        },
                    ) { Text("Save feed") }
                }
                @Composable fun CheckNowButton() {
                    Button(
                        enabled = feed.isNotBlank() && !checking && !downloading && !installing,
                        onClick = {
                            scope.launch {
                                checking = true
                                try {
                                    val candidate = updater.check()
                                    available = candidate.takeIf { it.versionCode > updater.currentVersionCode }
                                    verifiedFile = null
                                    status = if (available != null) "Version ${candidate.versionName} is available."
                                        else "You're using the latest published version."
                                } catch (cancel: CancellationException) { throw cancel }
                                catch (error: Exception) {
                                    status = "Update check failed: ${error.message ?: "Network error"}"
                                    available = null
                                } finally { checking = false }
                            }
                        },
                    ) { Text(if (checking) "Checking…" else "Check now") }
                }
                if (narrow) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SaveFeedButton()
                        CheckNowButton()
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SaveFeedButton()
                        CheckNowButton()
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Automatic update checks", style = MaterialTheme.typography.titleSmall)
                    Text("Once daily when connected; download and installation always need approval.",
                        style = MaterialTheme.typography.bodySmall)
                }
                Switch(
                    checked = automatic,
                    enabled = feed.isNotBlank(),
                    onCheckedChange = { value ->
                        try {
                            updater.setAutoCheckEnabled(value)
                            UpdateCheckScheduler.configure(context)
                            automatic = updater.autoCheckEnabled
                            status = if (value) "Daily checks enabled. APK installation remains manual."
                                else "Automatic checks disabled."
                        } catch (error: Exception) { status = "Cannot change checks: ${error.message}" }
                    },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Wi-Fi / unmetered only", style = MaterialTheme.typography.titleSmall)
                    Text("Avoid metered mobile data for update checks.",
                        style = MaterialTheme.typography.bodySmall)
                }
                Switch(
                    checked = unmeteredOnly,
                    onCheckedChange = { value ->
                        updater.setWifiOnly(value)
                        unmeteredOnly = value
                        UpdateCheckScheduler.configure(context)
                    },
                )
            }
            Text("Last background check: ${updater.lastAutoCheckMessage}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            updater.lastAvailableVersion.takeIf { it.isNotBlank() }?.let { version ->
                Text("Update detected in background: $version. Tap Check now to review.",
                    style = MaterialTheme.typography.bodySmall)
            }
            available?.let { release ->
                Text("New version: ${release.versionName} (build ${release.versionCode})", style = MaterialTheme.typography.titleMedium)
                if (release.notes.isNotBlank()) {
                    Text(release.notes, style = MaterialTheme.typography.bodySmall)
                }
                if (downloading) {
                    LinearProgressIndicator(
                        progress = { progress / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text("Downloading and verifying: $progress%")
                }
                Button(
                    enabled = !checking && !downloading && !installing,
                    onClick = {
                        scope.launch {
                            if (verifiedFile == null) {
                                downloading = true
                                progress = 0
                                status = "Downloading APK securely…"
                                try {
                                    verifiedFile = updater.downloadAndVerify(release) { progress = it }
                                    status = "APK hash, package ID, version and signing certificate verified."
                                } catch (cancel: CancellationException) {
                                    throw cancel
                                } catch (error: Exception) {
                                    status = "Update rejected: ${error.message ?: "Download failed"}"
                                } finally {
                                    downloading = false
                                }
                            } else {
                                installing = true
                                try {
                                    val result = updater.installVerified(verifiedFile!!, release)
                                    status = when (result) {
                                        InstallResult.PermissionNeeded ->
                                            "Android Settings opened. Allow installs from JARVIS only if you trust this update; return and tap Install again."
                                        InstallResult.InstallerOpened ->
                                            "Android installer opened. Confirm the update on your phone; Play Protect stays active."
                                    }
                                } catch (cancel: CancellationException) {
                                    throw cancel
                                } catch (error: Exception) {
                                    verifiedFile = null
                                    status = "Installation blocked: ${error.message ?: "APK verification failed"}"
                                } finally {
                                    installing = false
                                }
                            }
                        }
                    },
                ) {
                    Text(when {
                        downloading -> "Downloading…"
                        installing -> "Verifying…"
                        verifiedFile != null -> "Install verified update"
                        else -> "Download & verify APK"
                    })
                }
            }
            Text(
                "Updates require your consent. Android validates the app signature and Play Protect remains enabled. " +
                    "To keep your data, future APKs must use the same package and signing key.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
