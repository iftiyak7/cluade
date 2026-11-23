package com.hayat.core.datastore

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import com.hayat.core.domain.repository.PreferencesRepository
import com.hayat.core.model.UserPreferences
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

internal object PreferencesSerializer : Serializer<PreferencesDto> {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    override val defaultValue: PreferencesDto = PreferencesDto()
    override suspend fun readFrom(input: InputStream): PreferencesDto = try {
        json.decodeFromString(PreferencesDto.serializer(), input.readBytes().decodeToString())
    } catch (e: SerializationException) {
        throw CorruptionException("Unreadable preferences", e)
    } catch (e: IllegalArgumentException) {
        throw CorruptionException("Unreadable preferences", e)
    }
    override suspend fun writeTo(t: PreferencesDto, output: OutputStream) {
        output.write(json.encodeToString(PreferencesDto.serializer(), t).encodeToByteArray())
    }
}

/** Factory kept separate so tests can create a store on a temp file. */
internal fun createPreferencesStore(file: () -> File, scope: CoroutineScope): DataStore<PreferencesDto> =
    DataStoreFactory.create(
        serializer = PreferencesSerializer,
        // A corrupt settings file resets settings to defaults; it never affects the database.
        corruptionHandler = ReplaceFileCorruptionHandler { PreferencesDto() },
        scope = scope,
        produceFile = file,
    )

@Singleton
class DataStorePreferencesRepository internal constructor(private val store: DataStore<PreferencesDto>) : PreferencesRepository {
    @Inject constructor(@ApplicationContext context: Context) : this(
        createPreferencesStore({ File(context.filesDir, "datastore/user_preferences.json") }, CoroutineScope(SupervisorJob() + Dispatchers.IO)),
    )

    override val preferences: Flow<UserPreferences> = store.data.map { it.toModel() }.distinctUntilChanged()
    override suspend fun current(): UserPreferences = preferences.first()
    override suspend fun update(transform: (UserPreferences) -> UserPreferences) {
        store.updateData { dto -> transform(dto.toModel()).toDto() }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class DataStoreModule {
    @Binds abstract fun bindPreferences(impl: DataStorePreferencesRepository): PreferencesRepository
}
