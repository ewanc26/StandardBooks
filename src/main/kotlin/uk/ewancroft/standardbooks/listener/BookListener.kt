package uk.ewancroft.standardbooks.listener

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerEditBookEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uk.ewancroft.standardbooks.StandardBooksPlugin
import uk.ewancroft.standardbooks.gui.BrowseGui
import uk.ewancroft.standardbooks.book.BookConverter

class BookListener(private val plugin: StandardBooksPlugin) : Listener {
    private val scope = CoroutineScope(Dispatchers.IO)

    private val serializer = PlainTextComponentSerializer.plainText()

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val holder = event.inventory.holder
        if (holder !is BrowseGui.BrowseHolder) return

        event.isCancelled = true

        val player = event.whoClicked as? Player ?: return
        val clicked = event.currentItem ?: return

        if (clicked.type == org.bukkit.Material.ARROW) {
            val displayName = clicked.itemMeta?.let { meta -> meta.displayName()?.let { serializer.serialize(it) } } ?: ""
            val currentPage = holder.page
            if (displayName.contains("Previous")) {
                plugin.commandHandler?.reopenBrowse(player, currentPage - 1)
            } else if (displayName.contains("Next")) {
                plugin.commandHandler?.reopenBrowse(player, currentPage + 1)
            }
            return
        }

        if (clicked.type == org.bukkit.Material.WRITTEN_BOOK) {
            player.closeInventory()
            player.openBook(clicked)
        }
    }

    @EventHandler
    fun onBookSign(event: PlayerEditBookEvent) {
        if (!event.isSigning) return
        if (!plugin.config.books.autoPublishOnSign) return
        if (!event.player.hasPermission("standardbooks.publish")) return

        val player = event.player
        val uuid = player.uniqueId.toString()
        val session = plugin.sessionStore.get(uuid) ?: return

        val meta = event.newBookMeta
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
                val result = plugin.atProtoClient.createDocument(session, document)
                withContext(Dispatchers.Main) {
                    val title = meta.title()?.let { serializer.serialize(it) } ?: "Untitled"
                    player.sendMessage(plugin.messages.prefixed("publish.success", "title" to title, "uri" to result.uri))
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    player.sendMessage(plugin.messages.prefixed("publish.failure", "error" to (e.message ?: "Unknown error")))
                }
            }
        }
    }
}
