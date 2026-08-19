package dev.enthusiastdev.netinspector.data.persistence.preferences.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.enthusiastdev.netinspector.data.persistence.preferences.AppPreferencesSerializer
import dev.enthusiastdev.netinspector.data.persistence.preferences.AppSettingsRepository
import dev.enthusiastdev.netinspector.data.persistence.preferences.DefaultAppSettingsRepository
import dev.enthusiastdev.netinspector.data.persistence.preferences.DefaultLanAcknowledgementRepository
import dev.enthusiastdev.netinspector.data.persistence.preferences.DefaultRetentionSettingsRepository
import dev.enthusiastdev.netinspector.data.persistence.preferences.LanAcknowledgementRepository
import dev.enthusiastdev.netinspector.data.persistence.preferences.RetentionSettingsRepository
import dev.enthusiastdev.netinspector.data.persistence.proto.AppPreferences
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PreferencesModule {
    @Binds
    @Singleton
    abstract fun bindLanAcknowledgementRepository(
        impl: DefaultLanAcknowledgementRepository,
    ): LanAcknowledgementRepository

    @Binds
    @Singleton
    abstract fun bindRetentionSettingsRepository(impl: DefaultRetentionSettingsRepository): RetentionSettingsRepository

    @Binds
    @Singleton
    abstract fun bindAppSettingsRepository(impl: DefaultAppSettingsRepository): AppSettingsRepository

    companion object {
        @Provides
        @Singleton
        fun provideAppPreferencesDataStore(
            @ApplicationContext context: Context,
        ): DataStore<AppPreferences> =
            DataStoreFactory.create(serializer = AppPreferencesSerializer) {
                File(context.filesDir, "datastore/app_preferences.pb")
            }
    }
}
