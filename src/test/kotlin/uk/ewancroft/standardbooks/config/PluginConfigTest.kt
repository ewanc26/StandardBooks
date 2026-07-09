package uk.ewancroft.standardbooks.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PluginConfigTest {

    @Test
    fun `oauth callback url uses external url when set`() {
        val config = PluginConfig(
            enabled = true,
            oauth = PluginConfig.OAuthConfig(
                port = 8765,
                callbackPath = "/callback",
                externalUrl = "https://mc.example.com:8765"
            ),
            publication = PluginConfig.PublicationConfig(true, "Test", ""),
            books = PluginConfig.BookConfig(100, 1024, false),
            bstatsEnabled = true
        )

        assertEquals("https://mc.example.com:8765/callback", config.oauth.callbackUrl)
    }

    @Test
    fun `oauth callback url falls back to localhost`() {
        val config = PluginConfig(
            enabled = true,
            oauth = PluginConfig.OAuthConfig(
                port = 8765,
                callbackPath = "/callback",
                externalUrl = ""
            ),
            publication = PluginConfig.PublicationConfig(true, "Test", ""),
            books = PluginConfig.BookConfig(100, 1024, false),
            bstatsEnabled = true
        )

        assertEquals("http://localhost:8765/callback", config.oauth.callbackUrl)
    }

    @Test
    fun `oauth callback url trims trailing slash from external url`() {
        val config = PluginConfig(
            enabled = true,
            oauth = PluginConfig.OAuthConfig(
                port = 8765,
                callbackPath = "/callback",
                externalUrl = "https://mc.example.com:8765/"
            ),
            publication = PluginConfig.PublicationConfig(true, "Test", ""),
            books = PluginConfig.BookConfig(100, 1024, false),
            bstatsEnabled = true
        )

        assertEquals("https://mc.example.com:8765/callback", config.oauth.callbackUrl)
    }

    @Test
    fun `book config defaults are sensible`() {
        val config = PluginConfig(
            enabled = true,
            oauth = PluginConfig.OAuthConfig(8765, "/callback", ""),
            publication = PluginConfig.PublicationConfig(true, "Test", ""),
            books = PluginConfig.BookConfig(100, 1024, false),
            bstatsEnabled = true
        )

        assertEquals(100, config.books.maxPages)
        assertEquals(1024, config.books.maxCharsPerPage)
    }

    @Test
    fun `book config extends beyond vanilla limits`() {
        val config = PluginConfig(
            enabled = true,
            oauth = PluginConfig.OAuthConfig(8765, "/callback", ""),
            publication = PluginConfig.PublicationConfig(true, "Test", ""),
            books = PluginConfig.BookConfig(200, 2048, false),
            bstatsEnabled = true
        )

        assertTrue(config.books.maxPages > 50) // vanilla is 50
        assertTrue(config.books.maxCharsPerPage > 256) // vanilla is 256
    }
}
