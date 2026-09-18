package com.shnapps.couple

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.shnapps.couple.core.data.couple.PendingInvite
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        acceptInviteLink(intent)

        setContent {
            AfterhoursTheme {
                val navController = rememberNavController()
                val lockState by appLockManager.lockState
                    .collectAsStateWithLifecycle(initialValue = LockState.Locked)

                // The lock is a destination rather than an overlay, so the back stack
                // underneath it survives and the user returns exactly where they were.
                LaunchedEffect(lockState) {
                    if (lockState == LockState.Locked) {
                        navController.navigate(Route.AppLock) { launchSingleTop = true }
                    }
                }

                AfterhoursSurface {
                    CplNavHost(
                        navController = navController,
                        biometricAuthenticator = biometricAuthenticator,
                    )
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
