package uk.ewancroft.standardbooks.book

import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.BookMeta
import uk.ewancroft.standardbooks.atproto.AtProtoClient
import uk.ewancroft.standardbooks.atproto.model.StandardDocument
import uk.ewancroft.standardbooks.config.PluginConfig

/**
 * Renders Standard.site documents as in-game written books.
 *
 * Creates ItemStacks with BookMeta, extending beyond vanilla page limits
 * since this plugin operates server-side and is not bound by vanilla constraints.
 */
class BookRenderer(private val config: PluginConfig) {

    /**
     * Creates a written book ItemStack from a Standard.site document.
     */
    fun renderBook(document: StandardDocument, authorName: String): ItemStack {
        val item = ItemStack(Material.WRITTEN_BOOK)
        val meta = item.itemMeta as BookMeta

        meta.title(Component.text(document.title))
        meta.author(Component.text(authorName))

        val converter = BookConverter(config)
        val pages = converter.toPages(document)

        val pageComponents = pages.map { pageText ->
            Component.text(pageText)
        }

        meta.pages(pageComponents)

        item.itemMeta = meta
        return item
    }

    /**
     * Creates a book item for the browse GUI — just a display item
     * with the title and author, not a readable book.
     */
    fun renderBookItem(entry: AtProtoClient.DocumentEntry): ItemStack {
        val item = ItemStack(Material.WRITTEN_BOOK)
        val meta = item.itemMeta as BookMeta

        meta.title(Component.text(entry.document.title))
        val author = entry.document.contributors?.firstOrNull()?.displayName
            ?: entry.document.contributors?.firstOrNull()?.did
            ?: "Unknown"
        meta.author(Component.text(author))

        val converter = BookConverter(config)
        val pages = converter.toPages(entry.document)
        val pageComponents = pages.map { Component.text(it) }
        meta.pages(pageComponents)

        item.itemMeta = meta
        return item
    }
}
