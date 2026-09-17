package com.shnapps.couple

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Firebase initialisation, App Check, Crashlytics and Remote Config defaults are wired
 * in here in Phase 1. In the `dev` flavor everything points at the Firebase Emulator
 * Suite (BUILD_PROMPT.md §4.3).
 */
@HiltAndroidApp
class CplApplication : Application()
