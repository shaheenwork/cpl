package com.shnapps.couple.core.data.di

import com.shnapps.couple.core.data.account.AccountRepository
import com.shnapps.couple.core.data.account.DefaultAccountRepository
import com.shnapps.couple.core.data.auth.AuthRepository
import com.shnapps.couple.core.data.auth.DefaultAuthRepository
import com.shnapps.couple.core.data.billing.BillingRepository
import com.shnapps.couple.core.data.billing.DefaultBillingRepository
import com.shnapps.couple.core.data.boundaries.BoundaryRepository
import com.shnapps.couple.core.data.boundaries.DefaultBoundaryRepository
import com.shnapps.couple.core.data.content.AssetShippedContent
import com.shnapps.couple.core.data.content.ContentRepository
import com.shnapps.couple.core.data.content.DefaultContentRepository
import com.shnapps.couple.core.data.content.ShippedContent
import com.shnapps.couple.core.data.couple.CoupleRepository
import com.shnapps.couple.core.data.couple.DefaultCoupleRepository
import com.shnapps.couple.core.data.entitlement.Allowances
import com.shnapps.couple.core.data.entitlement.DefaultAllowances
import com.shnapps.couple.core.data.entitlement.LaunchOffer
import com.shnapps.couple.core.data.entitlement.RemoteLaunchOffer
import com.shnapps.couple.core.data.game.DefaultGameRepository
import com.shnapps.couple.core.data.game.GameRepository
import com.shnapps.couple.core.data.instant.ContentLevelWriter
import com.shnapps.couple.core.data.instant.DefaultInstantPlayRepository
import com.shnapps.couple.core.data.instant.InstantPlayRepository
import com.shnapps.couple.core.data.limits.DefaultLimitsRepository
import com.shnapps.couple.core.data.limits.LimitsRepository
import com.shnapps.couple.core.data.media.DefaultMediaRepository
import com.shnapps.couple.core.data.media.MediaRepository
import com.shnapps.couple.core.data.night.DefaultNightRepository
import com.shnapps.couple.core.data.night.NightRepository
import com.shnapps.couple.core.data.notifications.DefaultNotificationsRepository
import com.shnapps.couple.core.data.notifications.NotificationsRepository
import com.shnapps.couple.core.data.surprise.ArcRepository
import com.shnapps.couple.core.data.surprise.DefaultArcRepository
import com.shnapps.couple.core.data.surprise.DefaultSurpriseRepository
import com.shnapps.couple.core.data.surprise.SurpriseRepository
import com.shnapps.couple.core.data.taxonomy.ContentTaxonomyRepository
import com.shnapps.couple.core.data.taxonomy.TaxonomyRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: DefaultAuthRepository): AuthRepository

    @Binds
    @Singleton
    abstract fun bindCoupleRepository(impl: DefaultCoupleRepository): CoupleRepository

    @Binds
    @Singleton
    abstract fun bindTaxonomyRepository(impl: ContentTaxonomyRepository): TaxonomyRepository

    @Binds
    @Singleton
    abstract fun bindContentRepository(impl: DefaultContentRepository): ContentRepository

    @Binds
    @Singleton
    abstract fun bindShippedContent(impl: AssetShippedContent): ShippedContent

    @Binds
    @Singleton
    abstract fun bindBoundaryRepository(impl: DefaultBoundaryRepository): BoundaryRepository

    @Binds
    abstract fun bindLimitsRepository(impl: DefaultLimitsRepository): LimitsRepository

    @Binds
    abstract fun bindAllowances(impl: DefaultAllowances): Allowances

    @Binds
    abstract fun bindLaunchOffer(impl: RemoteLaunchOffer): LaunchOffer

    @Binds
    @Singleton
    abstract fun bindNightRepository(impl: DefaultNightRepository): NightRepository

    @Binds
    @Singleton
    abstract fun bindGameRepository(impl: DefaultGameRepository): GameRepository

    @Binds
    abstract fun bindMediaRepository(impl: DefaultMediaRepository): MediaRepository

    @Binds
    abstract fun bindSurpriseRepository(impl: DefaultSurpriseRepository): SurpriseRepository

    @Binds
    abstract fun bindArcRepository(impl: DefaultArcRepository): ArcRepository

    @Binds
    abstract fun bindNotificationsRepository(impl: DefaultNotificationsRepository): NotificationsRepository

    @Binds
    abstract fun bindBillingRepository(impl: DefaultBillingRepository): BillingRepository

    @Binds
    abstract fun bindAccountRepository(impl: DefaultAccountRepository): AccountRepository

    @Binds
    @Singleton
    abstract fun bindInstantPlayRepository(impl: DefaultInstantPlayRepository): InstantPlayRepository

    /** The one method the reconciler needs, rather than the whole of auth. */
    @Binds
    @Singleton
    abstract fun bindContentLevelWriter(impl: DefaultAuthRepository): ContentLevelWriter
}
