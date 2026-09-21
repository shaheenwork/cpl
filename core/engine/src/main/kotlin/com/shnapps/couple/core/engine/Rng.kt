package com.shnapps.couple.core.engine

import kotlin.math.floor

/**
 * mulberry32 (engine/SPEC.md §2). Only 32-bit integer arithmetic, so it produces exactly the
 * sequence the server's TypeScript produces for the same seed — which is what makes a night
 * reproducible on either side.
 */
internal class Rng(seed: Long) {

    private var state: Int = seed.toInt()

    /** A double in [0, 1). The shifts and multipliers are mulberry32's own. */
    @Suppress("MagicNumber")
    fun next(): Double {
        state += INCREMENT
        var t = state
        t = (t xor (t ushr 15)) * (t or 1)
        t = t xor (t + (t xor (t ushr 7)) * (t or 61))
        return ((t xor (t ushr 14)).toLong() and UINT_MASK).toDouble() / TWO_TO_32
    }

    /** An integer in [0, n). */
    fun nextInt(n: Int): Int = floor(next() * n).toInt()

    companion object {
        private const val INCREMENT = 0x6D2B79F5
        private const val UINT_MASK = 0xFFFFFFFFL
        private const val TWO_TO_32 = 4294967296.0
        private const val GOLDEN = 0x9E3779B9L

        /** The seed for retry [attempt] of a generation, as an unsigned 32-bit value. */
        fun attemptSeed(seed: Long, attempt: Int): Long = (seed + attempt * GOLDEN) and UINT_MASK
    }
}
