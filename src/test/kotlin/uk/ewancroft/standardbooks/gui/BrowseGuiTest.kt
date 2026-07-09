package uk.ewancroft.standardbooks.gui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import uk.ewancroft.standardbooks.atproto.AtProtoClient
import uk.ewancroft.standardbooks.atproto.model.StandardDocument

class BrowseGuiTest {

    private fun makeEntry(uri: String, title: String): AtProtoClient.DocumentEntry {
        return AtProtoClient.DocumentEntry(
            uri = uri,
            cid = "cid_$uri",
            document = StandardDocument(
                site = "https://example.com",
                title = title,
                publishedAt = "2026-07-04T12:00:00Z"
            )
        )
    }

    @Test
    fun `paginate returns first page`() {
        val entries = (1..10).map { makeEntry("at://did:plc:test/site.standard.document/$it", "Book $it") }
        val page = BrowseGui.paginate(entries, 0, pageSize = 5)

        assertEquals(0, page.page)
        assertEquals(2, page.totalPages)
        assertEquals(5, page.entries.size)
        assertEquals("Book 1", page.entries[0].document.title)
        assertEquals("Book 5", page.entries[4].document.title)
    }

    @Test
    fun `paginate returns second page`() {
        val entries = (1..10).map { makeEntry("at://did:plc:test/site.standard.document/$it", "Book $it") }
        val page = BrowseGui.paginate(entries, 1, pageSize = 5)

        assertEquals(1, page.page)
        assertEquals(2, page.totalPages)
        assertEquals(5, page.entries.size)
        assertEquals("Book 6", page.entries[0].document.title)
        assertEquals("Book 10", page.entries[4].document.title)
    }

    @Test
    fun `paginate handles empty list`() {
        val page = BrowseGui.paginate(emptyList(), 0)

        assertEquals(0, page.page)
        assertEquals(1, page.totalPages)
        assertTrue(page.entries.isEmpty())
    }

    @Test
    fun `paginate clamps page to valid range`() {
        val entries = (1..3).map { makeEntry("at://did:plc:test/site.standard.document/$it", "Book $it") }
        val page = BrowseGui.paginate(entries, 100, pageSize = 5)

        assertEquals(0, page.page)
        assertEquals(1, page.totalPages)
        assertEquals(3, page.entries.size)
    }

    @Test
    fun `paginate handles negative page`() {
        val entries = (1..3).map { makeEntry("at://did:plc:test/site.standard.document/$it", "Book $it") }
        val page = BrowseGui.paginate(entries, -1, pageSize = 5)

        assertEquals(0, page.page)
    }

    @Test
    fun `paginate with exactly one page`() {
        val entries = (1..5).map { makeEntry("at://did:plc:test/site.standard.document/$it", "Book $it") }
        val page = BrowseGui.paginate(entries, 0, pageSize = 5)

        assertEquals(1, page.totalPages)
        assertEquals(5, page.entries.size)
    }

    @Test
    fun `paginate with one more than page size`() {
        val entries = (1..6).map { makeEntry("at://did:plc:test/site.standard.document/$it", "Book $it") }
        val page = BrowseGui.paginate(entries, 0, pageSize = 5)

        assertEquals(2, page.totalPages)
        assertEquals(5, page.entries.size)
    }
}
