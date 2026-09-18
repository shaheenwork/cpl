package com.shnapps.couple.core.data.di

import com.shnapps.couple.core.data.auth.AuthRepository
import com.shnapps.couple.core.data.auth.DefaultAuthRepository
import com.shnapps.couple.core.data.couple.CoupleRepository
import com.shnapps.couple.core.data.couple.DefaultCoupleRepository
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
}
