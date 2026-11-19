package com.cbms.service;

import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Locale;

/** Validates profile pictures by their content and bounds before storage. */
public final class CustomerProfilePictureValidator {
    public static final long MAX_FILE_SIZE = 5L * 1024 * 1024;
    private static final int MIN_DIMENSION = 64;
    private static final int MAX_DIMENSION = 4096;
    private static final long MAX_PIXELS = 16_000_000L;

    private CustomerProfilePictureValidator() {
    }

    public static String validate(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Choose a non-empty image file.");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("Profile pictures must be 5 MB or smaller.");
        }

        String fileName = file.getOriginalFilename();
        String extension = fileName == null || fileName.lastIndexOf('.') < 0
                ? "" : fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        String declaredType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if (!(extension.equals("jpg") || extension.equals("jpeg") || extension.equals("png"))
                || !(declaredType.equals("image/jpeg") || declaredType.equals("image/png"))) {
            throw new IllegalArgumentException("Choose a JPG or PNG image (WebP selections are converted before upload).");
        }

        byte[] bytes = file.getBytes();
        String detectedType = validateImageIo(bytes);

        if (!detectedType.equals(declaredType) || !extensionMatches(extension, detectedType)) {
            throw new IllegalArgumentException("The file contents do not match the selected image format.");
        }
        return detectedType;
    }

    private static String validateImageIo(byte[] bytes) throws IOException {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (input == null) throw new IllegalArgumentException("The uploaded file is not a supported image.");
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IllegalArgumentException("The uploaded file is not a supported image.");
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                String type = switch (format) {
                    case "jpeg", "jpg" -> "image/jpeg";
                    case "png" -> "image/png";
                    default -> throw new IllegalArgumentException("Choose a JPG, PNG or WebP image.");
                };
                validateDimensions(reader.getWidth(0), reader.getHeight(0));
                // Decode only after the dimension limits have been checked, rejecting malformed image payloads.
                if (reader.read(0) == null) throw new IllegalArgumentException("The uploaded image could not be decoded.");
                return type;
            } finally {
                reader.dispose();
            }
        }
    }

    private static void validateDimensions(int width, int height) {
        if (width < MIN_DIMENSION || height < MIN_DIMENSION || width > MAX_DIMENSION || height > MAX_DIMENSION
                || (long) width * height > MAX_PIXELS) {
            throw new IllegalArgumentException("Image dimensions must be at least 64 × 64 and no larger than 4096 × 4096 (16 megapixels).");
        }
    }

    private static boolean extensionMatches(String extension, String type) {
        return switch (type) {
            case "image/jpeg" -> extension.equals("jpg") || extension.equals("jpeg");
            case "image/png" -> extension.equals("png");
            default -> false;
        };
    }
}
