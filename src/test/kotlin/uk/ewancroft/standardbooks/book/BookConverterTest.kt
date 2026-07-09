package uk.ewancroft.standardbooks.book

import net.kyori.adventure.text.Component
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

import uk.ewancroft.standardbooks.config.PluginConfig
import uk.ewancroft.standardbooks.atproto.model.StandardDocument

class BookConverterTest {

    private fun makeConfig(maxPages: Int = 100, maxCharsPerPage: Int = 1024, autoPublishOnSign: Boolean = false): PluginConfig {
        return PluginConfig(
            enabled = true,
            oauth = PluginConfig.OAuthConfig(8765, "/callback", ""),
            publication = PluginConfig.PublicationConfig(true, "{player}'s Books", ""),
            books = PluginConfig.BookConfig(maxPages, maxCharsPerPage, autoPublishOnSign),
            bstatsEnabled = true
        )
    }

    private fun makeBookMeta(pages: List<String>, title: String = "Test Book"): org.bukkit.inventory.meta.BookMeta {
        val meta = java.lang.reflect.Proxy.newProxyInstance(
            org.bukkit.inventory.meta.BookMeta::class.java.classLoader,
            arrayOf(org.bukkit.inventory.meta.BookMeta::class.java)
        ) { _, method, _ ->
            when (method.name) {
                "title" -> Component.text(title)
                "pages" -> pages.map { Component.text(it) }
                else -> null
            }
        } as org.bukkit.inventory.meta.BookMeta
        return meta
    }

    @Test
    fun `toDocument creates document with correct title`() {
        val converter = BookConverter(makeConfig())
        val meta = makeBookMeta(listOf("Page 1", "Page 2"))

        val doc = converter.toDocument(meta, "did:plc:abc", "ewan", "https://example.com")

        assertEquals("Test Book", doc.title)
    }

    @Test
    fun `toDocument creates content with pages`() {
        val converter = BookConverter(makeConfig())
        val meta = makeBookMeta(listOf("Page 1", "Page 2", "Page 3"))

        val doc = converter.toDocument(meta, "did:plc:abc", "ewan", "https://example.com")

        assertNotNull(doc.content)
        assertEquals(3, doc.content?.pages?.size)
        assertEquals("Page 1", doc.content?.pages?.get(0))
        assertEquals("Page 2", doc.content?.pages?.get(1))
        assertEquals("Page 3", doc.content?.pages?.get(2))
    }

    @Test
    fun `toDocument creates textContent with delimiter`() {
        val converter = BookConverter(makeConfig())
        val meta = makeBookMeta(listOf("Page 1", "Page 2"))

        val doc = converter.toDocument(meta, "did:plc:abc", "ewan", "https://example.com")

        assertNotNull(doc.textContent)
        assertTrue(doc.textContent!!.contains("---"))
        assertTrue(doc.textContent.contains("Page 1"))
        assertTrue(doc.textContent.contains("Page 2"))
    }

    @Test
    fun `toDocument adds contributor with author role`() {
        val converter = BookConverter(makeConfig())
        val meta = makeBookMeta(listOf("Page 1"))

        val doc = converter.toDocument(meta, "did:plc:abc", "ewan", "https://example.com")

        assertNotNull(doc.contributors)
        assertEquals(1, doc.contributors?.size)
        assertEquals("did:plc:abc", doc.contributors?.first()?.did)
        assertEquals("author", doc.contributors?.first()?.role)
        assertEquals("ewan", doc.contributors?.first()?.displayName)
    }

    @Test
    fun `toDocument adds minecraft tags`() {
        val converter = BookConverter(makeConfig())
        val meta = makeBookMeta(listOf("Page 1"))

        val doc = converter.toDocument(meta, "did:plc:abc", "ewan", "https://example.com")

        assertNotNull(doc.tags)
        assertTrue(doc.tags!!.contains("minecraft"))
        assertTrue(doc.tags.contains("standardbooks"))
    }

    @Test
    fun `toDocument sets publishedAt to ISO format`() {
        val converter = BookConverter(makeConfig())
        val meta = makeBookMeta(listOf("Page 1"))

        val doc = converter.toDocument(meta, "did:plc:abc", "ewan", "https://example.com")

        assertNotNull(doc.publishedAt)
        assertTrue(doc.publishedAt.contains("T"))
        assertTrue(doc.publishedAt.contains("Z"))
    }

    @Test
    fun `validateBook returns null for valid book`() {
        val converter = BookConverter(makeConfig())
        val meta = makeBookMeta(listOf("Page 1", "Page 2"))

        val error = converter.validateBook(meta)
        assertNull(error)
    }

    @Test
    fun `validateBook returns error for empty book`() {
        val converter = BookConverter(makeConfig())
        val meta = makeBookMeta(emptyList())

        val error = converter.validateBook(meta)
        assertNotNull(error)
        assertTrue(error!!.contains("empty"))
    }

    @Test
    fun `validateBook returns error for too many pages`() {
        val converter = BookConverter(makeConfig(maxPages = 5))
        val meta = makeBookMeta((1..10).map { "Page $it" })

        val error = converter.validateBook(meta)
        assertNotNull(error)
        assertTrue(error!!.contains("pages"))
    }

    @Test
    fun `toPages uses structured content when available`() {
        val converter = BookConverter(makeConfig())
        val content = uk.ewancroft.standardbooks.atproto.model.StandardBooksContent(
            pages = listOf("Structured Page 1", "Structured Page 2")
        )
        val doc = StandardDocument(
            site = "https://example.com",
            title = "Test",
            publishedAt = "2026-07-04T12:00:00Z",
            content = content,
            textContent = "Fallback text"
        )

        val pages = converter.toPages(doc)
        assertEquals(2, pages.size)
        assertEquals("Structured Page 1", pages[0])
        assertEquals("Structured Page 2", pages[1])
    }

    @Test
    fun `toPages falls back to textContent with delimiter`() {
        val converter = BookConverter(makeConfig())
        val doc = StandardDocument(
            site = "https://example.com",
            title = "Test",
            publishedAt = "2026-07-04T12:00:00Z",
            textContent = "Page A\n\n---\n\nPage B"
        )

        val pages = converter.toPages(doc)
        assertEquals(2, pages.size)
        assertEquals("Page A", pages[0])
        assertEquals("Page B", pages[1])
    }

    @Test
    fun `toPages falls back to chunking when no delimiter`() {
        val converter = BookConverter(makeConfig(maxCharsPerPage = 10))
        val doc = StandardDocument(
            site = "https://example.com",
            title = "Test",
            publishedAt = "2026-07-04T12:00:00Z",
            textContent = "This is a long text that exceeds the page limit"
        )

        val pages = converter.toPages(doc)
        assertTrue(pages.size > 1)
    }

    @Test
    fun `toPages returns title when no content or text`() {
        val converter = BookConverter(makeConfig())
        val doc = StandardDocument(
            site = "https://example.com",
            title = "Just Title",
            publishedAt = "2026-07-04T12:00:00Z"
        )

        val pages = converter.toPages(doc)
        assertEquals(1, pages.size)
        assertEquals("Just Title", pages[0])
    }

    @Test
    fun `toDocument filters blank pages`() {
        val converter = BookConverter(makeConfig())
        val meta = makeBookMeta(listOf("Page 1", "", "  ", "Page 4"))

        val doc = converter.toDocument(meta, "did:plc:abc", "ewan", "https://example.com")

        assertEquals(2, doc.content?.pages?.size)
        assertEquals("Page 1", doc.content?.pages?.get(0))
        assertEquals("Page 4", doc.content?.pages?.get(1))
    }
}
