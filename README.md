# StandardBooks

A PaperMC plugin that publishes and reads in-game books on the AT Protocol via Standard.site.

---

## Requirements

- PaperMC 26.1.2+ (api-version 1.21)
- Java 26+
- Optional: [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/) for placeholder expansion

---

## Installation

1. Download the latest release JAR from the [releases page](https://github.com/ewanc26/StandardBooks/releases)
2. Place the JAR in your server's `plugins/` folder
3. Restart the server
4. Edit `plugins/StandardBooks/config.yml` to configure the OAuth callback server
5. Run `/standardbooks reload` to apply changes

---

## Configuration

`plugins/StandardBooks/config.yml`:

```yaml
enabled: true

oauth:
  port: 8765
  callback-path: /callback
  external-url: "" # e.g. https://mc.example.com:8765

publication:
  auto-create: true
  default-name: "{player}'s Minecraft Books"
  default-url: ""

books:
  max-pages: 100
  max-chars-per-page: 1024
  auto-publish-on-sign: false

bstats-enabled: true
```

### OAuth Setup

The plugin runs an embedded HTTP server to receive OAuth callbacks. The `external-url` must be publicly accessible from the player's browser. If left blank, `http://localhost:{port}` is used (works only for single-player or local servers).

For production servers, set `external-url` to your server's public address and ensure the port is forwarded or tunnelled.

---

## Commands

| Command | Permission | Description |
|---|---|---|
| `/sb login <handle>` | `standardbooks.use` | Connect your AT Protocol account via OAuth |
| `/sb logout` | `standardbooks.use` | Disconnect your account |
| `/sb publish` | `standardbooks.use` | Publish the book you're holding to AT Protocol |
| `/sb browse [page]` | `standardbooks.use` | Browse published books in a chest GUI |
| `/sb read <uri>` | `standardbooks.use` | Read a book by its AT URI |
| `/sb list` | `standardbooks.use` | List your published books |
| `/sb delete <uri>` | `standardbooks.use` | Delete a book you've published |
| `/sb update <uri>` | `standardbooks.use` | Update a published book with the one you're holding |
| `/sb status` | `standardbooks.use` | Show your connection status |
| `/sb help` | `standardbooks.use` | Show help |
| `/sb reload` | `standardbooks.admin` | Reload config |

---

## How It Works

### Publishing

1. Player writes a book in-game (book & quill, signed)
2. Player runs `/sb publish` while holding the book
3. The plugin converts the book to a `site.standard.document` record:
   - Book title → document `title`
   - Book pages → `content` (structured `site.standard.content.standardbooks` format)
   - Book text → `textContent` (plain text with `---` page delimiters)
   - Player identity → `contributors` with role `author`
   - Tags: `minecraft`, `standardbooks`
4. The record is published to the player's PDS via `com.atproto.repo.createRecord`

### Reading

1. Player runs `/sb browse` to open a chest GUI
2. Book items show title and author
3. Clicking a book opens it for reading in-game
4. Alternatively, `/sb read <at-uri>` reads a specific book

### Interoperability

StandardBooks reuses the `site.standard.document` lexicon directly. Books published from Minecraft appear in [Inkwell](https://github.com/ewanc26/inkwell) on iOS, and books written in Inkwell appear in Minecraft. The `textContent` field ensures plain-text fallback for any Standard.site client.

### Extended Book Limits

Vanilla Minecraft limits books to 50 pages, 256 characters per page. StandardBooks extends these limits (configurable, defaults to 100 pages, 1024 chars/page) since the plugin operates server-side and is not bound by vanilla constraints.

---

## Placeholders

| Placeholder | Description |
|---|---|
| `%standardbooks_logged_in%` | `true` if the player is logged in, `false` otherwise |
| `%standardbooks_handle%` | Player's AT Protocol handle, or `none` |
| `%standardbooks_did%` | Player's DID, or `none` |

---

## Build

```bash
./gradlew build
```

Output: `build/libs/StandardBooks-0.1.0.jar`

---

## Tech Stack

- Kotlin 2.3.21
- PaperMC API 26.1.2
- atproto-kotlin SDK (runtime, models, oauth)
- Ktor 3.1.2 (embedded OAuth callback server)
- kotlinx.serialization 1.7.3
- Shadow plugin for shaded JAR
- MiniMessage for text formatting
- bStats for anonymous metrics

---

## Support

If you find this project useful, consider supporting its development:

[![Ko-fi](https://img.shields.io/badge/Ko--fi-F16061?style=for-the-badge&logo=ko-fi&logoColor=white)](https://ko-fi.com/ewancroft)
[![GitHub Sponsors](https://img.shields.io/badge/GitHub%20Sponsors-30363D?style=for-the-badge&logo=github&logoColor=white)](https://github.com/sponsors/ewanc26)

## License

AGPL-3.0-only
