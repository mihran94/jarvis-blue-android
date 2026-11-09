package com.jarvisblue.feature.command

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jarvisblue.core.database.AuditDao
import com.jarvisblue.core.database.toDomain
import com.jarvisblue.core.database.MemoryRepository
import com.jarvisblue.core.database.ProviderConfigRepository
import com.jarvisblue.core.model.AuditEvent
import com.jarvisblue.core.model.ProviderConfig
import com.jarvisblue.core.model.ProviderHealth
import com.jarvisblue.core.model.ProviderId
import com.jarvisblue.core.security.AppLockController
import com.jarvisblue.core.security.EmergencyStop
import com.jarvisblue.core.security.KeystoreKeyManager
import com.jarvisblue.core.security.SecretVault
import com.jarvisblue.core.ai.AiRouter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProviderRow(
    val providerId: ProviderId,
    val enabled: Boolean,
    val baseUrl: String,
    val modelId: String,
    val hasKey: Boolean,
    val maskedKey: String?,
    val health: ProviderHealth?,
)

data class SettingsUiState(
    val providers: List<ProviderRow> = emptyList(),
    val darkTheme: Boolean = true,
    val appLockEnabled: Boolean = false,
    val biometricRequired: Boolean = true,
    val sessionTimeoutMinutes: Int = 15,
    val hardwareBackedKey: Boolean = false,
    val auditEvents: List<AuditEvent> = emptyList(),
    val statusLine: String = "Ready",
    val error: String? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val secretVault: SecretVault,
    private val providerConfigRepository: ProviderConfigRepository,
    private val auditDao: AuditDao,
    private val memoryRepository: MemoryRepository,
    private val appLock: AppLockController,
    private val emergencyStop: EmergencyStop,
    private val keyManager: KeystoreKeyManager,
    private val router: AiRouter,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        _state.value = _state.value.copy(
            darkTheme = context.getSharedPreferences("jarvis_ui", Context.MODE_PRIVATE)
                .getBoolean("dark_theme", true),
        )
        viewModelScope.launch { refreshProviders() }
        viewModelScope.launch { refreshAudit() }
        _state.value = _state.value.copy(hardwareBackedKey = keyManager.isHardwareBacked)
    }

    private suspend fun refreshProviders() {
        val configs = providerConfigRepository.observeAll().first()
        val visibleProviders = listOf(ProviderId.GEMINI, ProviderId.OPENAI, ProviderId.MINIMAX, ProviderId.OPENAI_COMPATIBLE)
        val rows = visibleProviders.map { providerId ->
            val config = configs.firstOrNull { it.providerId == providerId } ?: ProviderConfig(providerId)
            val alias = when (providerId) {
                ProviderId.OPENAI -> SecretVault.ALIAS_OPENAI
                ProviderId.GEMINI -> SecretVault.ALIAS_GEMINI
                ProviderId.MINIMAX -> SecretVault.ALIAS_MINIMAX
                ProviderId.OLLAMA -> null
                ProviderId.OPENAI_COMPATIBLE -> SecretVault.ALIAS_OPENAI_COMPATIBLE
                ProviderId.OFFLINE_ECHO -> null
            }
            ProviderRow(
                providerId = providerId,
                enabled = config.enabled,
                baseUrl = config.baseUrl?.takeIf { it.isNotBlank() } ?: when (providerId) {
                    ProviderId.GEMINI -> "https://generativelanguage.googleapis.com/v1beta"
                    ProviderId.OPENAI -> "https://api.openai.com/v1"
                    ProviderId.MINIMAX -> "https://api.minimax.io/v1"
                    else -> ""
                },
                modelId = config.defaultModelId?.takeIf { it.isNotBlank() }?.let { saved ->
                    if (providerId == ProviderId.GEMINI && saved == "gemini-2.0-flash")
                        "gemini-3.8-flash" else saved
                } ?: when (providerId) {
                    ProviderId.GEMINI -> "gemini-3.8-flash"
                    ProviderId.OPENAI -> "gpt-4o-mini"
                    ProviderId.MINIMAX -> "MiniMax-M3.1-Flash"
                    else -> ""
                },
                hasKey = alias?.let { secretVault.has(it) } == true,
                maskedKey = alias?.let { secretVault.masked(it) },
                health = router.cachedHealth()[providerId],
            )
        }
        _state.value = _state.value.copy(providers = rows)
    }

    private suspend fun refreshAudit() {
        _state.value = _state.value.copy(auditEvents = auditDao.recent(200).map { it.toDomain() })
    }

    fun setProviderEnabled(providerId: ProviderId, enabled: Boolean) {
        viewModelScope.launch {
            val existing = providerConfigRepository.observeAll().first().firstOrNull { it.providerId == providerId }
            providerConfigRepository.save(
                (existing ?: ProviderConfig(providerId)).copy(enabled = enabled),
            )
            router.invalidateHealth(providerId)
            refreshProviders()
        }
    }

    /** Persist URL and model together to avoid racing two independent writes. */
    fun setProviderConnection(providerId: ProviderId, baseUrl: String, modelId: String) {
        viewModelScope.launch {
            val normalizedUrl = baseUrl.trim().trimEnd('/')
            if (normalizedUrl.isNotEmpty() && !normalizedUrl.startsWith("https://", ignoreCase = true)) {
                _state.value = _state.value.copy(
                    statusLine = "Base URL must use HTTPS",
                    error = "Use a trusted HTTPS provider endpoint",
                )
                return@launch
            }
            if (providerId == ProviderId.OPENAI_COMPATIBLE && normalizedUrl.isBlank()) {
                _state.value = _state.value.copy(statusLine = "Custom endpoint needs an HTTPS URL")
                return@launch
            }
            val existing = providerConfigRepository.observeAll().first().firstOrNull { it.providerId == providerId }
            providerConfigRepository.save(
                (existing ?: ProviderConfig(providerId)).copy(
                    baseUrl = normalizedUrl,
                    defaultModelId = modelId.trim(),
                ),
            )
            router.invalidateHealth(providerId)
            refreshProviders()
            _state.value = _state.value.copy(statusLine = "Provider settings saved. Run Health check.", error = null)
        }
    }

    fun saveApiKey(providerId: ProviderId, key: String) {
        val alias = when (providerId) {
            ProviderId.OPENAI -> SecretVault.ALIAS_OPENAI
            ProviderId.GEMINI -> SecretVault.ALIAS_GEMINI
            ProviderId.MINIMAX -> SecretVault.ALIAS_MINIMAX
            ProviderId.OLLAMA -> null
            ProviderId.OPENAI_COMPATIBLE -> SecretVault.ALIAS_OPENAI_COMPATIBLE
            ProviderId.OFFLINE_ECHO -> null
        } ?: return
        viewModelScope.launch {
            if (key.isBlank()) return@launch
            secretVault.put(alias, key.trim())
            router.invalidateHealth(providerId)
            refreshProviders()
            _state.value = _state.value.copy(statusLine = "Key for ${providerId.displayName} saved in the Keystore vault")
        }
    }

    fun runHealthCheck() {
        _state.value = _state.value.copy(statusLine = "Checking provider health…")
        viewModelScope.launch {
            val health = router.refreshHealth()
            refreshProviders()
            _state.value = _state.value.copy(
                statusLine = "Health check complete — " +
                    health.entries.count { it.value.usable } + " provider(s) reachable",
            )
        }
    }

    fun setDarkTheme(dark: Boolean) {
        context.getSharedPreferences("jarvis_ui", Context.MODE_PRIVATE)
            .edit().putBoolean("dark_theme", dark).apply()
        _state.value = _state.value.copy(darkTheme = dark)
    }

    fun setAppLockEnabled(enabled: Boolean) {
        appLock.appLockEnabled = enabled
        _state.value = _state.value.copy(appLockEnabled = enabled)
    }

    fun setBiometricRequired(enabled: Boolean) {
        appLock.biometricRequiredForTools = enabled
        _state.value = _state.value.copy(biometricRequired = enabled)
    }

    fun setSessionTimeout(minutes: Int) {
        appLock.sessionTimeoutMinutes = minutes
        _state.value = _state.value.copy(sessionTimeoutMinutes = minutes)
    }

    fun clearMemory() {
        viewModelScope.launch {
            memoryRepository.clearAll()
            _state.value = _state.value.copy(statusLine = "Personal memory deleted")
        }
    }

    fun clearAudit() {
        viewModelScope.launch {
            auditDao.clear()
            refreshAudit()
            _state.value = _state.value.copy(statusLine = "Audit log cleared")
        }
    }

    fun exportAuditText(): String {
        val builder = StringBuilder()
        builder.appendLine("JARVIS BLUE MOBILE — audit export")
        builder.appendLine("Generated: ${java.time.Instant.now()}")
        builder.appendLine("=".repeat(72))
        _state.value.auditEvents.forEach { event ->
            builder.appendLine(
                "[${event.timestampEpochMs}] ${event.severity} ${event.category} :: ${event.title}"
            )
            if (event.detail.isNotBlank()) builder.appendLine("    ${event.detail}")
            if (event.toolName != null) builder.appendLine("    tool=${event.toolName} risk=${event.riskLevel}")
        }
        return builder.toString()
    }

    fun engageEmergencyStop() {
        emergencyStop.engage()
        _state.value = _state.value.copy(statusLine = "Emergency stop engaged")
    }

    fun releaseEmergencyStop() {
        emergencyStop.release()
        _state.value = _state.value.copy(statusLine = "Emergency stop released")
    }
}
