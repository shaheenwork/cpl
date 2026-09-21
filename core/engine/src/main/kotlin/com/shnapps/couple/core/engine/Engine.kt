package com.shnapps.couple.core.engine

/**
 * The client half of the experience engine (BUILD_PROMPT.md §10.1; engine/SPEC.md).
 *
 * The client assembles, checks variety, fits duration and remixes — only ever within the pool
 * the server released for a night (§5.5). It never performs the hard boundary filter: that
 * runs server-side only, because the combined boundary set must not reach the device (§5.4).
 *
 * [VERSION] matches the server's ENGINE_VERSION, and both are raised whenever output changes
 * for identical inputs, so a stored night stays reproducible (§10.6).
 */
object Engine {
    const val VERSION: Int = 1
}
