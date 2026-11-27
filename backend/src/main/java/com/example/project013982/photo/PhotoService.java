package com.example.project013982.photo;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;

/** Applies membership and author rules and coordinates transactional metadata with private files. */
@Service
public class PhotoService {
    private final PhotoRepository repository;
    private final PhotoStorage storage;
    private final PhotoCleanupService cleanup;

    public PhotoService(PhotoRepository repository, PhotoStorage storage, PhotoCleanupService cleanup) {
        this.repository = repository;
        this.storage = storage;
        this.cleanup = cleanup;
    }

    @Transactional
    public Optional<PhotoResponse> getMetadata(Long userId, Long groupId, Long diaryId) {
        authorize(userId, groupId, diaryId, false);
        return repository.findByDiaryId(diaryId).map(photo -> PhotoResponse.from(photo, groupId));
    }

    @Transactional
    public PhotoContent getContent(Long userId, Long groupId, Long diaryId) {
        authorize(userId, groupId, diaryId, false);
        Photo photo = repository.findByDiaryId(diaryId).orElseThrow(PhotoNotFoundException::new);
        return new PhotoContent(photo.contentType(), storage.read(photo.storageKey()));
    }

    @Transactional
    public PhotoResponse replace(Long userId, Long groupId, Long diaryId, MultipartFile file) {
        authorize(userId, groupId, diaryId, true);
        PhotoStorage.StoredPhoto stored = storage.store(file);
        Optional<Photo> previous = repository.findByDiaryId(diaryId);
        try {
            Photo saved = repository.save(diaryId, stored.storageKey(), stored.contentType(), stored.sizeBytes());
            registerFileCompletion(stored.storageKey(), previous.map(Photo::storageKey).orElse(null));
            return PhotoResponse.from(saved, groupId);
        } catch (RuntimeException exception) {
            throw exception;
        }
    }

    @Transactional
    public void delete(Long userId, Long groupId, Long diaryId) {
        authorize(userId, groupId, diaryId, true);
        Optional<Photo> existing = repository.findByDiaryId(diaryId);
        if (existing.isEmpty()) return;
        repository.deleteByDiaryId(diaryId);
        registerFileCompletion(null, existing.get().storageKey());
    }

    /** Capture these keys inside the caller's diary/group deletion transaction, before cascading SQL. */
    @Transactional
    public List<String> storageKeysForDiaryDeletion(Long diaryId) {
        return cleanup.storageKeysForDiary(diaryId);
    }

    /** Capture keys before the caller deletes all diaries in a group. */
    @Transactional
    public List<String> storageKeysForGroupDeletion(Long groupId) {
        return cleanup.storageKeysForGroup(groupId);
    }

    /** Invoke after the caller's deletion transaction has committed. */
    public void cleanupAfterDeletionCommit(List<String> storageKeys) {
        cleanup.deleteAfterCommit(storageKeys);
    }

    private void authorize(Long userId, Long groupId, Long diaryId, boolean authorRequired) {
        requireLoggedIn(userId);
        requirePositive(groupId, "단체 일기장");
        requirePositive(diaryId, "일기");
        PhotoRepository.DiaryOwner diary = repository.lockDiary(groupId, diaryId)
                .orElseThrow(PhotoAccessNotFoundException::new);
        if (!repository.lockMembership(groupId, userId)) throw new PhotoAccessNotFoundException();
        if (authorRequired && !diary.authorId().equals(userId)) throw new PhotoForbiddenException();
    }

    private void registerFileCompletion(String newKey, String previousKey) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_COMMITTED) {
                    if (previousKey != null && !previousKey.equals(newKey)) cleanup.deleteAfterCommit(List.of(previousKey));
                } else if (newKey != null) {
                    cleanup.deleteAfterCommit(List.of(newKey));
                }
            }
        });
    }

    private static void requireLoggedIn(Long userId) {
        if (userId == null || userId <= 0) throw new LoginRequiredException();
    }

    private static void requirePositive(Long id, String name) {
        if (id == null || id <= 0) throw new IllegalArgumentException("올바른 " + name + " 번호가 아닙니다.");
    }

    public record PhotoContent(String contentType, byte[] bytes) { }
    public static class LoginRequiredException extends RuntimeException { public LoginRequiredException() { super("로그인이 필요합니다."); } }
    public static class PhotoAccessNotFoundException extends RuntimeException { public PhotoAccessNotFoundException() { super("일기를 찾을 수 없거나 접근 권한이 없습니다."); } }
    public static class PhotoForbiddenException extends RuntimeException { public PhotoForbiddenException() { super("일기 작성자만 사진을 변경하거나 삭제할 수 있습니다."); } }
    public static class PhotoNotFoundException extends RuntimeException { public PhotoNotFoundException() { super("사진을 찾을 수 없습니다."); } }
}
