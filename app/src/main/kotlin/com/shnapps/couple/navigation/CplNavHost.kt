package com.shnapps.couple.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.shnapps.couple.core.designsystem.gallery.ComponentGallery
import com.shnapps.couple.core.navigation.Route
import com.shnapps.couple.core.security.BiometricAuthenticator
import com.shnapps.couple.debug.CONTENT_INSPECTOR_AVAILABLE
import com.shnapps.couple.debug.ContentInspectorScreen
import com.shnapps.couple.feature.applock.AppLockScreen
import com.shnapps.couple.feature.applock.AppLockSetupScreen
import com.shnapps.couple.feature.boundaries.LimitsScreen
import com.shnapps.couple.feature.games.DealtGameScreen
import com.shnapps.couple.feature.games.GameScreen
import com.shnapps.couple.feature.home.HomeActions
import com.shnapps.couple.feature.home.HomeScreen
import com.shnapps.couple.feature.instant.GameNightScreen
import com.shnapps.couple.feature.instant.InstantPlayScreen
import com.shnapps.couple.feature.instant.StartScreen
import com.shnapps.couple.feature.onboarding.SplashScreen
import com.shnapps.couple.feature.onboarding.StartDestination
import com.shnapps.couple.feature.pairing.PairingScreen
import com.shnapps.couple.feature.settings.NotificationSettingsScreen
import com.shnapps.couple.feature.settings.SettingsRoutes
import com.shnapps.couple.feature.settings.SettingsScreen
import com.shnapps.couple.feature.store.StoreScreen

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
    /** Leaves the app. The age gate is the one screen whose "no" ends the session (V2 rule 1). */
    onExit: () -> Unit,
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

        // The one screen in front of the app. Not a form: one line, one button, and the heat
        // dial so the first choice is made here (V2 rule 1).
        composable<Route.AgeGate> {
            StartScreen(
                onStarted = {
                    navController.navigate(Route.Home) {
                        popUpTo(Route.AgeGate) { inclusive = true }
                    }
                },
                // Declining is a real outcome, not a nudge to try again. There is no account to
                // send anyone back to, so the app simply closes.
                onNotOldEnough = { onExit() },
                onPairInstead = { navController.navigate(Route.Pairing) },
            )
        }

        composable<Route.GameNight> {
            GameNightScreen(onBack = { navController.popBackStack() })
        }

        composable<Route.InstantPlay> {
            InstantPlayScreen(
                onBack = { navController.popBackStack() },
                // A locked level tapped mid-card: the store opens on that pack, and the card
                // underneath is exactly where it was on the way back (V2 rule 5).
                onStore = { level -> navController.navigate(Route.Store(level.name)) },
            )
        }

        // A game dealt on this phone. The one player screen draws it exactly as it draws a
        // session's, because a person should not be able to tell which one they are in.
        composable<Route.DealtGame> {
            DealtGameScreen(onBack = { navController.popBackStack() })
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

        // The only setup in the app. Reached from home or from settings, never in front of
        // anything (V2 rule 2).
        composable<Route.Pairing> {
            PairingScreen(
                onPaired = { navController.popBackStack() },
                onLeave = { navController.popBackStack() },
            )
        }

        composable<Route.Boundaries> {
            LimitsScreen(onBack = { navController.popBackStack() })
        }

        composable<Route.Store> {
            StoreScreen(onBack = { navController.popBackStack() })
        }

        composable<Route.Settings> {
            SettingsScreen(
                routes = SettingsRoutes(
                    onBoundaries = { navController.navigate(Route.Boundaries) },
                    onNotifications = { navController.navigate(Route.NotificationSettings) },
                    onAppLock = { navController.navigate(Route.AppLockSetup) },
                    onPremium = { navController.navigate(Route.Store()) },
                    onPairing = { navController.navigate(Route.Pairing) },
                    onBack = { navController.popBackStack() },
                ),
            )
        }

        composable<Route.NotificationSettings> {
            NotificationSettingsScreen(onBack = { navController.popBackStack() })
        }

        composable<Route.Game> {
            GameScreen(
                // There is no hub to go back to any more: the list is home's.
                onGames = {
                    if (!navController.popBackStack(Route.Home, inclusive = false)) {
                        navController.navigate(Route.Home) { popUpTo(0) { inclusive = true } }
                    }
                },
                onHome = {
                    if (!navController.popBackStack(Route.Home, inclusive = false)) {
                        navController.navigate(Route.Home) { popUpTo(0) { inclusive = true } }
                    }
                },
                onAdjustLimits = { navController.navigate(Route.Boundaries) },
            )
        }

        composable<Route.Home> {
            Home(navController)
        }

        composable<Route.DesignGallery> {
            ComponentGallery()
        }

        composable<Route.ContentInspector> {
            ContentInspectorScreen(onBack = { navController.popBackStack() })
        }
    }
}

private fun StartDestination.toRoute(): Any = when (this) {
    StartDestination.AgeGate -> Route.AgeGate
    StartDestination.Home -> Route.Home
    // Never navigated to; the splash stays put while it resolves.
    StartDestination.Loading -> Route.Splash
}

/**
 * Home, for everyone (REDESIGN_PROMPT.md §6).
 *
 * Nothing about this differs by who is using it: the screen itself says what an account would add,
 * and each row here leads to the one thing it names. Unpairing and signing out live in Settings,
 * where the rest of the account is.
 */
@Composable
private fun Home(navController: NavHostController) {
    HomeScreen(
        actions = HomeActions(
            onCards = { navController.navigate(Route.InstantPlay) },
            onGameNight = { navController.navigate(Route.GameNight) },
            onSettings = { navController.navigate(Route.Settings) },
            onPairPartner = { navController.navigate(Route.Pairing) },
            onPaywall = { navController.navigate(Route.Store()) },
            onStore = { level -> navController.navigate(Route.Store(level.name)) },
            onDealtGame = { gameId -> navController.navigate(Route.DealtGame(gameId)) },
            onOpen = { experienceId, _ -> navController.navigate(Route.Game(experienceId)) },
            onGallery = { navController.navigate(Route.DesignGallery) },
            onInspector = if (CONTENT_INSPECTOR_AVAILABLE) {
                { navController.navigate(Route.ContentInspector) }
            } else {
                null
            },
        ),
    )
}
