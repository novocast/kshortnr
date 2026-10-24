package com.example.shortener

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class ShortLinkStoreTest {
    @TempDir
    lateinit var temporaryDirectory: Path

    @Test
    fun `stores links and counts redirects across store instances`() {
        val databaseFile = temporaryDirectory.resolve("links.sqlite")
        val store = ShortLinkStore(databaseFile)
        val created = store.create("https://example.com/a-long-path")

        assertEquals("https://example.com/a-long-path", store.incrementAndGetTarget(created.code))
        assertEquals(null, store.incrementAndGetTarget("missing1"))

        val persisted = ShortLinkStore(databaseFile).list().single()
        assertEquals(created.code, persisted.code)
        assertEquals(1, persisted.clicks)
    }
}