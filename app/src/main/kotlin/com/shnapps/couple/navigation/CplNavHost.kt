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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.shnapps.couple.core.designsystem.component.GlowButton
import com.shnapps.couple.core.designsystem.component.GlowButtonStyle
import com.shnapps.couple.core.designsystem.gallery.ComponentGallery
import com.shnapps.couple.core.designsystem.theme.AfterhoursTheme
import com.shnapps.couple.core.navigation.Route
import com.shnapps.couple.core.security.BiometricAuthenticator
import com.shnapps.couple.feature.applock.AppLockScreen
import com.shnapps.couple.feature.applock.AppLockSetupScreen
import com.shnapps.couple.feature.auth.AuthScreen
import com.shnapps.couple.feature.auth.SignOutViewModel
import com.shnapps.couple.feature.onboarding.AgeGateScreen
import com.shnapps.couple.feature.onboarding.SplashScreen
import com.shnapps.couple.feature.onboarding.StartDestination
import com.shnapps.couple.feature.onboarding.WelcomeScreen

/**
 * The app's navigation graph.
 *
 * Destinations are registered here by the phase that builds them, each one calling into
 * its own :feature: module. Features never reference each other (BUILD_PROMPT.md §4.2),
 * which is why every route in this graph comes from :core:navigation and every callback is
 * wired here rather than inside a screen.
 */
@Composable
fun CplNavHost(
    navController: NavHostController,
    biometricAuthenticator: BiometricAuthenticator,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = Route.Splash,
        modifier = modifier,
    ) {
        composable<Route.Splash> {
            SplashScreen(
                onDestination = { destination ->
                    navController.navigate(destination.toRoute()) {
                        // The splash is a routing decision, not a screen anyone should be
                        // able to go back to.
                        popUpTo(Route.Splash) { inclusive = true }
                    }
                },
            )
        }

        composable<Route.AgeGate> {
            AgeGateScreen(
                onConfirmed = {
                    navController.navigate(Route.Welcome) {
                        popUpTo(Route.AgeGate) { inclusive = true }
                    }
                },
                // Declining is a real outcome, not a nudge to try again: it returns to
                // auth rather than looping the gate (§3.1).
                onDeclined = {
                    navController.navigate(Route.Auth) {
                        popUpTo(Route.AgeGate) { inclusive = true }
                    }
                },
            )
        }

        composable<Route.Auth> {
            AuthScreen(
                onSignedIn = {
                    navController.navigate(Route.Splash) {
                        popUpTo(Route.Auth) { inclusive = true }
                    }
                },
            )
        }

        composable<Route.Welcome> {
            WelcomeScreen(
                onFinished = {
                    navController.navigate(Route.CoupleSetup) {
                        popUpTo(Route.Welcome) { inclusive = true }
                    }
                },
            )
        }

        composable<Route.AppLock> {
            AppLockScreen(
                onUnlocked = { navController.popBackStack() },
                biometricAuthenticator = biometricAuthenticator,
            )
        }

        composable<Route.AppLockSetup> {
            AppLockSetupScreen(
                onFinished = { navController.popBackStack() },
                biometricAuthenticator = biometricAuthenticator,
            )
        }

        // Placeholders until the phase that owns them lands. They carry the account actions
        // that have no permanent home until Settings (Phase 20), so Phase 3's features are
        // reachable on a device rather than only in tests.
        composable<Route.CoupleSetup> {
            ComingSoon(
                title = "Pair with your partner",
                detail = "Phase 4 builds invite codes, deep links and QR pairing.",
                navController = navController,
            )
        }

        composable<Route.Home> {
            ComingSoon(
                title = "Tonight could get interesting.",
                detail = "Phase 13 builds the home surface.",
                navController = navController,
            )
        }

        composable<Route.DesignGallery> {
            ComponentGallery()
        }
    }
}

private fun StartDestination.toRoute(): Any = when (this) {
    StartDestination.Auth -> Route.Auth
    StartDestination.AgeGate -> Route.AgeGate
    StartDestination.Welcome -> Route.Welcome
    StartDestination.CoupleSetup -> Route.CoupleSetup
    StartDestination.Home -> Route.Home
    // Never navigated to; the splash stays put while it resolves.
    StartDestination.Loading -> Route.Splash
}

@Composable
private fun ComingSoon(
    title: String,
    detail: String,
    navController: NavHostController,
    modifier: Modifier = Modifier,
    signOutViewModel: SignOutViewModel = hiltViewModel(),
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            text = detail,
            style = MaterialTheme.typography.bodyMedium,
            color = AfterhoursTheme.colors.textMuted,
            textAlign = TextAlign.Center,
        )
        GlowButton(
            text = "Set up app lock",
            onClick = { navController.navigate(Route.AppLockSetup) },
            style = GlowButtonStyle.Secondary,
        )
        GlowButton(
            text = "Component gallery",
            onClick = { navController.navigate(Route.DesignGallery) },
            style = GlowButtonStyle.Quiet,
        )
        GlowButton(
            text = "Sign out",
            onClick = {
                signOutViewModel.signOut {
                    navController.navigate(Route.Auth) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            },
            style = GlowButtonStyle.Quiet,
        )
    }
}
