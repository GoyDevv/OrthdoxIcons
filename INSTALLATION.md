# Installation Guide

## Requirements

- **Server:** Paper, Spigot, or Purpur for Minecraft **1.20.1**.
- **Java:** 17 or newer.
- **Optional:** ViaVersion / ViaBackwards / ViaRewind to allow clients on other
  versions (1.20.1 – 26.2) to connect. OrthodoxIcons detects them automatically
  and needs no configuration to work with them.
- **Optional:** WorldGuard, GriefPrevention, Lands, or Towny for build
  protection. Detected automatically; the plugin never hard-depends on them.

## Steps

1. **Install the jar.** Copy `OrthodoxIcons.jar` into `plugins/`.
2. **First start.** Launch the server. The plugin creates:
   ```
   plugins/OrthodoxIcons/
     config.yml
     messages.yml
     lang/en.yml
     curated-icons.json
     cache/{images,metadata,processed,thumbnails}/
     orthodoxicons.db
   ```
3. **Fetch icons.** With `fetch-on-startup: true` (default) the curated set is
   downloaded automatically. Otherwise run `/icons update`.
4. **Browse.** Run `/icons` or `/icons browse` to open the gallery.
5. **Obtain & place.** Click an icon to receive the item, then right-click a
   wall to place it. Break it to recover the item.

## Enabling the Wikimedia Commons provider

Edit `config.yml`:

```yaml
providers:
  wikimedia:
    enabled: true
    categories:
      - "Icons of Jesus Christ"
      - "Icons of Mary (Mother of Jesus)"
    allowed-licenses:
      - "public domain"
      - "cc0"
      - "cc-by"
      - "cc-by-sa"
```

Then run `/icons reload` followed by `/icons update`.

## Upgrading

Replace the jar and restart. The SQLite schema uses `CREATE TABLE IF NOT EXISTS`
and `ON CONFLICT` upserts, so existing data and cached images are preserved.

## Uninstalling

Remove the jar. To purge data, delete the `plugins/OrthodoxIcons/` folder. Any
placed item frames become ordinary (empty) frames once the plugin is gone.

## Troubleshooting

- **No icons appear:** run `/icons update`, then `/icons stats`. Check the
  console for provider warnings, and enable `/icons debug` for verbose logs.
- **Downloads blocked:** a source's `robots.txt` may disallow crawling, or the
  license may not be in your allow-list. Both are respected by design.
- **Artwork missing after restart:** map data is rebound on chunk load; ensure
  the chunk containing the frame has loaded at least once.
