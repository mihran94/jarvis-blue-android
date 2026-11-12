package com.jarvisblue.mobile.navigation

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
 * Requires a short-lived, server-minted Gemini Live ephemeral token.
 * No long-lived Gemini API key is shipped in the APK or persisted here.
 */
@Composable
fun GeminiLiveRoute(onRequestMicrophone: () -> Unit, microphoneGranted: Boolean) {
    val context = LocalContext.current
    var token by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Disconnected") }
    var session by remember { mutableStateOf<GeminiLiveAudioSession?>(null) }
    DisposableEffect(Unit) { onDispose { session?.close() } }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("JARVIS BLUE", style = MaterialTheme.typography.headlineMedium)
        Text("Gemini Live • Natural voice conversation")
        Text(status, color = MaterialTheme.colorScheme.primary)
        if (session == null) {
            OutlinedTextField(
                value = token, onValueChange = { token = it.trim() },
                label = { Text("Short-lived Gemini Live token") },
                supportingText = { Text("Obtain from your own authenticated token server. Never paste a permanent API key.") },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
            )
            Button(onClick = {
                if (!microphoneGranted ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    onRequestMicrophone()
                    status = "Allow microphone, then tap Connect again"
                } else if (token.isBlank()) {
                    status = "A short-lived Live token is required"
                } else {
                    val live = GeminiLiveAudioSession(
                        token = token,
                        onStatus = { status = it },
                    )
                    session = live
                    token = ""
                    live.connect()
                }
            }, modifier = Modifier.fillMaxWidth()) { Text("Connect & start talking") }
        } else {
            Text("Microphone is active only during this session. Tap End to stop.")
            Button(onClick = {
                session?.close()
                session = null
                status = "Disconnected"
            }, modifier = Modifier.fillMaxWidth()) { Text("End voice conversation") }
        }
        Text("Speak naturally in Tamil or English. Gemini Live can respond with streaming audio. " +
            "PC commands are not enabled in this voice session.")
    }
}

private class GeminiLiveAudioSession(
    private val token: String,
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
            .header("Authorization", "Token $token").build()
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
