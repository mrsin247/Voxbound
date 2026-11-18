package com.example.data.network

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object AwsSigV4Signer {

    private const val ALGORITHM = "AWS4-HMAC-SHA256"
    private const val SERVICE = "polly"

    fun buildSignedRequest(
        method: String,
        region: String,
        path: String,
        queryParams: Map<String, String> = emptyMap(),
        jsonBody: String? = null,
        accessKeyId: String,
        secretAccessKey: String,
        sessionToken: String = ""
    ): Request {
        val cleanRegion = region.trim()
        val host = "polly.$cleanRegion.amazonaws.com"
        val now = Date()
        val amzDate = formatAmzDate(now)
        val dateStamp = amzDate.substring(0, 8)

        val payloadBytes = jsonBody?.toByteArray(Charsets.UTF_8) ?: ByteArray(0)
        val payloadHash = sha256Hex(payloadBytes)

        val headers = sortedMapOf<String, String>(
            "host" to host,
            "x-amz-content-sha256" to payloadHash,
            "x-amz-date" to amzDate
        )
        if (jsonBody != null) {
            headers["content-type"] = "application/json; charset=utf-8"
        }
        if (sessionToken.isNotBlank()) {
            headers["x-amz-security-token"] = sessionToken.trim()
        }

        val canonicalQuery = queryParams.entries
            .sortedBy { it.key }
            .joinToString("&") { (k, v) -> "${awsEncode(k)}=${awsEncode(v)}" }

        val canonicalHeaders = headers.entries.joinToString("\n") { (k, v) -> "$k:${v.trim()}" } + "\n"
        val signedHeaders = headers.keys.joinToString(";")

        val canonicalRequest = buildString {
            append(method.uppercase(Locale.US)).append('\n')
            append(path).append('\n')
            append(canonicalQuery).append('\n')
            append(canonicalHeaders).append('\n')
            append(signedHeaders).append('\n')
            append(payloadHash)
        }

        val credentialScope = "$dateStamp/$cleanRegion/$SERVICE/aws4_request"
        val stringToSign = buildString {
            append(ALGORITHM).append('\n')
            append(amzDate).append('\n')
            append(credentialScope).append('\n')
            append(sha256Hex(canonicalRequest.toByteArray(Charsets.UTF_8)))
        }

        val signingKey = getSignatureKey(secretAccessKey.trim(), dateStamp, cleanRegion, SERVICE)
        val signature = hmacSha256(signingKey, stringToSign).toHex()

        val authorizationHeader = "$ALGORITHM Credential=${accessKeyId.trim()}/$credentialScope, " +
            "SignedHeaders=$signedHeaders, Signature=$signature"

        val url = buildString {
            append("https://").append(host).append(path)
            if (canonicalQuery.isNotEmpty()) {
                append('?').append(canonicalQuery)
            }
        }

        val requestBuilder = Request.Builder()
            .url(url)
            .addHeader("Host", host)
            .addHeader("X-Amz-Date", amzDate)
            .addHeader("X-Amz-Content-Sha256", payloadHash)
            .addHeader("Authorization", authorizationHeader)

        if (sessionToken.isNotBlank()) {
            requestBuilder.addHeader("X-Amz-Security-Token", sessionToken.trim())
        }

        when (method.uppercase(Locale.US)) {
            "POST" -> {
                val body = (jsonBody ?: "").toRequestBody("application/json; charset=utf-8".toMediaType())
                requestBuilder.post(body)
            }
            else -> requestBuilder.get()
        }

        return requestBuilder.build()
    }

    private fun getSignatureKey(
        key: String,
        dateStamp: String,
        regionName: String,
        serviceName: String
    ): ByteArray {
        val kSecret = ("AWS4$key").toByteArray(Charsets.UTF_8)
        val kDate = hmacSha256(kSecret, dateStamp)
        val kRegion = hmacSha256(kDate, regionName)
        val kService = hmacSha256(kRegion, serviceName)
        return hmacSha256(kService, "aws4_request")
    }

    private fun hmacSha256(key: ByteArray, data: String): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data.toByteArray(Charsets.UTF_8))
    }

    private fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.toHex()
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private fun awsEncode(value: String): String =
        URLEncoder.encode(value, "UTF-8")
            .replace("+", "%20")
            .replace("*", "%2A")
            .replace("%7E", "~")

    private fun formatAmzDate(date: Date): String {
        val sdf = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(date)
    }
}
