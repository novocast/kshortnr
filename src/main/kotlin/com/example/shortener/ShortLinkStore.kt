package com.example.shortener

import kotlinx.serialization.Serializable
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.security.SecureRandom
import java.sql.DriverManager
import java.time.Instant

class ShortLinkStore(private val databaseFile: Path) {
    init {
        Files.createDirectories(databaseFile.toAbsolutePath().parent)
        connect().use { connection ->
            connection.createStatement().use { statement ->
                statement.executeUpdate(
                    """
                    CREATE TABLE IF NOT EXISTS links (
                        code TEXT PRIMARY KEY,
                        target_url TEXT NOT NULL,
                        created_at TEXT NOT NULL,
                        clicks INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent(),
                )
            }
        }
    }

    fun create(targetUrl: String): ShortLink {
        repeat(10) {
            val link = ShortLink(
                code = newCode(),
                targetUrl = targetUrl,
                createdAt = Instant.now().toString(),
                clicks = 0,
            )

            connect().use { connection ->
                connection.prepareStatement(
                    "INSERT OR IGNORE INTO links (code, target_url, created_at) VALUES (?, ?, ?)",
                ).use { statement ->
                    statement.setString(1, link.code)
                    statement.setString(2, link.targetUrl)
                    statement.setString(3, link.createdAt)
                    if (statement.executeUpdate() == 1) return link
                }
            }
        }
        error("Could not generate a unique short code")
    }

    fun list(): List<ShortLink> = connect().use { connection ->
        connection.prepareStatement(
            "SELECT code, target_url, created_at, clicks FROM links ORDER BY created_at DESC",
        ).use { statement ->
            statement.executeQuery().use { results ->
                buildList {
                    while (results.next()) {
                        add(
                            ShortLink(
                                code = results.getString("code"),
                                targetUrl = results.getString("target_url"),
                                createdAt = results.getString("created_at"),
                                clicks = results.getInt("clicks"),
                            ),
                        )
                    }
                }
            }
        }
    }

    fun incrementAndGetTarget(code: String): String? = connect().use { connection ->
        connection.prepareStatement("UPDATE links SET clicks = clicks + 1 WHERE code = ?").use { statement ->
            statement.setString(1, code)
            if (statement.executeUpdate() == 0) return@use null
        }

        connection.prepareStatement("SELECT target_url FROM links WHERE code = ?").use { statement ->
            statement.setString(1, code)
            statement.executeQuery().use { results ->
                if (results.next()) results.getString("target_url") else null
            }
        }
    }

    private fun connect() = DriverManager.getConnection("jdbc:sqlite:${databaseFile.toAbsolutePath()}")

    private fun newCode(): String = CharArray(CODE_LENGTH) {
        CODE_ALPHABET[random.nextInt(CODE_ALPHABET.length)]
    }.concatToString()

    companion object {
        private const val CODE_LENGTH = 7
        private const val CODE_ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
        private val random = SecureRandom()

        fun fromEnvironment(): ShortLinkStore {
            val databaseFile = Paths.get(System.getenv("SHORTENER_DB_FILE") ?: "data/shortener.sqlite")
            return ShortLinkStore(databaseFile)
        }
    }
}

@Serializable
data class ShortLink(
    val code: String,
    val targetUrl: String,
    val createdAt: String,
    val clicks: Int,
)