package com.jarvisblue.core.updates

import java.io.ByteArrayOutputStream
import java.io.IOException
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.Test

class UpdateCheckerTest {

    private val apk = "PK\u0003\u0004 pretend this is an apk".toByteArray()

    private fun clientThatAnswers(body: String, status: Int = 200): OkHttpClient =
        OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(status)
                .message("ok")
                .body(body.toResponseBody(null))
                .build()
        }.build()

    private fun manifestJson(
        packageId: String = "com.jarvisblue.mobile",
        versionCode: Int = 9,
        url: String = "https://example.com/app.apk",
        sha256: String = UpdateChecker.sha256(apk),
    ): String = """
        {"packageId":"$packageId","versionCode":$versionCode,"versionName":"1.$versionCode.0",
         "apkUrl":"$url","sha256":"$sha256","notes":"A fix"}
    """.trimIndent()

    private fun checker(
        client: OkHttpClient,
        currentVersionCode: Int = 3,
        packageId: String = "com.jarvisblue.mobile",
        bytes: ByteArray = apk,
    ) = UpdateChecker(
        packageId = packageId,
        currentVersionCode = currentVersionCode,
        httpClient = client,
        downloader = { bytes },
    )

    @Test
    fun `a newer version with a matching file is offered`() = runBlocking {
        val result = checker(clientThatAnswers(manifestJson())).check("https://example.com/update.json")
        assertTrue(result is UpdateResult.Available)
        val available = result as UpdateResult.Available
        assertEquals(9, available.manifest.versionCode)
        assertEquals(apk.size, available.bytes.size)
}

    @Test
    fun `a file that does not match the digest is refused`() = runBlocking {
        val wrong = UpdateChecker.sha256("something else".toByteArray())
        val result = checker(clientThatAnswers(manifestJson(sha256 = wrong)))
            .check("https://example.com/update.json")
        assertTrue(result is UpdateResult.DigestMismatch)
}

    @Test
    fun `the same version is not offered again`() = runBlocking {
        val result = checker(clientThatAnswers(manifestJson(versionCode = 3)))
            .check("https://example.com/update.json")
        assertTrue(result is UpdateResult.UpToDate)
}

    @Test
    fun `an older version is not offered`() = runBlocking {
        val result = checker(clientThatAnswers(manifestJson(versionCode = 1)))
            .check("https://example.com/update.json")
        assertTrue(result is UpdateResult.UpToDate)
}

    @Test
    fun `a feed for another app is refused`() = runBlocking {
        val result = checker(clientThatAnswers(manifestJson(packageId = "com.other.app")))
            .check("https://example.com/update.json")
        assertTrue(result is UpdateResult.Refused)
}

    @Test
    fun `a feed that is not over https is refused`() = runBlocking {
        val result = checker(clientThatAnswers(manifestJson())).check("http://example.com/update.json")
        assertTrue(result is UpdateResult.Refused)
}

    @Test
    fun `a download address that is not over https is refused`() = runBlocking {
        val result = checker(clientThatAnswers(manifestJson(url = "http://example.com/app.apk")))
            .check("https://example.com/update.json")
        assertTrue(result is UpdateResult.Refused)
}

    @Test
    fun `a feed that is broken is refused`() = runBlocking {
        val result = checker(clientThatAnswers("this is not json")).check("https://example.com/update.json")
        assertTrue(result is UpdateResult.Refused)
}

    @Test
    fun `a feed that cannot be reached is reported`() = runBlocking {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            throw IOException("the network is down")
        }.build()
        val result = checker(client).check("https://example.com/update.json")
        assertTrue(result is UpdateResult.Unreachable)
}

    @Test
    fun `an answer that is not a success is reported`() = runBlocking {
        val result = checker(clientThatAnswers("nope", status = 500)).check("https://example.com/update.json")
        assertTrue(result is UpdateResult.Unreachable)
}

    @Test
    fun `the digest is the sha-256 of the file`() = runBlocking {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            UpdateChecker.sha256("abc".toByteArray()),
        )
}
}
