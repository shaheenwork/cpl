package com.shnapps.couple.core.security

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What this app looks like on a home screen (M8).
 *
 * For a phone somebody else might pick up. [AFTERHOURS] is what it is; the other two are a generic
 * name and a drawn icon that do not say. Neither imitates a real product — no other app's name,
 * colours or mark — because an app that passes for a different app is what Play's
 * deceptive-behaviour policy is about. The point is an icon that does not announce this app, not
 * one that claims to be another.
 */
enum class DiscreetIcon(val label: String, val describes: String, private val alias: String) {
    AFTERHOURS("Afterhours", "The real one", "Launcher"),
    NOTES("Notes", "A notes page", "LauncherNotes"),
    CALCULATOR("Calculator", "A calculator", "LauncherCalculator"),
    ;

    /** The alias component this icon is, in [packageName]. */
    fun component(packageName: String): ComponentName = ComponentName(packageName, "$packageName.$alias")

    companion object {
        val DEFAULT = AFTERHOURS
    }
}

/**
 * Switches which launcher icon the phone shows.
 *
 * An interface because the only implementation needs a `Context` and the platform's package
 * manager, which a unit test cannot reasonably hold — and the rule here is fakes rather than mocks
 * (DECISIONS.md D-013).
 */
interface DiscreetIcons {
    /** Which icon is showing now. */
    fun current(): DiscreetIcon

    /** Shows [icon] and hides the rest. */
    fun show(icon: DiscreetIcon)
}

/**
 * The real one: three aliases over one activity, exactly one enabled.
 *
 * The activity itself is never touched, so a deep link and a notification keep working whichever
 * icon is showing — disabling it would break both, and the icon is meant to be cosmetic.
 *
 * `DONT_KILL_APP` matters: without it the system stops the process the moment the component changes,
 * which from the user's side is the app closing itself as they tap a setting.
 */
@Singleton
class LauncherAliasIcons @Inject constructor(
    @ApplicationContext private val context: Context,
) : DiscreetIcons {
    /** Which icon is showing now, read from the components rather than from a stored guess. */
    override fun current(): DiscreetIcon {
        val packages = context.packageManager
        return DiscreetIcon.entries.firstOrNull { icon ->
            packages.getComponentEnabledSetting(icon.component(context.packageName)) ==
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } ?: DiscreetIcon.DEFAULT
    }

    /**
     * Shows [icon] and hides the rest.
     *
     * The wanted one is enabled before the others are disabled. The other order leaves a moment
     * with no launcher entry at all, and a launcher that reads the package in that moment drops the
     * app off the home screen until something refreshes it.
     */
    override fun show(icon: DiscreetIcon) {
        val packages = context.packageManager
        packages.setComponentEnabledSetting(
            icon.component(context.packageName),
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP,
        )
        DiscreetIcon.entries.filterNot { it == icon }.forEach { other ->
            packages.setComponentEnabledSetting(
                other.component(context.packageName),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP,
            )
        }
    }
}

/** Binds the real one. Here rather than in `:app` so the module that owns it also wires it. */
@Module
@InstallIn(SingletonComponent::class)
abstract class DiscreetIconsModule {
    @Binds
    abstract fun bindDiscreetIcons(impl: LauncherAliasIcons): DiscreetIcons
}
