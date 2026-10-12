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
import com.shnapps.couple.feature.applock.AppLockSettingsScreen
import com.shnapps.couple.feature.applock.AppLockSetupScreen
import com.shnapps.couple.feature.boundaries.LimitsScreen
import com.shnapps.couple.feature.chat.ChatLayer
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
    // The chat is drawn **over** the graph, not in it (CHAT_PROMPT.md). A route would move the
    // back stack and take the player's ViewModel out of the composition with it — the run, the
    // round, the answer in flight — and the whole point of it is that the game does not notice.
    // It is hosted here because only :app may depend on every feature it has to cover (§4.2).
    // It wraps the graph rather than sitting beside it, because the way *in* to the chat is
    // offered to each screen's own bar (LocalBarExtra) instead of floating over it.
    ChatLayer(modifier = modifier) {
        NavHost(
            navController = navController,
            startDestination = Route.Splash,
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

            composable<Route.AppLockSettings> {
                AppLockSettingsScreen(
                    onBack = { navController.popBackStack() },
                    onSetUp = { navController.navigate(Route.AppLockSetup) },
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
                        onAppLock = { navController.navigate(Route.AppLockSettings) },
                        onPairing = { navController.navigate(Route.Pairing) },
                        onBack = { navController.popBackStack() },
                        // Debug builds only, and in Settings rather than on home: a couple opening
                        // the app should never be offered the component library (D-146).
                        onGallery = if (DEV_TOOLS_AVAILABLE) {
                            { navController.navigate(Route.DesignGallery) }
                        } else {
                            null
                        },
                        onInspector = if (DEV_TOOLS_AVAILABLE) {
                            { navController.navigate(Route.ContentInspector) }
                        } else {
                            null
                        },
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

            // Both are reachable only from a debug build's Settings, and are not even registered in
            // a product build: the gallery is shipped code, so the route is what keeps it out.
            if (DEV_TOOLS_AVAILABLE) {
                composable<Route.DesignGallery> {
                    ComponentGallery()
                }

                composable<Route.ContentInspector> {
                    ContentInspectorScreen(onBack = { navController.popBackStack() })
                }
            }
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
            onStore = { level -> navController.navigate(Route.Store(level.name)) },
            onDealtGame = { gameId -> navController.navigate(Route.DealtGame(gameId)) },
            onOpen = { experienceId, _, twoPhones ->
                navController.navigate(Route.Game(experienceId, twoPhones))
            },
        ),
    )
}

/**
 * Whether the component gallery and the content inspector exist in this build.
 *
 * Debug builds only. The inspector has a release twin that says no; the gallery is ordinary shipped
 * code, so before this it was reachable from home in every build, a product one included (D-146).
 */
private const val DEV_TOOLS_AVAILABLE = CONTENT_INSPECTOR_AVAILABLE
