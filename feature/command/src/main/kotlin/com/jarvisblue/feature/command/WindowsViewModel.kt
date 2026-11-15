package com.jarvisblue.feature.command

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jarvisblue.core.database.PeerRepository
import com.jarvisblue.core.model.BridgeJob
import com.jarvisblue.core.model.BridgeJobKind
import com.jarvisblue.core.model.BridgeRequest
import com.jarvisblue.core.model.PcState
import com.jarvisblue.core.model.PcStatus
import com.jarvisblue.core.model.WindowsPeer
import com.jarvisblue.core.security.SecretVault
import com.jarvisblue.core.windows.BridgeClient
import com.jarvisblue.core.windows.PairingCode
import com.jarvisblue.core.windows.PairingCodeError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WindowsUiState(
    val peer: WindowsPeer? = null,
    val hostInput: String = "",
    val portInput: String = "8765",
    val secretInput: String = "",
    val codeInput: String = "",
    val status: PcStatus? = null,
    val jobs: List<BridgeJob> = emptyList(),
    val isBusy: Boolean = false,
    val statusLine: String = "Not paired",
    val error: String? = null,
    val lastEvent: String? = null,
)

@HiltViewModel
class WindowsViewModel @Inject constructor(
    private val bridgeClient: BridgeClient,
    private val secretVault: SecretVault,
    private val peerRepository: PeerRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(WindowsUiState())
    val state: StateFlow<WindowsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            peerRepository.observeAll().collect { peers ->
                _state.value = _state.value.copy(peer = peers.firstOrNull())
            }
        }
    }

    fun setHost(host: String) {
        _state.value = _state.value.copy(hostInput = host)
    }

    fun setPort(port: String) {
        _state.value = _state.value.copy(portInput = port.filter { it.isDigit() })
    }

    fun setSecret(secret: String) {
        _state.value = _state.value.copy(secretInput = secret)
    }

    /** The whole pairing code, as the desktop shows it. */
    fun setCode(code: String) {
        _state.value = _state.value.copy(codeInput = code)
    }

    /**
     * Pair with the code the desktop showed.
     *
     * The code carries the address, the secret, the expiry and the
     * fingerprint of the desktop certificate. The fingerprint is kept so the
     * phone trusts that certificate and nothing else.
     */
    fun pairWithCode() {
        val code = _state.value.codeInput.trim()
        if (code.isEmpty()) {
            _state.value = _state.value.copy(error = "Paste the pairing code from the desktop")
            return
        }
        val pairing = try {
            PairingCode.parse(code)
        } catch (error: PairingCodeError) {
            _state.value = _state.value.copy(error = error.message)
            return
        }
        viewModelScope.launch {
            secretVault.put(SecretVault.ALIAS_WINDOWS_PEER, pairing.secret)
            if (pairing.fingerprint.isNotEmpty()) {
                secretVault.put(SecretVault.ALIAS_WINDOWS_FINGERPRINT, pairing.fingerprint)
            }
            val peer = WindowsPeer(
                peerId = "windows-${pairing.host.lowercase()}",
                displayName = "JARVIS BLUE desktop",
                host = pairing.host,
                port = pairing.port,
                pairedAtEpochMs = System.currentTimeMillis(),
                lastSeenEpochMs = 0L,
                sharedSecretAlias = SecretVault.ALIAS_WINDOWS_PEER,
                trusted = true,
            )
            peerRepository.save(peer)
            _state.value = _state.value.copy(
                codeInput = "",
                secretInput = "",
                hostInput = pairing.host,
                portInput = pairing.port.toString(),
                statusLine = "Paired with ${pairing.host}:${pairing.port}",
                error = null,
            )
            refresh()
        }
    }

    /** Stores the shared secret in the Keystore-backed vault and remembers the peer. */
    fun pair() {
        val host = _state.value.hostInput.trim()
        val port = _state.value.portInput.toIntOrNull() ?: 8765
        val secret = _state.value.secretInput.trim()
        if (host.isEmpty() || secret.isEmpty()) {
            _state.value = _state.value.copy(error = "A host and the pairing secret are required")
            return
        }
        viewModelScope.launch {
            secretVault.put(SecretVault.ALIAS_WINDOWS_PEER, secret)
            val peer = WindowsPeer(
                peerId = "windows-${host.lowercase()}",
                displayName = "JARVIS BLUE desktop",
                host = host,
                port = port,
                pairedAtEpochMs = System.currentTimeMillis(),
                lastSeenEpochMs = 0L,
                sharedSecretAlias = SecretVault.ALIAS_WINDOWS_PEER,
                trusted = true,
            )
            peerRepository.save(peer)
            _state.value = _state.value.copy(
                secretInput = "",
                statusLine = "Paired with $host:$port",
                error = null,
            )
            refresh()
        }
    }

    fun unpair() {
        viewModelScope.launch {
            secretVault.remove(SecretVault.ALIAS_WINDOWS_PEER)
            secretVault.remove(SecretVault.ALIAS_WINDOWS_FINGERPRINT)
            _state.value.peer?.let { peerRepository.delete(it.peerId) }
            _state.value = _state.value.copy(
                peer = null,
                status = null,
                jobs = emptyList(),
                statusLine = "Unpaired — the pairing secret was deleted",
            )
        }
    }

    fun refresh() {
        val peer = _state.value.peer ?: run {
            _state.value = _state.value.copy(statusLine = "Pair with a Windows machine first")
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(isBusy = true)
            val base = baseUrl(peer)
            val statusResult = bridgeClient.status(base)
            val jobsResult = bridgeClient.jobs(base)
            _state.value = _state.value.copy(
                status = statusResult.valueOrNull,
                jobs = jobsResult.valueOrNull ?: _state.value.jobs,
                isBusy = false,
                statusLine = statusResult.errorOrNull?.message ?: "Status updated",
                error = statusResult.errorOrNull?.message,
            )
        }
    }

    fun submitJob(title: String, kind: BridgeJobKind) {
        val peer = _state.value.peer ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(isBusy = true)
            val result = bridgeClient.submitJob(
                baseUrl(peer),
                BridgeRequest(kind = kind, title = title, payloadJson = "{}", requireApproval = true),
            )
            _state.value = _state.value.copy(
                isBusy = false,
                statusLine = result.errorOrNull?.message ?: "Job submitted",
                error = result.errorOrNull?.message,
            )
            refresh()
        }
    }

    fun cancelJob(jobId: String) {
        val peer = _state.value.peer ?: return
        viewModelScope.launch {
            val result = bridgeClient.cancelJob(baseUrl(peer), jobId)
            _state.value = _state.value.copy(
                statusLine = result.errorOrNull?.message
                    ?: if (result.valueOrNull == true) "Job cancelled" else "Cancellation was refused",
            )
            refresh()
        }
    }

    /** Polls the bridge so the dashboard reflects the desktop in near real time. */
    fun startPolling(intervalSeconds: Long = 5) {
        viewModelScope.launch {
            while (isActive) {
                refresh()
                delay(intervalSeconds * 1000)
            }
        }
    }

    private fun baseUrl(peer: WindowsPeer): String =
        if (peer.host.startsWith("http://") || peer.host.startsWith("https://")) {
            "${peer.host.trimEnd('/')}:${peer.port}"
        } else {
            "http://${peer.host}:${peer.port}"
        }
}
