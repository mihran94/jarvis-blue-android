package com.jarvisblue.mobile.navigation

import com.jarvisblue.core.security.SecretVault
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import android.util.Base64
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Explicitly started Gemini Live native-audio session.
 * Personal-use direct Gemini API key is encrypted at rest with Android Keystore.
 * For production distribution use a backend and short-lived tokens instead.
 */
@Composable
fun GeminiLiveRoute(onRequestMicrophone: () -> Unit, microphoneGranted: Boolean) {
    val context = LocalContext.current
    val vault = remember(context) { SecretVault(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var apiKey by remember { mutableStateOf("") }
    var hasSavedKey by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { hasSavedKey = vault.has(SecretVault.ALIAS_GEMINI) }
    var status by remember { mutableStateOf("Disconnected") }
    var session by remember { mutableStateOf<GeminiLiveAudioSession?>(null) }
    DisposableEffect(Unit) { onDispose { session?.close() } }
    var showConnectionSettings by remember { mutableStateOf(false) }
    val connected = session != null
    val blue = Color(0xFF4B8DFF)
    Column(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF091429), Color(0xFF101E3C), Color(0xFF091429))))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text("JARVIS BLUE", color = Color.White, style = MaterialTheme.typography.headlineMedium)
        Text("Your personal AI companion", color = Color(0xFFB5C6E8))
        Spacer(Modifier.height(24.dp))
        Box(
            Modifier.size(210.dp)
                .background(Brush.radialGradient(
                    listOf(if (connected) Color(0xFF41D6F3) else blue, Color(0xFF172C58), Color(0xFF0B1830))),
                    CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (connected) Icons.Filled.Mic else Icons.Filled.Mic,
                contentDescription = if (connected) "Live microphone" else "Start conversation",
                modifier = Modifier.size(64.dp),
                tint = Color.White,
            )
        }
        Text(
            if (connected) "I'm listening…" else "Hey! Ready when you are.",
            style = MaterialTheme.typography.headlineSmall, color = Color.White,
        )
        Text(
            status,
            color = if (connected) Color(0xFF79E3D4) else Color(0xFFB5C6E8),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            if (connected) "Speak naturally. You can interrupt me anytime."
            else "Talk with me in Tamil or English.",
            color = Color(0xFFB5C6E8),
        )
        Spacer(Modifier.height(8.dp))
        if (!connected) {
            Button(
                onClick = {
                    if (!microphoneGranted ||
                        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                        onRequestMicrophone()
                        status = "Allow microphone, then tap Start again"
                    } else {
                        scope.launch {
                            val key = vault.get(SecretVault.ALIAS_GEMINI)
                            if (key.isNullOrBlank()) {
                                showConnectionSettings = true
                                status = "Enter your Gemini API key once in Settings"
                            } else {
                                val live = GeminiLiveAudioSession(apiKey = key, onStatus = { status = it })
                                session = live
                                live.connect()
                            }
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = blue),
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(18.dp),
            ) {
                Icon(Icons.Filled.Mic, contentDescription = null)
                Spacer(Modifier.width(10.dp))
                Text(if (!microphoneGranted) "Allow microphone" else "Start conversation")
            }
        } else {
            Button(
                onClick = {
                    session?.close()
                    session = null
                    status = "Disconnected"
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFBC4056)),
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(18.dp),
            ) {
                Icon(Icons.Filled.CallEnd, contentDescription = null)
                Spacer(Modifier.width(10.dp))
                Text("End conversation")
            }
        }
        if (!connected) {
            TextButton(onClick = { showConnectionSettings = !showConnectionSettings }) {
                Icon(Icons.Filled.Settings, contentDescription = null, tint = Color(0xFFB5C6E8))
                Spacer(Modifier.width(8.dp))
                Text("Connection setup", color = Color(0xFFB5C6E8))
            }
            if (showConnectionSettings) {
                OutlinedTextField(
                    value = apiKey, onValueChange = { apiKey = it.trim() },
                    label = { Text("Gemini API Key") },
                    supportingText = { Text(if (hasSavedKey) "API key saved securely on this device" else "Enter your Google AI Studio API key once") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                )
                Button(onClick = {
                    if (apiKey.isNotBlank()) scope.launch {
                        try {
                            vault.put(SecretVault.ALIAS_GEMINI, apiKey)
                            apiKey = ""
                            hasSavedKey = true
                            showConnectionSettings = false
                            status = "Gemini API key saved. Ready to talk."
                        } catch (e: Exception) { status = "Could not securely save API key" }
                    }
                }, enabled = apiKey.isNotBlank()) { Text("Save API key") }
            }
        }
        Text(
            "Your microphone is used only during an active Live session. " +
                "PC commands and wake-word activation are not yet enabled here.",
            style = MaterialTheme.typography.bodySmall, color = Color(0xFF92A5C7),
        )
    }
}

