package com.shop.service;

import java.util.Optional;

import org.springframework.core.io.Resource;

/**
 * Stores uploaded files under generated keys such as {@code products/<uuid>.jpg}.
 * Keys never contain user-supplied names, so path traversal is impossible.
 * Local disk is the default; a Cloudinary implementation can be added behind this interface.
 */
public interface StorageService {

    record StoredFile(String key, String url) {
    }

    StoredFile store(String folder, byte[] content, String extension);

    Optional<Resource> load(String key);

    void delete(String key);
}
