package com.shnapps.couple.core.data.couple

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * An invite code that arrived by link before the user could act on it.
 *
 * A link can be opened while signed out, or before the age gate. Rather than routing the
 * deep link straight to pairing — which would skip those steps — the code waits here and
 * the pairing screen picks it up whenever the user reaches it.
 *
 * In memory only. If the process dies first, the six digits still work typed by hand.
 */
@Singleton
class PendingInvite @Inject constructor() {
    private val code = MutableStateFlow<String?>(null)
    val pending: StateFlow<String?> = code.asStateFlow()

    fun offer(candidate: String?) {
        if (candidate != null && CODE_PATTERN.matches(candidate)) code.value = candidate
    }

    /** Returns the waiting code, once. */
    fun consume(): String? = code.value.also { code.value = null }

    private companion object {
        val CODE_PATTERN = Regex("^[0-9]{6}$")
    }
}
