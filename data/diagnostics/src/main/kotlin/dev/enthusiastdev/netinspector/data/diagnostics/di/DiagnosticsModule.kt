package dev.enthusiastdev.netinspector.data.diagnostics.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.enthusiastdev.netinspector.data.diagnostics.icmp.DefaultPingRepository
import dev.enthusiastdev.netinspector.data.diagnostics.icmp.PingRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DiagnosticsModule {
    @Binds
    @Singleton
    abstract fun bindPingRepository(impl: DefaultPingRepository): PingRepository
}
