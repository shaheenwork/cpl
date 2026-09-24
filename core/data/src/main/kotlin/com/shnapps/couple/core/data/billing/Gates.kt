package com.shnapps.couple.core.data.billing

import com.shnapps.couple.core.common.AppError
import com.shnapps.couple.core.model.Gate

/**
 * The limit a refusal was about, if it was about a limit at all (BUILD_PROMPT.md §19).
 *
 * The server refuses with `premium-required:GATE`, so every screen that can reach a gate reads it
 * the same way and shows the same nudge — and anything else stays an error, not a sales pitch.
 */
fun AppError.gate(): Gate? = (this as? AppError.Validation)?.let { Gate.of(it.code) }
