package uk.ewancroft.standardbooks.util

/**
 * Parses and represents an AT Protocol URI: at://<did>/<collection>/<rkey>
 */
data class AtUri(
    val did: String,
    val collection: String,
    val rkey: String
) {
    val uri: String get() = "at://$did/$collection/$rkey"

    companion object {
        fun parse(uri: String): AtUri {
            require(uri.startsWith("at://")) { "Invalid AT-URI: must start with at://" }
            val parts = uri.removePrefix("at://").split("/", limit = 3)
            require(parts.size == 3) { "Invalid AT-URI: expected at://<did>/<collection>/<rkey>" }
            return AtUri(parts[0], parts[1], parts[2])
        }

        fun tryParse(uri: String): AtUri? = try {
            parse(uri)
        } catch (e: IllegalArgumentException) {
            null
        }
    }
}
