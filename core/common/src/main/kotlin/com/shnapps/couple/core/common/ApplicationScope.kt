package com.shnapps.couple.core.common

import javax.inject.Qualifier

/**
 * The application-lifetime CoroutineScope, provided by :app.
 *
 * Lives in :core:common rather than :app so repositories can share hot flows across the
 * whole app — one Firestore listener per document, not one per collector.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope
