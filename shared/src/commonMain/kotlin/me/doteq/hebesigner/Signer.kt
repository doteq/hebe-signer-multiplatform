package me.doteq.hebesigner

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.asymmetric.RSA
import dev.whyoleg.cryptography.algorithms.digest.SHA256
import kotlinx.datetime.*
import kotlinx.datetime.format.DateTimeComponents
import net.thauvin.erik.urlencoder.UrlEncoderUtil
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@OptIn(ExperimentalEncodingApi::class)
private fun getDigest(body: String?): String? {
    if (body == null) return null
    return Base64.encode(CryptographyProvider.Default.get(SHA256).hasher().hashBlocking(body.encodeToByteArray()))
}

@OptIn(ExperimentalEncodingApi::class)
private fun getSignatureValue(values: String, privatePem: String): String {
    val crypto = CryptographyProvider.Default.get(RSA.PKCS1)
    val privateKey = crypto.privateKeyDecoder(SHA256).decodeFromBlocking(
        RSA.PrivateKey.Format.PEM,
        "-----BEGIN PRIVATE KEY-----\n$privatePem\n-----END PRIVATE KEY-----".encodeToByteArray()
    )
    return Base64.encode(privateKey.signatureGenerator().generateSignatureBlocking(values.encodeToByteArray()))
}

private fun getEncodedPath(path: String): String {
    val url = ("(api/mobile/.+)".toRegex().find(path))
        ?: throw IllegalArgumentException("The URL does not seem correct (does not match `(api/mobile/.+)` regex)")

    return UrlEncoderUtil.encode(url.groupValues[0], "UTF-8").lowercase()
}

private fun getHeaders(digest: String?, canonicalUrl: String, timestamp: Instant): MutableMap<String, String> {
    val headers = mutableMapOf<String, String>()
    headers["vCanonicalUrl"] = canonicalUrl
    if (digest != null) headers["Digest"] = digest
    headers["vDate"] = timestamp.format(DateTimeComponents.Formats.RFC_1123)
    return headers
}

fun getSignatureHeaders(
    keyId: String,
    privatePem: String,
    body: String?,
    requestPath: String,
    timestamp: LocalDateTime
): Map<String, String> {
    val canonicalUrl = getEncodedPath(requestPath)
    val digest = getDigest(body)
    val headers = getHeaders(digest, canonicalUrl, timestamp.toInstant(TimeZone.UTC))
    val headerNames = headers.keys.joinToString(" ")
    val headerValues = headers.values.joinToString("")
    val signatureValue = getSignatureValue(headerValues, privatePem)

    if (body != null) headers["Digest"] = "SHA-256=${digest}"
    headers["Signature"] = """keyId="$keyId,headers="$headerNames",algorithm="sha256",signature=Base64(sha256withrsa($signatureValue))"""

    return headers
}
