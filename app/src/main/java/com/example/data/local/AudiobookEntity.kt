package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "audiobooks")
data class AudiobookEntity(
    @PrimaryKey val id: String,
    val title: String,
    val sourceFileName: String,
    val sourceType: String,
    val fullText: String,
    val segmentsJson: String,
    val audioFilePath: String?,
    val audioFormat: String?,
    val audioFileSizeBytes: Long,
    val durationMs: Long,
    val lastPositionMs: Long,
    val voiceUsedLabel: String,
    val voiceProvider: String,
    val characterCount: Int,
    val pageCount: Int,
    val createdAt: Long,
    val updatedAt: Long
) {
    val hasIncludedAudio: Boolean
        get() = !audioFilePath.isNullOrBlank() && durationMs > 0L
}
