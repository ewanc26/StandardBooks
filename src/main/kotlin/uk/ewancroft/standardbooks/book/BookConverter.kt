package uk.ewancroft.standardbooks.book

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.inventory.meta.BookMeta
import uk.ewancroft.standardbooks.atproto.model.StandardBooksContent
import uk.ewancroft.standardbooks.atproto.model.StandardDocument
import uk.ewancroft.standardbooks.config.PluginConfig
import java.time.Instant
import java.time.format.DateTimeFormatter

/**
 * Converts between Minecraft written books and Standard.site document records.
 */
class BookConverter(private val config: PluginConfig) {

    /**
     * Converts a Minecraft BookMeta to a Standard.site document.
     * The player's DID is used as the contributor.
     */
    fun toDocument(
        bookMeta: BookMeta,
        playerDid: String,
        playerHandle: String,
        siteUrl: String
    ): StandardDocument {
        val pages = extractPages(bookMeta)
        val title = bookMeta.title()?.let { PlainTextComponentSerializer.plainText().serialize(it) } ?: "Untitled"
        val publishedAt = DateTimeFormatter.ISO_INSTANT.format(Instant.now())

        val textContent = pages.joinToString("\n\n---\n\n")

        val content = StandardBooksContent(
            pages = pages,
            version = 1
        )

        val contributor = StandardDocument.Contributor(
            did = playerDid,
            role = "author",
            displayName = playerHandle
        )

        return StandardDocument(
            site = siteUrl,
            title = title,
            publishedAt = publishedAt,
            content = content,
            textContent = textContent,
            contributors = listOf(contributor),
            tags = listOf("minecraft", "standardbooks")
        )
    }

    /**
     * Extracts page text from a BookMeta, respecting extended limits.
     */
    private fun extractPages(bookMeta: BookMeta): List<String> {
        val pages = bookMeta.pages()
        val serializer = PlainTextComponentSerializer.plainText()
        return pages.map { page ->
            serializer.serialize(page)
        }.filter { it.isNotBlank() }
    }

    /**
     * Validates a book against the configured limits.
     * Returns null if valid, or an error message if invalid.
     */
    fun validateBook(bookMeta: BookMeta): String? {
        val pages = bookMeta.pages() ?: return "Book has no pages"

        if (pages.isEmpty()) return "Book is empty"

        if (pages.size > config.books.maxPages) {
            return "Book has ${pages.size} pages, but the maximum is ${config.books.maxPages}"
        }

        val serializer = PlainTextComponentSerializer.plainText()
        for ((index, page) in pages.withIndex()) {
            val text = serializer.serialize(page)
            if (text.length > config.books.maxCharsPerPage) {
                return "Page ${index + 1} has ${text.length} characters, but the maximum is ${config.books.maxCharsPerPage}"
            }
        }

        return null
    }

    /**
     * Converts a Standard.site document back to a list of page text strings.
     * If the document has StandardBooksContent, uses the structured pages.
     * Otherwise, splits textContent by the delimiter or by length.
     */
    fun toPages(document: StandardDocument): List<String> {
        // If we have structured content, use it directly
        document.content?.let { content ->
            if (content.pages.isNotEmpty()) {
                return content.pages
            }
        }

        // Fall back to textContent
        val text = document.textContent ?: return listOf(document.title)

        // Try splitting by the delimiter
        val delimiter = "\n\n---\n\n"
        if (text.contains(delimiter)) {
            return text.split(delimiter)
        }

        // Fall back to splitting by length
        val maxCharsPerPage = config.books.maxCharsPerPage
        return text.chunked(maxCharsPerPage)
    }
}
