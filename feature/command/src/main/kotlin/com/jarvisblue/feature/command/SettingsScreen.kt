package com.jarvisblue.feature.command

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jarvisblue.core.model.ProviderHealthState
import com.jarvisblue.core.model.ProviderId
import com.jarvisblue.core.ui.components.JarvisScaffold
import com.jarvisblue.core.ui.components.NoticeBanner
import com.jarvisblue.core.ui.components.StatusPill
import com.jarvisblue.core.ui.theme.AmberWarn
import com.jarvisblue.core.ui.theme.BlueCore
import com.jarvisblue.core.ui.theme.CyanAccent
import com.jarvisblue.core.ui.theme.GreenOk
import com.jarvisblue.core.ui.theme.RedCritical

@Composable
fun SettingsRoute(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
    topContent: @Composable () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SettingsScreen(
        state = state,
        onProviderEnabled = viewModel::setProviderEnabled,
        onProviderConnection = viewModel::setProviderConnection,
        onSaveKey = viewModel::saveApiKey,
        onHealthCheck = viewModel::runHealthCheck,
        onDarkTheme = viewModel::setDarkTheme,
        onAppLock = viewModel::setAppLockEnabled,
        onBiometric = viewModel::setBiometricRequired,
        onSessionTimeout = viewModel::setSessionTimeout,
        onClearMemory = viewModel::clearMemory,
        onClearAudit = viewModel::clearAudit,
        onEmergencyStop = viewModel::engageEmergencyStop,
        onReleaseStop = viewModel::releaseEmergencyStop,
        topContent = topContent,
        modifier = modifier,
    )
}

