package com.example.project013982.photo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class PhotoStorageTests {
    @TempDir Path temp;

    @Test
    void storesAndReadsRealJpegAndPngWithGeneratedPrivateKeys() throws Exception {
        PhotoStorage storage = new PhotoStorage(temp.resolve("uploads").toString());
        for (String format : new String[]{"jpg", "png"}) {
            byte[] image = image(format, 12, 9);
            PhotoStorage.StoredPhoto stored = storage.store(file("../../unsafe." + format, "text/plain", image));
            assertEquals(format.equals("jpg") ? "image/jpeg" : "image/png", stored.contentType());
            assertTrue(stored.storageKey().matches("[0-9a-f-]{36}\\.(jpg|png)"));
            assertArrayEquals(image, storage.read(stored.storageKey()));
            assertEquals(image.length, stored.sizeBytes());
            assertTrue(storage.delete(stored.storageKey()));
            assertFalse(storage.delete(stored.storageKey()));
        }
    }

    @Test
    void acceptsExactFiveMiBAndRejectsOneByteMore() throws Exception {
        PhotoStorage storage = new PhotoStorage(temp.resolve("uploads").toString());
        byte[] png = image("png", 1, 1);
        byte[] exact = java.util.Arrays.copyOf(png, (int) PhotoStorage.MAX_BYTES);
        byte[] tooLarge = java.util.Arrays.copyOf(png, (int) PhotoStorage.MAX_BYTES + 1);
        assertEquals(PhotoStorage.MAX_BYTES, storage.store(file("x.png", "image/png", exact)).sizeBytes());
        assertThrows(PhotoStorage.PhotoTooLargeException.class,
                () -> storage.store(file("x.png", "image/png", tooLarge)));
    }

    @Test
    void rejectsEmptySpoofedCorruptAndOversizedImages() throws Exception {
        PhotoStorage storage = new PhotoStorage(temp.resolve("uploads").toString());
        assertThrows(PhotoStorage.InvalidPhotoException.class, () -> storage.store(file("x.jpg", "image/jpeg", new byte[0])));
        assertThrows(PhotoStorage.UnsupportedPhotoException.class,
                () -> storage.store(file("x.jpg", "image/jpeg", "not an image".getBytes())));
        assertThrows(PhotoStorage.UnsupportedPhotoException.class,
                () -> storage.store(file("x.gif", "image/gif", image("png", 2, 2))));
        assertThrows(PhotoStorage.UnsupportedPhotoException.class,
                () -> storage.store(file("x.png", "image/png", hugePngHeader(5000, 4001))));
    }

    @Test
    void restrictsStorageKeysToGeneratedNamesInsideRoot() {
        PhotoStorage storage = new PhotoStorage(temp.resolve("uploads").toString());
        assertThrows(IllegalArgumentException.class, () -> storage.read("../../secret.jpg"));
    }

    private static MockMultipartFile file(String name, String type, byte[] bytes) {
        return new MockMultipartFile("file", name, type, bytes);
    }

    private static byte[] image(String format, int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(image, format, output));
        image.flush();
        return output.toByteArray();
    }

    private static byte[] hugePngHeader(int width, int height) throws Exception {
        byte[] png = image("png", 1, 1);
        java.nio.ByteBuffer.wrap(png, 16, 8).putInt(width).putInt(height);
        return png;
    }
}
