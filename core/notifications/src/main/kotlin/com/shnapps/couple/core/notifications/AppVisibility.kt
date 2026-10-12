package com.shnapps.couple.core.notifications

import android.app.Activity
import android.app.Application
import android.os.Bundle
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Whether the app is on the screen right now (D-151).
 *
 * A notification is for someone who is looking at something else. When the app is open the screen
 * already says what a push would — a partner's answer arrives in the round, a game they started
 * appears on home — so a banner on top of it is the same news twice, and one banner a step is what
 * "annoying" is. [Notifier] asks this before it posts.
 */
interface AppVisibility {
    val inForeground: Boolean
}

/**
 * The real one: the app is in the foreground while any of its activities is started.
 *
 * Counted rather than asked of the process lifecycle so that it needs nothing the project does not
 * already have. A rotation stops an activity and starts it again in the same breath, so for that
 * instant this says "background" — and a push that lands in that instant is shown, which is the
 * right way round to be wrong.
 */
@Singleton
class TrackedAppVisibility @Inject constructor() : AppVisibility, Application.ActivityLifecycleCallbacks {

    private val started = AtomicInteger(0)

    override val inForeground: Boolean get() = started.get() > 0

    /** Called once, from `Application.onCreate`: before anything can start, so nothing is missed. */
    fun register(application: Application) = application.registerActivityLifecycleCallbacks(this)

    override fun onActivityStarted(activity: Activity) {
        started.incrementAndGet()
    }

    override fun onActivityStopped(activity: Activity) {
        started.updateAndGet { (it - 1).coerceAtLeast(0) }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

    override fun onActivityResumed(activity: Activity) = Unit

    override fun onActivityPaused(activity: Activity) = Unit

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

    override fun onActivityDestroyed(activity: Activity) = Unit
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AppVisibilityModule {
    @Binds
    abstract fun bindAppVisibility(impl: TrackedAppVisibility): AppVisibility
}
