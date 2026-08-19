package dev.enthusiastdev.netinspector.data.wifi.di

import android.content.Context
import android.net.wifi.WifiManager
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.enthusiastdev.netinspector.data.wifi.ConnectionRepository
import dev.enthusiastdev.netinspector.data.wifi.DefaultConnectionRepository
import dev.enthusiastdev.netinspector.data.wifi.DefaultWifiScanRepository
import dev.enthusiastdev.netinspector.data.wifi.WifiScanRepository
import java.time.Clock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class WifiModule {
    @Binds
    @Singleton
    abstract fun bindConnectionRepository(impl: DefaultConnectionRepository): ConnectionRepository

    @Binds
    @Singleton
    abstract fun bindWifiScanRepository(impl: DefaultWifiScanRepository): WifiScanRepository

    companion object {
        @Provides
        @Singleton
        fun provideWifiManager(
            @ApplicationContext context: Context,
        ): WifiManager = requireNotNull(context.getSystemService(WifiManager::class.java)) { "WifiManager unavailable" }

        @Provides
        @Singleton
        fun provideClock(): Clock = Clock.systemUTC()
    }
}
