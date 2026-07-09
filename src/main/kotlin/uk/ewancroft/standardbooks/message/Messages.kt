package uk.ewancroft.standardbooks.message

import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.File

/**
 * Wraps messages.yml and returns MiniMessage-parsed Components.
 * Falls back to defaults if a key is missing.
 */
class Messages(private val plugin: JavaPlugin) {
    private val miniMessage = MiniMessage.miniMessage()
    private var config: FileConfiguration = plugin.config

    companion object {
        private val DEFAULTS = mapOf(
            "prefix" to "<gradient:blue:aqua>StandardBooks</gradient> <dark_gray>»</dark_gray> ",

            "login.prompt" to "<yellow>Click to login to AT Protocol:</yellow> <click:open_url:'{url}'><blue><underlined>{url}</underlined></click>",
            "login.success" to "<green>Logged in as {handle}!</green>",
            "login.failure" to "<red>Login failed: {error}</red>",
            "login.already-logged-in" to "<yellow>Already logged in as {handle}. Use /sb logout to disconnect.</yellow>",
            "login.not-logged-in" to "<red>Not logged in. Use /sb login <handle> to connect.</red>",

            "publish.success" to "<green>Published '{title}' to AT Protocol!</green> <gray>({uri})</gray>",
            "publish.no-book" to "<red>You must be holding a written book to publish.</red>",
            "publish.empty-book" to "<red>The book is empty.</red>",
            "publish.failure" to "<red>Failed to publish: {error}</red>",
            "publish.no-publication" to "<red>No publication found. Publishing will be attempted automatically.</red>",

            "browse.title" to "<dark_blue>StandardBooks Browser</dark_blue>",
            "browse.empty" to "<gray>No books found.</gray>",
            "browse.next-page" to "<green>Next Page</green>",
            "browse.prev-page" to "<green>Previous Page</green>",
            "browse.page-info" to "<gray>Page {page}/{pages}</gray>",

            "read.not-found" to "<red>Book not found: {uri}</red>",
            "read.failure" to "<red>Failed to fetch book: {error}</red>",

            "logout.success" to "<green>Logged out successfully.</green>",

            "status.header" to "<gradient:blue:aqua>StandardBooks Status</gradient>",
            "status.logged-in" to "<green>Logged in as: {handle} ({did})</green>",
            "status.logged-out" to "<red>Not logged in</red>",
            "status.books-published" to "<gray>Books published: {count}</gray>",

            "help.header" to "<gradient:blue:aqua>StandardBooks Help</gradient>",
            "help.login" to "<gray>/sb login <handle></gray> <dark_gray>— Connect your AT Protocol account</dark_gray>",
            "help.logout" to "<gray>/sb logout</gray> <dark_gray>— Disconnect your account</dark_gray>",
            "help.publish" to "<gray>/sb publish</gray> <dark_gray>— Publish the book you're holding</dark_gray>",
            "help.browse" to "<gray>/sb browse</gray> <dark_gray>— Browse published books</dark_gray>",
            "help.read" to "<gray>/sb read <uri></gray> <dark_gray>— Read a book by its AT URI</dark_gray>",
            "help.list" to "<gray>/sb list</gray> <dark_gray>— List your published books</dark_gray>",
            "help.status" to "<gray>/sb status</gray> <dark_gray>— Show your connection status</dark_gray>",
            "help.help" to "<gray>/sb help</gray> <dark_gray>— Show this help</dark_gray>",
            "help.reload" to "<gray>/sb reload</gray> <dark_gray>— Reload config (admin)</dark_gray>"
        )
    }

    fun load() {
        val file = File(plugin.dataFolder, "messages.yml")
        if (!file.exists()) {
            plugin.saveResource("messages.yml", false)
        }
        config = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file)
    }

    fun reload() {
        load()
    }

    fun raw(key: String): String {
        return config.getString(key) ?: DEFAULTS[key] ?: ""
    }

    fun get(key: String, vararg replacements: Pair<String, String>): net.kyori.adventure.text.Component {
        var text = raw(key)
        for ((placeholder, value) in replacements) {
            text = text.replace("{$placeholder}", value)
        }
        return miniMessage.deserialize(text)
    }

    fun prefixed(key: String, vararg replacements: Pair<String, String>): net.kyori.adventure.text.Component {
        val prefix = raw("prefix")
        var text = raw(key)
        for ((placeholder, value) in replacements) {
            text = text.replace("{$placeholder}", value)
        }
        return miniMessage.deserialize(prefix + text)
    }
}
