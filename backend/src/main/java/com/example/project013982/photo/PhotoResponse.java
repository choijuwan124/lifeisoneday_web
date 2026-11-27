package com.example.project013982.photo;

import java.time.LocalDateTime;

/** Public photo metadata. Internal storage details are intentionally omitted. */
public record PhotoResponse(
        Long id,
        Long diaryId,
        String contentType,
        long sizeBytes,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        String contentPath) {

    public static PhotoResponse from(Photo photo) {
        return new PhotoResponse(photo.id(), photo.diaryId(), photo.contentType(), photo.sizeBytes(),
                photo.createdAt(), photo.updatedAt(), "/api/groups/%d/diaries/%d/photo/content"
                .formatted(photo.groupId(), photo.diaryId()));
    }
}
