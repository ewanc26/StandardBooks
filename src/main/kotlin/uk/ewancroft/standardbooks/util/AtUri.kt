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

    fun requireDocumentFor(did: String): AtUri {
        require(collection == "site.standard.document") { "URI is not a Standard document" }
        require(this.did == did) { "URI does not belong to the authenticated identity" }
        return this
    }

    companion object {
        fun parse(uri: String): AtUri {
            require(uri.startsWith("at://")) { "Invalid AT-URI: must start with at://" }
            val parts = uri.removePrefix("at://").split("/", limit = 3)
            require(parts.size == 3) { "Invalid AT-URI: expected at://<did>/<collection>/<rkey>" }
            require(parts.all { it.isNotEmpty() && it != "." && it != ".." && !it.contains('%') }) {
                "Invalid AT-URI: components must be non-empty and unencoded"
            }
            require(parts[0].startsWith("did:") && parts[0].count { it == ':' } >= 1) {
                "Invalid AT-URI: invalid DID"
            }
            require(parts[1].matches(Regex("[A-Za-z0-9-]+\\.[A-Za-z0-9-]+(?:\\.[A-Za-z0-9-]+)*"))) {
                "Invalid AT-URI: invalid collection"
            }
            require(parts[2].matches(Regex("[A-Za-z0-9._~-]+"))) {
                "Invalid AT-URI: invalid record key"
            }
            return AtUri(parts[0], parts[1], parts[2])
        }

        fun tryParse(uri: String): AtUri? = try {
            parse(uri)
        } catch (e: IllegalArgumentException) {
            null
        }
    }
}
