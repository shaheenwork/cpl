package com.shnapps.couple.core.ui

import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * FLAG_SECURE held for as long as any private screen is showing (BUILD_PROMPT.md §3.4). The
 * handover between two private screens is the case that matters: the next one arrives before
 * the last one leaves, and the leaving one must not switch protection off (D-073).
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class SecureScreenTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val secure: Boolean
        get() = rule.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0

    @Test
    fun `a private screen replacing another stays secure, and leaving the last one clears it`() {
        var leaving by mutableStateOf(true)
        var arriving by mutableStateOf(false)
        rule.setContent {
            if (leaving) SecureScreen()
            if (arriving) SecureScreen()
        }
        assertThat(secure).isTrue()

        // Navigation's order: the next screen enters, then the last one is disposed.
        arriving = true
        rule.waitForIdle()
        leaving = false
        rule.waitForIdle()
        assertThat(secure).isTrue()

        arriving = false
        rule.waitForIdle()
        assertThat(secure).isFalse()
    }

    @Test
    fun `a screen that is not private leaves the window as it was`() {
        var shown by mutableStateOf(true)
        rule.setContent { if (shown) SecureScreen(enabled = false) }
        assertThat(secure).isFalse()
        shown = false
        rule.waitForIdle()
        assertThat(secure).isFalse()
    }
}
