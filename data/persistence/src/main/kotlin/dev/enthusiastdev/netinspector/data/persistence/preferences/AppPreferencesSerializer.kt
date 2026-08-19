package dev.enthusiastdev.netinspector.data.persistence.preferences

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import com.google.protobuf.InvalidProtocolBufferException
import dev.enthusiastdev.netinspector.data.persistence.proto.AppPreferences
import java.io.InputStream
import java.io.OutputStream

internal object AppPreferencesSerializer : Serializer<AppPreferences> {
    override val defaultValue: AppPreferences = AppPreferences.getDefaultInstance()

    override suspend fun readFrom(input: InputStream): AppPreferences =
        try {
            AppPreferences.parseFrom(input)
        } catch (exception: InvalidProtocolBufferException) {
            throw CorruptionException("Cannot read AppPreferences proto", exception)
        }

    override suspend fun writeTo(
        t: AppPreferences,
        output: OutputStream,
    ) {
        t.writeTo(output)
    }
}
