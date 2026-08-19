package dev.enthusiastdev.netinspector.data.wifi.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.enthusiastdev.netinspector.data.wifi.ConnectionRepository
import dev.enthusiastdev.netinspector.data.wifi.DefaultConnectionRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class WifiModule {
    @Binds
    @Singleton
    abstract fun bindConnectionRepository(impl: DefaultConnectionRepository): ConnectionRepository
}
