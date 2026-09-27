package com.shnapps.couple.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.shnapps.couple.core.designsystem.gallery.ComponentGallery
import com.shnapps.couple.core.model.ExperienceType
import com.shnapps.couple.core.navigation.Route
import com.shnapps.couple.core.security.BiometricAuthenticator
import com.shnapps.couple.debug.CONTENT_INSPECTOR_AVAILABLE
import com.shnapps.couple.debug.ContentInspectorScreen
import com.shnapps.couple.feature.applock.AppLockScreen
import com.shnapps.couple.feature.applock.AppLockSetupScreen
import com.shnapps.couple.feature.auth.AuthScreen
import com.shnapps.couple.feature.auth.SignOutViewModel
import com.shnapps.couple.feature.boundaries.BoundariesScreen
import com.shnapps.couple.feature.discovery.MutualDiscoveryScreen
import com.shnapps.couple.feature.games.DealtGameScreen
import com.shnapps.couple.feature.games.GameScreen
import com.shnapps.couple.feature.home.HomeActions
import com.shnapps.couple.feature.home.HomeScreen
import com.shnapps.couple.feature.instant.InstantPlayScreen
import com.shnapps.couple.feature.instant.StartScreen
import com.shnapps.couple.feature.memories.MemoriesScreen
import com.shnapps.couple.feature.memories.SaveMemoryScreen
import com.shnapps.couple.feature.night.BuildNightScreen
import com.shnapps.couple.feature.night.NightScreen
import com.shnapps.couple.feature.onboarding.AgeGateScreen
import com.shnapps.couple.feature.onboarding.SplashScreen
import com.shnapps.couple.feature.onboarding.StartDestination
import com.shnapps.couple.feature.onboarding.WelcomeScreen
import com.shnapps.couple.feature.pairing.PairingScreen
import com.shnapps.couple.feature.paywall.PaywallScreen
import com.shnapps.couple.feature.preferences.PreferenceDiscoveryScreen
import com.shnapps.couple.feature.send.ArcScreen
import com.shnapps.couple.feature.send.LaterScreen
import com.shnapps.couple.feature.send.SendScreen
import com.shnapps.couple.feature.settings.DeleteAccountScreen
import com.shnapps.couple.feature.settings.NotificationSettingsScreen
import com.shnapps.couple.feature.settings.SettingsRoutes
import com.shnapps.couple.feature.settings.SettingsScreen

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

        // First launch with no account: age and heat on one screen, then a card
        // (REDESIGN_PROMPT.md §5.1). The signed-in path still goes through Route.AgeGate,
        // where the attestation is written to the person's own record.
        composable<Route.Start> {
            StartScreen(
                onStarted = {
                    navController.navigate(Route.Home) {
                        popUpTo(Route.Start) { inclusive = true }
                    }
                },
                onNotOldEnough = {
                    navController.navigate(Route.Auth) {
                        popUpTo(Route.Start) { inclusive = true }
                    }
                },
                onPairInstead = { navController.navigate(Route.Auth) },
            )
        }

        composable<Route.InstantPlay> {
            InstantPlayScreen(
                onBack = { navController.popBackStack() },
                onKeepThis = { navController.navigate(Route.Auth) },
            )
        }

        // A game dealt on this phone. The one player screen draws it exactly as it draws a
        // session's, because a person should not be able to tell which one they are in.
        composable<Route.DealtGame> {
            DealtGameScreen(onBack = { navController.popBackStack() })
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
        // Reached from home, by someone who wants the half of the app that needs a partner —
        // never stood in front of them on the way in (REDESIGN_PROMPT.md §6).
        composable<Route.CoupleSetup> {
            PairingScreen(
                onPaired = {
                    navController.navigate(Route.Home) {
                        popUpTo(Route.CoupleSetup) { inclusive = true }
                    }
                },
            )
        }

        composable<Route.PreferenceDiscovery> {
            PreferenceDiscoveryScreen(
                onFinished = {
                    // Reached from Home: go back to it. On first run Home is not on the stack
                    // yet, and the journey continues into mutual discovery (§14.1).
                    if (!navController.popBackStack(Route.Home, inclusive = false)) {
                        navController.navigate(Route.MutualDiscovery) {
                            popUpTo(Route.PreferenceDiscovery) { inclusive = true }
                        }
                    }
                },
            )
        }

        composable<Route.MutualDiscovery> {
            MutualDiscoveryScreen(
                // Every reveal ends with one tap into a night built around it (§14.7).
                onBuildNight = { itemId -> navController.navigate(Route.BuildNight(focus = itemId)) },
                onDone = {
                    if (!navController.popBackStack(Route.Home, inclusive = false)) {
                        navController.navigate(Route.Home) {
                            popUpTo(Route.MutualDiscovery) { inclusive = true }
                        }
                    }
                },
            )
        }

        composable<Route.Boundaries> {
            BoundariesScreen(onBack = { navController.popBackStack() })
        }

        composable<Route.BuildNight> {
            BuildNightScreen(
                onPaywall = { gate -> navController.navigate(Route.Paywall(gate.name)) },
                // The builder has done its job once a night exists: back from the night is home.
                onBuilt = { experienceId ->
                    navController.navigate(Route.Night(experienceId)) {
                        popUpTo<Route.BuildNight> { inclusive = true }
                    }
                },
                onOpenNight = { experienceId -> navController.navigate(Route.Night(experienceId)) },
                onBack = { navController.popBackStack() },
            )
        }

        composable<Route.Night> {
            NightScreen(
                onHome = {
                    if (!navController.popBackStack(Route.Home, inclusive = false)) {
                        navController.navigate(Route.Home) { popUpTo(0) { inclusive = true } }
                    }
                },
                onAdjustLimits = { navController.navigate(Route.Boundaries) },
                onKeepIt = { experienceId -> navController.navigate(Route.SaveMemory(experienceId)) },
            )
        }

        composable<Route.Send> {
            SendScreen(onBack = { navController.popBackStack() })
        }

        composable<Route.Later> {
            LaterScreen(
                onPaywall = { gate -> navController.navigate(Route.Paywall(gate.name)) },
                onBack = { navController.popBackStack() },
            )
        }

        composable<Route.Paywall> {
            PaywallScreen(onBack = { navController.popBackStack() })
        }

        composable<Route.Memories> {
            MemoriesScreen(onBack = { navController.popBackStack() })
        }

        composable<Route.SaveMemory> {
            SaveMemoryScreen(
                // Kept, and then the library, so they see it land — with the night behind them.
                onSaved = {
                    navController.navigate(Route.Memories) {
                        popUpTo(Route.Home) { inclusive = false }
                    }
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable<Route.Settings> {
            val signOutViewModel: SignOutViewModel = hiltViewModel()
            val leave: () -> Unit = {
                signOutViewModel.signOut {
                    navController.navigate(Route.Auth) { popUpTo(0) { inclusive = true } }
                }
            }
            SettingsScreen(
                routes = SettingsRoutes(
                    onPreferences = { navController.navigate(Route.PreferenceDiscovery) },
                    onBoundaries = { navController.navigate(Route.Boundaries) },
                    onNotifications = { navController.navigate(Route.NotificationSettings) },
                    onAppLock = { navController.navigate(Route.AppLockSetup) },
                    onMemories = { navController.navigate(Route.Memories) },
                    onPremium = { navController.navigate(Route.Paywall()) },
                    onDeleteAccount = { navController.navigate(Route.DeleteAccount) },
                    onSignOut = leave,
                    onBack = { navController.popBackStack() },
                ),
                onSignedOut = leave,
            )
        }

        composable<Route.DeleteAccount> {
            DeleteAccountScreen(
                // Nothing left to sign out of: the account is gone, and the repository has already
                // cleared this phone. All that remains is to go back to the beginning.
                onDeleted = { navController.navigate(Route.Auth) { popUpTo(0) { inclusive = true } } },
                onBack = { navController.popBackStack() },
            )
        }

        composable<Route.NotificationSettings> {
            NotificationSettingsScreen(onBack = { navController.popBackStack() })
        }

        composable<Route.Arc> {
            ArcScreen(
                onNight = { experienceId -> navController.navigate(Route.Night(experienceId)) },
                onPaywall = { gate -> navController.navigate(Route.Paywall(gate.name)) },
                onBack = { navController.popBackStack() },
            )
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

/** The player for what is under way: a night's, or a game's. */
private fun experienceRoute(experienceId: String, type: ExperienceType): Any = when (type) {
    ExperienceType.NIGHT -> Route.Night(experienceId)
    ExperienceType.GAME -> Route.Game(experienceId)
}

private fun StartDestination.toRoute(): Any = when (this) {
    StartDestination.Auth -> Route.Auth
    StartDestination.Start -> Route.Start
    StartDestination.AgeGate -> Route.AgeGate
    StartDestination.Welcome -> Route.Welcome
    StartDestination.CoupleSetup -> Route.CoupleSetup
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
            onPlan = { navController.navigate(Route.BuildNight()) },
            onExpress = { navController.navigate(Route.BuildNight(express = true)) },
            onApart = { navController.navigate(Route.Send) },
            onPlanForThem = { navController.navigate(Route.BuildNight(forPartner = true)) },
            onMemories = { navController.navigate(Route.Memories) },
            onCuriosities = { navController.navigate(Route.PreferenceDiscovery) },
            onSettings = { navController.navigate(Route.Settings) },
            onPairPartner = { navController.navigate(Route.CoupleSetup) },
            // An account, asked for where it buys something and never before (§6).
            onAccount = { navController.navigate(Route.Auth) },
            onPaywall = { gate -> navController.navigate(Route.Paywall(gate.name)) },
            onDealtGame = { gameId -> navController.navigate(Route.DealtGame(gameId)) },
            onOpen = { experienceId, type -> navController.navigate(experienceRoute(experienceId, type)) },
            onGallery = { navController.navigate(Route.DesignGallery) },
            onInspector = if (CONTENT_INSPECTOR_AVAILABLE) {
                { navController.navigate(Route.ContentInspector) }
            } else {
                null
            },
        ),
    )
}
