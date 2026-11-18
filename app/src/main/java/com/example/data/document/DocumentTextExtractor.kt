package com.example.data.document

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Xml
import androidx.core.text.HtmlCompat
import com.example.data.model.AudiobookSegment
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.charset.Charset
import java.text.BreakIterator
import java.util.Locale
import java.util.zip.ZipInputStream

data class ExtractedDocument(
    val title: String,
    val sourceFileName: String,
    val sourceType: String,
    val fullText: String,
    val segments: List<AudiobookSegment>,
    val pageCount: Int
)

class DocumentTextExtractor(private val context: Context) {

    @Volatile
    private var pdfBoxInitialized = false

    private fun ensurePdfBox() {
        if (!pdfBoxInitialized) {
            synchronized(this) {
                if (!pdfBoxInitialized) {
                    PDFBoxResourceLoader.init(context.applicationContext)
                    pdfBoxInitialized = true
                }
            }
        }
    }

    suspend fun extractFromUri(uri: Uri): Result<ExtractedDocument> = withContext(Dispatchers.IO) {
        runCatching {
            val fileName = resolveFileName(uri) ?: "Document.txt"
            val mimeType = context.contentResolver.getType(uri).orEmpty().lowercase(Locale.US)
            val ext = fileName.substringAfterLast('.', "").lowercase(Locale.US)

            val sourceType = when {
                ext == "pdf" || mimeType == "application/pdf" -> "PDF"
                ext == "epub" || mimeType == "application/epub+zip" -> "EPUB"
                ext == "docx" || mimeType.contains("wordprocessingml") -> "DOCX"
                ext == "html" || ext == "htm" || mimeType == "text/html" -> "HTML"
                ext == "md" || ext == "markdown" -> "MD"
                ext == "rtf" || mimeType == "application/rtf" -> "RTF"
                else -> ext.uppercase(Locale.US).ifBlank { "TXT" }
            }

            val rawPages: List<Pair<Int, String>> = context.contentResolver.openInputStream(uri)?.use { stream ->
                when (sourceType) {
                    "PDF" -> extractPdfPages(stream)
                    "EPUB" -> extractEpubPages(stream)
                    "DOCX" -> listOf(1 to extractDocxText(stream))
                    "HTML" -> listOf(1 to stripHtml(stream.readBytes().toString(Charsets.UTF_8)))
                    "RTF" -> listOf(1 to stripRtf(stream.readBytes().toString(Charsets.UTF_8)))
                    else -> listOf(1 to decodePlainText(stream.readBytes()))
                }
            } ?: error("Unable to open the selected file.")

            val cleanedPages = rawPages
                .map { (page, text) -> page to normalizeWhitespace(text) }
                .filter { it.second.isNotBlank() }

            if (cleanedPages.isEmpty()) {
                error("No readable text was found inside \"$fileName\". Scanned image-only PDFs require OCR first.")
            }

            buildExtractedDocument(
                title = fileName.substringBeforeLast('.').replace('_', ' ').replace('-', ' ').trim()
                    .ifBlank { "Untitled Audiobook" },
                sourceFileName = fileName,
                sourceType = sourceType,
                pages = cleanedPages
            )
        }
    }

    fun extractFromPlainText(title: String, rawText: String, sourceType: String = "TEXT"): ExtractedDocument {
        val normalized = normalizeWhitespace(rawText)
        val estimatedPages = normalized.chunked(1800).mapIndexed { idx, chunk -> (idx + 1) to chunk }
            .ifEmpty { listOf(1 to "") }
        return buildExtractedDocument(
            title = title.trim().ifBlank { "Untitled Note" },
            sourceFileName = "${title.trim().ifBlank { "note" }}.txt",
            sourceType = sourceType,
            pages = estimatedPages
        )
    }

