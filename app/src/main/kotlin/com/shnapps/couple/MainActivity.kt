package com.shnapps.couple

import android.content.Intent
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.shnapps.couple.core.data.couple.PendingInvite
import com.shnapps.couple.core.data.mutual.MutualRepository
import com.shnapps.couple.core.designsystem.theme.AfterhoursSurface
import com.shnapps.couple.core.designsystem.theme.AfterhoursTheme
import com.shnapps.couple.core.navigation.Route
import com.shnapps.couple.core.security.AppLockManager
import com.shnapps.couple.core.security.BiometricAuthenticator
import com.shnapps.couple.core.security.LockState
import com.shnapps.couple.navigation.CplNavHost
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The app's single Activity. Every screen is a Compose destination (BUILD_PROMPT.md §1.2).
 *
 * A [FragmentActivity] rather than a plain ComponentActivity because `BiometricPrompt`
 * requires one for the app lock (§3.4).
 */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject
    lateinit var appLockManager: AppLockManager

    @Inject
    lateinit var biometricAuthenticator: BiometricAuthenticator

    @Inject
    lateinit var pendingInvite: PendingInvite

    @Inject
    lateinit var mutualRepository: MutualRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Light system-bar icons always. The default follows the system theme, and on a
        // phone in light mode that drew dark icons on this dark-only app (DECISIONS.md D-009),
        // leaving the clock and battery all but invisible.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        acceptInviteLink(intent)

        setContent {
            AfterhoursTheme {
                val navController = rememberNavController()
                // Null until the lock state has been read. Assuming "locked" instead sent every
                // launch to the lock screen before the real state arrived — and with no lock
                // set, that screen had nothing to offer and never left.
                val lockState by appLockManager.lockState
                    .collectAsStateWithLifecycle(initialValue = null)

                // The lock is a destination rather than an overlay, so the back stack
                // underneath it survives and the user returns exactly where they were.
                LaunchedEffect(lockState) {
                    if (lockState == LockState.Locked) {
                        navController.navigate(Route.AppLock) { launchSingleTop = true }
                    }
                }

                AfterhoursSurface {
                    // Nothing renders until the lock state is known, so a cold start can
                    // never show a frame of the app to someone who should see the lock.
                    if (lockState != null) {
                        CplNavHost(
                            navController = navController,
                            biometricAuthenticator = biometricAuthenticator,
                            // Edge to edge: the surface paints behind the system bars while
                            // every screen is inset from them, and from the keyboard, so a
                            // form's button is never left underneath it.
                            modifier = Modifier.safeDrawingPadding(),
                        )
                    }
                }
            }
        }
    }

    /**
     * Re-lock is time-based, not instant (see [AppLockManager]). Checking on resume rather
     * than on pause means a glance at a notification does not demand a fingerprint, while a
     * phone left on a table still seals itself.
     */
    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                appLockManager.onForegrounded()
            }
        }
    }

    /**
     * The "release now" on opening the app (BUILD_PROMPT.md §5.3): anything that has already
     * waited the minimum delay is revealed now rather than at its random time. Throttled in
     * the repository, and harmless when signed out or unpaired.
     */
    override fun onStart() {
        super.onStart()
        lifecycleScope.launch { mutualRepository.releaseNow() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        acceptInviteLink(intent)
    }

    /**
     * An invite link never navigates on its own: the user may be signed out, or not past
     * the age gate. The code waits in [PendingInvite] until the pairing screen is reached
     * through the normal flow.
     */
    private fun acceptInviteLink(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme == "afterhours" && data.host == "pair") {
            pendingInvite.offer(data.getQueryParameter("code"))
        }
    }

    override fun onPause() {
        super.onPause()
        appLockManager.onBackgrounded()
    }
}
