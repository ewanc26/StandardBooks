package uk.ewancroft.standardbooks

import org.bukkit.plugin.java.JavaPlugin
import org.bstats.bukkit.Metrics
import uk.ewancroft.standardbooks.atproto.AtProtoClient
import uk.ewancroft.standardbooks.auth.AuthManager
import uk.ewancroft.standardbooks.auth.OAuthServer
import uk.ewancroft.standardbooks.auth.SessionStore
import uk.ewancroft.standardbooks.book.BookConverter
import uk.ewancroft.standardbooks.book.BookRenderer
import uk.ewancroft.standardbooks.command.StandardBooksCommand
import uk.ewancroft.standardbooks.config.PluginConfig
import uk.ewancroft.standardbooks.gui.BrowseGui
import uk.ewancroft.standardbooks.listener.BookListener
import uk.ewancroft.standardbooks.message.Messages

class StandardBooksPlugin : JavaPlugin() {

    lateinit var config: PluginConfig
        private set
    lateinit var messages: Messages
        private set
    lateinit var sessionStore: SessionStore
        private set
    lateinit var authManager: AuthManager
        private set
    lateinit var atProtoClient: AtProtoClient
        private set
    lateinit var oauthServer: OAuthServer
        private set
    lateinit var browseGui: BrowseGui
        private set
    lateinit var bookConverter: BookConverter
        private set
    lateinit var bookRenderer: BookRenderer
        private set
    var commandHandler: StandardBooksCommand? = null
        private set
    private var metrics: Metrics? = null
    private var placeholderExpansion: StandardBooksExpansion? = null

    override fun onEnable() {
        config = PluginConfig.from(this)
        messages = Messages(this)
        messages.load()

        if (!config.enabled) {
            logger.info("StandardBooks is disabled in config.")
            return
        }

        sessionStore = SessionStore(dataFolder)
        sessionStore.load()

        authManager = AuthManager(config, sessionStore)
        atProtoClient = AtProtoClient()

        oauthServer = OAuthServer(config.oauth.port, config.oauth.callbackPath)
        oauthServer.start()
        logger.info("OAuth callback server started on port ${config.oauth.port}")

        browseGui = BrowseGui(config, this)
        bookConverter = BookConverter(config)
        bookRenderer = BookRenderer(config)

        val command = StandardBooksCommand(this)
        commandHandler = command

        getCommand("standardbooks")?.setExecutor(command)
        getCommand("standardbooks")?.tabCompleter = command

        server.pluginManager.registerEvents(BookListener(this), this)

        // PlaceholderAPI (optional)
        if (config.bstatsEnabled) {
            try {
                metrics = Metrics(this, 0) // bStats ID — replace with real one
            } catch (e: Exception) {
                logger.warning("Failed to initialise bStats: ${e.message}")
            }
        }

        // PlaceholderAPI expansion (optional)
        try {
            if (server.pluginManager.getPlugin("PlaceholderAPI") != null) {
                placeholderExpansion = StandardBooksExpansion(this)
                placeholderExpansion?.register()
            }
        } catch (e: NoClassDefFoundError) {
            // PlaceholderAPI not installed — fine
        }

        logger.info("StandardBooks enabled.")
    }

    override fun onDisable() {
        oauthServer?.stop()
        authManager?.close()
        atProtoClient?.close()
        metrics?.shutdown()
        placeholderExpansion?.unregister()
        logger.info("StandardBooks disabled.")
    }

    fun reload() {
        reloadConfig()
        config = PluginConfig.from(this)
        messages.reload()
    }
}
