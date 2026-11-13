package com.example.project013982.diary;

import java.time.LocalDateTime;

public record DiaryResponse(Long id, Long groupId, Long authorId, String authorNickname,
                            String title, String content, LocalDateTime createdAt,
                            LocalDateTime updatedAt, boolean canEdit, boolean canDelete) { }