private class GeminiLiveAudioSession(
    private val apiKey: String,
    private val onStatus: (String) -> Unit,
) {
    private val client = OkHttpClient.Builder().readTimeout(0, TimeUnit.MILLISECONDS).build()
    private val running = AtomicBoolean(false)
    private var socket: WebSocket? = null
    private var recorder: AudioRecord? = null
    private var player: AudioTrack? = null
    private var microphoneThread: Thread? = null
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private fun status(message: String) = mainHandler.post { onStatus(message) }

    fun connect() {
        val endpoint = "wss://generativelanguage.googleapis.com/ws/" +
            "google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContentConstrained"
        val request = Request.Builder().url(endpoint)
            .header("x-goog-api-key", apiKey).build()
        socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                val setup = JSONObject().put("setup", JSONObject()
                    .put("model", "models/gemini-2.5-flash-native-audio-preview-12-2025")
                    .put("generationConfig", JSONObject().put("responseModalities",
                        org.json.JSONArray().put("AUDIO")))
                    .put("systemInstruction", JSONObject().put("parts",
                        org.json.JSONArray().put(JSONObject().put("text",
                            "You are JARVIS BLUE, a friendly voice assistant. Respond naturally " +
                            "in the user's language, including Tamil and English. Keep answers conversational.")))))
                webSocket.send(setup.toString())
                status("Connecting to Gemini Live…")
            }
            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val message = JSONObject(text)
                    if (message.has("setupComplete")) {
                        startAudio()
                        status("Live • Listening")
                    }
                    val content = message.optJSONObject("serverContent")
                    if (content?.optBoolean("interrupted") == true) {
                        player?.pause()
                        player?.flush()
                        player?.play()
                    }
                    val parts = content?.optJSONObject("modelTurn")?.optJSONArray("parts")
                    if (parts != null) for (i in 0 until parts.length()) {
                        val inline = parts.optJSONObject(i)?.optJSONObject("inlineData")
                        val audio = inline?.optString("data").orEmpty()
                        if (audio.isNotEmpty()) {
                            val bytes = Base64.decode(audio, Base64.DEFAULT)
                            player?.write(bytes, 0, bytes.size)
                        }
                    }
                } catch (e: Exception) { status("Audio error: ${e.message}") }
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                status("Connection failed: ${response?.code ?: ""} ${t.message ?: "Unknown error"}")
                close()
            }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                close()
                status("Session closed")
            }
        })
    }

    @Suppress("MissingPermission")
    private fun startAudio() {
        if (!running.compareAndSet(false, true)) return
        val minInput = AudioRecord.getMinBufferSize(16000,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minInput <= 0) { status("Microphone unsupported"); close(); return }
        try {
            recorder = AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, 16000,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minInput, 3200))
            val minOutput = AudioTrack.getMinBufferSize(24000,
                AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
            if (minOutput <= 0 || recorder?.state != AudioRecord.STATE_INITIALIZED) {
                status("Audio device initialization failed"); close(); return
            }
            player = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                .setAudioFormat(AudioFormat.Builder().setSampleRate(24000)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
                .setBufferSizeInBytes(maxOf(minOutput, 4800))
                .setTransferMode(AudioTrack.MODE_STREAM).build()
            player?.play()
            recorder?.startRecording()
            microphoneThread = Thread {
                val buffer = ByteArray(1280) // 40 ms of 16 kHz PCM16 mono
                while (running.get()) {
                    val n = recorder?.read(buffer, 0, buffer.size) ?: break
                    if (n > 0) {
                        val frame = JSONObject().put("realtimeInput", JSONObject()
                            .put("audio", JSONObject().put("mimeType", "audio/pcm;rate=16000")
                                .put("data", Base64.encodeToString(buffer, 0, n, Base64.NO_WRAP))))
                        if (socket?.send(frame.toString()) != true) break
                    } else if (n < 0) break
                }
            }.apply { name = "jarvis-live-mic"; start() }
        } catch (e: Exception) {
            status("Microphone error: ${e.message}")
            close()
        }
    }

    fun close() {
        running.set(false)
        try { recorder?.stop() } catch (_: Exception) {}
        recorder?.release()
        recorder = null
        try { player?.stop() } catch (_: Exception) {}
        player?.release()
        player = null
        socket?.close(1000, "User ended session")
        socket = null
        client.dispatcher.executorService.shutdown()
    }
}
