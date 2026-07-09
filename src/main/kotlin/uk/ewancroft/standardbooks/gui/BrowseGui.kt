package uk.ewancroft.standardbooks.gui

import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.NamespacedKey
import uk.ewancroft.standardbooks.atproto.AtProtoClient
import uk.ewancroft.standardbooks.book.BookRenderer
import uk.ewancroft.standardbooks.config.PluginConfig

/**
 * Chest GUI for browsing published books.
 *
 * Shows a paginated grid of book items. Clicking a book opens it for reading.
 * Navigation buttons on the bottom row for next/previous page.
 */
class BrowseGui(
    private val config: PluginConfig,
    private val plugin: org.bukkit.plugin.java.JavaPlugin
) {
    private val pageSize = 45 // 5 rows of 9, leaving 1 row for navigation
    private val uriKey = NamespacedKey(plugin, "document_uri")

    data class BrowsePage(
        val entries: List<AtProtoClient.DocumentEntry>,
        val page: Int,
        val totalPages: Int
    )

    fun createInventory(page: BrowsePage): Inventory {
        val holder = BrowseHolder(page.page)
        val title = Component.text("StandardBooks Browser — Page ${page.page + 1}/${page.totalPages}")
        val size = 54
        val inventory = Bukkit.createInventory(holder, size, title)

        // Fill book slots
        for ((index, entry) in page.entries.withIndex()) {
            val renderer = BookRenderer(config)
            val item = renderer.renderBookItem(entry)
            val meta = item.itemMeta
            meta?.persistentDataContainer?.set(uriKey, PersistentDataType.STRING, entry.uri)
            item.itemMeta = meta
            inventory.setItem(index, item)
        }

        // Navigation: previous button (slot 45)
        if (page.page > 0) {
            val prev = ItemStack(org.bukkit.Material.ARROW)
            val meta = prev.itemMeta
            meta?.displayName(Component.text("§a← Previous Page"))
            prev.itemMeta = meta
            inventory.setItem(45, prev)
        }

        // Page info (slot 49)
        val info = ItemStack(org.bukkit.Material.PAPER)
        val infoMeta = info.itemMeta
        infoMeta?.displayName(Component.text("§7Page ${page.page + 1}/${page.totalPages}"))
        info.itemMeta = infoMeta
        inventory.setItem(49, info)

        // Next button (slot 53)
        if (page.page < page.totalPages - 1) {
            val next = ItemStack(org.bukkit.Material.ARROW)
            val meta = next.itemMeta
            meta?.displayName(Component.text("§aNext Page →"))
            next.itemMeta = meta
            inventory.setItem(53, next)
        }

        return inventory
    }

    fun getDocumentUri(item: ItemStack): String? {
        val meta = item.itemMeta ?: return null
        val container = meta.persistentDataContainer
        return container.get(uriKey, PersistentDataType.STRING)
    }

    class BrowseHolder(val page: Int) : InventoryHolder {
        private var inventory: Inventory? = null
        override fun getInventory(): Inventory = inventory ?: Bukkit.createInventory(this, 0)
    }

    companion object {
        fun paginate(entries: List<AtProtoClient.DocumentEntry>, page: Int, pageSize: Int = 45): BrowsePage {
            val totalPages = if (entries.isEmpty()) 1 else (entries.size + pageSize - 1) / pageSize
            val clampedPage = page.coerceIn(0, totalPages - 1)
            val start = clampedPage * pageSize
            val end = minOf(start + pageSize, entries.size)
            return BrowsePage(
                entries = entries.subList(start, end),
                page = clampedPage,
                totalPages = totalPages
            )
        }
    }
}
