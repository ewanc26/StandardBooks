package uk.ewancroft.standardbooks.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class AtUriTest {

    @Test
    fun `parse valid at-uri`() {
        val uri = "at://did:plc:ofrbh253gwicbkc5nktqepol/site.standard.document/abc123"
        val parsed = AtUri.parse(uri)

        assertEquals("did:plc:ofrbh253gwicbkc5nktqepol", parsed.did)
        assertEquals("site.standard.document", parsed.collection)
        assertEquals("abc123", parsed.rkey)
    }

    @Test
    fun `parse returns correct uri string`() {
        val did = "did:plc:abc123"
        val collection = "site.standard.document"
        val rkey = "xyz789"
        val atUri = AtUri(did, collection, rkey)

        assertEquals("at://did:plc:abc123/site.standard.document/xyz789", atUri.uri)
    }

    @Test
    fun `parse throws on missing prefix`() {
        assertThrows(IllegalArgumentException::class.java) {
            AtUri.parse("https://example.com")
        }
    }

    @Test
    fun `parse throws on too few segments`() {
        assertThrows(IllegalArgumentException::class.java) {
            AtUri.parse("at://did:plc:abc")
        }
        assertThrows(IllegalArgumentException::class.java) {
            AtUri.parse("at://did:plc:abc/site.standard.document")
        }
    }

    @Test
    fun `tryParse returns null on invalid uri`() {
        assertNull(AtUri.tryParse("not-a-uri"))
        assertNull(AtUri.tryParse("at://incomplete"))
    }

    @Test
    fun `tryParse returns parsed on valid uri`() {
        val uri = "at://did:plc:test/site.standard.document/rkey1"
        val parsed = AtUri.tryParse(uri)
        assert(parsed != null)
        assertEquals("did:plc:test", parsed!!.did)
    }

    @Test
    fun `parse handles did web`() {
        val uri = "at://did:web:example.com/site.standard.document/doc1"
        val parsed = AtUri.parse(uri)

        assertEquals("did:web:example.com", parsed.did)
        assertEquals("doc1", parsed.rkey)
    }

    @Test
    fun `parse handles rkey with special characters`() {
        val uri = "at://did:plc:abc/site.standard.document/3js4abc-xyz"
        val parsed = AtUri.parse(uri)

        assertEquals("3js4abc-xyz", parsed.rkey)
    }
}
