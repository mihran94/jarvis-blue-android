package com.jarvisblue.mobile

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.jarvisblue.core.tools.CodeQualityChecker
import com.jarvisblue.core.tools.DeviceControlEngine
import com.jarvisblue.core.tools.FileTools
import com.jarvisblue.core.tools.JarvisToolRegistrar
import com.jarvisblue.core.tools.MemoryTools
import com.jarvisblue.core.tools.NetworkDiagnostics
import com.jarvisblue.core.tools.QuantumSimulator
import com.jarvisblue.core.tools.SecurityScanner
import com.jarvisblue.core.tools.ToolRegistry
import android.util.Log
import dagger.Lazy
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class JarvisApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    @Inject lateinit var toolRegistry: Lazy<ToolRegistry>

    @Inject lateinit var deviceControlEngine: Lazy<DeviceControlEngine>

    @Inject lateinit var fileTools: Lazy<FileTools>

    @Inject lateinit var networkDiagnostics: Lazy<NetworkDiagnostics>

    @Inject lateinit var securityScanner: Lazy<SecurityScanner>

    @Inject lateinit var codeQualityChecker: Lazy<CodeQualityChecker>

    @Inject lateinit var quantumSimulator: Lazy<QuantumSimulator>

    @Inject lateinit var memoryTools: Lazy<MemoryTools>

    @Inject lateinit var assistantServices: Lazy<AssistantServicesImpl>

    @Inject lateinit var workspaceRoot: Lazy<java.io.File>

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()

    override fun onCreate() {
        super.onCreate()
        // WorkManager has a custom Configuration.Provider and the AndroidX Startup
        // initializer is removed in the manifest. Let WorkManager initialise on
        // first use instead of forcing database/worker startup on the UI path.
        createNotificationChannels()
        try {
            registerTools()
        } catch (exception: Exception) {
            // Optional assistant tools must not prevent the shell UI from opening.
            // The full stack trace remains available through adb logcat.
            Log.e(TAG, "Optional tool registration failed; continuing app startup", exception)
        }
    }

    /**
     * Registers the capability surface exactly once at startup.
     *
     * Everything the assistant can do is declared here — there is no dynamic
     * tool creation at runtime, so the permission matrix always matches reality.
     */
    private fun registerTools() {
        val registry = toolRegistry.get()
        if (registry.all().isNotEmpty()) return
        JarvisToolRegistrar(
            registry = registry,
            device = deviceControlEngine.get(),
            files = fileTools.get(),
            network = networkDiagnostics.get(),
            scanner = securityScanner.get(),
            quality = codeQualityChecker.get(),
            quantum = quantumSimulator.get(),
            services = assistantServices.get(),
            workspaceRoot = workspaceRoot.get(),
        ).registerAll()
        // Encrypted database loading is optional at startup. If keystore or
        // SQLCipher setup fails the base assistant UI can still be opened.
        try {
            memoryTools.get().register(registry)
        } catch (exception: Exception) {
            Log.e(TAG, "Memory tools unavailable at startup", exception)
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return

        val wake = NotificationChannel(
            WAKE_CHANNEL_ID,
            getString(R.string.wake_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.wake_channel_description)
            setShowBadge(false)
        }

        val reminders = NotificationChannel(
            REMINDER_CHANNEL_ID,
            getString(R.string.reminder_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = getString(R.string.reminder_channel_description)
        }

        manager.createNotificationChannels(listOf(wake, reminders))
    }

    companion object {
        private const val TAG = "JarvisBlueStartup"
        const val WAKE_CHANNEL_ID = "jarvis_wake_word"
        const val REMINDER_CHANNEL_ID = "jarvis_reminders"
    }
}
