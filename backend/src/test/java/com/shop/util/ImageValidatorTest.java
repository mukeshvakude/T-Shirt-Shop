package com.shop.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

import com.shop.exception.BadRequestException;
import com.shop.support.TestImages;

class ImageValidatorTest {

    private static final long MAX_BYTES = 5L * 1024 * 1024;
    private static final int MAX_DIM = 4000;

    @Test
    void acceptsRealPngAndJpeg() {
        ImageValidator.ValidatedImage png = ImageValidator.validate(TestImages.png(40, 30), MAX_BYTES, MAX_DIM);
        assertThat(png.type()).isEqualTo(ImageValidator.ImageType.PNG);
        assertThat(png.width()).isEqualTo(40);
        assertThat(png.height()).isEqualTo(30);

        assertThat(ImageValidator.validate(TestImages.jpeg(20, 20), MAX_BYTES, MAX_DIM).type())
                .isEqualTo(ImageValidator.ImageType.JPEG);
    }

    @Test
    void rejectsNonImagesRegardlessOfName() {
        byte[] html = "<html><script>alert(1)</script></html>".getBytes(StandardCharsets.UTF_8);
        assertCode(() -> ImageValidator.validate(html, MAX_BYTES, MAX_DIM), "IMAGE_TYPE_NOT_ALLOWED");

        byte[] gif = "GIF89a....".getBytes(StandardCharsets.US_ASCII);
        assertCode(() -> ImageValidator.validate(gif, MAX_BYTES, MAX_DIM), "IMAGE_TYPE_NOT_ALLOWED");
    }

    @Test
    void rejectsTruncatedFileWithValidSignature() {
        byte[] png = TestImages.png(50, 50);
        byte[] truncated = Arrays.copyOf(png, 40);
        assertCode(() -> ImageValidator.validate(truncated, MAX_BYTES, MAX_DIM), "IMAGE_INVALID");
    }

    @Test
    void rejectsEmptyOversizedAndHugeDimensions() {
        assertCode(() -> ImageValidator.validate(new byte[0], MAX_BYTES, MAX_DIM), "IMAGE_EMPTY");
        assertCode(() -> ImageValidator.validate(TestImages.png(40, 40), 10, MAX_DIM), "IMAGE_TOO_LARGE");
        assertCode(() -> ImageValidator.validate(TestImages.png(101, 5), MAX_BYTES, 100), "IMAGE_DIMENSIONS_NOT_ALLOWED");
    }

    private static void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, String code) {
        assertThatThrownBy(call).isInstanceOfSatisfying(BadRequestException.class,
                ex -> assertThat(ex.getCode()).isEqualTo(code));
    }
}