/** Friendly settings: one section at a time, with advanced endpoint options hidden by default. */
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onProviderEnabled: (ProviderId, Boolean) -> Unit,
    onProviderConnection: (ProviderId, String, String) -> Unit,
    onSaveKey: (ProviderId, String) -> Unit,
    onHealthCheck: () -> Unit,
    onDarkTheme: (Boolean) -> Unit,
    onAppLock: (Boolean) -> Unit,
    onBiometric: (Boolean) -> Unit,
    onSessionTimeout: (Int) -> Unit,
    onClearMemory: () -> Unit,
    onClearAudit: () -> Unit,
    onEmergencyStop: () -> Unit,
    onReleaseStop: () -> Unit,
    topContent: @Composable () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var expandedSection by rememberSaveable { mutableStateOf("ai") }
    var selectedProvider by remember { mutableStateOf<ProviderId?>(ProviderId.GEMINI) }
    var advancedProvider by remember { mutableStateOf<ProviderId?>(null) }
    var keyDrafts by remember { mutableStateOf<Map<ProviderId, String>>(emptyMap()) }
    var urlDrafts by remember { mutableStateOf<Map<ProviderId, String>>(emptyMap()) }
    var modelDrafts by remember { mutableStateOf<Map<ProviderId, String>>(emptyMap()) }
    var deletionRequest by remember { mutableStateOf<String?>(null) }

    JarvisScaffold(
        title = "Settings",
        subtitle = "Make JARVIS your own",
        modifier = modifier,
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding)
                .verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            NoticeBanner(text = state.statusLine, accent = BlueCore)
            state.error?.let { NoticeBanner(text = it, accent = RedCritical) }

            SettingsCategory(
                title = "AI brain & providers", subtitle = "Gemini, OpenAI and MiniMax",
                icon = Icons.Filled.Psychology, expanded = expandedSection == "ai",
                onToggle = { expandedSection = if (expandedSection == "ai") "" else "ai" },
            ) {
                Text(
                    "Add your own API key and turn on a provider. Connection testing needs internet and may use API credits.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = onHealthCheck) { Text("Test AI connections") }
                state.providers.forEach { provider ->
                    val id = provider.providerId
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                selectedProvider = if (selectedProvider == id) null else id
                            },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(id.displayName, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    if (provider.hasKey) "API key saved securely" else "Add your API key to connect",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            StatusPill(
                                text = when {
                                    !provider.enabled -> "OFF"
                                    !provider.hasKey -> "SET UP"
                                    provider.health == null -> "NOT TESTED"
                                    else -> provider.health.state.name.replace('_', ' ')
                                },
                                color = when (provider.health?.state) {
                                    ProviderHealthState.HEALTHY -> GreenOk
                                    ProviderHealthState.DEGRADED -> AmberWarn
                                    ProviderHealthState.UNAVAILABLE -> RedCritical
                                    else -> BlueCore
                                },
                            )
                            Switch(checked = provider.enabled,
                                onCheckedChange = { onProviderEnabled(id, it) })
                        }
                        if (selectedProvider == id) {
                            provider.health?.detail?.takeIf { it.isNotBlank() }?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            OutlinedTextField(
                                value = keyDrafts[id].orEmpty(),
                                onValueChange = { keyDrafts = keyDrafts + (id to it) },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text(if (provider.hasKey) "Replace API key" else "API key") },
                                placeholder = { Text("Paste your private key") },
                                visualTransformation = PasswordVisualTransformation(),
                                shape = RoundedCornerShape(16.dp), singleLine = true,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Button(
                                    enabled = keyDrafts[id].orEmpty().isNotBlank(),
                                    onClick = {
                                        onSaveKey(id, keyDrafts[id].orEmpty())
                                        keyDrafts = keyDrafts - id
                                    },
                                ) { Text("Save key securely") }
                                TextButton(onClick = {
                                    advancedProvider = if (advancedProvider == id) null else id
                                }) { Text(if (advancedProvider == id) "Hide advanced" else "Advanced") }
                            }
                            if (advancedProvider == id) {
                                Text("Only change these when using a supported provider endpoint.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                OutlinedTextField(
                                    value = urlDrafts[id] ?: provider.baseUrl,
                                    onValueChange = { urlDrafts = urlDrafts + (id to it) },
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text("Base URL (HTTPS)") }, singleLine = true,
                                )
                                OutlinedTextField(
                                    value = modelDrafts[id] ?: provider.modelId,
                                    onValueChange = { modelDrafts = modelDrafts + (id to it) },
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text("Model ID") }, singleLine = true,
                                )
                                OutlinedButton(onClick = {
                                    onProviderConnection(
                                        id, urlDrafts[id] ?: provider.baseUrl,
                                        modelDrafts[id] ?: provider.modelId,
                                    )
                                }) { Text("Save model & URL") }
                            }
                        }
                        if (provider != state.providers.last()) Spacer(Modifier.height(8.dp))
                    }
                }
            }

            SettingsCategory(
                title = "Voice & personality", subtitle = "Tamil and other voice options",
                icon = Icons.Filled.Mic, expanded = expandedSection == "voice",
                onToggle = { expandedSection = if (expandedSection == "voice") "" else "voice" },
            ) {
                Text("From Home, tap Talk to JARVIS. Choose Tamil, English or another supported language. Microphone and wake-word use require your permission.",
                    style = MaterialTheme.typography.bodyMedium)
            }

            SettingsCategory(
                title = "Appearance", subtitle = "Midnight Blue or Arctic Light",
                icon = Icons.Filled.Brightness6, expanded = expandedSection == "appearance",
                onToggle = { expandedSection = if (expandedSection == "appearance") "" else "appearance" },
            ) {
                SettingToggle("Midnight Blue (dark)", "Applies to every screen and is saved on this device",
                    state.darkTheme, onDarkTheme)
            }

            SettingsCategory(
                title = "Gold intelligence", subtitle = "XAU/USD market research",
                icon = Icons.Filled.ShowChart, expanded = expandedSection == "gold",
                onToggle = { expandedSection = if (expandedSection == "gold") "" else "gold" },
            ) {
                Text("Open Home → Gold intelligence for indicators, risk and market analysis. Signals are not guaranteed trades. No automatic broker orders.",
                    style = MaterialTheme.typography.bodyMedium)
            }

            SettingsCategory(
                title = "Exness broker (read-only)",
                subtitle = "Secure broker keys stay on your Windows bridge",
                icon = Icons.Filled.Security, expanded = expandedSection == "broker",
                onToggle = { expandedSection = if (expandedSection == "broker") "" else "broker" },
            ) {
                Text("Exness Trader API uses Ed25519 signed requests and account Access Point discovery. " +
                    "Configure the broker on your trusted Windows bridge, NOT inside the mobile app.",
                    style = MaterialTheme.typography.bodyMedium)
                Text("Status: NOT CONNECTED (run the read-only broker check on your PC). " +
                    "This version does not fetch Exness quotes into Gold and cannot place orders.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Keep secrets off GitHub/APKs. Read EXNESS_SETUP_TAMIL.md before enabling it.",
                    style = MaterialTheme.typography.bodySmall)
            }

            SettingsCategory(
                title = "Privacy & security", subtitle = "App lock, vault and activity history",
                icon = Icons.Filled.Security, expanded = expandedSection == "security",
                onToggle = { expandedSection = if (expandedSection == "security") "" else "security" },
            ) {
                SettingToggle("App lock", "Protect JARVIS when opening the app",
                    state.appLockEnabled, onAppLock)
                SettingToggle("Biometric approval", "Require confirmation for sensitive tools",
                    state.biometricRequired, onBiometric)
                Text("Session timeout", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(5, 15, 60).forEach { minutes ->
                        FilterChip(selected = state.sessionTimeoutMinutes == minutes,
                            onClick = { onSessionTimeout(minutes) },
                            label = { Text("$minutes min") })
                    }
                }
                Text("Keystore: " + (if (state.hardwareBackedKey) "Hardware-backed" else "Software-backed"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = onEmergencyStop) { Text("Emergency stop") }
                OutlinedButton(onClick = onReleaseStop) { Text("Release emergency stop") }
                Text("Your data", style = MaterialTheme.typography.titleSmall)
                OutlinedButton(onClick = { deletionRequest = "memory" }) { Text("Delete all app memories") }
                OutlinedButton(onClick = { deletionRequest = "audit" }) { Text("Clear activity history") }
                Text("Recent activity: ${state.auditEvents.size} events. Sensitive permissions can be revoked in Android Settings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            SettingsCategory(
                title = "App updates & about", subtitle = "Check, verify and install updates",
                icon = Icons.Filled.SystemUpdate, expanded = expandedSection == "updates",
                onToggle = { expandedSection = if (expandedSection == "updates") "" else "updates" },
            ) {
                topContent()
                Text("JARVIS BLUE MOBILE 1.1.4 · Android 12+ · private sideload build",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace)
                Text("APK updates require an HTTPS feed, matching package/signature and Android confirmation. Never bypass Play Protect.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(20.dp))
        }
    }

    deletionRequest?.let { request ->
        AlertDialog(
            onDismissRequest = { deletionRequest = null },
            title = { Text(if (request == "memory") "Delete all memories?" else "Clear activity history?") },
            text = { Text("This cannot be undone. Your API keys are not affected.") },
            confirmButton = {
                TextButton(onClick = {
                    if (request == "memory") onClearMemory() else onClearAudit()
                    deletionRequest = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deletionRequest = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SettingsCategory(
    title: String,
    subtitle: String,
    icon: ImageVector,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp,
            (if (expanded) CyanAccent else MaterialTheme.colorScheme.outline).copy(alpha = 0.5f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(icon, contentDescription = null,
                    tint = if (expanded) CyanAccent else MaterialTheme.colorScheme.primary)
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "Collapse $title" else "Expand $title")
            }
            if (expanded) content()
        }
    }
}

@Composable
private fun SettingToggle(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
