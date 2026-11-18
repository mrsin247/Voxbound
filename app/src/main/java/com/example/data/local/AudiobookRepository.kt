package com.example.data.local

import com.example.data.model.AudiobookSegment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class AudiobookRepository(
    private val dao: AudiobookDao,
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    val allAudiobooks: Flow<List<AudiobookEntity>> = dao.observeAll()

    fun observeAudiobook(id: String): Flow<AudiobookEntity?> = dao.observeById(id)

    suspend fun getAudiobook(id: String): AudiobookEntity? = dao.getById(id)

    suspend fun upsert(entity: AudiobookEntity) = dao.upsert(entity)

    suspend fun updatePlaybackPosition(id: String, positionMs: Long) {
        dao.updatePlaybackPosition(id, positionMs, System.currentTimeMillis())
    }

    suspend fun deleteAudiobook(entity: AudiobookEntity) {
        withContext(Dispatchers.IO) {
            entity.audioFilePath?.let { path ->
                runCatching { File(path).delete() }
            }
        }
        dao.deleteById(entity.id)
    }

    fun decodeSegments(segmentsJson: String): List<AudiobookSegment> {
        return runCatching {
            json.decodeFromString<List<AudiobookSegment>>(segmentsJson)
        }.getOrDefault(emptyList())
    }

    fun encodeSegments(segments: List<AudiobookSegment>): String {
        return json.encodeToString(segments)
    }
}
