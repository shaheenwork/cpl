package com.shnapps.couple.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shnapps.couple.core.designsystem.theme.AfterhoursTheme
import com.shnapps.couple.core.ui.SecureScreen

/**
 * The first frame (BUILD_PROMPT.md §14.1).
 *
 * Shows nothing identifying while it resolves. On a shared or borrowed phone the app's
 * opening frame should not announce what it is, so this is a line of the product's own
 * voice rather than a logo and a tagline.
 */
@Composable
fun SplashScreen(
    onDestination: (StartDestination) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SplashViewModel = hiltViewModel(),
) {
    SecureScreen()

    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.destination) {
        if (state.destination != StartDestination.Loading) {
            onDestination(state.destination)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(AfterhoursTheme.spacing.xl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Tonight could get interesting.",
            style = MaterialTheme.typography.headlineMedium,
            color = AfterhoursTheme.colors.textMuted,
            textAlign = TextAlign.Center,
        )
    }
}
