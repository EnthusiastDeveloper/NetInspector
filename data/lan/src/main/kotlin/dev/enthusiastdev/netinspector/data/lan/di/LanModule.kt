package dev.enthusiastdev.netinspector.data.lan.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.enthusiastdev.netinspector.data.lan.DefaultLanDiscoveryRepository
import dev.enthusiastdev.netinspector.data.lan.LanDiscoveryRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class LanModule {
    @Binds
    @Singleton
    abstract fun bindLanDiscoveryRepository(impl: DefaultLanDiscoveryRepository): LanDiscoveryRepository
}
