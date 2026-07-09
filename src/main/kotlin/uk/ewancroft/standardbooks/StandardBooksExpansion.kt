package uk.ewancroft.standardbooks

import me.clip.placeholderapi.expansion.PlaceholderExpansion
import org.bukkit.entity.Player

/**
 * PlaceholderAPI integration for StandardBooks.
 *
 * Placeholders:
 * - %standardbooks_logged_in% — true/false
 * - %standardbooks_handle% — player's AT Protocol handle, or "none"
 * - %standardbooks_did% — player's DID, or "none"
 */
class StandardBooksExpansion(private val plugin: StandardBooksPlugin) : PlaceholderExpansion() {

    override fun getIdentifier(): String = "standardbooks"
    override fun getAuthor(): String = "ewanc26"
    override fun getVersion(): String = plugin.description.version
    override fun persist(): Boolean = true

    override fun onPlaceholderRequest(player: Player?, params: String): String? {
        if (player == null) return null
        val session = plugin.sessionStore.get(player.uniqueId.toString()) ?: return when (params) {
            "logged_in" -> "false"
            "handle" -> "none"
            "did" -> "none"
            else -> null
        }

        return when (params) {
            "logged_in" -> "true"
            "handle" -> session.handle
            "did" -> session.did
            else -> null
        }
    }
}
