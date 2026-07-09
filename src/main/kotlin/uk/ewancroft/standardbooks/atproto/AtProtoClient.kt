package uk.ewancroft.standardbooks.atproto

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.delete
import io.ktor.client.request.setBody
import io.ktor.client.request.headers
import io.ktor.client.request.bearerAuth
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import uk.ewancroft.standardbooks.atproto.model.StandardDocument
import uk.ewancroft.standardbooks.atproto.model.StandardPublication
import uk.ewancroft.standardbooks.auth.SessionStore

/**
 * Handles AT Protocol XRPC calls for creating, reading, and listing
 * site.standard.document and site.standard.publication records.
 *
 * Uses direct XRPC calls (like Inkwell) rather than the atproto-kotlin SDK's
 * generated service classes, for finer control over the request/response.
 */
class AtProtoClient {
    private val json = Json { ignoreUnknownKeys = true }
    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(this@AtProtoClient.json)
        }
    }

    private data class CachedDocument(val entry: DocumentEntry?, val timestamp: Long)
    private val documentCache = java.util.concurrent.ConcurrentHashMap<String, CachedDocument>()
    private val CACHE_TTL_MS = 5 * 60 * 1000L // 5 minutes

    data class CreateRecordResult(
        val uri: String,
        val cid: String
    )

    data class DocumentEntry(
        val uri: String,
        val cid: String?,
        val document: StandardDocument
    )

    data class PublicationEntry(
        val uri: String,
        val cid: String?,
        val publication: StandardPublication
    )

    /**
     * Creates a site.standard.document record.
     */
    suspend fun createDocument(
        session: SessionStore.Session,
        document: StandardDocument
    ): CreateRecordResult {
        val record = buildJsonObject {
            put("\$type", JsonPrimitive("site.standard.document"))
            put("site", JsonPrimitive(document.site))
            put("title", JsonPrimitive(document.title))
            put("publishedAt", JsonPrimitive(document.publishedAt))
            document.path?.let { put("path", JsonPrimitive(it)) }
            document.description?.let { put("description", JsonPrimitive(it)) }
            document.textContent?.let { put("textContent", JsonPrimitive(it)) }
            document.tags?.let { tags ->
                put("tags", kotlinx.serialization.json.JsonArray(tags.map { JsonPrimitive(it) }))
            }
            document.contributors?.let { contributors ->
                put("contributors", kotlinx.serialization.json.JsonArray(contributors.map { c ->
                    buildJsonObject {
                        put("did", JsonPrimitive(c.did))
                        c.role?.let { put("role", JsonPrimitive(it)) }
                        c.displayName?.let { put("displayName", JsonPrimitive(it)) }
                    }
                }))
            }
            document.content?.let { content ->
                put("content", buildJsonObject {
                    put("\$type", JsonPrimitive(content.type))
                    put("pages", kotlinx.serialization.json.JsonArray(content.pages.map { JsonPrimitive(it) }))
                    put("version", JsonPrimitive(content.version))
                })
            }
            document.updatedAt?.let { put("updatedAt", JsonPrimitive(it)) }
        }

        return createRecord(session, "site.standard.document", record).also {
            documentCache.remove(it.uri)
        }
    }

    /**
     * Updates an existing site.standard.document record.
     */
    suspend fun updateDocument(
        session: SessionStore.Session,
        rkey: String,
        document: StandardDocument
    ): CreateRecordResult {
        val record = buildJsonObject {
            put("\$type", JsonPrimitive("site.standard.document"))
            put("site", JsonPrimitive(document.site))
            put("title", JsonPrimitive(document.title))
            put("publishedAt", JsonPrimitive(document.publishedAt))
            document.path?.let { put("path", JsonPrimitive(it)) }
            document.description?.let { put("description", JsonPrimitive(it)) }
            document.textContent?.let { put("textContent", JsonPrimitive(it)) }
            document.tags?.let { tags ->
                put("tags", kotlinx.serialization.json.JsonArray(tags.map { JsonPrimitive(it) }))
            }
            document.contributors?.let { contributors ->
                put("contributors", kotlinx.serialization.json.JsonArray(contributors.map { c ->
                    buildJsonObject {
                        put("did", JsonPrimitive(c.did))
                        c.role?.let { put("role", JsonPrimitive(it)) }
                        c.displayName?.let { put("displayName", JsonPrimitive(it)) }
                    }
                }))
            }
            document.content?.let { content ->
                put("content", buildJsonObject {
                    put("\$type", JsonPrimitive(content.type))
                    put("pages", kotlinx.serialization.json.JsonArray(content.pages.map { JsonPrimitive(it) }))
                    put("version", JsonPrimitive(content.version))
                })
            }
            document.updatedAt?.let { put("updatedAt", JsonPrimitive(it)) }
        }

        return putRecord(session, "site.standard.document", rkey, record).also {
            documentCache.remove(it.uri)
        }
    }

    /**
     * Creates a site.standard.publication record.
     */
    suspend fun createPublication(
        session: SessionStore.Session,
        publication: StandardPublication
    ): CreateRecordResult {
        val record = buildJsonObject {
            put("\$type", JsonPrimitive("site.standard.publication"))
            put("name", JsonPrimitive(publication.name))
            put("url", JsonPrimitive(publication.url))
            publication.description?.let { put("description", JsonPrimitive(it)) }
            publication.preferences?.let { prefs ->
                put("preferences", buildJsonObject {
                    prefs.showInDiscover?.let { put("showInDiscover", JsonPrimitive(it)) }
                })
            }
        }

        return createRecord(session, "site.standard.publication", record)
    }

    /**
     * Lists site.standard.document records for a repo.
     */
    suspend fun listDocuments(
        session: SessionStore.Session,
        limit: Int = 50
    ): List<DocumentEntry> {
        val response = httpClient.get("${session.pdsUrl.trimEnd('/')}/xrpc/com.atproto.repo.listRecords") {
            parameter("repo", session.did)
            parameter("collection", "site.standard.document")
            parameter("limit", limit.toString())
        }
        val body = response.body<JsonObject>()
        val records = body["records"]?.jsonArray ?: return emptyList()

        return records.mapNotNull { record ->
            val obj = record.jsonObject
            val uri = obj["uri"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val cid = obj["cid"]?.jsonPrimitive?.content
            val value = obj["value"]?.jsonObject ?: return@mapNotNull null
            val document = parseDocument(value)
            DocumentEntry(uri, cid, document)
        }
    }

    /**
     * Lists site.standard.document records for any DID (unauthenticated).
     */
    suspend fun listDocumentsForDid(
        did: String,
        pdsUrl: String,
        limit: Int = 50
    ): List<DocumentEntry> {
        val response = httpClient.get("${pdsUrl.trimEnd('/')}/xrpc/com.atproto.repo.listRecords") {
            parameter("repo", did)
            parameter("collection", "site.standard.document")
            parameter("limit", limit.toString())
        }
        val body = response.body<JsonObject>()
        val records = body["records"]?.jsonArray ?: return emptyList()

        return records.mapNotNull { record ->
            val obj = record.jsonObject
            val uri = obj["uri"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val cid = obj["cid"]?.jsonPrimitive?.content
            val value = obj["value"]?.jsonObject ?: return@mapNotNull null
            val document = parseDocument(value)
            DocumentEntry(uri, cid, document)
        }
    }

    /**
     * Gets a single site.standard.document record by URI.
     */
    suspend fun getDocument(
        session: SessionStore.Session,
        uri: String
    ): DocumentEntry? {
        val cached = documentCache[uri]
        if (cached != null && System.currentTimeMillis() - cached.timestamp < CACHE_TTL_MS) {
            return cached.entry
        }

        val atUri = uk.ewancroft.standardbooks.util.AtUri.parse(uri)
        val response = httpClient.get("${session.pdsUrl.trimEnd('/')}/xrpc/com.atproto.repo.getRecord") {
            parameter("repo", atUri.did)
            parameter("collection", atUri.collection)
            parameter("rkey", atUri.rkey)
        }
        if (response.status.value !in 200..299) {
            documentCache[uri] = CachedDocument(null, System.currentTimeMillis())
            return null
        }
        val body = response.body<JsonObject>()
        val value = body["value"]?.jsonObject ?: return null
        val cid = body["cid"]?.jsonPrimitive?.content
        val entry = DocumentEntry(uri, cid, parseDocument(value))
        documentCache[uri] = CachedDocument(entry, System.currentTimeMillis())
        return entry
    }

    /**
     * Gets a document from any repo (unauthenticated).
     */
    suspend fun getDocumentFromRepo(
        did: String,
        pdsUrl: String,
        uri: String
    ): DocumentEntry? {
        val cached = documentCache[uri]
        if (cached != null && System.currentTimeMillis() - cached.timestamp < CACHE_TTL_MS) {
            return cached.entry
        }

        val atUri = uk.ewancroft.standardbooks.util.AtUri.parse(uri)
        val response = httpClient.get("${pdsUrl.trimEnd('/')}/xrpc/com.atproto.repo.getRecord") {
            parameter("repo", atUri.did)
            parameter("collection", atUri.collection)
            parameter("rkey", atUri.rkey)
        }
        if (response.status.value !in 200..299) {
            documentCache[uri] = CachedDocument(null, System.currentTimeMillis())
            return null
        }
        val body = response.body<JsonObject>()
        val value = body["value"]?.jsonObject ?: return null
        val cid = body["cid"]?.jsonPrimitive?.content
        val entry = DocumentEntry(uri, cid, parseDocument(value))
        documentCache[uri] = CachedDocument(entry, System.currentTimeMillis())
        return entry
    }

    /**
     * Ensures a site.standard.publication record exists for this DID at the given
     * URL, reusing it if one is already present.
     *
     * This is a live check against the PDS every call — it deliberately does not
     * rely on any local/plugin-side cache of "have we already created this."
     * Publications belong to the atproto identity, not to any particular
     * Minecraft server instance, so as long as the player is logged into the
     * same DID, an existing publication is found and reused regardless of
     * whether it was created from this server, a previous run of it, or
     * anywhere else. A new one is only created when none exists and
     * [autoCreate] is true.
     */
    suspend fun getOrCreatePublication(
        session: SessionStore.Session,
        name: String,
        url: String,
        description: String? = null,
        autoCreate: Boolean = true
    ): PublicationEntry? {
        val existing = listPublications(session).firstOrNull { it.publication.url == url }
        if (existing != null) return existing

        if (!autoCreate) return null

        val publication = StandardPublication(
            name = name,
            url = url,
            description = description
        )
        val result = createPublication(session, publication)
        return PublicationEntry(result.uri, result.cid, publication)
    }

    /**
     * Lists site.standard.publication records for a repo.
     */
    suspend fun listPublications(
        session: SessionStore.Session
    ): List<PublicationEntry> {
        val response = httpClient.get("${session.pdsUrl.trimEnd('/')}/xrpc/com.atproto.repo.listRecords") {
            parameter("repo", session.did)
            parameter("collection", "site.standard.publication")
            parameter("limit", "10")
        }
        val body = response.body<JsonObject>()
        val records = body["records"]?.jsonArray ?: return emptyList()

        return records.mapNotNull { record ->
            val obj = record.jsonObject
            val uri = obj["uri"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val cid = obj["cid"]?.jsonPrimitive?.content
            val value = obj["value"]?.jsonObject ?: return@mapNotNull null
            val pub = parsePublication(value)
            PublicationEntry(uri, cid, pub)
        }
    }

    /**
     * Deletes a record.
     */
    suspend fun deleteRecord(
        session: SessionStore.Session,
        collection: String,
        rkey: String
    ): Boolean {
        val response = httpClient.post("${session.pdsUrl.trimEnd('/')}/xrpc/com.atproto.repo.deleteRecord") {
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject {
                put("repo", JsonPrimitive(session.did))
                put("collection", JsonPrimitive(collection))
                put("rkey", JsonPrimitive(rkey))
            })
        }
        val success = response.status.value in 200..299
        if (success) {
            val uri = "at://${session.did}/$collection/$rkey"
            documentCache.remove(uri)
        }
        return success
    }

    private suspend fun createRecord(
        session: SessionStore.Session,
        collection: String,
        record: JsonObject
    ): CreateRecordResult {
        val response = httpClient.post("${session.pdsUrl.trimEnd('/')}/xrpc/com.atproto.repo.createRecord") {
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject {
                put("repo", JsonPrimitive(session.did))
                put("collection", JsonPrimitive(collection))
                put("record", record)
            })
        }
        val body = response.body<JsonObject>()
        val uri = body["uri"]?.jsonPrimitive?.content
            ?: throw IllegalStateException("No URI in createRecord response")
        val cid = body["cid"]?.jsonPrimitive?.content ?: ""
        return CreateRecordResult(uri, cid)
    }

    private suspend fun putRecord(
        session: SessionStore.Session,
        collection: String,
        rkey: String,
        record: JsonObject
    ): CreateRecordResult {
        val response = httpClient.post("${session.pdsUrl.trimEnd('/')}/xrpc/com.atproto.repo.putRecord") {
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject {
                put("repo", JsonPrimitive(session.did))
                put("collection", JsonPrimitive(collection))
                put("rkey", JsonPrimitive(rkey))
                put("record", record)
            })
        }
        val body = response.body<JsonObject>()
        val uri = body["uri"]?.jsonPrimitive?.content
            ?: throw IllegalStateException("No URI in putRecord response")
        val cid = body["cid"]?.jsonPrimitive?.content ?: ""
        return CreateRecordResult(uri, cid)
    }

    private fun parseDocument(value: JsonObject): StandardDocument {
        val content = value["content"]?.let { contentElement ->
            val contentObj = contentElement.jsonObject
            val pages = contentObj["pages"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
            val version = contentObj["version"]?.jsonPrimitive?.content?.toIntOrNull() ?: 1
            uk.ewancroft.standardbooks.atproto.model.StandardBooksContent(
                type = contentObj["\$type"]?.jsonPrimitive?.content ?: "site.standard.content.standardbooks",
                pages = pages,
                version = version
            )
        }

        val contributors = value["contributors"]?.jsonArray?.map { c ->
            val cObj = c.jsonObject
            StandardDocument.Contributor(
                did = cObj["did"]?.jsonPrimitive?.content ?: "",
                role = cObj["role"]?.jsonPrimitive?.content,
                displayName = cObj["displayName"]?.jsonPrimitive?.content
            )
        }

        val tags = value["tags"]?.jsonArray?.map { it.jsonPrimitive.content }

        return StandardDocument(
            site = value["site"]?.jsonPrimitive?.content ?: "",
            title = value["title"]?.jsonPrimitive?.content ?: "",
            publishedAt = value["publishedAt"]?.jsonPrimitive?.content ?: "",
            path = value["path"]?.jsonPrimitive?.content,
            description = value["description"]?.jsonPrimitive?.content,
            content = content,
            textContent = value["textContent"]?.jsonPrimitive?.content,
            tags = tags,
            contributors = contributors,
            updatedAt = value["updatedAt"]?.jsonPrimitive?.content
        )
    }

    private fun parsePublication(value: JsonObject): StandardPublication {
        val prefs = value["preferences"]?.let { p ->
            val pObj = p.jsonObject
            StandardPublication.Preferences(
                showInDiscover = pObj["showInDiscover"]?.jsonPrimitive?.content?.toBooleanStrictOrNull()
            )
        }
        return StandardPublication(
            name = value["name"]?.jsonPrimitive?.content ?: "",
            url = value["url"]?.jsonPrimitive?.content ?: "",
            description = value["description"]?.jsonPrimitive?.content,
            preferences = prefs
        )
    }

    private fun io.ktor.client.request.HttpRequestBuilder.parameter(key: String, value: String) {
        url.parameters.append(key, value)
    }

    private fun buildJsonObject(block: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit): JsonObject {
        return kotlinx.serialization.json.buildJsonObject(block)
    }

    fun close() {
        httpClient.close()
    }
}
