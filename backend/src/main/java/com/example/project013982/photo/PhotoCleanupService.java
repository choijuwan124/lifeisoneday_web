package com.example.project013982.photo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/** Post-commit file cleanup with a private on-disk retry queue. */
@Service
public class PhotoCleanupService {
    private static final System.Logger LOGGER = System.getLogger(PhotoCleanupService.class.getName());
    private final PhotoRepository repository;
    private final PhotoStorage storage;
    private final Path queueFile;

    public PhotoCleanupService(PhotoRepository repository, PhotoStorage storage,
                               @Value("${app.upload.directory:../.local/uploads}") String directory) {
        this.repository = repository;
        this.storage = storage;
        this.queueFile = Path.of(directory).toAbsolutePath().normalize().resolve(".cleanup-pending");
    }

    public List<String> storageKeysForDiary(Long diaryId) {
        return List.copyOf(new LinkedHashSet<>(repository.storageKeysForDiary(diaryId)));
    }

    public List<String> storageKeysForGroup(Long groupId) {
        return List.copyOf(new LinkedHashSet<>(repository.storageKeysForGroup(groupId)));
    }

    /** Call only after the transaction that removed/replaced metadata has committed. */
    public synchronized void deleteAfterCommit(List<String> storageKeys) {
        List<String> failed = new ArrayList<>();
        for (String key : new LinkedHashSet<>(storageKeys)) {
            try {
                storage.delete(key);
            } catch (RuntimeException exception) {
                failed.add(key);
                LOGGER.log(System.Logger.Level.ERROR, "Photo cleanup failed; queued for retry: " + key, exception);
            }
        }
        if (!failed.isEmpty()) appendPending(failed);
    }

    /** Retries queued cleanup failures on startup and when explicitly invoked. */
    @EventListener(ApplicationReadyEvent.class)
    public synchronized void retryPending() {
        if (!Files.exists(queueFile)) return;
        List<String> pending;
        try {
            pending = Files.readAllLines(queueFile).stream().map(String::strip)
                    .filter(line -> line.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(jpg|png)"))
                    .distinct().toList();
        } catch (IOException exception) {
            LOGGER.log(System.Logger.Level.ERROR, "Could not read photo cleanup retry queue", exception);
            return;
        }
        List<String> failed = new ArrayList<>();
        for (String key : pending) {
            try {
                storage.delete(key);
            } catch (RuntimeException exception) {
                failed.add(key);
                LOGGER.log(System.Logger.Level.ERROR, "Photo cleanup retry failed: " + key, exception);
            }
        }
        rewritePending(failed);
    }

    private void appendPending(List<String> keys) {
        try {
            Files.createDirectories(queueFile.getParent());
            Files.write(queueFile, keys, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException exception) {
            LOGGER.log(System.Logger.Level.ERROR, "Could not persist photo cleanup retry queue", exception);
        }
    }

    private void rewritePending(List<String> keys) {
        try {
            if (keys.isEmpty()) Files.deleteIfExists(queueFile);
            else Files.write(queueFile, keys, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException exception) {
            LOGGER.log(System.Logger.Level.ERROR, "Could not update photo cleanup retry queue", exception);
        }
    }
}
