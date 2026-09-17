package com.shnapps.couple.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.shnapps.couple.core.designsystem.component.GlowButton
import com.shnapps.couple.core.designsystem.component.GlowButtonStyle
import com.shnapps.couple.core.designsystem.gallery.ComponentGallery
import com.shnapps.couple.core.designsystem.theme.AfterhoursTheme
import com.shnapps.couple.core.navigation.Route

/**
 * The app's navigation graph.
 *
 * Destinations are registered here by the phase that builds them, each one calling into
 * its own :feature: module. Features never reference each other (BUILD_PROMPT.md §4.2).
 *
 * Phase 0 ships the graph with a single placeholder so the skeleton is verifiably
 * runnable; the first-run chain from §14.1 lands across Phases 3 to 7.
 */
@Composable
fun CplNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = Route.Splash,
        modifier = modifier,
    ) {
        composable<Route.Splash> {
            DevLandingScreen(onOpenGallery = { navController.navigate(Route.DesignGallery) })
        }

        composable<Route.DesignGallery> {
            ComponentGallery()
        }
    }
}

@Composable
private fun DevLandingScreen(
    onOpenGallery: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Tonight could get interesting.",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "Phase 2 — design system. Auth, pairing and the experience engine follow.",
            style = MaterialTheme.typography.bodyMedium,
            color = AfterhoursTheme.colors.textMuted,
            textAlign = TextAlign.Center,
        )
        GlowButton(
            text = "Component gallery",
            onClick = onOpenGallery,
            style = GlowButtonStyle.Secondary,
        )
    }
}
