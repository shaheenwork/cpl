package com.shnapps.couple

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.shnapps.couple.core.data.couple.PendingInvite
import com.shnapps.couple.core.data.notifications.NotificationsRepository
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
    lateinit var notifications: NotificationsRepository

    /** Android 13+ asks before a single notification may be posted (§16). Asked once, on start. */
    private val askToNotify = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Before super, as the platform requires: it swaps the splash theme for the app's own.
        // The two are painted the same ink, so there is nothing to see at the handover (§7).
        installSplashScreen()
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

                // Panic: two taps of back, and the app is locked (M8).
                //
                // The brief asks for a shake or a double back-press onto a neutral screen. Shake
                // is Heat Dice's gesture, and one gesture cannot both roll the dice and hide the
                // app, so panic is the back-press alone. Where it lands is this app's own lock
                // screen rather than an imitation of some other app: the user gets the same
                // benefit — nothing on screen says what this is — without the app pretending to
                // be a product it is not, which is what Play's deceptive-behaviour policy covers.
                //
                // With no lock set there is nothing to show, so the second press does what a
                // second press does and leaves. That is discreet enough on its own.
                var armed by remember { mutableStateOf(false) }
                BackHandler(enabled = lockState == LockState.Unlocked) {
                    if (armed) {
                        appLockManager.lockNow()
                        armed = false
                    } else {
                        armed = true
                        navController.popBackStack()
                    }
                }

                AfterhoursSurface {
                    // Nothing renders until the lock state is known, so a cold start can
                    // never show a frame of the app to someone who should see the lock.
                    if (lockState != null) {
                        CplNavHost(
                            navController = navController,
                            biometricAuthenticator = biometricAuthenticator,
                            // The age gate's "no" is the one answer that ends the session, and
                            // only an Activity can close itself (V2 rule 1).
                            onExit = { finish() },
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
        // Where this phone can be reached (§16). Harmless when signed out — there is no user to
        // register a token against, and the repository says so rather than writing anything.
        lifecycleScope.launch { notifications.registerThisPhone() }
        askToNotifyOnce()
    }

    /**
     * The system shows this at most twice in a phone's lifetime and ignores it afterwards, so it
     * is asked plainly rather than wrapped in a screen of its own: nothing in the app depends on
     * the answer, and a "no" costs only the notifications.
     */
    private fun askToNotifyOnce() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) askToNotify.launch(Manifest.permission.POST_NOTIFICATIONS)
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
