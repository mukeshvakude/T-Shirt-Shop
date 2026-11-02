package com.shop.util;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import com.shop.exception.BadRequestException;

/**
 * Validates uploaded images by content, not by file name or browser-supplied type:
 * magic bytes (PNG or JPEG), byte size, pixel dimensions (checked before decoding,
 * to stop decompression bombs) and a full decode to prove the file is a real image.
 */
public final class ImageValidator {

    public enum ImageType {
        PNG("png", "image/png"),
        JPEG("jpg", "image/jpeg");

        private final String extension;
        private final String mimeType;

        ImageType(String extension, String mimeType) {
            this.extension = extension;
            this.mimeType = mimeType;
        }

        public String extension() {
            return extension;
        }

        public String mimeType() {
            return mimeType;
        }
    }

    public record ValidatedImage(ImageType type, int width, int height, byte[] content) {
    }

    private static final byte[] PNG_MAGIC = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] JPEG_MAGIC = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};

    private ImageValidator() {
    }

    public static ValidatedImage validate(byte[] content, long maxBytes, int maxDimension) {
        if (content == null || content.length == 0) {
            throw new BadRequestException("IMAGE_EMPTY", "The uploaded file is empty.");
        }
        if (content.length > maxBytes) {
            throw new BadRequestException("IMAGE_TOO_LARGE",
                    "Images must be at most " + (maxBytes / (1024 * 1024)) + " MB.");
        }
        ImageType type = detectType(content);
        if (type == null) {
            throw new BadRequestException("IMAGE_TYPE_NOT_ALLOWED", "Only PNG and JPG images are allowed.");
        }

        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(content))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw invalid();
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width < 1 || height < 1 || width > maxDimension || height > maxDimension) {
                    throw new BadRequestException("IMAGE_DIMENSIONS_NOT_ALLOWED",
                            "Images must be at most " + maxDimension + " x " + maxDimension + " pixels.");
                }
                BufferedImage decoded = reader.read(0);
                if (decoded == null) {
                    throw invalid();
                }
                return new ValidatedImage(type, width, height, content);
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException ex) {
            if (ex instanceof BadRequestException bad) {
                throw bad;
            }
            throw invalid();
        }
    }

    static ImageType detectType(byte[] content) {
        if (startsWith(content, PNG_MAGIC)) {
            return ImageType.PNG;
        }
        if (startsWith(content, JPEG_MAGIC)) {
            return ImageType.JPEG;
        }
        return null;
    }

    private static boolean startsWith(byte[] content, byte[] prefix) {
        if (content.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (content[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private static BadRequestException invalid() {
        return new BadRequestException("IMAGE_INVALID", "The file is not a valid image.");
    }
}
