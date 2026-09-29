package com.shnapps.couple.core.testing

import com.shnapps.couple.core.security.DiscreetIcon
import com.shnapps.couple.core.security.DiscreetIcons

/**
 * In-memory [DiscreetIcons]: which launcher icon a test thinks is showing.
 *
 * A real object with real state rather than a mock (DECISIONS.md D-013). The real one talks to the
 * platform's package manager, which is exactly why the seam exists.
 */
class FakeDiscreetIcons(private var showing: DiscreetIcon = DiscreetIcon.DEFAULT) : DiscreetIcons {

    /** Every icon asked for, in order. */
    val shown = mutableListOf<DiscreetIcon>()

    override fun current(): DiscreetIcon = showing

    override fun show(icon: DiscreetIcon) {
        shown += icon
        showing = icon
    }
}
