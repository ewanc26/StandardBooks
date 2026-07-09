package uk.ewancroft.standardbooks.config

import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.plugin.java.JavaPlugin

/**
 * Typed config wrapper over config.yml.
 */
data class PluginConfig(
    val enabled: Boolean,
    val oauth: OAuthConfig,
    val publication: PublicationConfig,
    val books: BookConfig,
    val bstatsEnabled: Boolean
) {
    data class OAuthConfig(
        val port: Int,
        val callbackPath: String,
        val externalUrl: String
    ) {
        val callbackUrl: String
            get() = if (externalUrl.isNotBlank()) {
                "${externalUrl.trimEnd('/')}${callbackPath}"
            } else {
                "http://localhost:${port}${callbackPath}"
            }
    }

    data class PublicationConfig(
        val autoCreate: Boolean,
        val defaultName: String,
        val defaultUrl: String
    )

    data class BookConfig(
        val maxPages: Int,
        val maxCharsPerPage: Int,
        val autoPublishOnSign: Boolean
    )

    companion object {
        fun from(plugin: JavaPlugin): PluginConfig {
            plugin.saveDefaultConfig()
            val config: FileConfiguration = plugin.config

            return PluginConfig(
                enabled = config.getBoolean("enabled", true),
                oauth = OAuthConfig(
                    port = config.getInt("oauth.port", 8765).coerceIn(1024, 65535),
                    callbackPath = config.getString("oauth.callback-path", "/callback") ?: "/callback",
                    externalUrl = config.getString("oauth.external-url", "") ?: ""
                ),
                publication = PublicationConfig(
                    autoCreate = config.getBoolean("publication.auto-create", true),
                    defaultName = config.getString("publication.default-name", "{player}'s Minecraft Books") ?: "{player}'s Minecraft Books",
                    defaultUrl = config.getString("publication.default-url", "") ?: ""
                ),
                books = BookConfig(
                    maxPages = config.getInt("books.max-pages", 100).coerceIn(1, 1000),
                    maxCharsPerPage = config.getInt("books.max-chars-per-page", 1024).coerceIn(1, 8192),
                    autoPublishOnSign = config.getBoolean("books.auto-publish-on-sign", false)
                ),
                bstatsEnabled = config.getBoolean("bstats-enabled", true)
            )
        }
    }
}
