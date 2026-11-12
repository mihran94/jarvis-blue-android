package com.jarvisblue.mobile.navigation

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import android.view.TextureView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import android.util.Base64
import java.io.ByteArrayOutputStream

/** User-controlled camera: no capture or transmission before explicit enable. */
@Composable
fun JarvisCameraPanel(
    liveEnabled: Boolean,
    onFrame: (String) -> Unit,
    onPhoto: suspend (String) -> String,
) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    ) }
    var enabled by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<TextureView?>(null) }
    var result by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
        if (it) enabled = true
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (!enabled) {
            Button(onClick = {
                if (granted) enabled = true else permission.launch(Manifest.permission.CAMERA)
            }, modifier = Modifier.fillMaxWidth()) { Text("Open camera") }
        } else {
            AndroidView(factory = { ctx ->
                TextureView(ctx).also { preview = it }
            }, modifier = Modifier.fillMaxWidth().height(240.dp))
            Button(onClick = {
                val frame = preview?.bitmap ?: return@Button
                busy = true
                scope.launch {
                    try {
                        result = onPhoto(encodeJpeg(frame))
                    } catch (e: Exception) {
                        result = "Could not analyze photo: ${e.message ?: "Unknown error"}"
                    } finally { busy = false }
                }
            }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text(if (busy) "Analyzing…" else "Take photo & ask Gemini")
            }
            if (liveEnabled) {
                Text("Live vision: one low-resolution frame approximately every 3 seconds.")
                LaunchedEffect(enabled, liveEnabled, preview) {
                    while (enabled && liveEnabled) {
                        delay(3000)
                        preview?.bitmap?.let { onFrame(encodeJpeg(it)) }
                    }
                }
            }
            OutlinedButton(onClick = { enabled = false; preview = null },
                modifier = Modifier.fillMaxWidth()) { Text("Close camera") }
            if (result.isNotBlank()) Text(result)
        }
    }
    if (enabled && granted) {
        DisposableEffect(enabled) {
            onDispose { preview = null }
        }
    }
    if (enabled && granted) {
        // Camera opens only while the preview is composed and closes on disposal.
        key(enabled) {
            CameraPreviewLifecycle(preview)
        }
    }
}

@Composable
private fun CameraPreviewLifecycle(texture: TextureView?) {
    val context = LocalContext.current
    DisposableEffect(texture) {
        if (texture == null) return@DisposableEffect onDispose {}
        val controller = CameraPreviewController(context, texture)
        controller.start()
        onDispose { controller.close() }
    }
}

private fun encodeJpeg(bitmap: Bitmap): String {
    val width = 640
    val scaled = Bitmap.createScaledBitmap(bitmap, width,
        (bitmap.height * width / bitmap.width).coerceAtLeast(1), true)
    val out = ByteArrayOutputStream()
    scaled.compress(Bitmap.CompressFormat.JPEG, 65, out)
    if (scaled !== bitmap) scaled.recycle()
    return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
}

suspend fun analyzeGeminiPhoto(apiKey: String, jpegBase64: String): String =
    withContext(Dispatchers.IO) {
        val body = JSONObject().put("contents", JSONArray().put(
            JSONObject().put("parts", JSONArray()
                .put(JSONObject().put("text",
                    "Describe what you can see and help the user in Tamil or English. " +
                    "Read visible text when relevant. Do not guess uncertain details."))
                .put(JSONObject().put("inline_data", JSONObject()
                    .put("mime_type", "image/jpeg").put("data", jpegBase64))))))
        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent")
            .header("x-goog-api-key", apiKey)
            .post(body.toString().toRequestBody("application/json".toMediaType())).build()
        OkHttpClient().newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Gemini error HTTP ${response.code}")
            val json = JSONObject(response.body?.string().orEmpty())
            val parts = json.optJSONArray("candidates")?.optJSONObject(0)
                ?.optJSONObject("content")?.optJSONArray("parts")
            buildString {
                if (parts != null) for (i in 0 until parts.length()) {
                    append(parts.optJSONObject(i)?.optString("text").orEmpty())
                }
            }.ifBlank { "No visual description returned." }
        }
    }

private class CameraPreviewController(
    private val context: Context, private val texture: TextureView,
) {
    private val thread = HandlerThread("jarvis-camera").apply { start() }
    private val handler = Handler(thread.looper)
    private var camera: CameraDevice? = null
    private var capture: CameraCaptureSession? = null
    private var surface: Surface? = null
    private var closed = false

    @Suppress("MissingPermission")
    fun start() {
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val id = manager.cameraIdList.firstOrNull { cameraId ->
            manager.getCameraCharacteristics(cameraId).get(
                android.hardware.camera2.CameraCharacteristics.LENS_FACING
            ) == android.hardware.camera2.CameraCharacteristics.LENS_FACING_BACK
        } ?: manager.cameraIdList.firstOrNull() ?: return
        if (texture.isAvailable) open(manager, id)
        else texture.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(s: SurfaceTexture, w: Int, h: Int) {
                open(manager, id)
            }
            override fun onSurfaceTextureSizeChanged(s: SurfaceTexture, w: Int, h: Int) {}
            override fun onSurfaceTextureDestroyed(s: SurfaceTexture): Boolean = true
            override fun onSurfaceTextureUpdated(s: SurfaceTexture) {}
        }
    }
    @Suppress("MissingPermission")
    private fun open(manager: CameraManager, id: String) {
        if (closed) return
        try {
            manager.openCamera(id, object : CameraDevice.StateCallback() {
                override fun onOpened(device: CameraDevice) {
                    if (closed) { device.close(); return }
                    camera = device
                    val st = texture.surfaceTexture ?: return
                    st.setDefaultBufferSize(1280, 720)
                    val target = Surface(st)
                    surface = target
                    device.createCaptureSession(listOf(target),
                        object : CameraCaptureSession.StateCallback() {
                            override fun onConfigured(session: CameraCaptureSession) {
                                if (closed) { session.close(); return }
                                capture = session
                                val request = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                                    .apply { addTarget(target) }.build()
                                session.setRepeatingRequest(request, null, handler)
                            }
                            override fun onConfigureFailed(session: CameraCaptureSession) {}
                        }, handler)
                }
                override fun onDisconnected(device: CameraDevice) { device.close() }
                override fun onError(device: CameraDevice, error: Int) { device.close() }
            }, handler)
        } catch (_: Exception) { close() }
    }
    fun close() {
        closed = true
        try { capture?.close() } catch (_: Exception) {}
        try { camera?.close() } catch (_: Exception) {}
        surface?.release()
        thread.quitSafely()
    }
}
