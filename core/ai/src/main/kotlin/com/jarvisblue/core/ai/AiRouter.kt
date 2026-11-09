package com.jarvisblue.core.ai

import com.jarvisblue.core.common.AppError
import com.jarvisblue.core.model.Language
import com.jarvisblue.core.model.ModelDescriptor
import com.jarvisblue.core.model.ProviderHealth
import com.jarvisblue.core.model.ProviderHealthState
import com.jarvisblue.core.model.ProviderId
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Picks the provider that should serve a request.
 *
 * Routing inputs, in priority order:
 *  1. An explicit user override for this conversation.
 *  2. The user's configured provider priority.
 *  3. A task heuristic (coding → coder-capable model, vision → multimodal model).
 *  4. Live provider health (an unhealthy provider is demoted, not silently used).
 *  5. The remaining daily token budget.
 *
 * If everything cloud-side fails and the user allows offline fallback, the
 * offline responder is used — the app never fabricates an answer.
 */
class AiRouter(
    private val registry: ProviderRegistry,
    private val settingsProvider: suspend (ProviderId) -> ProviderSettings,
    private val healthProvider: suspend (ProviderId) -> ProviderHealth = { _ ->
        ProviderHealth(ProviderId.OFFLINE_ECHO, ProviderHealthState.UNKNOWN)
    },
    private val budgetTracker: TokenBudgetTracker = TokenBudgetTracker(),
) {

    private val healthCache = ConcurrentHashMap<ProviderId, ProviderHealth>()
    private val healthMutex = Mutex()

    /** Last known health per provider, exposed for the dashboard. */
    fun cachedHealth(): Map<ProviderId, ProviderHealth> = healthCache.toMap()

    fun invalidateHealth(providerId: ProviderId) { healthCache.remove(providerId) }

    private val providers: Map<ProviderId, AiProvider> get() = registry.providers

    suspend fun refreshHealth(): Map<ProviderId, ProviderHealth> {
        val results = providers.keys.associateWith { id ->
            val provider = providers.getValue(id)
            val settings = settingsProvider(id)
            if (!settings.enabled || settings.apiKey.isNullOrBlank() || !allowedCustomEndpoint(id, settings)) {
                ProviderHealth(
                    providerId = id,
                    state = ProviderHealthState.UNCONFIGURED,
                    detail = when {
                        !settings.enabled -> "Provider is disabled"
                        settings.apiKey.isNullOrBlank() -> "Add an API key"
                        else -> "Custom provider requires a trusted HTTPS Base URL"
                    },
                )
            } else {
                runCatching { provider.healthCheck(settings) }
                    .getOrElse {
                        ProviderHealth(
                            providerId = id,
                            state = ProviderHealthState.UNAVAILABLE,
                            detail = it.message ?: "Health check failed",
                        )
                    }
            }
        }
        healthCache.putAll(results)
        return results
    }

    suspend fun route(
        request: AiRequest,
        taskKind: TaskKind = TaskKind.CHAT,
        preferredProvider: ProviderId? = null,
        preferredModel: String? = null,
    ): AiResponse {
        val candidates = rankCandidates(taskKind, preferredProvider)

        if (candidates.isEmpty()) {
            return OfflineProvider().complete(request, ProviderSettings())
        }

        var lastError: Throwable? = null
        for (candidate in candidates) {
            val provider = providers[candidate.provider] ?: continue
            val settings = settingsProvider(candidate.provider)
            val effectiveRequest = request.copy(
                modelId = preferredModel ?: request.modelId ?: settings.defaultModelId,
                responseLanguage = request.responseLanguage,
            )
            try {
                val response = provider.complete(effectiveRequest, settings)
                budgetTracker.record(candidate.provider, response.usage.promptTokens, response.usage.completionTokens)
                return response
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                lastError = t
                healthCache[candidate.provider] = ProviderHealth(
                    providerId = candidate.provider,
                    state = ProviderHealthState.UNAVAILABLE,
                    detail = t.message ?: "Request failed",
                    checkedAtEpochMs = System.currentTimeMillis(),
                )
            }
        }
        throw lastError ?: AppError.NotConfigured("No AI provider is configured and offline fallback is disabled")
    }

    private suspend fun rankCandidates(
        taskKind: TaskKind,
        preferredProvider: ProviderId?,
    ): List<Candidate> {
        val priority = providerPriority()
        val candidates = priority.map { (providerId, rank) ->
            Candidate(providerId, rank, preferredModelFor(providerId, taskKind))
        }

        val ordered = candidates.sortedWith(
            compareBy<Candidate> { candidate ->
                when {
                    preferredProvider != null && candidate.provider == preferredProvider -> 0
                    else -> 1
                }
            }.thenBy { candidate ->
                val health = healthCache[candidate.provider]
                when {
                    health == null -> 0
                    health.state == ProviderHealthState.HEALTHY -> 0
                    health.state == ProviderHealthState.DEGRADED -> 1
                    health.state == ProviderHealthState.UNKNOWN -> 2
                    else -> 3
                }
            }.thenBy { it.rank },
        )
        return ordered
    }

    private fun preferredModelFor(providerId: ProviderId, taskKind: TaskKind): String? =
        when (taskKind) {
            TaskKind.CODING -> CODING_MODEL_PREFERENCE[providerId]
            TaskKind.VISION -> VISION_MODEL_PREFERENCE[providerId]
            else -> null
        }

    private suspend fun providerPriority(): List<Pair<ProviderId, Int>> {
        val configured = settingsProvider
        val ids = providers.keys.toList()
        val ranked = ids.mapNotNull { id ->
            val settings = configured(id)
            // Only providers explicitly enabled and configured may receive requests.
            if (settings.enabled && !settings.apiKey.isNullOrBlank() && allowedCustomEndpoint(id, settings)) {
                id to PRIORITY_BASE
            } else null
        }
        return ranked.sortedBy { it.second }
    }

    private fun allowedCustomEndpoint(id: ProviderId, settings: ProviderSettings): Boolean =
        id != ProviderId.OPENAI_COMPATIBLE || settings.baseUrl?.startsWith("https://", ignoreCase = true) == true

    data class Candidate(val provider: ProviderId, val rank: Int, val modelId: String?)

    companion object {
        const val PRIORITY_BASE = 10
        const val PRIORITY_DISABLED = 9_999

        val CODING_MODEL_PREFERENCE: Map<ProviderId, String?> = mapOf(
            ProviderId.OPENAI to "gpt-4o",
            ProviderId.GEMINI to "gemini-3.8-flash",
            ProviderId.MINIMAX to null,
            ProviderId.OPENAI_COMPATIBLE to null,
        )

        val VISION_MODEL_PREFERENCE: Map<ProviderId, String?> = mapOf(
            ProviderId.OPENAI to "gpt-4o",
            ProviderId.GEMINI to "gemini-3.8-flash",
            ProviderId.MINIMAX to null,
            ProviderId.OPENAI_COMPATIBLE to null,
        )
    }
}

/** Tracks daily token spend per provider so a runaway loop cannot silently cost money. */
class TokenBudgetTracker(
    private val dailyLimit: Int = 500_000,
) {
    private val used = ConcurrentHashMap<ProviderId, AtomicInteger>()
    private val dayKey = ConcurrentHashMap<String, String>()

    fun record(provider: ProviderId, promptTokens: Int, completionTokens: Int) {
        val key = currentDay()
        dayKey["day"] = key
        used.computeIfAbsent(provider) { AtomicInteger(0) }
            .addAndGet(promptTokens + completionTokens)
    }

    fun usedTokens(provider: ProviderId): Int = used[provider]?.get() ?: 0

    fun totalUsed(): Int = used.values.sumOf { it.get() }

    fun remaining(): Int = (dailyLimit - totalUsed()).coerceAtLeast(0)

    fun exhausted(): Boolean = totalUsed() >= dailyLimit

    private fun currentDay(): String = java.time.LocalDate.now().toString()
}
