package com.example.project013982.group;

import java.time.LocalDateTime;

/** Persisted group diary data used by the repository and service layer. */
public record DiaryGroup(
        Long id,
        String name,
        String description,
        Long ownerId,
        LocalDateTime createdAt) {
}