    private fun buildExtractedDocument(
        title: String,
        sourceFileName: String,
        sourceType: String,
        pages: List<Pair<Int, String>>
    ): ExtractedDocument {
        val segments = mutableListOf<AudiobookSegment>()
        val fullBuilder = StringBuilder()
        var idx = 0

        for ((pageNum, pageText) in pages) {
            val paragraphs = pageText.split(Regex("\\n{2,}")).map { it.trim() }.filter { it.isNotBlank() }
            for (para in paragraphs) {
                val sentences = splitIntoSentences(para)
                for (sentence in sentences) {
                    val trimmed = sentence.trim()
                    if (trimmed.isNotEmpty()) {
                        val isHeading = trimmed.length in 3..80 &&
                            !trimmed.endsWith(".") &&
                            (trimmed == trimmed.uppercase(Locale.US) ||
                                trimmed.startsWith("Chapter ", ignoreCase = true) ||
                                trimmed.startsWith("Part ", ignoreCase = true))
                        segments.add(
                            AudiobookSegment(
                                index = idx++,
                                text = trimmed,
                                startMs = 0L,
                                endMs = 0L,
                                pageNumber = pageNum,
                                chapterTitle = if (isHeading) trimmed else "",
                                isHeading = isHeading
                            )
                        )
                        if (fullBuilder.isNotEmpty()) fullBuilder.append(' ')
                        fullBuilder.append(trimmed)
                    }
                }
            }
        }

        // If text was split cleanly into pages already, use pages count; if single text block, synthesize logical pages (~1800 chars each)
        val finalSegments = if (pages.size == 1 && segments.sumOf { it.text.length } > 2200) {
            var charAcc = 0
            segments.map { seg ->
                val computedPage = (charAcc / 1800) + 1
                charAcc += seg.text.length + 1
                seg.copy(pageNumber = computedPage)
            }
        } else {
            segments
        }

        val totalPages = finalSegments.maxOfOrNull { it.pageNumber } ?: pages.size.coerceAtLeast(1)

        return ExtractedDocument(
            title = title,
            sourceFileName = sourceFileName,
            sourceType = sourceType,
            fullText = fullBuilder.toString(),
            segments = finalSegments,
            pageCount = totalPages
        )
    }

    private fun extractPdfPages(stream: InputStream): List<Pair<Int, String>> {
        ensurePdfBox()
        val pages = mutableListOf<Pair<Int, String>>()
        PDDocument.load(stream).use { doc ->
            val stripper = PDFTextStripper()
            val count = doc.numberOfPages
            for (page in 1..count) {
                stripper.startPage = page
                stripper.endPage = page
                val text = stripper.getText(doc).orEmpty()
                if (text.isNotBlank()) {
                    pages.add(page to text)
                }
            }
        }
        return pages
    }

    private fun extractEpubPages(stream: InputStream): List<Pair<Int, String>> {
        val htmlEntries = linkedMapOf<String, String>()
        var opfContent: String? = null
        var opfDir = ""

        ZipInputStream(stream).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val name = entry.name
                    val bytes = zip.readBytes()
                    when {
                        name.endsWith(".opf", ignoreCase = true) -> {
                            opfContent = bytes.toString(Charsets.UTF_8)
                            opfDir = name.substringBeforeLast('/', "")
                        }
                        name.endsWith(".xhtml", ignoreCase = true) ||
                            name.endsWith(".html", ignoreCase = true) ||
                            name.endsWith(".htm", ignoreCase = true) -> {
                            htmlEntries[name] = bytes.toString(Charsets.UTF_8)
                        }
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }

        val orderedNames = if (opfContent != null) {
            resolveEpubSpineOrder(opfContent!!, opfDir, htmlEntries.keys)
        } else {
            htmlEntries.keys.sorted()
        }

        return orderedNames.mapIndexedNotNull { index, key ->
            val rawHtml = htmlEntries[key] ?: return@mapIndexedNotNull null
            val text = stripHtml(rawHtml)
            if (text.isBlank()) null else (index + 1) to text
        }
    }

