package com.shnapps.couple

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay

/**
 * Draws nothing, and asks. Hosted at the root so it does not matter which screen pairing finished
 * on: the pairing screen, home, or Settings.
 *
 * The system shows its question at most twice in a phone's lifetime and ignores it afterwards, so
 * it is put once and plainly rather than wrapped in a screen of its own: nothing in the app depends
 * on the answer, and a "no" costs only the notifications. A phone that already said yes, or that is
 * too old to be asked, is marked as asked without a question.
 */
@Composable
internal fun AskToNotifyAfterPairing(viewModel: NotifyAskViewModel = hiltViewModel()) {
    val shouldAsk by viewModel.shouldAsk.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.asked()
    }
    LaunchedEffect(shouldAsk) {
        if (!shouldAsk) return@LaunchedEffect
        val known = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (known) {
            viewModel.asked()
            return@LaunchedEffect
        }
        // Let "You're paired" land before the system puts a question over it.
        delay(SETTLE_MS)
        launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}

private const val SETTLE_MS = 1_200L
