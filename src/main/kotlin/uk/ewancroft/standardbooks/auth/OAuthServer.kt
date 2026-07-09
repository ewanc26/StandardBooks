package uk.ewancroft.standardbooks.auth

import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.routing.routing
import io.ktor.server.routing.get
import io.ktor.server.response.respondText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.ConcurrentHashMap

/**
 * Embedded HTTP server that receives OAuth callbacks.
 *
 * When a player authenticates at their PDS, the PDS redirects to this server.
 * The server captures the authorization code and state, then completes the flow.
 */
class OAuthServer(
    private val port: Int,
    private val callbackPath: String
) {
    private var server: Any? = null
    private val pendingCallbacks = ConcurrentHashMap<String, CompletableDeferred<OAuthCallback>>()

    data class OAuthCallback(
        val code: String,
        val state: String
    )

    fun start() {
        server = embeddedServer(Netty, port = port) {
            routing {
                get(callbackPath) {
                    val code = call.request.queryParameters["code"]
                    val state = call.request.queryParameters["state"]
                    val error = call.request.queryParameters["error"]

                    if (error != null) {
                        call.respondText(
                            "Login failed: $error",
                            ContentType.Text.Plain,
                            HttpStatusCode.BadRequest
                        )
                        return@get
                    }

                    if (code == null || state == null) {
                        call.respondText(
                            "Missing code or state parameter",
                            ContentType.Text.Plain,
                            HttpStatusCode.BadRequest
                        )
                        return@get
                    }

                    val callback = OAuthCallback(code, state)
                    pendingCallbacks[state]?.complete(callback)

                    call.respondText(
                        "Login successful! You can close this window and return to Minecraft.",
                        ContentType.Text.Plain
                    )
                }
            }
        }.start(wait = false)
    }

    fun stop() {
        (server as? ApplicationEngine)?.stop(1000, 2000)
        server = null
        pendingCallbacks.clear()
    }

    /**
     * Registers a pending callback for the given state and returns a deferred
     * that completes when the callback is received.
     */
    fun expectCallback(state: String): CompletableDeferred<OAuthCallback> {
        val deferred = CompletableDeferred<OAuthCallback>()
        pendingCallbacks[state] = deferred
        return deferred
    }

    fun cancelCallback(state: String) {
        pendingCallbacks.remove(state)
    }
}
