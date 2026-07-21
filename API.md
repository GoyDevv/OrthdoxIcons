# API Documentation

OrthodoxIcons exposes a small, stable API for other plugins via the
`com.orthodoxicons.OrthodoxIconsApi` interface, obtained from the main plugin
instance.

## Obtaining the API

```java
OrthodoxIconsPlugin plugin = (OrthodoxIconsPlugin) Bukkit.getPluginManager()
        .getPlugin("OrthodoxIcons");
if (plugin == null || !plugin.isEnabled()) {
    // OrthodoxIcons is not installed / not enabled.
    return;
}
OrthodoxIconsApi api = plugin.api();
```

Add OrthodoxIcons as a `softdepend` (or `depend`) in your own `plugin.yml` so it
loads first:

```yaml
softdepend: [OrthodoxIcons]
```

## Interface reference

### `List<Icon> allIcons()`
Returns an immutable snapshot of all stored icons. Safe on the main thread
(served from the in-memory cache).

### `Optional<Icon> icon(UUID id)`
Looks up a single icon by its stable id.

### `List<Icon> search(String query)`
Case-insensitive substring search across name, saint, feast, category, and tags.

### `ItemStack createItem(Icon icon, int amount)`
Builds a fully-decorated, PDC-tagged icon item (a filled map by default). Must
be called on the main thread because it may create a `MapView`.

### `void giveIcon(Player player, Icon icon, int amount)`
Gives the icon item to a player, dropping any overflow at their feet.

### `Optional<UUID> readIconId(ItemStack item)`
Reads the icon id stored in an item's `PersistentDataContainer`; empty if the
item is not an OrthodoxIcons item.

### `void registerProvider(IconProvider provider)`
Registers a new icon source at runtime. See the developer guide for the
`IconProvider` contract. Idempotent per provider id (re-registering replaces).

### `CompletableFuture<FetchResult> triggerUpdate()`
Starts an asynchronous fetch/update pass across enabled providers and completes
with a `FetchResult` (`added`, `updated`, `skipped`, `failed`). If a pass is
already running, completes immediately with an empty result.

## Domain types

### `Icon`
Immutable value object. Key accessors: `id()`, `name()`, `saint()`, `feast()`,
`category()`, `description()`, `imageUrl()`, `sourceUrl()`, `attribution()`,
`license()`, `tags()`, `providerId()`, `imageHash()`, `dateAdded()`.

### `IconMetadata`
Provider-supplied description of an icon before it is downloaded. Built with
`IconMetadata.builder()`.

### `License`
Enum of recognized licenses with `isFree()` and `fromLabel(String)` for lenient
parsing of provider license strings.

### `FetchResult`
Immutable counters: `added()`, `updated()`, `skipped()`, `failed()`.

## Threading contract

- Read methods (`allIcons`, `icon`, `search`, `readIconId`) are safe from any
  thread.
- `createItem` and `giveIcon` must run on the main server thread.
- `triggerUpdate` may be called from any thread and performs its work
  asynchronously; observe completion with `whenComplete` and hop back to the
  main thread (e.g. via the scheduler) before touching the Bukkit API.

## Stability

The `OrthodoxIconsApi` interface and the domain accessors listed above are the
supported surface. Internal service classes may change between releases; depend
only on the API interface and the `model` package.
