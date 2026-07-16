# AGENTS.md

Guidance for agents working on StandardBooks, a Kotlin/Paper plugin intended to exchange Minecraft books as `site.standard.document` AT Protocol records.

## Repository map

- `StandardBooksPlugin.kt` wires configuration, messages, encrypted session storage, OAuth callback HTTP server, commands, listeners, optional PlaceholderAPI, and nominal bStats setup.
- `auth/` contains identity discovery, an OAuth flow sketch, callback state routing, and AES-GCM session persistence.
- `atproto/AtProtoClient.kt` makes direct Ktor XRPC calls and caches document lookups; `atproto/model/` defines publication/document/content JSON.
- `book/` converts plain Adventure text to/from structured pages; `gui/` and `listener/` implement the 54-slot browser and sign/click events; `command/` owns all `/sb` actions.
- `config.yml`, `messages.yml`, `paper-plugin.yml`, and `plugin.yml` are shipped resources. Tests cover models, store encryption, conversion, configuration, pagination, and AT-URI parsing, but not a live Paper lifecycle or OAuth/XRPC flow.

## Current implementation gaps

- Treat the advertised OAuth support as unfinished. It generates 32 random bytes rather than a usable asymmetric DPoP key, does not produce DPoP proofs, skips modern PAR/client-metadata requirements, and posts token parameters as JSON. Do not claim interoperable AT Protocol OAuth without validating against the current specification and a real compatible PDS.
- `SessionStore` encrypts tokens, but stores the AES key alongside ciphertext in the plugin data directory and only calls Java's best-effort `setReadOnly`. This protects against casual plaintext inspection, not compromise of that directory. Corrupt/decryption-failing state is silently deleted.
- Coroutine work uses `Dispatchers.IO`, then switches to `Dispatchers.Main`; this project includes no JVM Main dispatcher implementation and does not use Paper's scheduler. Bukkit/Paper player, inventory, and messaging calls must be scheduled on the server thread, and plugin-owned jobs must be cancelled on disable.
- Reload only replaces `plugin.config` and messages. Existing `AuthManager`, callback server port/path, browser, converter, and renderer instances retain old configuration; a real reload needs coordinated reconstruction or an explicit restart requirement.
- When `enabled: false`, `onEnable` returns before most `lateinit` fields are initialized, while `onDisable` still accesses them. Verify disabled lifecycle before relying on the toggle.
- `messages.yml` currently ends the `update.failure` value with an extra quote and must be parsed on a real server. Both Paper and legacy plugin descriptors are bundled; keep command/dependency declarations compatible with the actual loader rather than assuming both are merged.
- The bStats plugin ID is the placeholder `0`. Auto-publish-on-sign checks undeclared permission `standardbooks.publish` and creates a document without the command path's get-or-create-publication step.
- Extended pages are plain text only. Rendering does not clamp remote documents to configured page/count limits, and Minecraft client/server serialization may still enforce limits despite the README claim.

## AT Protocol and safety rules

- Preserve DIDs, AT URIs, rkeys, CIDs, record collection names, unknown JSON fields where models permit them, and publication/document interoperability with other Standard.site clients.
- Validate an AT URI's DID, exact collection, and rkey before update/delete; the current parser only checks the three slash-separated components.
- Never log or commit access/refresh tokens, DPoP material, session keys, app passwords, callback codes, `.env`, or generated plugin data. Do not expose callback errors or upstream bodies directly to players without sanitizing them.
- Network and crypto work stays off the Paper thread; all Bukkit/Paper API access returns through the Paper scheduler, not a generic coroutine Main dispatcher.

## Validation

Use Java 26; Gradle compiles/shades for JVM 25. Run `./gradlew clean test shadowJar` (or `./gradlew build`) and inspect the shaded JAR/resource YAML. On a disposable Paper 26.1.2+ server verify enabled and disabled lifecycle, command registration/permissions, clean shutdown, reload semantics, PlaceholderAPI absence/presence, GUI navigation, Unicode and oversized remote books, and no async Bukkit calls. OAuth and write validation require a dedicated account/PDS and current protocol traces; a successful unit build is not evidence that login or publishing works.
