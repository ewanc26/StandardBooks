package uk.ewancroft.standardbooks.command

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import org.bukkit.inventory.meta.BookMeta
import uk.ewancroft.standardbooks.StandardBooksPlugin
import uk.ewancroft.standardbooks.atproto.AtProtoClient
import uk.ewancroft.standardbooks.auth.AuthManager
import uk.ewancroft.standardbooks.book.BookConverter
import uk.ewancroft.standardbooks.book.BookRenderer
import uk.ewancroft.standardbooks.gui.BrowseGui
import uk.ewancroft.standardbooks.util.AtUri

class StandardBooksCommand(
    private val plugin: StandardBooksPlugin
) : CommandExecutor, TabCompleter {

    private val scope = CoroutineScope(Dispatchers.IO)

    private val subcommands = listOf(
        "login", "logout", "publish", "browse", "read", "list", "delete", "update", "status", "help", "reload"
    )

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage("StandardBooks can only be used by players.")
            return true
        }

        if (!plugin.config.enabled) {
            sender.sendMessage(plugin.messages.prefixed("status.logged-out"))
            return true
        }

        val sub = if (args.isEmpty()) "help" else args[0].lowercase()

        when (sub) {
            "login" -> handleLogin(sender, args)
            "logout" -> handleLogout(sender)
            "publish" -> handlePublish(sender)
            "browse" -> handleBrowse(sender, args)
            "read" -> handleRead(sender, args)
            "list" -> handleList(sender)
            "delete" -> handleDelete(sender, args)
            "update" -> handleUpdate(sender, args)
            "status" -> handleStatus(sender)
            "reload" -> handleReload(sender)
            "help" -> handleHelp(sender)
            else -> {
                sender.sendMessage(plugin.messages.prefixed("help.help"))
            }
        }

        return true
    }

    private fun handleLogin(player: Player, args: Array<out String>) {
        if (args.size < 2) {
            player.sendMessage(plugin.messages.prefixed("login.not-logged-in", "handle" to "<handle>"))
            return
        }

        val handle = args[1]
        val session = plugin.sessionStore.get(player.uniqueId.toString())
        if (session != null) {
            player.sendMessage(plugin.messages.prefixed(
                "login.already-logged-in",
                "handle" to session.handle
            ))
            return
        }

        scope.launch {
            try {
                val identity = plugin.authManager.resolveIdentity(handle)
                val auth = plugin.authManager.beginAuthorization(identity)

                // Register callback expectation
                val callbackDeferred = plugin.oauthServer.expectCallback(auth.state)

                // Send auth URL to player
                withContext(Dispatchers.Main) {
                    player.sendMessage(plugin.messages.get(
                        "login.prompt",
                        "url" to auth.authUrl
                    ))
                }

                // Wait for callback (with timeout)
                val callback = withTimeoutOrNull(300_000L) { callbackDeferred.await() }
                if (callback == null) {
                    plugin.oauthServer.cancelCallback(auth.state)
                    withContext(Dispatchers.Main) {
                        player.sendMessage(plugin.messages.prefixed("login.failure", "error" to "Login timed out"))
                    }
                    return@launch
                }

                // Exchange code for tokens
                val newSession = plugin.authManager.completeAuthorization(identity, auth, callback.code)
                plugin.sessionStore.put(player.uniqueId.toString(), newSession)

                withContext(Dispatchers.Main) {
                    player.sendMessage(plugin.messages.prefixed(
                        "login.success",
                        "handle" to newSession.handle
                    ))
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    player.sendMessage(plugin.messages.prefixed(
                        "login.failure",
                        "error" to (e.message ?: "Unknown error")
                    ))
                }
            }
        }
    }

    private fun handleLogout(player: Player) {
        val uuid = player.uniqueId.toString()
        if (!plugin.sessionStore.has(uuid)) {
            player.sendMessage(plugin.messages.prefixed("login.not-logged-in"))
            return
        }
        plugin.sessionStore.remove(uuid)
        player.sendMessage(plugin.messages.prefixed("logout.success"))
    }

    private fun handlePublish(player: Player) {
        val uuid = player.uniqueId.toString()
        val session = plugin.sessionStore.get(uuid)
        if (session == null) {
            player.sendMessage(plugin.messages.prefixed("login.not-logged-in"))
            return
        }

        val item = player.inventory.itemInMainHand
        if (item.type != org.bukkit.Material.WRITTEN_BOOK) {
            player.sendMessage(plugin.messages.prefixed("publish.no-book"))
            return
        }

        val meta = item.itemMeta as? BookMeta
        if (meta == null) {
            player.sendMessage(plugin.messages.prefixed("publish.no-book"))
            return
        }

        val converter = BookConverter(plugin.config)
        val validationError = converter.validateBook(meta)
        if (validationError != null) {
            player.sendMessage(plugin.messages.prefixed("publish.failure", "error" to validationError))
            return
        }

        val siteUrl = plugin.config.publication.defaultUrl.ifBlank {
            plugin.config.oauth.externalUrl.ifBlank { "https://standardbooks.local" }
        }

        val document = converter.toDocument(
            bookMeta = meta,
            playerDid = session.did,
            playerHandle = session.handle,
            siteUrl = siteUrl
        )

        scope.launch {
            try {
                // Resolve the publication for this atproto identity first. This is a
                // live lookup against the player's PDS, not anything cached locally,
                // so an existing publication is reused whether it was created just now,
                // last week, or from a different server entirely — the only thing that
                // matters is that the player is logged into the same DID. A new one is
                // only created if none exists and auto-create is enabled.
                val publicationName = plugin.config.publication.defaultName.replace("{player}", player.name)
                plugin.atProtoClient.getOrCreatePublication(
                    session = session,
                    name = publicationName,
                    url = siteUrl,
                    autoCreate = plugin.config.publication.autoCreate
                )

                val result = plugin.atProtoClient.createDocument(session, document)
                withContext(Dispatchers.Main) {
                    player.sendMessage(plugin.messages.prefixed(
                        "publish.success",
                        "title" to document.title,
                        "uri" to result.uri
                    ))
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    player.sendMessage(plugin.messages.prefixed(
                        "publish.failure",
                        "error" to (e.message ?: "Unknown error")
                    ))
                }
            }
        }
    }

    private fun handleBrowse(player: Player, args: Array<out String>) {
        val uuid = player.uniqueId.toString()
        val session = plugin.sessionStore.get(uuid)

        scope.launch {
            try {
                val entries = if (session != null) {
                    plugin.atProtoClient.listDocuments(session)
                } else {
                    // If not logged in, can't browse
                    emptyList()
                }

                if (entries.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        player.sendMessage(plugin.messages.prefixed("browse.empty"))
                    }
                    return@launch
                }

                val page = if (args.size > 1) args[1].toIntOrNull() ?: 0 else 0
                val browsePage = BrowseGui.paginate(entries, page)

                withContext(Dispatchers.Main) {
                    val inventory = plugin.browseGui.createInventory(browsePage)
                    player.openInventory(inventory)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    player.sendMessage(plugin.messages.prefixed(
                        "read.failure",
                        "error" to (e.message ?: "Unknown error")
                    ))
                }
            }
        }
    }

    private fun handleRead(player: Player, args: Array<out String>) {
        if (args.size < 2) {
            player.sendMessage(plugin.messages.prefixed("read.not-found", "uri" to "<uri>"))
            return
        }

        val uri = args[1]
        val uuid = player.uniqueId.toString()
        val session = plugin.sessionStore.get(uuid)
        if (session == null) {
            player.sendMessage(plugin.messages.prefixed("login.not-logged-in"))
            return
        }

        scope.launch {
            try {
                val entry = plugin.atProtoClient.getDocument(session, uri)
                if (entry == null) {
                    withContext(Dispatchers.Main) {
                        player.sendMessage(plugin.messages.prefixed("read.not-found", "uri" to uri))
                    }
                    return@launch
                }

                val renderer = BookRenderer(plugin.config)
                val authorName = entry.document.contributors?.firstOrNull()?.displayName
                    ?: entry.document.contributors?.firstOrNull()?.did
                    ?: "Unknown"
                val book = renderer.renderBook(entry.document, authorName)

                withContext(Dispatchers.Main) {
                    player.openBook(book)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    player.sendMessage(plugin.messages.prefixed(
                        "read.failure",
                        "error" to (e.message ?: "Unknown error")
                    ))
                }
            }
        }
    }

    private fun handleList(player: Player) {
        val uuid = player.uniqueId.toString()
        val session = plugin.sessionStore.get(uuid)
        if (session == null) {
            player.sendMessage(plugin.messages.prefixed("login.not-logged-in"))
            return
        }

        scope.launch {
            try {
                val entries = plugin.atProtoClient.listDocuments(session)
                withContext(Dispatchers.Main) {
                    player.sendMessage(plugin.messages.prefixed(
                        "status.books-published",
                        "count" to entries.size.toString()
                    ))
                    for (entry in entries) {
                        val author = entry.document.contributors?.firstOrNull()?.displayName ?: "Unknown"
                        val bookComponent = Component.text("§7- §f${entry.document.title} §7by $author §8(${entry.uri})")
                            .clickEvent(ClickEvent.runCommand("/sb read ${entry.uri}"))
                            .hoverEvent(HoverEvent.showText(Component.text("§eClick to read this book")))
                        player.sendMessage(bookComponent)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    player.sendMessage(plugin.messages.prefixed(
                        "read.failure",
                        "error" to (e.message ?: "Unknown error")
                    ))
                }
            }
        }
    }

    private fun handleStatus(player: Player) {
        val uuid = player.uniqueId.toString()
        val session = plugin.sessionStore.get(uuid)

        player.sendMessage(plugin.messages.get("status.header"))
        if (session != null) {
            player.sendMessage(plugin.messages.get(
                "status.logged-in",
                "handle" to session.handle,
                "did" to session.did
            ))
        } else {
            player.sendMessage(plugin.messages.get("status.logged-out"))
        }
    }

    private fun handleReload(sender: CommandSender) {
        if (!sender.hasPermission("standardbooks.admin")) {
            sender.sendMessage("§cYou don't have permission to reload StandardBooks.")
            return
        }
        plugin.reload()
        sender.sendMessage("§aStandardBooks config reloaded.")
    }

    private fun handleHelp(player: Player) {
        player.sendMessage(plugin.messages.get("help.header"))
        player.sendMessage(plugin.messages.get("help.login"))
        player.sendMessage(plugin.messages.get("help.logout"))
        player.sendMessage(plugin.messages.get("help.publish"))
        player.sendMessage(plugin.messages.get("help.browse"))
        player.sendMessage(plugin.messages.get("help.read"))
        player.sendMessage(plugin.messages.get("help.list"))
        player.sendMessage(plugin.messages.get("help.delete"))
        player.sendMessage(plugin.messages.get("help.update"))
        player.sendMessage(plugin.messages.get("help.status"))
        player.sendMessage(plugin.messages.get("help.help"))
        if (player.hasPermission("standardbooks.admin")) {
            player.sendMessage(plugin.messages.get("help.reload"))
        }
    }

    private fun handleDelete(player: Player, args: Array<out String>) {
        if (args.size < 2) {
            player.sendMessage(plugin.messages.prefixed("read.not-found", "uri" to "<uri>"))
            return
        }

        val uri = args[1]
        val uuid = player.uniqueId.toString()
        val session = plugin.sessionStore.get(uuid)
        if (session == null) {
            player.sendMessage(plugin.messages.prefixed("login.not-logged-in"))
            return
        }

        val atUri = try {
            AtUri.parse(uri)
        } catch (e: Exception) {
            player.sendMessage(plugin.messages.prefixed("read.not-found", "uri" to uri))
            return
        }

        scope.launch {
            try {
                val success = plugin.atProtoClient.deleteRecord(session, atUri.collection, atUri.rkey)
                withContext(Dispatchers.Main) {
                    if (success) {
                        player.sendMessage(plugin.messages.prefixed("delete.success", "uri" to uri))
                    } else {
                        player.sendMessage(plugin.messages.prefixed("delete.not-found", "uri" to uri))
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    player.sendMessage(plugin.messages.prefixed("delete.failure", "error" to (e.message ?: "Unknown error")))
                }
            }
        }
    }

    private fun handleUpdate(player: Player, args: Array<out String>) {
        if (args.size < 2) {
            player.sendMessage(plugin.messages.prefixed("read.not-found", "uri" to "<uri>"))
            return
        }

        val uri = args[1]
        val uuid = player.uniqueId.toString()
        val session = plugin.sessionStore.get(uuid)
        if (session == null) {
            player.sendMessage(plugin.messages.prefixed("login.not-logged-in"))
            return
        }

        val item = player.inventory.itemInMainHand
        if (item.type != org.bukkit.Material.WRITTEN_BOOK) {
            player.sendMessage(plugin.messages.prefixed("publish.no-book"))
            return
        }

        val meta = item.itemMeta as? BookMeta
        if (meta == null) {
            player.sendMessage(plugin.messages.prefixed("publish.no-book"))
            return
        }

        val converter = BookConverter(plugin.config)
        val validationError = converter.validateBook(meta)
        if (validationError != null) {
            player.sendMessage(plugin.messages.prefixed("publish.failure", "error" to validationError))
            return
        }

        val atUri = try {
            AtUri.parse(uri)
        } catch (e: Exception) {
            player.sendMessage(plugin.messages.prefixed("read.not-found", "uri" to uri))
            return
        }

        val siteUrl = plugin.config.publication.defaultUrl.ifBlank {
            plugin.config.oauth.externalUrl.ifBlank { "https://standardbooks.local" }
        }

        val document = converter.toDocument(
            bookMeta = meta,
            playerDid = session.did,
            playerHandle = session.handle,
            siteUrl = siteUrl
        )

        scope.launch {
            try {
                // Same identity-scoped, live resolution as publish — reuses the
                // existing publication for this DID rather than assuming one
                // was already created locally.
                val publicationName = plugin.config.publication.defaultName.replace("{player}", player.name)
                plugin.atProtoClient.getOrCreatePublication(
                    session = session,
                    name = publicationName,
                    url = siteUrl,
                    autoCreate = plugin.config.publication.autoCreate
                )

                plugin.atProtoClient.updateDocument(session, atUri.rkey, document)
                withContext(Dispatchers.Main) {
                    player.sendMessage(plugin.messages.prefixed("update.success", "uri" to uri))
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    player.sendMessage(plugin.messages.prefixed("update.failure", "error" to (e.message ?: "Unknown error")))
                }
            }
        }
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): List<String> {
        if (args.size == 1) {
            return subcommands.filter { it.startsWith(args[0].lowercase()) }
        }
        return emptyList()
    }

    private suspend fun <T> withTimeoutOrNull(timeoutMs: Long, block: suspend () -> T): T? {
        return try {
            kotlinx.coroutines.withTimeout(timeoutMs) { block() }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            null
        }
    }

    /**
     * Reopens the browse GUI at a specific page. Called from BookListener
     * when the player clicks the next/previous navigation buttons.
     */
    fun reopenBrowse(player: Player, page: Int) {
        val uuid = player.uniqueId.toString()
        val session = plugin.sessionStore.get(uuid) ?: return

        scope.launch {
            try {
                val entries = plugin.atProtoClient.listDocuments(session)
                if (entries.isEmpty()) return@launch

                val browsePage = BrowseGui.paginate(entries, page)
                withContext(Dispatchers.Main) {
                    val inventory = plugin.browseGui.createInventory(browsePage)
                    player.openInventory(inventory)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    player.sendMessage(plugin.messages.prefixed(
                        "read.failure",
                        "error" to (e.message ?: "Unknown error")
                    ))
                }
            }
        }
    }
}