    private fun resolveEpubSpineOrder(
        opfXml: String,
        opfDir: String,
        availableKeys: Set<String>
    ): List<String> {
        val idToHref = mutableMapOf<String, String>()
        val spineIds = mutableListOf<String>()
        runCatching {
            val parser = Xml.newPullParser()
            parser.setInput(ByteArrayInputStream(opfXml.toByteArray(Charsets.UTF_8)), "UTF-8")
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    when (parser.name.lowercase(Locale.US)) {
                        "item" -> {
                            val id = parser.getAttributeValue(null, "id")
                            val href = parser.getAttributeValue(null, "href")
                            if (!id.isNullOrBlank() && !href.isNullOrBlank()) {
                                idToHref[id] = href
                            }
                        }
                        "itemref" -> {
                            val idref = parser.getAttributeValue(null, "idref")
                            if (!idref.isNullOrBlank()) spineIds.add(idref)
                        }
                    }
                }
                event = parser.next()
            }
        }
        val prefix = if (opfDir.isNotBlank()) "$opfDir/" else ""
        val resolved = spineIds.mapNotNull { id ->
            val href = idToHref[id] ?: return@mapNotNull null
            val candidate = "$prefix$href".removePrefix("./")
            availableKeys.firstOrNull { it.equals(candidate, ignoreCase = true) || it.endsWith("/$href", ignoreCase = true) }
        }.distinct()
        return if (resolved.isNotEmpty()) resolved else availableKeys.sorted()
    }

    private fun extractDocxText(stream: InputStream): String {
        val builder = StringBuilder()
        ZipInputStream(stream).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory && entry.name.equals("word/document.xml", ignoreCase = true)) {
                    val xmlBytes = zip.readBytes()
                    val parser = Xml.newPullParser()
                    parser.setInput(ByteArrayInputStream(xmlBytes), "UTF-8")
                    var event = parser.eventType
                    var inParagraph = false
                    while (event != XmlPullParser.END_DOCUMENT) {
                        when (event) {
                            XmlPullParser.START_TAG -> {
                                when (parser.name) {
                                    "w:p", "p" -> inParagraph = true
                                    "w:tab", "tab" -> if (inParagraph) builder.append(' ')
                                    "w:br", "br" -> if (inParagraph) builder.append('\n')
                                }
                            }
                            XmlPullParser.TEXT -> {
                                if (inParagraph) builder.append(parser.text)
                            }
                            XmlPullParser.END_TAG -> {
                                if (parser.name == "w:p" || parser.name == "p") {
                                    builder.append("\n\n")
                                    inParagraph = false
                                }
                            }
                        }
                        event = parser.next()
                    }
                    break
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return builder.toString()
    }

    private fun stripHtml(html: String): String {
        val cleaned = html
            .replace(Regex("(?is)<style.*?>.*?</style>"), " ")
            .replace(Regex("(?is)<script.*?>.*?</script>"), " ")
            .replace(Regex("(?i)</(p|div|h1|h2|h3|h4|h5|h6|li|tr)>"), "\n\n")
            .replace(Regex("(?i)<br\\s*/?>"), "\n")
        return HtmlCompat.fromHtml(cleaned, HtmlCompat.FROM_HTML_MODE_LEGACY).toString()
    }

    private fun stripRtf(rtf: String): String {
        return rtf
            .replace(Regex("\\{\\\\\\*?[^{}]*\\}"), " ")
            .replace(Regex("\\\\par\\b"), "\n\n")
            .replace(Regex("\\\\line\\b"), "\n")
            .replace(Regex("\\\\'[0-9a-fA-F]{2}"), " ")
            .replace(Regex("\\\\[a-zA-Z]+-?\\d* ?"), "")
            .replace("{", "")
            .replace("}", "")
    }

    private fun decodePlainText(bytes: ByteArray): String {
        return when {
            bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() ->
                String(bytes, Charset.forName("UTF-16LE"))
            bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() ->
                String(bytes, Charset.forName("UTF-16BE"))
            else -> String(bytes, Charsets.UTF_8)
        }
    }

    private fun normalizeWhitespace(input: String): String {
        return input
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .replace('\u00A0', ' ')
            .replace(Regex("[ \\t]+"), " ")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
    }

    private fun splitIntoSentences(paragraph: String): List<String> {
        val iterator = BreakIterator.getSentenceInstance(Locale.US)
        iterator.setText(paragraph)
        val result = mutableListOf<String>()
        var start = iterator.first()
        var end = iterator.next()
        while (end != BreakIterator.DONE) {
            val part = paragraph.substring(start, end).trim()
            if (part.isNotEmpty()) {
                if (part.length > 350) {
                    result.addAll(splitLongSentence(part, 240))
                } else {
                    result.add(part)
                }
            }
            start = end
            end = iterator.next()
        }
        return if (result.isEmpty()) listOf(paragraph) else result
    }

    private fun splitLongSentence(text: String, maxLen: Int): List<String> {
        val parts = mutableListOf<String>()
        var remaining = text
        while (remaining.length > maxLen) {
            val cut = remaining.lastIndexOf(' ', maxLen).takeIf { it > 40 } ?: maxLen
            parts.add(remaining.substring(0, cut).trim())
            remaining = remaining.substring(cut).trim()
        }
        if (remaining.isNotEmpty()) parts.add(remaining)
        return parts
    }

    private fun resolveFileName(uri: Uri): String? {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) return cursor.getString(idx)
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/')
    }

    companion object {
        fun groupSegmentsIntoChunks(
            segments: List<AudiobookSegment>,
            maxCharsPerChunk: Int = 2400
        ): List<List<AudiobookSegment>> {
            if (segments.isEmpty()) return emptyList()
            val chunks = mutableListOf<List<AudiobookSegment>>()
            var current = mutableListOf<AudiobookSegment>()
            var currentLen = 0
            for (seg in segments) {
                val segLen = seg.text.length + 1
                if (current.isNotEmpty() && currentLen + segLen > maxCharsPerChunk) {
                    chunks.add(current)
                    current = mutableListOf()
                    currentLen = 0
                }
                current.add(seg)
                currentLen += segLen
            }
            if (current.isNotEmpty()) chunks.add(current)
            return chunks
        }
    }
}
