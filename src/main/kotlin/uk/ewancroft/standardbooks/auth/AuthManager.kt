package uk.ewancroft.standardbooks.auth

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.headers
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.jsonObject
import uk.ewancroft.standardbooks.config.PluginConfig
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * Manages the AT Protocol OAuth flow for Minecraft players.
 *
 * Ported from Inkwell's LoginStateManager:
 * 1. Handle resolution → DID + PDS
 * 2. OAuth server metadata discovery
 * 3. DPoP key generation
 * 4. Authorization URL generation
 * 5. Token exchange (via embedded HTTP callback server)
 * 6. Session persistence
 */
class AuthManager(
    private val config: PluginConfig,
    private val sessionStore: SessionStore
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(this@AuthManager.json)
        }
    }

    data class ResolvedIdentity(
        val did: String,
        val handle: String,
        val pdsUrl: String
    )

    data class AuthorizationResult(
        val authUrl: String,
        val state: String,
        val codeVerifier: String,
        val dpopKey: ByteArray
    )

    /**
     * Resolves a handle or DID to a DID, handle, and PDS URL.
     */
    suspend fun resolveIdentity(handleOrDid: String): ResolvedIdentity {
        val isDid = handleOrDid.startsWith("did:")
        val did = if (isDid) handleOrDid else resolveHandle(handleOrDid)
        val pdsUrl = resolvePds(did)
        val handle = if (isDid) {
            // Try to resolve handle from DID doc
            try {
                resolveHandleFromDid(did)
            } catch (e: Exception) {
                did
            }
        } else {
            handleOrDid
        }
        return ResolvedIdentity(did, handle, pdsUrl)
    }

    /**
     * Begins the OAuth flow by fetching server metadata and generating
     * the authorization URL, DPoP key, and PKCE verifier.
     */
    suspend fun beginAuthorization(identity: ResolvedIdentity): AuthorizationResult {
        val metadata = fetchServerMetadata(identity.pdsUrl)
        val codeVerifier = generateCodeVerifier()
        val codeChallenge = generateCodeChallenge(codeVerifier)
        val state = generateState()
        val dpopKey = generateDpopKey()

        val authEndpoint = metadata["authorization_endpoint"]?.jsonPrimitive?.content
            ?: throw IllegalStateException("No authorization_endpoint in server metadata")

        val callbackUrl = config.oauth.callbackUrl
        val clientId = callbackUrl // AT Protocol uses the client ID as URL

        val authUrl = buildString {
            append(authEndpoint)
            append("?response_type=code")
            append("&client_id=").append(java.net.URLEncoder.encode(clientId, "UTF-8"))
            append("&redirect_uri=").append(java.net.URLEncoder.encode(callbackUrl, "UTF-8"))
            append("&scope=").append(java.net.URLEncoder.encode("atproto repo:site.standard.* blob:*", "UTF-8"))
            append("&state=").append(state)
            append("&code_challenge=").append(codeChallenge)
            append("&code_challenge_method=S256")
        }

        return AuthorizationResult(authUrl, state, codeVerifier, dpopKey)
    }

    /**
     * Completes the OAuth flow by exchanging the authorization code for tokens.
     */
    suspend fun completeAuthorization(
        identity: ResolvedIdentity,
        auth: AuthorizationResult,
        code: String
    ): SessionStore.Session {
        val metadata = fetchServerMetadata(identity.pdsUrl)
        val tokenEndpoint = metadata["token_endpoint"]?.jsonPrimitive?.content
            ?: throw IllegalStateException("No token_endpoint in server metadata")

        val callbackUrl = config.oauth.callbackUrl

        val tokenResponse = httpClient.post(tokenEndpoint) {
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject {
                put("grant_type", "authorization_code")
                put("code", code)
                put("redirect_uri", callbackUrl)
                put("client_id", callbackUrl)
                put("code_verifier", auth.codeVerifier)
            })
        }

        val tokenJson = tokenResponse.body<JsonObject>()
        val accessToken = tokenJson["access_token"]?.jsonPrimitive?.content
            ?: throw IllegalStateException("No access_token in response")
        val refreshToken = tokenJson["refresh_token"]?.jsonPrimitive?.content

        return SessionStore.Session(
            did = identity.did,
            handle = identity.handle,
            pdsUrl = identity.pdsUrl,
            accessToken = accessToken,
            refreshToken = refreshToken,
            dpopKey = auth.dpopKey,
            createdAt = System.currentTimeMillis()
        )
    }

    private suspend fun resolveHandle(handle: String): String {
        val response = httpClient.get("https://bsky.social/xrpc/com.atproto.identity.resolveHandle?handle=$handle")
        val body = response.body<JsonObject>()
        return body["did"]?.jsonPrimitive?.content
            ?: throw IllegalStateException("Could not resolve handle: $handle")
    }

    private suspend fun resolvePds(did: String): String {
        val didMethod = did.split(":")[1]
        val doc = when (didMethod) {
            "plc" -> {
                val response = httpClient.get("https://plc.directory/$did")
                response.body<JsonObject>()
            }
            "web" -> {
                val domain = did.split(":").last()
                val response = httpClient.get("https://$domain/.well-known/did.json")
                response.body<JsonObject>()
            }
            else -> throw IllegalArgumentException("Unsupported DID method: $didMethod")
        }

        val services = doc["service"] as? kotlinx.serialization.json.JsonArray
            ?: throw IllegalStateException("No service in DID document")
        for (service in services) {
            val serviceObj = service.jsonObject
            if (serviceObj["id"]?.jsonPrimitive?.content == "#atproto_pds") {
                return serviceObj["serviceEndpoint"]?.jsonPrimitive?.content
                    ?: throw IllegalStateException("No serviceEndpoint in PDS service")
            }
        }
        throw IllegalStateException("No atproto_pds service in DID document")
    }

    private suspend fun resolveHandleFromDid(did: String): String {
        val didMethod = did.split(":")[1]
        val doc = when (didMethod) {
            "plc" -> {
                val response = httpClient.get("https://plc.directory/$did")
                response.body<JsonObject>()
            }
            "web" -> {
                val domain = did.split(":").last()
                val response = httpClient.get("https://$domain/.well-known/did.json")
                response.body<JsonObject>()
            }
            else -> return did
        }
        val alsoKnownAs = doc["alsoKnownAs"] as? kotlinx.serialization.json.JsonArray
        alsoKnownAs?.firstOrNull()?.let {
            val aka = it.jsonPrimitive.content
            if (aka.startsWith("at://")) {
                return aka.removePrefix("at://")
            }
        }
        return did
    }

    private suspend fun fetchServerMetadata(pdsUrl: String): JsonObject {
        val response = httpClient.get("${pdsUrl.trimEnd('/')}/.well-known/oauth-authorization-server")
        return response.body()
    }

    private fun generateCodeVerifier(): String {
        val random = ByteArray(32)
        SecureRandom().nextBytes(random)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(random)
    }

    private fun generateCodeChallenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(verifier.toByteArray())
        return Base64.getUrlEncoder().withoutPadding().encodeToString(hash)
    }

    private fun generateState(): String {
        val random = ByteArray(16)
        SecureRandom().nextBytes(random)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(random)
    }

    private fun generateDpopKey(): ByteArray {
        val random = ByteArray(32)
        SecureRandom().nextBytes(random)
        return random
    }

    private fun buildJsonObject(block: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit): JsonObject {
        return kotlinx.serialization.json.buildJsonObject(block)
    }

    private fun kotlinx.serialization.json.JsonObjectBuilder.put(key: String, value: String) {
        put(key, kotlinx.serialization.json.JsonPrimitive(value))
    }

    fun close() {
        httpClient.close()
    }
}
