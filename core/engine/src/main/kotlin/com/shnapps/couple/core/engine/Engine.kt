package com.shnapps.couple.core.engine

/**
 * Marker for the client half of the experience engine (BUILD_PROMPT.md §10.1).
 *
 * The client side owns chapter assembly, interaction-type variety, pacing, duration
 * fitting and local remixing within the bounded alternates pool the server returned.
 * It never performs hard boundary filtering: that runs server-side only, because the
 * combined boundary set must not reach the device (§5.4).
 *
 * Bumped whenever generation output changes for identical inputs, so that stored
 * experiences remain reproducible (§10.6).
 */
object Engine {
    const val VERSION: Int = 1
}
