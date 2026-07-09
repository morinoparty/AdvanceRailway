# AdvanceRailway

AdvanceRailway is a [Paper](https://papermc.io/)/Bukkit plugin that lets you build and manage in-game railway
networks: stations, railway lines, and groups of lines. It renders the network on the
[squaremap](https://github.com/jpenilla/squaremap) web map and, when the MineAuth plugin is present,
exposes a read-only HTTP API for querying stations, railways, and groups as JSON.

## Features

- Define stations and railways with commands, stored as JSON under the plugin's data folder.
- Group railway lines together (e.g. by line color/name) via `GroupData`.
- Draw stations and railway lines as markers on the squaremap live map (optional soft dependency).
- Optional MineAuth integration that publishes `/api/v1/plugins/advancerailway/` HTTP endpoints for
  stations, railways, and groups (list + get-by-id). See the docs site for the full API reference.

## Building

AdvanceRailway targets Java 25 and is built with Gradle.

```bash
./gradlew build
```

This compiles the plugin, runs [detekt](https://detekt.dev/) static analysis, runs the test suite, and
produces a shaded jar under `build/libs/`.

To run a local Paper test server with the plugin loaded:

```bash
./gradlew runServer
```

## Documentation

Full documentation (setup, commands, usage, and the MineAuth HTTP API) is published at
<https://advance-railway.nikomaru.page>, built from the [`docs/`](docs) directory with
[MkDocs Material](https://squidfunk.github.io/mkdocs-material/).

## License

See the repository for license details.
