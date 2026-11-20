# Contributing to StandardBooks

A PaperMC plugin that publishes and reads in-game books on the AT Protocol via Standard.site: sign books, publish them as documents, browse published books in a chest GUI, all through player OAuth. Thanks for wanting to help!

## Getting started

Requires PaperMC 26.1.2+ (api-version 1.21) and Java 26+; optional PlaceholderAPI. Build with the Gradle wrapper and load the JAR on a test Paper server.

## Notes for this project

- **OAuth is per-player.** The embedded callback server means testing auth changes requires a real browser round trip; use a scratch AT Protocol account and never commit your session data.
- Page and size limits in `config.yml` (`max-pages`, `max-chars-per-page`) exist because published books become records in the player's own PDS; don't remove limits, size the record to what a repo can absorb.
- Treat book content correctly per the Markdown/Standard.site conversion: players' line breaks and page breaks should survive publication and read-back.
- `/sb` commands and the chest GUI are both first-class: a publish-path change should be verified from the book in hand all the way to reading it back in-game.
- Don't store credentials server-side beyond what's needed; the plugin works on behalf of the player, not the server.

## Before opening a PR

- Build passes; you published and read back a book end-to-end on a test server with your scratch account.
- Conventional commits; focused PRs; describe the verification.
- AI-assisted PRs welcome; stand behind every line and run the round trip yourself.

## Licence

See [LICENSE](LICENSE). Contributions accepted under the same terms.