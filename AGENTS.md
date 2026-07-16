# AGENTS.md

Guidance for agents working on StandardBooks, a Kotlin PaperMC plugin that publishes Minecraft books as `standard.site` AT Protocol records.

## Architecture and contracts

- `src/main/kotlin/` contains commands, OAuth/PDS integration, book conversion, publication management, and optional PlaceholderAPI support.
- `src/main/resources/plugin.yml` and default configuration define commands, permissions, callback settings, and user-facing defaults.
- AT Protocol identifiers and records are compatibility contracts: preserve DIDs, AT URIs, rkeys, CIDs, facets, blobs, and open-union content.
- Minecraft books have page and component limits. Extended-book behavior must remain bounded and round-trip predictably.

## Development rules

- Keep app passwords, OAuth tokens, private keys, and authorization headers out of logs and persistent plaintext unless the existing secure store explicitly owns them.
- Perform HTTP/OAuth work asynchronously; access Paper player, inventory, and world APIs on the server thread.
- Preserve interoperability with other `standard.site` publishers rather than relying on private fields.
- Optional PlaceholderAPI behavior must degrade cleanly when the plugin is absent.
- Add configuration keys with backward-compatible defaults and reload behavior.

## Validation

Run `./gradlew build`. On a test Paper server verify install, reload, OAuth start/callback/state rejection, publication auto-creation, publish/read round trips, Unicode, formatting, long books, missing permissions, expired sessions, PDS errors, and optional PlaceholderAPI absence. Never use production credentials in tests or commit generated server data.
