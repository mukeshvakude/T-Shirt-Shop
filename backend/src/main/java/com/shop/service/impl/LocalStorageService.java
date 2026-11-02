package com.shop.service.impl;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import com.shop.config.AppProperties;
import com.shop.service.StorageService;

/**
 * Stores files on local disk under {@code app.storage.local-dir}. Files are served
 * by MediaController at {@code /media/<key>}.
 */
@Service
public class LocalStorageService implements StorageService {

    private static final Logger log = LoggerFactory.getLogger(LocalStorageService.class);
    public static final String URL_PREFIX = "/media/";
    private static final Set<String> FOLDERS = Set.of("products", "categories", "designs", "previews");
    private static final Set<String> EXTENSIONS = Set.of("jpg", "png");
    /** folder/uuid.ext only: no dots, slashes or user text elsewhere. */
    static final Pattern KEY = Pattern.compile("^(products|categories|designs|previews)/[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}\\.(jpg|png)$");

    private final Path root;

    public LocalStorageService(AppProperties properties) {
        this.root = Path.of(properties.storage().localDir()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException ex) {
            throw new UncheckedIOException("Cannot create storage directory " + root, ex);
        }
        log.info("Local file storage at {}", root);
    }

    @Override
    public StoredFile store(String folder, byte[] content, String extension) {
        if (!FOLDERS.contains(folder) || !EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("Unsupported storage target " + folder + "/" + extension);
        }
        String key = folder + "/" + UUID.randomUUID() + "." + extension;
        Path target = resolve(key);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, content, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (IOException ex) {
            throw new UncheckedIOException("Could not store file " + key, ex);
        }
        return new StoredFile(key, URL_PREFIX + key);
    }

    @Override
    public Optional<Resource> load(String key) {
        if (!isValidKey(key)) {
            return Optional.empty();
        }
        Path path = resolve(key);
        return Files.isRegularFile(path) ? Optional.of(new FileSystemResource(path)) : Optional.empty();
    }

    @Override
    public void delete(String key) {
        if (!isValidKey(key)) {
            return;
        }
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException ex) {
            log.warn("Could not delete stored file {}: {}", key, ex.getMessage());
        }
    }

    public static boolean isValidKey(String key) {
        return key != null && KEY.matcher(key).matches();
    }

    private Path resolve(String key) {
        Path path = root.resolve(key).normalize();
        if (!path.startsWith(root)) {
            throw new IllegalArgumentException("Invalid storage key");
        }
        return path;
    }
}
