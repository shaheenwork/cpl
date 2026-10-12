package com.shnapps.couple.core.data.entitlement

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * When this app arrived on this phone, from the package manager (V2 rule 5).
 *
 * The opening month needs a start, and this is the only one nobody has to maintain: the system
 * already holds it, it survives app data being cleared, and no screen or document has to defend it.
 * A reinstall resets it and therefore starts the month again — a knowing trade, because the
 * alternative is an account, and the product does not have one.
 */
@Singleton
class AndroidInstallDate @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : InstallDate {

    // Zero means "would not say", which the caller reads as "just now" rather than "long ago":
    // only a package manager that will not answer about this app's own package gets there.
    override suspend fun firstInstalledAtMillis(): Long = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).firstInstallTime
    }.getOrDefault(0L)
}
