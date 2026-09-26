package com.shnapps.couple.navigation

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.shnapps.couple.feature.games.GameScreen
import com.shnapps.couple.feature.games.GamesScreen
import com.shnapps.couple.feature.home.HomeActions
import com.shnapps.couple.feature.home.HomeScreen
import com.shnapps.couple.feature.instant.InstantGameScreen
import com.shnapps.couple.feature.instant.InstantHomeScreen
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
import com.shnapps.couple.feature.pairing.UnpairViewModel
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
                    navController.navigate(Route.InstantHome) {
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
                onBack = { navController.navigate(Route.InstantHome) },
                onKeepThis = { navController.navigate(Route.Auth) },
            )
        }

        // Home for a couple with no account: what to play, and how far (REDESIGN_PROMPT.md §5.2).
        composable<Route.InstantHome> {
            InstantHomeScreen(
                onStartNow = { navController.navigate(Route.InstantPlay) },
                onGame = { gameId -> navController.navigate(Route.InstantGame(gameId)) },
                // Apart and a planned evening both need a partner, so both ask for an account at
                // the moment it is wanted rather than before anything has happened.
                onApart = { navController.navigate(Route.Auth) },
                onPlanEvening = { navController.navigate(Route.Auth) },
            )
        }

        composable<Route.InstantGame> {
            InstantGameScreen(onBack = { navController.popBackStack() })
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
            PairingScreen(
                // Straight to the app (REDESIGN_PROMPT.md §4.2). Pairing used to hand people
                // twenty preference cards before they had done anything together; the deck is
                // offered from home now, once they have.
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

        composable<Route.Games> {
            GamesScreen(
                // The setup has done its job once a game exists: back from the game is the games.
                onStarted = { experienceId, twoPhones -> navController.navigate(Route.Game(experienceId, twoPhones)) },
                onOpen = { experienceId, type -> navController.navigate(experienceRoute(experienceId, type)) },
                onBack = { navController.popBackStack() },
            )
        }

        composable<Route.Game> {
            GameScreen(
                onGames = {
                    if (!navController.popBackStack(Route.Games, inclusive = false)) {
                        navController.navigate(Route.Games) { popUpTo<Route.Game> { inclusive = true } }
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
    StartDestination.InstantPlay -> Route.InstantHome
    StartDestination.AgeGate -> Route.AgeGate
    StartDestination.Welcome -> Route.Welcome
    StartDestination.CoupleSetup -> Route.CoupleSetup
    StartDestination.Home -> Route.Home
    // Never navigated to; the splash stays put while it resolves.
    StartDestination.Loading -> Route.Splash
}

@Composable
private fun Home(
    navController: NavHostController,
    signOutViewModel: SignOutViewModel = hiltViewModel(),
    unpairViewModel: UnpairViewModel = hiltViewModel(),
) {
    var confirmingUnpair by remember { mutableStateOf(false) }

    HomeScreen(
        onNight = { experienceId -> navController.navigate(Route.Night(experienceId)) },
        actions = HomeActions(
            onBuild = { navController.navigate(Route.BuildNight()) },
            onExpress = { navController.navigate(Route.BuildNight(express = true)) },
            onGames = { navController.navigate(Route.Games) },
            onApartNight = { navController.navigate(Route.BuildNight(apart = true)) },
            onPlanForThem = { navController.navigate(Route.BuildNight(forPartner = true)) },
            onSend = { navController.navigate(Route.Send) },
            onLater = { navController.navigate(Route.Later) },
            onArc = { navController.navigate(Route.Arc) },
            onNotifications = { navController.navigate(Route.NotificationSettings) },
            onMemories = { navController.navigate(Route.Memories) },
            onPaywall = { gate -> navController.navigate(Route.Paywall(gate.name)) },
            onCuriosities = { navController.navigate(Route.PreferenceDiscovery) },
            onMutual = { navController.navigate(Route.MutualDiscovery) },
            onLimits = { navController.navigate(Route.Boundaries) },
            onAppLock = { navController.navigate(Route.AppLockSetup) },
            onSettings = { navController.navigate(Route.Settings) },
            onOpen = { experienceId, type -> navController.navigate(experienceRoute(experienceId, type)) },
            onSignOut = {
                signOutViewModel.signOut {
                    navController.navigate(Route.Auth) { popUpTo(0) { inclusive = true } }
                }
            },
            onUnpair = { confirmingUnpair = true },
            onGallery = { navController.navigate(Route.DesignGallery) },
            onInspector = if (CONTENT_INSPECTOR_AVAILABLE) {
                { navController.navigate(Route.ContentInspector) }
            } else {
                null
            },
        ),
    )

    // Unpairing ends the couple for both partners at once, so it gets one clear confirmation.
    // Unlike STOP in a session (§3.1), this is not time-critical and is not trivially undone.
    if (confirmingUnpair) {
        AlertDialog(
            onDismissRequest = { confirmingUnpair = false },
            title = { Text("Unpair?") },
            text = {
                Text(
                    "This ends your shared space for both of you. You'll each keep your own " +
                        "account, and can pair again later.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmingUnpair = false
                    unpairViewModel.unpair {
                        navController.navigate(Route.CoupleSetup) { popUpTo(0) { inclusive = true } }
                    }
                }) { Text("Unpair") }
            },
            dismissButton = {
                TextButton(onClick = { confirmingUnpair = false }) { Text("Keep us paired") }
            },
        )
    }
}
