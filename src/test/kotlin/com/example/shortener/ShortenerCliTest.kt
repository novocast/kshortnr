package com.example.shortener

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Path

class ShortenerCliTest {
    @TempDir
    lateinit var temporaryDirectory: Path

    @Test
    fun `create persists a link and prints its short URL`() {
        val store = ShortLinkStore(temporaryDirectory.resolve("links.sqlite"))
        val output = ByteArrayOutputStream()

        val result = runCli(
            arrayOf("create", "https://example.com/a-long-page"),
            store,
            PrintStream(output),
            "http://short.test",
        )

        assertEquals(0, result)
        assertTrue(output.toString().contains("Created: http://short.test/"))
        assertEquals("https://example.com/a-long-page", store.list().single().targetUrl)
    }

    @Test
    fun `list prints short URLs and click counts`() {
        val store = ShortLinkStore(temporaryDirectory.resolve("links.sqlite"))
        val link = store.create("https://example.com/a-long-page")
        store.incrementAndGetTarget(link.code)
        val output = ByteArrayOutputStream()

        val result = runCli(arrayOf("list"), store, PrintStream(output), "http://short.test")

        assertEquals(0, result)
        assertTrue(output.toString().contains("http://short.test/${link.code}\t1\thttps://example.com/a-long-page"))
    }

    @Test
    fun `create rejects invalid URLs without storing a link`() {
        val store = ShortLinkStore(temporaryDirectory.resolve("links.sqlite"))
        val output = ByteArrayOutputStream()

        val result = runCli(arrayOf("create", "ftp://example.com/file"), store, PrintStream(output), "http://short.test")

        assertEquals(2, result)
        assertTrue(output.toString().contains("valid http or https URL"))
        assertTrue(store.list().isEmpty())
    }
}