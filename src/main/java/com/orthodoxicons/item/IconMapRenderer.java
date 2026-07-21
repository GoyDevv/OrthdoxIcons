package com.orthodoxicons.item;

import com.orthodoxicons.cache.CacheManager;
import com.orthodoxicons.image.ImageProcessor;
import org.bukkit.entity.Player;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapPalette;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;

import java.awt.image.BufferedImage;
import java.util.UUID;

/**
 * Renders a cached, processed icon texture onto a Minecraft map exactly once per
 * map view. Rendering the image to the 128x128 map canvas is the version-neutral
 * way to display arbitrary artwork on all clients (including those connecting
 * through ViaVersion / ViaBackwards / ViaRewind), because maps are part of
 * vanilla data and require no resource pack or version-specific packets.
 */
public final class IconMapRenderer extends MapRenderer {

    private final UUID iconId;
    private final CacheManager cache;
    private final ImageProcessor images;
    private volatile boolean rendered;

    /**
     * @param iconId the icon to render
     * @param cache  cache manager providing the processed texture
     * @param images image processor for decoding / letterboxing
     */
    public IconMapRenderer(UUID iconId, CacheManager cache, ImageProcessor images) {
        super(false); // non-contextual: same image for every player
        this.iconId = iconId;
        this.cache = cache;
        this.images = images;
    }

    @Override
    public void render(MapView map, MapCanvas canvas, Player player) {
        if (rendered) {
            return;
        }
        BufferedImage texture = images.readPngQuietly(cache.processedFile(iconId));
        if (texture == null) {
            // No texture available yet; leave the canvas blank and retry later.
            return;
        }
        BufferedImage sized = texture.getWidth() == 128 && texture.getHeight() == 128
                ? texture
                : MapPalette.resizeImage(texture);
        canvas.drawImage(0, 0, sized);
        rendered = true;
    }

    /**
     * Forces the next {@link #render} call to redraw (used after a texture is
     * regenerated).
     */
    public void invalidate() {
        this.rendered = false;
    }

    /** @return the icon id this renderer draws */
    public UUID iconId() {
        return iconId;
    }
}
