package com.example.data.network

import com.example.data.model.AwsCredentials
import com.example.data.model.PollyEngine
import com.example.data.model.PollyVoice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class AmazonPollyClient(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()
) {

    suspend fun fetchVoices(credentials: AwsCredentials): Result<List<PollyVoice>> = withContext(Dispatchers.IO) {
        runCatching {
            require(credentials.isConfigured) { "AWS IAM Access Key ID, Secret Access Key, and Region are required." }
            val voices = mutableListOf<PollyVoice>()
            var nextToken: String? = null

            do {
                val params = if (nextToken.isNullOrBlank()) {
                    emptyMap()
                } else {
                    mapOf("NextToken" to nextToken)
                }
                val request = AwsSigV4Signer.buildSignedRequest(
                    method = "GET",
                    region = credentials.region,
                    path = "/v1/voices",
                    queryParams = params,
                    accessKeyId = credentials.accessKeyId,
                    secretAccessKey = credentials.secretAccessKey,
                    sessionToken = credentials.sessionToken
                )
                httpClient.newCall(request).execute().use { response ->
                    val bodyStr = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        throw IllegalStateException(parseAwsError(response.code, bodyStr))
                    }
                    val root = JSONObject(bodyStr)
                    val array: JSONArray = root.optJSONArray("Voices") ?: JSONArray()
                    for (i in 0 until array.length()) {
                        val obj = array.getJSONObject(i)
                        val enginesJson = obj.optJSONArray("SupportedEngines") ?: JSONArray()
                        val engines = List(enginesJson.length()) { idx -> enginesJson.getString(idx) }
                        voices.add(
                            PollyVoice(
                                id = obj.getString("Id"),
                                name = obj.optString("Name", obj.getString("Id")),
                                gender = obj.optString("Gender", "Neutral"),
                                languageCode = obj.optString("LanguageCode", "en-US"),
                                languageName = obj.optString("LanguageName", "English"),
                                supportedEngines = engines
                            )
                        )
                    }
                    nextToken = root.optString("NextToken").takeIf { it.isNotBlank() }
                }
            } while (nextToken != null)

            voices.sortedWith(
                compareByDescending<PollyVoice> { it.languageCode.startsWith("en") }
                    .thenBy { it.languageName }
                    .thenBy { it.name }
            )
        }
    }

    suspend fun synthesizeSpeech(
        credentials: AwsCredentials,
        text: String,
        voiceId: String,
        engine: PollyEngine,
        pollyOutputFormat: String,
        sampleRate: String? = null
    ): Result<ByteArray> = withContext(Dispatchers.IO) {
        runCatching {
            require(credentials.isConfigured) { "AWS IAM credentials are not configured." }
            val payload = JSONObject().apply {
                put("OutputFormat", pollyOutputFormat)
                put("Text", text)
                put("TextType", "text")
                put("VoiceId", voiceId)
                put("Engine", engine.apiValue)
                if (!sampleRate.isNullOrBlank()) {
                    put("SampleRate", sampleRate)
                }
            }
            val request = AwsSigV4Signer.buildSignedRequest(
                method = "POST",
                region = credentials.region,
                path = "/v1/speech",
                jsonBody = payload.toString(),
                accessKeyId = credentials.accessKeyId,
                secretAccessKey = credentials.secretAccessKey,
                sessionToken = credentials.sessionToken
            )
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorStr = response.body?.string().orEmpty()
                    throw IllegalStateException(parseAwsError(response.code, errorStr))
                }
                response.body?.bytes() ?: error("Amazon Polly returned an empty audio stream.")
            }
        }
    }

    private fun parseAwsError(code: Int, rawBody: String): String {
        val parsedMsg = runCatching {
            val obj = JSONObject(rawBody)
            val msg = obj.optString("message").ifBlank { obj.optString("Message") }
            val type = obj.optString("__type").substringAfterLast('#')
            listOf(type, msg).filter { it.isNotBlank() }.joinToString(": ")
        }.getOrNull()

        return if (!parsedMsg.isNullOrBlank()) {
            "AWS Polly ($code): $parsedMsg"
        } else {
            "AWS Polly HTTP $code error. Verify your IAM Access Key, Secret Key, Region, and Polly permissions."
        }
    }

    companion object {
        val SUPPORTED_REGIONS = listOf(
            "us-east-1" to "US East (N. Virginia)",
            "us-east-2" to "US East (Ohio)",
            "us-west-2" to "US West (Oregon)",
            "eu-west-1" to "Europe (Ireland)",
            "eu-west-2" to "Europe (London)",
            "eu-central-1" to "Europe (Frankfurt)",
            "ap-southeast-1" to "Asia Pacific (Singapore)",
            "ap-southeast-2" to "Asia Pacific (Sydney)",
            "ap-northeast-1" to "Asia Pacific (Tokyo)",
            "ap-south-1" to "Asia Pacific (Mumbai)",
            "ca-central-1" to "Canada (Central)"
        )

        val CURATED_DEFAULT_VOICES = listOf(
            PollyVoice("Joanna", "Joanna", "Female", "en-US", "US English", listOf("neural", "standard", "long-form")),
            PollyVoice("Matthew", "Matthew", "Male", "en-US", "US English", listOf("neural", "standard", "long-form", "generative")),
            PollyVoice("Ruth", "Ruth", "Female", "en-US", "US English", listOf("neural", "long-form", "generative")),
            PollyVoice("Stephen", "Stephen", "Male", "en-US", "US English", listOf("neural", "generative")),
            PollyVoice("Danielle", "Danielle", "Female", "en-US", "US English", listOf("neural", "long-form", "generative")),
            PollyVoice("Gregory", "Gregory", "Male", "en-US", "US English", listOf("neural", "long-form")),
            PollyVoice("Amy", "Amy", "Female", "en-GB", "British English", listOf("neural", "standard", "generative")),
            PollyVoice("Brian", "Brian", "Male", "en-GB", "British English", listOf("neural", "standard")),
            PollyVoice("Arthur", "Arthur", "Male", "en-GB", "British English", listOf("neural")),
            PollyVoice("Olivia", "Olivia", "Female", "en-AU", "Australian English", listOf("neural")),
            PollyVoice("Kajal", "Kajal", "Female", "en-IN", "Indian English", listOf("neural")),
            PollyVoice("Lupe", "Lupe", "Female", "es-US", "US Spanish", listOf("neural", "standard")),
            PollyVoice("Lea", "Léa", "Female", "fr-FR", "French", listOf("neural", "standard"))
        )
    }
}
