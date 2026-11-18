package com.example.data.model

enum class AudiobookFormat(
    val extension: String,
    val mimeType: String,
    val displayName: String,
    val description: String,
    val supportsOfflineEncoding: Boolean
) {
    M4B(
        extension = "m4b",
        mimeType = "audio/mp4",
        displayName = "M4B Audiobook",
        description = "Standard audiobook container (AAC-LC)",
        supportsOfflineEncoding = true
    ),
    M4A(
        extension = "m4a",
        mimeType = "audio/mp4",
        displayName = "M4A Audio",
        description = "MPEG-4 AAC high-efficiency audio",
        supportsOfflineEncoding = true
    ),
    MP3(
        extension = "mp3",
        mimeType = "audio/mpeg",
        displayName = "MP3 Audio",
        description = "Universal MPEG audio (Polly native)",
        supportsOfflineEncoding = false
    ),
    WAV(
        extension = "wav",
        mimeType = "audio/wav",
        displayName = "WAV Lossless",
        description = "Uncompressed PCM audio",
        supportsOfflineEncoding = true
    ),
    OGG(
        extension = "ogg",
        mimeType = "audio/ogg",
        displayName = "OGG Vorbis",
        description = "Compact open-source audio (Polly)",
        supportsOfflineEncoding = false
    );

    companion object {
        fun fromExtension(ext: String?): AudiobookFormat =
            entries.firstOrNull { it.extension.equals(ext, ignoreCase = true) } ?: M4B
    }
}
