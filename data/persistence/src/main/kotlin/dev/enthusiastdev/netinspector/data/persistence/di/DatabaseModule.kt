package dev.enthusiastdev.netinspector.data.persistence.di

import android.content.Context
import androidx.room.Room
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.enthusiastdev.netinspector.data.persistence.NetInspectorDatabase
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

    companion object {
        @Provides
        @Singleton
        fun provideDatabase(
            @ApplicationContext context: Context,
        ): NetInspectorDatabase =
            Room.databaseBuilder(context, NetInspectorDatabase::class.java, "netinspector.db").build()

        @Provides
        @Singleton
        fun provideSavedWolTargetDao(database: NetInspectorDatabase): SavedWolTargetDao = database.savedWolTargetDao()
    }
}
