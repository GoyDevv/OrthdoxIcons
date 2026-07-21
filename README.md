# OrthodoxIcons

A production-quality Paper plugin that automatically downloads authentic Eastern
Orthodox icons from trusted, freely-licensed online sources, caches them locally,
turns them into obtainable in-game items, and lets players **craft, browse, and
place** them as decorative wall icons.

- **Target API:** Paper 1.20.1 (Java 17)
- **Runs on:** Paper, Spigot, Purpur
- **Client versions:** Minecraft Java 1.20.1 through 26.2 via the Via* ecosystem
  (ViaVersion / ViaBackwards / ViaRewind) — no client-version-specific code.

---

## How it works

Vanilla Minecraft cannot inject arbitrary block/item textures at runtime without
a resource pack. To stay **100% version-neutral and resource-pack-free**,
OrthodoxIcons renders each icon onto a **128×128 filled map** using a custom
`MapRenderer`, and "placing" an icon spawns an **item frame holding that map** on
the target wall. Maps and item frames are rendered server-side and translated
automatically by Via*, so every supported client sees the artwork identically.

```
Provider (metadata) ──▶ Fetch service ──▶ Cache (images/processed/thumbnails)
                                   │
                                   ▼
                            SQLite storage
                                   │
           ┌───────────────────────┼────────────────────────┐
           ▼                       ▼                        ▼
     Item factory            GUI browser              Placement service
     (filled map)          (search/filter)          (item-frame maps)
```

---

## Features

- **Provider system** (Open/Closed): add new sources without touching existing
  code. Ships with a bundled curated public-domain manifest and a Wikimedia
  Commons crawler that filters by license.
- **Smart fetching:** ETag + Last-Modified conditional downloads, SHA-256 hash
  verification, duplicate skipping, retries with exponential backoff, image
  validation, and corrupted-file recovery. Nothing unchanged is re-downloaded.
- **Cache** under `plugins/OrthodoxIcons/cache/{images,metadata,processed,thumbnails}`
  with automatic orphan cleanup and size enforcement.
- **Icon items** with display name, rich lore (saint, feast, source, provider,
  description) and a unique id stored in the `PersistentDataContainer`.
- **Crafting:** configurable shaped recipe for a "blank icon panel" that opens
  the browser; reloadable without a restart.
- **Placement:** wall orientation, rotation, center alignment, persistent across
  restarts, breakable (drops the original item), protection-plugin aware, and
  collision-safe.
- **Modern GUI:** pagination, live artwork previews, search (chat-capture
  keyboard input), category cycling, favorites, random, and cycleable sorting.
- **Commands & permissions** for every operation.
- **Fully configurable**, **localizable**, and backed by an **abstract storage
  layer** (SQLite today, MySQL-ready).
- **Performance:** all HTTP is off-thread on a pooled JDK `HttpClient` with a
  concurrency limiter; reads are served from in-memory caches; DB writes are
  serialized on a single-threaded executor.

---

## Quick start

1. Drop `OrthodoxIcons.jar` into your server's `plugins/` folder.
2. Start the server once to generate `config.yml`, `messages.yml`,
   `lang/en.yml`, and `curated-icons.json`.
3. (Optional) Enable the Wikimedia provider and tune categories in `config.yml`.
4. Run `/icons update` to fetch, then `/icons browse` to open the gallery.

See [INSTALLATION.md](INSTALLATION.md) for the full guide,
[permissions.md](permissions.md) for the permission reference,
[developer-guide.md](developer-guide.md) for architecture, and
[API.md](API.md) for the developer API.

---

## Building from source

```bash
./gradlew clean shadowJar
```

The shaded, relocated jar is written to `build/libs/OrthodoxIcons.jar`.
SQLite JDBC and Gson are relocated under `com.orthodoxicons.lib.*` to avoid
classloader conflicts with other plugins.

---

## Licensing note

The plugin only persists icons whose metadata reports a **free / public-domain**
license, respects `robots.txt` for every request, and records attribution,
license, and source URL for each icon so you can credit the original works.
