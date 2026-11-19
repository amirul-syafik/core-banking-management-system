package com.cbms.service;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CustomerProfilePictureValidatorTest {
    @Test
    void acceptsDecodedPngWithinBounds() throws Exception {
        var image = new BufferedImage(128, 128, BufferedImage.TYPE_INT_RGB);
        var output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        var file = new MockMultipartFile("file", "customer.png", "image/png", output.toByteArray());

        assertEquals("image/png", CustomerProfilePictureValidator.validate(file));
    }

    @Test
    void rejectsUnsupportedOrMismatchedContent() {
        var executable = new MockMultipartFile("file", "photo.png", "image/png", "MZ executable".getBytes());
        assertThrows(IllegalArgumentException.class, () -> CustomerProfilePictureValidator.validate(executable));

        var mismatched = new MockMultipartFile("file", "photo.png", "image/jpeg", new byte[64]);
        assertThrows(IllegalArgumentException.class, () -> CustomerProfilePictureValidator.validate(mismatched));
    }

    @Test
    void rejectsOversizedUploadsBeforeDecoding() {
        var oversized = new MockMultipartFile("file", "photo.jpg", "image/jpeg",
                new byte[(int) CustomerProfilePictureValidator.MAX_FILE_SIZE + 1]);
        assertThrows(IllegalArgumentException.class, () -> CustomerProfilePictureValidator.validate(oversized));
    }

    @Test
    void rejectsImagesBelowMinimumDimensions() throws Exception {
        var image = new BufferedImage(32, 64, BufferedImage.TYPE_INT_RGB);
        var output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        var file = new MockMultipartFile("file", "small.png", "image/png", output.toByteArray());
        assertThrows(IllegalArgumentException.class, () -> CustomerProfilePictureValidator.validate(file));
    }

    @Test
    void rejectsRawWebpThatWasNotNormalizedByTheUploadClient() {
        var webp = new MockMultipartFile("file", "customer.webp", "image/webp",
                "RIFF0000WEBP".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        assertThrows(IllegalArgumentException.class, () -> CustomerProfilePictureValidator.validate(webp));
    }
}
