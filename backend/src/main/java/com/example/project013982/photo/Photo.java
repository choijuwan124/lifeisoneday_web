package com.example.project013982.photo;

import java.time.LocalDateTime;

/** Internal photo metadata. The storage key is never exposed by the API. */
public record Photo(
        Long id,
        Long diaryId,
        String storageKey,
        String contentType,
        long sizeBytes,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
