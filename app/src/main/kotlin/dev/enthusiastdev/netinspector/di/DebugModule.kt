package dev.enthusiastdev.netinspector.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.enthusiastdev.netinspector.core.common.log.LogRingBuffer
import javax.inject.Singleton

/** [LogRingBuffer] lives in `:core:common`, which has no Hilt processor (the same reason a
 * plain [kotlinx.coroutines.CoroutineScope] needs [CoroutineScopeModule] rather than an
 * `@Inject constructor`), so it needs an explicit provider. */
@Module
@InstallIn(SingletonComponent::class)
object DebugModule {
    @Provides
    @Singleton
    fun provideLogRingBuffer(): LogRingBuffer = LogRingBuffer(capacity = 300)
}
