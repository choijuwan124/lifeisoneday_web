package com.example.project013982.photo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Iterator;
import java.util.Locale;
import java.util.UUID;

/** Private file storage and image content validation. */
@Component
public class PhotoStorage {
    public static final long MAX_BYTES = 5L * 1024 * 1024;
    public static final long MAX_PIXELS = 20_000_000L;

    private final Path configuredRoot;

    public PhotoStorage(@Value("${app.upload.directory:../.local/uploads}") String directory) {
        this.configuredRoot = Path.of(directory).toAbsolutePath().normalize();
    }

    public StoredPhoto store(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new InvalidPhotoException("사진 파일을 선택해 주세요.");
        if (file.getSize() > MAX_BYTES) throw new PhotoTooLargeException();
        final byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException exception) {
            throw new InvalidPhotoException("사진 파일을 읽지 못했습니다.");
        }
        if (bytes.length == 0) throw new InvalidPhotoException("사진 파일을 선택해 주세요.");
        if (bytes.length > MAX_BYTES) throw new PhotoTooLargeException();

        String contentType = inspect(bytes);
        String suffix = contentType.equals("image/jpeg") ? ".jpg" : ".png";
        String storageKey = UUID.randomUUID() + suffix;
        Path destination = resolve(storageKey, true);
        try {
            Files.write(destination, bytes);
        } catch (IOException exception) {
            throw new PhotoStorageException("사진 파일을 저장하지 못했습니다.", exception);
        }
        return new StoredPhoto(storageKey, contentType, bytes.length);
    }

    public byte[] read(String storageKey) {
        Path path = resolve(storageKey, false);
        try {
            return Files.readAllBytes(path);
        } catch (IOException exception) {
            throw new PhotoMissingFileException();
        }
    }

    /** Returns false only when the file is already absent; errors remain retryable. */
    public boolean delete(String storageKey) {
        Path path = resolve(storageKey, false);
        try {
            return Files.deleteIfExists(path);
        } catch (IOException exception) {
            throw new PhotoStorageException("사진 파일을 정리하지 못했습니다.", exception);
        }
    }

    private String inspect(byte[] bytes) {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (input == null) throw new UnsupportedPhotoException();
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new UnsupportedPhotoException();
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                String contentType = switch (format) {
                    case "jpg", "jpeg" -> "image/jpeg";
                    case "png" -> "image/png";
                    default -> throw new UnsupportedPhotoException();
                };
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || (long) width * height > MAX_PIXELS) {
                    throw new UnsupportedPhotoException("사진 크기는 최대 20메가픽셀까지 지원합니다.");
                }
                BufferedImage decoded = reader.read(0);
                if (decoded == null || decoded.getWidth() != width || decoded.getHeight() != height) {
                    throw new UnsupportedPhotoException();
                }
                decoded.flush();
                return contentType;
            } finally {
                reader.dispose();
            }
        } catch (UnsupportedPhotoException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw new UnsupportedPhotoException();
        }
    }

    private Path resolve(String storageKey, boolean createRoot) {
        if (storageKey == null || !storageKey.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(jpg|png)")) {
            throw new IllegalArgumentException("올바르지 않은 사진 저장 키입니다.");
        }
        try {
            if (createRoot) Files.createDirectories(configuredRoot);
            Path root = configuredRoot.toRealPath();
            Path candidate = root.resolve(storageKey).normalize();
            if (!candidate.getParent().equals(root)) throw new IllegalArgumentException("올바르지 않은 사진 경로입니다.");
            return candidate;
        } catch (IOException exception) {
            throw new PhotoStorageException("사진 저장소에 접근하지 못했습니다.", exception);
        }
    }

    public record StoredPhoto(String storageKey, String contentType, long sizeBytes) { }

    public static class InvalidPhotoException extends RuntimeException {
        public InvalidPhotoException(String message) { super(message); }
    }
    public static class UnsupportedPhotoException extends RuntimeException {
        public UnsupportedPhotoException() { this("JPG 또는 PNG 사진만 업로드할 수 있습니다."); }
        public UnsupportedPhotoException(String message) { super(message); }
    }
    public static class PhotoTooLargeException extends RuntimeException {
        public PhotoTooLargeException() { super("사진은 5 MiB 이하로 업로드해 주세요."); }
    }
    public static class PhotoStorageException extends RuntimeException {
        public PhotoStorageException(String message, Throwable cause) { super(message, cause); }
    }
    public static class PhotoMissingFileException extends RuntimeException {
        public PhotoMissingFileException() { super("사진 파일을 찾을 수 없습니다."); }
    }
}
