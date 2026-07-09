package uk.ewancroft.standardbooks.auth

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class SessionStoreTest {

    @TempDir
    lateinit var tempDir: File

    private lateinit var store: SessionStore

    @BeforeEach
    fun setUp() {
        store = SessionStore(tempDir)
    }

    @Test
    fun `get returns null for non-existent session`() {
        assertNull(store.get("uuid-123"))
    }

    @Test
    fun `put and get roundtrip`() {
        val session = SessionStore.Session(
            did = "did:plc:abc123",
            handle = "ewan.bsky.social",
            pdsUrl = "https://pds.example.com",
            accessToken = "access-token-123",
            refreshToken = "refresh-token-456",
            dpopKey = ByteArray(32) { it.toByte() },
            createdAt = System.currentTimeMillis()
        )

        store.put("uuid-1", session)
        val retrieved = store.get("uuid-1")

        assertNotNull(retrieved)
        assertEquals("did:plc:abc123", retrieved!!.did)
        assertEquals("ewan.bsky.social", retrieved.handle)
        assertEquals("https://pds.example.com", retrieved.pdsUrl)
        assertEquals("access-token-123", retrieved.accessToken)
        assertEquals("refresh-token-456", retrieved.refreshToken)
    }

    @Test
    fun `has returns true for existing session`() {
        val session = SessionStore.Session(
            did = "did:plc:abc",
            handle = "test",
            pdsUrl = "https://pds.example.com",
            accessToken = "token",
            refreshToken = null,
            dpopKey = ByteArray(32),
            createdAt = 0
        )

        store.put("uuid-2", session)
        assertTrue(store.has("uuid-2"))
    }

    @Test
    fun `has returns false for non-existent session`() {
        assertFalse(store.has("uuid-999"))
    }

    @Test
    fun `remove deletes session`() {
        val session = SessionStore.Session(
            did = "did:plc:abc",
            handle = "test",
            pdsUrl = "https://pds.example.com",
            accessToken = "token",
            refreshToken = null,
            dpopKey = ByteArray(32),
            createdAt = 0
        )

        store.put("uuid-3", session)
        assertTrue(store.has("uuid-3"))

        store.remove("uuid-3")
        assertFalse(store.has("uuid-3"))
        assertNull(store.get("uuid-3"))
    }

    @Test
    fun `sessions persist across instances`() {
        val session = SessionStore.Session(
            did = "did:plc:persist",
            handle = "persistent",
            pdsUrl = "https://pds.example.com",
            accessToken = "persist-token",
            refreshToken = "persist-refresh",
            dpopKey = ByteArray(32) { 42 },
            createdAt = 1234567890
        )

        store.put("uuid-persist", session)

        // Create a new store pointing at the same directory
        val store2 = SessionStore(tempDir)
        store2.load()
        val retrieved = store2.get("uuid-persist")

        assertNotNull(retrieved)
        assertEquals("did:plc:persist", retrieved!!.did)
        assertEquals("persistent", retrieved.handle)
        assertEquals("persist-token", retrieved.accessToken)
    }

    @Test
    fun `session file is encrypted`() {
        val session = SessionStore.Session(
            did = "did:plc:secret",
            handle = "secret",
            pdsUrl = "https://pds.example.com",
            accessToken = "secret-token",
            refreshToken = null,
            dpopKey = ByteArray(32),
            createdAt = 0
        )

        store.put("uuid-secret", session)

        val sessionsFile = File(tempDir, "sessions.json")
        assertTrue(sessionsFile.exists())

        // The file should not contain the plaintext token
        val content = sessionsFile.readBytes()
        val asString = String(content, Charsets.ISO_8859_1)
        assertFalse(asString.contains("secret-token"))
        assertFalse(asString.contains("did:plc:secret"))
    }

    @Test
    fun `multiple sessions coexist`() {
        val session1 = SessionStore.Session(
            did = "did:plc:1", handle = "one", pdsUrl = "https://pds1.example.com",
            accessToken = "token1", refreshToken = null, dpopKey = ByteArray(32), createdAt = 0
        )
        val session2 = SessionStore.Session(
            did = "did:plc:2", handle = "two", pdsUrl = "https://pds2.example.com",
            accessToken = "token2", refreshToken = null, dpopKey = ByteArray(32), createdAt = 0
        )

        store.put("uuid-1", session1)
        store.put("uuid-2", session2)

        assertEquals("did:plc:1", store.get("uuid-1")?.did)
        assertEquals("did:plc:2", store.get("uuid-2")?.did)

        store.remove("uuid-1")
        assertNull(store.get("uuid-1"))
        assertNotNull(store.get("uuid-2"))
    }
}
