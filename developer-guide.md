# Developer Guide

This document explains the architecture so you can extend OrthodoxIcons safely.

## Package layout

```
com.orthodoxicons
├── OrthodoxIconsPlugin      Composition root / lifecycle (manual DI)
├── OrthodoxIconsApi         Public developer API (stable surface)
├── model                    Immutable domain types (Builder pattern)
│   ├── License, Icon, IconMetadata, PlacedIcon
├── config                   ConfigManager + typed PluginConfig snapshot
├── locale                   LocaleManager (message files, placeholders)
├── util                     Scheduler, Hashing, Text helpers
├── storage                  Database abstraction + repositories
│   ├── Database, SqliteDatabase
│   ├── IconRepository, PlacedIconRepository, FavoritesRepository
├── http                     HttpClientFactory, HttpService, RobotsTxtChecker
├── provider                 IconProvider + ProviderRegistry + providers
│   ├── CuratedProvider, WikimediaCommonsProvider
├── cache                    CacheManager (on-disk layout + cleanup)
├── image                    ImageProcessor (validate/resize/fit/thumbnail)
├── fetch                    IconFetchService + FetchResult
├── item                     IconItemKeys, IconMapRenderer, IconItemFactory
├── craft                    RecipeManager, CraftListener
├── placement                PlacementService + listeners
├── gui                      GuiManager, BrowserHolder, GuiSort, listeners
└── command                  IconsCommand, IconsTabCompleter
```

## Design patterns

- **Dependency Injection:** `OrthodoxIconsPlugin#onEnable` is the single
  composition root; every service receives its collaborators via constructor.
  No service reaches back into the plugin singleton except for lifecycle hooks.
- **Repository pattern:** all persistence goes through `*Repository` classes
  that own an in-memory cache for main-thread reads and serialize writes on a
  single-threaded executor.
- **Provider pattern (Open/Closed):** implement `IconProvider` and register it
  with `ProviderRegistry` (or `OrthodoxIconsApi#registerProvider`). No existing
  class needs to change.
- **Service layer:** `IconFetchService`, `PlacementService`, `HttpService`, etc.
  encapsulate business logic away from Bukkit event plumbing.
- **Factory pattern:** `IconItemFactory` builds decorated item stacks and
  memoizes map views per icon.
- **Builder pattern:** `Icon`, `IconMetadata` use fluent builders.

## Threading model

- **Main thread:** all Bukkit API calls (inventory, entities, maps, items).
- **HTTP pool:** the JDK `HttpClient` runs on a dedicated daemon executor; a
  `Semaphore` limits concurrent downloads to `max-concurrent-downloads`.
- **DB executor:** a single-threaded daemon executor serializes every SQL
  statement, so repositories are thread-safe by construction.
- **`Scheduler`** bridges Bukkit tasks to `CompletableFuture`s; use
  `scheduler.async(...)` for off-thread work and `scheduler.runSync(...)` to
  return to the main thread before touching the Bukkit API.

**Rule:** never call the Bukkit API from an async task, and never perform HTTP
or SQL on the main thread.

## Adding a new provider

```java
public final class MyProvider implements IconProvider {
    public static final String ID = "myprovider";

    @Override public String id() { return ID; }
    @Override public String displayName() { return "My Source"; }
    @Override public boolean isEnabled() { return true; }

    @Override
    public List<IconMetadata> discover() throws Exception {
        return List.of(IconMetadata.builder()
            .providerId(ID)
            .name("Christ Pantocrator")
            .saint("Jesus Christ")
            .feast("")
            .category("Christ")
            .description("Sixth-century encaustic icon.")
            .imageUrl("https://example.org/pantocrator.jpg")
            .sourceUrl("https://example.org/pantocrator")
            .attribution("Public domain")
            .license(License.PUBLIC_DOMAIN)
            .tags(List.of("christ", "sinai"))
            .build());
    }
}
```

Register it during your own plugin's enable:

```java
OrthodoxIconsPlugin oi = (OrthodoxIconsPlugin) Bukkit.getPluginManager()
        .getPlugin("OrthodoxIcons");
oi.api().registerProvider(new MyProvider());
oi.api().triggerUpdate();
```

The fetch service handles conditional downloading, hashing, dedup, image
processing, and persistence for you. Only free-licensed icons are stored.

## Storage abstraction (MySQL-readiness)

`Database` is a small interface (`initialize`, `connection`, `kind`, `close`).
`SqliteDatabase` is the shipped implementation. To add MySQL, implement
`Database` with a pooled `DataSource`, adjust the schema DDL for MySQL types,
and select it in `OrthodoxIconsPlugin#setupDatabase` based on
`storage.type`. Repositories are unchanged because they depend only on the
interface and standard JDBC.

## Rendering model

`IconItemFactory#mapIdFor` creates a persistent `MapView`, strips default
renderers, and attaches an `IconMapRenderer` that draws the cached processed
PNG onto the map canvas once per viewer. `PlacementService` spawns an
`ItemFrame` holding that map. `WorldListener` rebinds renderers on chunk load
so artwork survives restarts. This approach is fully server-side and therefore
compatible with every client version routed through Via*.
