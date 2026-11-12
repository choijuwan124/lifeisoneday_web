package com.example.project013982.group;

import java.time.LocalDateTime;

/** Safe representation of a group diary; it contains no member credentials or private account data. */
public record GroupResponse(
        Long id,
        String name,
        String description,
        Long ownerId,
        LocalDateTime createdAt) {
}
