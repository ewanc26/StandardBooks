package uk.ewancroft.standardbooks.atproto.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * StandardBooks content format for site.standard.document.content.
 *
 * Stores book pages as a structured list so page boundaries are preserved
 * when reading back. Also stored in textContent as plain text for interoperability
 * with other Standard.site clients like Inkwell.
 */
@Serializable
data class StandardBooksContent(
    @SerialName("\$type")
    val type: String = "site.standard.content.standardbooks",
    val pages: List<String>,
    val version: Int = 1
)

/**
 * A site.standard.document record.
 *
 * @see <a href="https://github.com/standard-site/spec">Standard.site spec</a>
 */
@Serializable
data class StandardDocument(
    val site: String,
    val title: String,
    @SerialName("publishedAt")
    val publishedAt: String,
    val path: String? = null,
    val description: String? = null,
    val content: StandardBooksContent? = null,
    @SerialName("textContent")
    val textContent: String? = null,
    val tags: List<String>? = null,
    val contributors: List<Contributor>? = null,
    @SerialName("updatedAt")
    val updatedAt: String? = null,
    @SerialName("coverImage")
    val coverImage: JsonElement? = null,
    @SerialName("bskyPostRef")
    val bskyPostRef: JsonElement? = null,
    val links: JsonElement? = null,
    val labels: JsonElement? = null
) {
    @Serializable
    data class Contributor(
        val did: String,
        val role: String? = null,
        val displayName: String? = null
    )
}

/**
 * A site.standard.publication record.
 */
@Serializable
data class StandardPublication(
    val name: String,
    val url: String,
    val description: String? = null,
    val icon: JsonElement? = null,
    val preferences: Preferences? = null
) {
    @Serializable
    data class Preferences(
        @SerialName("showInDiscover")
        val showInDiscover: Boolean? = null
    )
}

/**
 * A site.standard.graph.recommend record.
 */
@Serializable
data class StandardRecommend(
    val document: String,
    @SerialName("createdAt")
    val createdAt: String
)

/**
 * A site.standard.graph.subscription record.
 */
@Serializable
data class StandardSubscription(
    val publication: String,
    @SerialName("createdAt")
    val createdAt: String? = null
)
