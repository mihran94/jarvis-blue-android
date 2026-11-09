package com.jarvisblue.mobile

import android.Manifest
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.jarvisblue.core.security.AppLockController
import com.jarvisblue.core.security.BiometricGate
import com.jarvisblue.core.ui.theme.JarvisBlueTheme
import com.jarvisblue.mobile.navigation.JarvisApp
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The single Compose host.
 *
 * It extends [FragmentActivity] because the biometric gate needs a fragment
 * host to attach its prompt to.
 */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject lateinit var appLock: AppLockController

    private val microphonePermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        microphonePermissionGranted = granted
    }

    private var microphonePermissionGranted by mutableStateOf(false)
    private var screenLocked by mutableStateOf(false)
    private var darkThemeEnabled by mutableStateOf(true)
    private val uiPreferences by lazy { getSharedPreferences("jarvis_ui", MODE_PRIVATE) }
    private val themeListener = SharedPreferences.OnSharedPreferenceChangeListener { preferences, key ->
        if (key == "dark_theme") darkThemeEnabled = preferences.getBoolean("dark_theme", true)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        darkThemeEnabled = uiPreferences.getBoolean("dark_theme", true)
        uiPreferences.registerOnSharedPreferenceChangeListener(themeListener)
        screenLocked = appLock.appLockEnabled
        microphonePermissionGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED

        setContent {
            val scope = rememberCoroutineScope()
            var assistantRequested by remember {
                mutableStateOf(intent?.getBooleanExtra(EXTRA_FROM_ASSISTANT, false) == true)
            }

            JarvisBlueTheme(darkTheme = darkThemeEnabled) {
                if (screenLocked) {
                    com.jarvisblue.mobile.navigation.LockScreen(
                        onUnlock = {
                            appLock.unlock()
                            screenLocked = false
                        },
                        onUseBiometrics = {
                            val gate = BiometricGate(this@MainActivity)
                            scope.launch {
                                val result = gate.authenticate(
                                    activity = this@MainActivity,
                                    title = "Unlock JARVIS BLUE",
                                    subtitle = "Confirm it is you to continue",
                                )
                                if (result is BiometricGate.BiometricResult.Success) {
                                    appLock.unlock()
                                    screenLocked = false
                                }
                            }
                        },
                    )
                } else {
                    JarvisApp(
                        assistantRequested = assistantRequested,
                        onAssistantHandled = { assistantRequested = false },
                        onRequestMicrophone = {
                            microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
                        },
                        microphoneGranted = microphonePermissionGranted,
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        uiPreferences.unregisterOnSharedPreferenceChangeListener(themeListener)
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        if (appLock.appLockEnabled && !appLock.refreshSession()) {
            // Do not recreate a locked activity in onResume: the locked state
            // otherwise causes an endless recreate/onResume loop.
            screenLocked = true
        }
    }

    companion object {
        const val EXTRA_FROM_ASSISTANT = "com.jarvisblue.mobile.FROM_ASSISTANT"
    }
}
