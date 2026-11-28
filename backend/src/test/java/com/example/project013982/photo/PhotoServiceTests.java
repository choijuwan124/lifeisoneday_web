package com.example.project013982.photo;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PhotoServiceTests {
    private static final long GROUP = 4, DIARY = 7, ME = 9, FRIEND = 10;
    @TempDir Path temp;
    private final PhotoRepository repository = mock(PhotoRepository.class);
    private final PhotoStorage storage = mock(PhotoStorage.class);
    private final PhotoCleanupService cleanup = mock(PhotoCleanupService.class);
    private final PhotoService service = new PhotoService(repository, storage, cleanup);

    @AfterEach void clearTransactions() {
        if (TransactionSynchronizationManager.isSynchronizationActive())
            TransactionSynchronizationManager.clearSynchronization();
    }

    @Test
    void requiresMembershipBeforeExposingPhotoAndAuthorForChanges() {
        when(repository.lockDiary(GROUP, DIARY)).thenReturn(Optional.of(new PhotoRepository.DiaryOwner(GROUP, FRIEND)));
        when(repository.lockMembership(GROUP, ME)).thenReturn(false);
        assertThrows(PhotoService.PhotoAccessNotFoundException.class, () -> service.getMetadata(ME, GROUP, DIARY));
        verify(repository, never()).findByDiaryId(DIARY);

        when(repository.lockMembership(GROUP, ME)).thenReturn(true);
        assertThrows(PhotoService.PhotoForbiddenException.class,
                () -> service.replace(ME, GROUP, DIARY, new MockMultipartFile("file", "x.png", "image/png", new byte[]{1})));
        verifyNoInteractions(storage);
    }

    @Test
    void replacementCleansOldFileOnlyAfterCommitAndRollsNewFileBackOnFailure() {
        allowAuthor();
        Photo old = photo("old.png");
        Photo next = new Photo(2L, DIARY, "new.png", "image/png", 10, LocalDateTime.now(), LocalDateTime.now());
        when(repository.findByDiaryId(DIARY)).thenReturn(Optional.of(old));
        when(storage.store(any())).thenReturn(new PhotoStorage.StoredPhoto("new.png", "image/png", 10));
        when(repository.save(DIARY, "new.png", "image/png", 10)).thenReturn(next);
        TransactionSynchronizationManager.initSynchronization();

        PhotoResponse response = service.replace(ME, GROUP, DIARY,
                new MockMultipartFile("file", "x.png", "image/png", new byte[]{1}));
        assertEquals("/api/groups/4/diaries/7/photo/content", response.contentPath());
        verify(cleanup, never()).deleteAfterCommit(anyList());
        TransactionSynchronizationManager.getSynchronizations().forEach(s -> s.afterCompletion(0));
        verify(cleanup).deleteAfterCommit(List.of("old.png"));

        reset(cleanup, repository, storage);
        allowAuthor();
        when(storage.store(any())).thenReturn(new PhotoStorage.StoredPhoto("new.png", "image/png", 10));
        when(repository.save(DIARY, "new.png", "image/png", 10)).thenThrow(new IllegalStateException("DB failure"));
        assertThrows(IllegalStateException.class, () -> service.replace(ME, GROUP, DIARY,
                new MockMultipartFile("file", "x.png", "image/png", new byte[]{1})));
        verify(cleanup).deleteAfterCommit(List.of("new.png"));
    }

    @Test
    void transactionRollbackKeepsPreviousPhotoAndRemovesNewFile() {
        allowAuthor();
        Photo old = photo("old.png");
        Photo next = new Photo(2L, DIARY, "new.png", "image/png", 10, LocalDateTime.now(), LocalDateTime.now());
        when(repository.findByDiaryId(DIARY)).thenReturn(Optional.of(old));
        when(storage.store(any())).thenReturn(new PhotoStorage.StoredPhoto("new.png", "image/png", 10));
        when(repository.save(DIARY, "new.png", "image/png", 10)).thenReturn(next);
        TransactionSynchronizationManager.initSynchronization();

        service.replace(ME, GROUP, DIARY, new MockMultipartFile("file", "x.png", "image/png", new byte[]{1}));
        TransactionSynchronizationManager.getSynchronizations().forEach(s -> s.afterCompletion(1));

        verify(cleanup).deleteAfterCommit(List.of("new.png"));
        verify(cleanup, never()).deleteAfterCommit(List.of("old.png"));
    }

    @Test
    void realNewFileIsRemovedAfterRepositoryFailureAndOldFileAndMetadataRemain() throws Exception {
        Path uploadRoot = temp.resolve("uploads");
        Files.createDirectories(uploadRoot);
        String oldKey = "123e4567-e89b-12d3-a456-426614174000.png";
        byte[] oldBytes = png();
        Files.write(uploadRoot.resolve(oldKey), oldBytes);
        Photo old = photo(oldKey);
        when(repository.lockDiary(GROUP, DIARY)).thenReturn(Optional.of(new PhotoRepository.DiaryOwner(GROUP, ME)));
        when(repository.lockMembership(GROUP, ME)).thenReturn(true);
        when(repository.findByDiaryId(DIARY)).thenReturn(Optional.of(old));
        when(repository.save(eq(DIARY), anyString(), eq("image/png"), anyLong()))
                .thenThrow(new IllegalStateException("simulated DB failure"));
        PhotoStorage realStorage = new PhotoStorage(uploadRoot.toString());
        PhotoCleanupService realCleanup = new PhotoCleanupService(repository, realStorage, uploadRoot.toString());
        PhotoService realService = new PhotoService(repository, realStorage, realCleanup);

        assertThrows(IllegalStateException.class, () -> realService.replace(ME, GROUP, DIARY,
                new MockMultipartFile("file", "new.png", "image/png", png())));

        try (var files = Files.list(uploadRoot)) {
            assertEquals(List.of(oldKey), files.map(file -> file.getFileName().toString()).toList());
        }
        assertArrayEquals(oldBytes, Files.readAllBytes(uploadRoot.resolve(oldKey)));
        assertEquals(Optional.of(old), repository.findByDiaryId(DIARY));
    }

    @Test
    void metadataLookupFailureRemovesNewFileAndPreservesExistingPhoto() throws Exception {
        Path uploadRoot = temp.resolve("lookup-failure-uploads");
        Files.createDirectories(uploadRoot);
        String oldKey = "123e4567-e89b-12d3-a456-426614174000.png";
        byte[] oldBytes = png();
        Files.write(uploadRoot.resolve(oldKey), oldBytes);
        allowAuthor();
        when(repository.findByDiaryId(DIARY)).thenThrow(new IllegalStateException("metadata lookup failed"));
        PhotoStorage realStorage = new PhotoStorage(uploadRoot.toString());
        PhotoCleanupService realCleanup = new PhotoCleanupService(repository, realStorage, uploadRoot.toString());
        PhotoService realService = new PhotoService(repository, realStorage, realCleanup);
        TransactionSynchronizationManager.initSynchronization();

        assertThrows(IllegalStateException.class, () -> realService.replace(ME, GROUP, DIARY,
                new MockMultipartFile("file", "new.png", "image/png", png())));
        TransactionSynchronizationManager.getSynchronizations().forEach(s -> s.afterCompletion(1));

        try (var files = Files.list(uploadRoot)) {
            assertEquals(List.of(oldKey), files.map(file -> file.getFileName().toString()).toList());
        }
        assertArrayEquals(oldBytes, Files.readAllBytes(uploadRoot.resolve(oldKey)));
        verify(repository, never()).save(any(), anyString(), anyString(), anyLong());
    }

    @Test
    void deleteOfAbsentPhotoIsIdempotentAfterAuthorization() {
        allowAuthor();
        when(repository.findByDiaryId(DIARY)).thenReturn(Optional.empty());
        service.delete(ME, GROUP, DIARY);
        verify(repository, never()).deleteByDiaryId(DIARY);
        verify(cleanup, never()).deleteAfterCommit(anyList());
    }

    @Test
    void validatesIdentityAndGroupDiaryMatch() {
        assertThrows(PhotoService.LoginRequiredException.class, () -> service.getMetadata(null, GROUP, DIARY));
        assertThrows(IllegalArgumentException.class, () -> service.getMetadata(ME, 0L, DIARY));
        when(repository.lockDiary(GROUP, DIARY)).thenReturn(Optional.empty());
        assertThrows(PhotoService.PhotoAccessNotFoundException.class, () -> service.getMetadata(ME, GROUP, DIARY));
        verify(repository, never()).lockMembership(any(), any());
    }

    private void allowAuthor() {
        when(repository.lockDiary(GROUP, DIARY)).thenReturn(Optional.of(new PhotoRepository.DiaryOwner(GROUP, ME)));
        when(repository.lockMembership(GROUP, ME)).thenReturn(true);
        when(repository.findByDiaryId(DIARY)).thenReturn(Optional.empty());
    }

    private static Photo photo(String key) {
        return new Photo(1L, DIARY, key, "image/png", 12, LocalDateTime.now(), LocalDateTime.now());
    }

    private static byte[] png() throws Exception {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        image.flush();
        return output.toByteArray();
    }
}
