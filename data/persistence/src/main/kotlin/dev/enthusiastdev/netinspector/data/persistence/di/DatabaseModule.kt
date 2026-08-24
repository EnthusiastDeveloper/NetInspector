package dev.enthusiastdev.netinspector.data.persistence.di

import android.content.Context
import androidx.room.Room
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.enthusiastdev.netinspector.data.persistence.MIGRATION_1_2
import dev.enthusiastdev.netinspector.data.persistence.MIGRATION_2_3
import dev.enthusiastdev.netinspector.data.persistence.MIGRATION_3_4
import dev.enthusiastdev.netinspector.data.persistence.MIGRATION_4_5
import dev.enthusiastdev.netinspector.data.persistence.NetInspectorDatabase
import dev.enthusiastdev.netinspector.data.persistence.diagnostics.DefaultDiagnosticRunRepository
import dev.enthusiastdev.netinspector.data.persistence.diagnostics.DiagnosticRunDao
import dev.enthusiastdev.netinspector.data.persistence.diagnostics.DiagnosticRunRepository
import dev.enthusiastdev.netinspector.data.persistence.host.DefaultSavedHostRepository
import dev.enthusiastdev.netinspector.data.persistence.host.SavedHostDao
import dev.enthusiastdev.netinspector.data.persistence.host.SavedHostRepository
import dev.enthusiastdev.netinspector.data.persistence.lan.DefaultKnownLanHostRepository
import dev.enthusiastdev.netinspector.data.persistence.lan.KnownLanHostDao
import dev.enthusiastdev.netinspector.data.persistence.lan.KnownLanHostRepository
import dev.enthusiastdev.netinspector.data.persistence.scan.DefaultScanHistoryRepository
import dev.enthusiastdev.netinspector.data.persistence.scan.KnownApDao
import dev.enthusiastdev.netinspector.data.persistence.scan.ScanHistoryRepository
import dev.enthusiastdev.netinspector.data.persistence.scan.ScanObservationDao
import dev.enthusiastdev.netinspector.data.persistence.scan.ScanSessionDao
import dev.enthusiastdev.netinspector.data.persistence.wol.DefaultSavedWolTargetRepository
import dev.enthusiastdev.netinspector.data.persistence.wol.SavedWolTargetDao
import dev.enthusiastdev.netinspector.data.persistence.wol.SavedWolTargetRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DatabaseModule {
    @Binds
    @Singleton
    abstract fun bindSavedWolTargetRepository(impl: DefaultSavedWolTargetRepository): SavedWolTargetRepository

    @Binds
    @Singleton
    abstract fun bindScanHistoryRepository(impl: DefaultScanHistoryRepository): ScanHistoryRepository

    @Binds
    @Singleton
    abstract fun bindDiagnosticRunRepository(impl: DefaultDiagnosticRunRepository): DiagnosticRunRepository

    @Binds
    @Singleton
    abstract fun bindSavedHostRepository(impl: DefaultSavedHostRepository): SavedHostRepository

    @Binds
    @Singleton
    abstract fun bindKnownLanHostRepository(impl: DefaultKnownLanHostRepository): KnownLanHostRepository

    companion object {
        @Provides
        @Singleton
        fun provideDatabase(
            @ApplicationContext context: Context,
        ): NetInspectorDatabase =
            Room
                .databaseBuilder(context, NetInspectorDatabase::class.java, "netinspector.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .build()

        @Provides
        @Singleton
        fun provideSavedWolTargetDao(database: NetInspectorDatabase): SavedWolTargetDao = database.savedWolTargetDao()

        @Provides
        @Singleton
        fun provideScanSessionDao(database: NetInspectorDatabase): ScanSessionDao = database.scanSessionDao()

        @Provides
        @Singleton
        fun provideScanObservationDao(database: NetInspectorDatabase): ScanObservationDao =
            database.scanObservationDao()

        @Provides
        @Singleton
        fun provideKnownApDao(database: NetInspectorDatabase): KnownApDao = database.knownApDao()

        @Provides
        @Singleton
        fun provideDiagnosticRunDao(database: NetInspectorDatabase): DiagnosticRunDao = database.diagnosticRunDao()

        @Provides
        @Singleton
        fun provideSavedHostDao(database: NetInspectorDatabase): SavedHostDao = database.savedHostDao()

        @Provides
        @Singleton
        fun provideKnownLanHostDao(database: NetInspectorDatabase): KnownLanHostDao = database.knownLanHostDao()
    }
}
