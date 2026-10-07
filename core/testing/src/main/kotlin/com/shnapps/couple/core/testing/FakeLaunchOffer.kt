package com.shnapps.couple.core.testing

import com.shnapps.couple.core.data.entitlement.LaunchOffer

/** A [LaunchOffer] a test sets by hand. Off by default: most tests are not about the launch. */
class FakeLaunchOffer(
    override val everythingFree: Boolean = false,
    override val freeUntilMillis: Long? = null,
    override val daysLeft: Int? = null,
    override val shouldRemind: Boolean = false,
) : LaunchOffer
