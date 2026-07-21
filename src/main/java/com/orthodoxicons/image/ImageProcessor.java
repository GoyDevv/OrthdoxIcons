package com.orthodoxicons.image;

import com.orthodoxicons.config.PluginConfig;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;

/**
 * Pure image-processing service: validation, resizing/cropping with aspect-ratio
 * preservation, thumbnail generation and map-texture generation. Contains no
 * Bukkit dependencies so it can be unit-reasoned about in isolation and reused.
 */
public final class ImageProcessor {

    /**
     * Decodes and validates raw image bytes.
     *
     * @param data raw bytes
     * @return the decoded image
     * @throws IOException if the bytes are not a valid, non-empty image
     */
    public BufferedImage decode(byte[] data) throws IOException {
        if (data == null || data.length == 0) {
            throw new IOException("Empty image data");
        }
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(data));
        if (image == null) {
            throw new IOException("Unrecognized or corrupt image data");
        }
        if (image.getWidth() <= 0 || image.getHeight() <= 0) {
            throw new IOException("Image has invalid dimensions");
        }
        return image;
    }

    /**
     * Produces a square texture of the configured size, fitting the source
     * according to the configured {@link PluginConfig.FitMode}.
     *
     * @param source source image
     * @param config configuration snapshot
     * @return a square {@link BufferedImage}
     */
    public BufferedImage toTexture(BufferedImage source, PluginConfig config) {
        return fit(source, config.textureSize(), config.fitMode(), config.backgroundColor());
    }

    /**
     * Produces a square thumbnail of the configured thumbnail size.
     *
     * @param source source image
     * @param config configuration snapshot
     * @return a square thumbnail
     */
    public BufferedImage toThumbnail(BufferedImage source, PluginConfig config) {
        return fit(source, config.thumbnailSize(), config.fitMode(), config.backgroundColor());
    }

    /**
     * Fits a source image into a square canvas of the given size.
     *
     * @param source  source image
     * @param size    target square edge length
     * @param mode    fit mode
     * @param bgColor ARGB background color for letterboxing
     * @return the fitted square image
     */
    public BufferedImage fit(BufferedImage source, int size,
                             PluginConfig.FitMode mode, int bgColor) {
        BufferedImage canvas = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = canvas.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING,
                    RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new java.awt.Color(bgColor, true));
            g.fillRect(0, 0, size, size);

            int sw = source.getWidth();
            int sh = source.getHeight();
            switch (mode) {
                case STRETCH -> g.drawImage(source, 0, 0, size, size, null);
                case COVER -> {
                    double scale = Math.max((double) size / sw, (double) size / sh);
                    int dw = (int) Math.round(sw * scale);
                    int dh = (int) Math.round(sh * scale);
                    int dx = (size - dw) / 2;
                    int dy = (size - dh) / 2;
                    g.drawImage(source, dx, dy, dw, dh, null);
                }
                case CONTAIN -> {
                    double scale = Math.min((double) size / sw, (double) size / sh);
                    int dw = (int) Math.round(sw * scale);
                    int dh = (int) Math.round(sh * scale);
                    int dx = (size - dw) / 2;
                    int dy = (size - dh) / 2;
                    g.drawImage(source, dx, dy, dw, dh, null);
                }
            }
        } finally {
            g.dispose();
        }
        return canvas;
    }

    /**
     * Writes an image to disk as PNG atomically-adjacent (caller controls path).
     *
     * @param image image to write
     * @param file  destination file
     * @throws IOException on failure
     */
    public void writePng(BufferedImage image, File file) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Could not create directory " + parent);
        }
        if (!ImageIO.write(image, "png", file)) {
            throw new IOException("No PNG writer available");
        }
    }

    /**
     * Attempts to read a PNG from disk, returning {@code null} if the file is
     * missing or corrupt so the caller can regenerate it (corruption recovery).
     *
     * @param file file to read
     * @return the decoded image, or {@code null}
     */
    public BufferedImage readPngQuietly(File file) {
        if (file == null || !file.exists()) {
            return null;
        }
        try {
            return ImageIO.read(file);
        } catch (IOException e) {
            return null;
        }
    }
}
