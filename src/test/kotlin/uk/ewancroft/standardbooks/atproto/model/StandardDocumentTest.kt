package uk.ewancroft.standardbooks.atproto.model

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StandardDocumentTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test
    fun `document serializes with required fields`() {
        val doc = StandardDocument(
            site = "https://example.com",
            title = "Test Book",
            publishedAt = "2026-07-04T12:00:00Z"
        )

        val encoded = json.encodeToString(StandardDocument.serializer(), doc)
        assertTrue(encoded.contains("\"site\":\"https://example.com\""))
        assertTrue(encoded.contains("\"title\":\"Test Book\""))
        assertTrue(encoded.contains("\"publishedAt\":\"2026-07-04T12:00:00Z\""))
    }

    @Test
    fun `document serializes with content`() {
        val content = StandardBooksContent(
            pages = listOf("Page 1", "Page 2", "Page 3"),
            version = 1
        )

        val doc = StandardDocument(
            site = "https://example.com",
            title = "Test Book",
            publishedAt = "2026-07-04T12:00:00Z",
            content = content,
            textContent = "Page 1\n\n---\n\nPage 2\n\n---\n\nPage 3"
        )

        val encoded = json.encodeToString(StandardDocument.serializer(), doc)
        assertTrue(encoded.contains("site.standard.content.standardbooks"))
        assertTrue(encoded.contains("\"pages\""))
        assertTrue(encoded.contains("Page 1"))
        assertTrue(encoded.contains("Page 2"))
        assertTrue(encoded.contains("Page 3"))
    }

    @Test
    fun `document serializes with contributors`() {
        val contributor = StandardDocument.Contributor(
            did = "did:plc:abc123",
            role = "author",
            displayName = "ewan"
        )

        val doc = StandardDocument(
            site = "https://example.com",
            title = "Test Book",
            publishedAt = "2026-07-04T12:00:00Z",
            contributors = listOf(contributor)
        )

        val encoded = json.encodeToString(StandardDocument.serializer(), doc)
        assertTrue(encoded.contains("\"did\":\"did:plc:abc123\""))
        assertTrue(encoded.contains("\"role\":\"author\""))
        assertTrue(encoded.contains("\"displayName\":\"ewan\""))
    }

    @Test
    fun `document deserializes from json`() {
        val jsonString = """
            {
                "site": "https://example.com",
                "title": "My Book",
                "publishedAt": "2026-07-04T12:00:00Z",
                "textContent": "Hello world",
                "tags": ["minecraft", "standardbooks"],
                "contributors": [
                    {"did": "did:plc:abc", "role": "author", "displayName": "ewan"}
                ]
            }
        """.trimIndent()

        val doc = json.decodeFromString(StandardDocument.serializer(), jsonString)

        assertEquals("https://example.com", doc.site)
        assertEquals("My Book", doc.title)
        assertEquals("2026-07-04T12:00:00Z", doc.publishedAt)
        assertEquals("Hello world", doc.textContent)
        assertEquals(2, doc.tags?.size)
        assertEquals("did:plc:abc", doc.contributors?.first()?.did)
        assertEquals("author", doc.contributors?.first()?.role)
    }

    @Test
    fun `document with content deserializes`() {
        val jsonString = """
            {
                "site": "https://example.com",
                "title": "Book With Content",
                "publishedAt": "2026-07-04T12:00:00Z",
                "content": {
                    "${'$'}type": "site.standard.content.standardbooks",
                    "pages": ["Page 1 text", "Page 2 text"],
                    "version": 1
                }
            }
        """.trimIndent()

        val doc = json.decodeFromString(StandardDocument.serializer(), jsonString)

        assertNotNull(doc.content)
        assertEquals(2, doc.content?.pages?.size)
        assertEquals("Page 1 text", doc.content?.pages?.get(0))
        assertEquals("Page 2 text", doc.content?.pages?.get(1))
    }

    @Test
    fun `document without optional fields deserializes`() {
        val jsonString = """
            {
                "site": "https://example.com",
                "title": "Minimal",
                "publishedAt": "2026-07-04T12:00:00Z"
            }
        """.trimIndent()

        val doc = json.decodeFromString(StandardDocument.serializer(), jsonString)

        assertNull(doc.content)
        assertNull(doc.textContent)
        assertNull(doc.tags)
        assertNull(doc.contributors)
        assertNull(doc.path)
        assertNull(doc.description)
    }

    @Test
    fun `publication serializes with required fields`() {
        val pub = StandardPublication(
            name = "My Bookshelf",
            url = "https://example.com"
        )

        val encoded = json.encodeToString(StandardPublication.serializer(), pub)
        assertTrue(encoded.contains("\"name\":\"My Bookshelf\""))
        assertTrue(encoded.contains("\"url\":\"https://example.com\""))
    }

    @Test
    fun `publication with preferences serializes`() {
        val pub = StandardPublication(
            name = "My Bookshelf",
            url = "https://example.com",
            preferences = StandardPublication.Preferences(showInDiscover = true)
        )

        val encoded = json.encodeToString(StandardPublication.serializer(), pub)
        assertTrue(encoded.contains("showInDiscover"))
        assertTrue(encoded.contains("true"))
    }

    @Test
    fun `content type is standardbooks`() {
        val content = StandardBooksContent(pages = listOf("test"))
        assertEquals("site.standard.content.standardbooks", content.type)
    }
}
