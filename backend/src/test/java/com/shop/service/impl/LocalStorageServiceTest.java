package com.shop.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.shop.config.AppProperties;
import com.shop.service.StorageService;

class LocalStorageServiceTest {

    @TempDir
    Path tempDir;

    private LocalStorageService service() {
        return new LocalStorageService(new AppProperties("http://localhost:4200",
                new AppProperties.Cors(List.of("http://localhost:4200")),
                new AppProperties.Storage("local", tempDir.toString(), 5_242_880, 4000),
                new AppProperties.Stock(15), true));
    }

    @Test
    void storesUnderGeneratedKeyAndLoadsBack() throws Exception {
        LocalStorageService storage = service();

        StorageService.StoredFile stored = storage.store("products", new byte[] {1, 2, 3}, "png");

        assertThat(stored.key()).matches("products/[0-9a-f-]{36}\\.png");
        assertThat(stored.url()).isEqualTo("/media/" + stored.key());
        assertThat(Files.readAllBytes(tempDir.resolve(stored.key()))).containsExactly(1, 2, 3);
        assertThat(storage.load(stored.key())).isPresent();

        storage.delete(stored.key());
        assertThat(storage.load(stored.key())).isEmpty();
    }

    @Test
    void neverResolvesUserControlledPaths() throws Exception {
        LocalStorageService storage = service();
        Files.writeString(tempDir.resolve("secret.txt"), "secret");

        assertThat(storage.load("../secret.txt")).isEmpty();
        assertThat(storage.load("products/../../secret.txt")).isEmpty();
        assertThat(storage.load("products/not-a-uuid.png")).isEmpty();
        storage.delete("../secret.txt");
        assertThat(tempDir.resolve("secret.txt")).exists();
    }

    @Test
    void rejectsUnknownFoldersAndExtensions() {
        LocalStorageService storage = service();

        assertThatThrownBy(() -> storage.store("../etc", new byte[] {1}, "png")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> storage.store("products", new byte[] {1}, "html")).isInstanceOf(IllegalArgumentException.class);
    }
}
