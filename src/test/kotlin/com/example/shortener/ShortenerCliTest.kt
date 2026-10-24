package com.example.shortener

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.io.StringReader
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

    @Test
    fun `show returns link details as JSON`() {
        val store = ShortLinkStore(temporaryDirectory.resolve("links.sqlite"))
        val link = store.create("https://example.com/a-long-page")
        val output = ByteArrayOutputStream()

        val result = runCli(arrayOf("show", link.code, "--json"), store, PrintStream(output), "http://short.test")
        val shown = Json.decodeFromString<CliLink>(output.toString())

        assertEquals(0, result)
        assertEquals(link.code, shown.code)
        assertEquals("http://short.test/${link.code}", shown.shortUrl)
        assertEquals(link.targetUrl, shown.targetUrl)
        assertEquals(0, shown.clicks)
    }

    @Test
    fun `delete requires confirmation and removes the link when confirmed`() {
        val store = ShortLinkStore(temporaryDirectory.resolve("links.sqlite"))
        val link = store.create("https://example.com/a-long-page")
        val output = ByteArrayOutputStream()
        val prompt = ByteArrayOutputStream()

        val result = runCli(
            arrayOf("delete", link.code),
            store,
            PrintStream(output),
            "http://short.test",
            BufferedReader(StringReader("y\n")),
            PrintStream(prompt),
        )

        assertEquals(0, result)
        assertTrue(prompt.toString().contains("Delete ${link.code}"))
        assertTrue(store.list().isEmpty())
        assertTrue(output.toString().contains("Deleted ${link.code}"))
    }

    @Test
    fun `delete can be declined without removing the link`() {
        val store = ShortLinkStore(temporaryDirectory.resolve("links.sqlite"))
        val link = store.create("https://example.com/a-long-page")
        val output = ByteArrayOutputStream()

        val result = runCli(
            arrayOf("delete", link.code),
            store,
            PrintStream(output),
            "http://short.test",
            BufferedReader(StringReader("n\n")),
            PrintStream(ByteArrayOutputStream()),
        )

        assertEquals(1, result)
        assertEquals(link.code, store.list().single().code)
        assertTrue(output.toString().contains("Deletion cancelled"))
    }

    @Test
    fun `delete with yes flag returns JSON result`() {
        val store = ShortLinkStore(temporaryDirectory.resolve("links.sqlite"))
        val link = store.create("https://example.com/a-long-page")
        val output = ByteArrayOutputStream()

        val result = runCli(
            arrayOf("delete", link.code, "--yes", "--json"),
            store,
            PrintStream(output),
            "http://short.test",
        )
        val deleted = Json.decodeFromString<CliDeleteResult>(output.toString())

        assertEquals(0, result)
        assertTrue(deleted.deleted)
        assertEquals(link.code, deleted.code)
        assertTrue(store.list().isEmpty())
    }

    @Test
    fun `stats reports link and click totals with most-clicked links`() {
        val store = ShortLinkStore(temporaryDirectory.resolve("links.sqlite"))
        val popular = store.create("https://example.com/popular")
        val other = store.create("https://example.com/other")
        repeat(2) { store.incrementAndGetTarget(popular.code) }
        store.incrementAndGetTarget(other.code)
        val output = ByteArrayOutputStream()

        val result = runCli(arrayOf("stats", "--json"), store, PrintStream(output), "http://short.test")
        val stats = Json.decodeFromString<CliStats>(output.toString())

        assertEquals(0, result)
        assertEquals(2, stats.linkCount)
        assertEquals(3, stats.totalClicks)
        assertEquals(popular.code, stats.topLinks.first().code)
    }

    @Test
    fun `easter egg and forecast commands print their one-line outputs`() {
        val store = ShortLinkStore(temporaryDirectory.resolve("links.sqlite"))
        val eggOutput = ByteArrayOutputStream()
        val forecastOutput = ByteArrayOutputStream()

        assertEquals(0, runCli(arrayOf("easter-egg"), store, PrintStream(eggOutput), "http://short.test"))
        assertEquals(0, runCli(arrayOf("forecast"), store, PrintStream(forecastOutput), "http://short.test"))
        assertTrue(eggOutput.toString().contains("Long URLs have been shortened"))
        assertEquals("https://youtu.be/dQw4w9WgXcQ", forecastOutput.toString().trim())
    }

    @Test
    fun `forecast does not require opening the database`() {
        val output = ByteArrayOutputStream()

        val result = runCli(
            arrayOf("forecast"),
            { error("Forecast should not access the store") },
            PrintStream(output),
            "http://short.test",
            BufferedReader(StringReader("")),
            PrintStream(ByteArrayOutputStream()),
        )

        assertEquals(0, result)
        assertEquals("https://youtu.be/dQw4w9WgXcQ", output.toString().trim())
    }
}