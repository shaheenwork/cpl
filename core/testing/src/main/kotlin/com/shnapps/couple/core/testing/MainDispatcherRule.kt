package com.shnapps.couple.core.testing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Swaps `Dispatchers.Main` for a test dispatcher.
 *
 * Needed by every ViewModel test, because `viewModelScope` runs on Main and there is no
 * Looper in a JVM unit test.
 *
 * Uses [StandardTestDispatcher] rather than an unconfined one on purpose: work launched in
 * a ViewModel's `init` or from a click should not complete before the test says so, and a
 * test that only passes because everything ran eagerly is testing the dispatcher, not the
 * code.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val testDispatcher: TestDispatcher = StandardTestDispatcher(),
) : TestWatcher() {

    override fun starting(description: Description) {
        Dispatchers.setMain(testDispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
