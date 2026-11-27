package com.example.project013982.like;

import com.example.project013982.diary.DiaryRepository;
import com.example.project013982.diary.DiaryService.DiaryNotFoundException;
import com.example.project013982.diary.DiaryService.LoginRequiredException;
import com.example.project013982.member.GroupMemberRepository;
import org.springframework.stereotype.Service;

@Service
public class LikeService {
    private final LikeRepository likes;
    private final DiaryRepository diaries;
    private final GroupMemberRepository members;

    public LikeService(LikeRepository likes, DiaryRepository diaries, GroupMemberRepository members) {
        this.likes = likes;
        this.diaries = diaries;
        this.members = members;
    }

    public LikeStatus get(Long userId, long groupId, long diaryId) {
        requireAccess(userId, groupId, diaryId);
        return likes.status(diaryId, userId);
    }

    public LikeStatus add(Long userId, long groupId, long diaryId) {
        requireAccess(userId, groupId, diaryId);
        likes.add(diaryId, userId);
        return likes.status(diaryId, userId);
    }

    public LikeStatus remove(Long userId, long groupId, long diaryId) {
        requireAccess(userId, groupId, diaryId);
        likes.remove(diaryId, userId);
        return likes.status(diaryId, userId);
    }

    private void requireAccess(Long userId, long groupId, long diaryId) {
        if (userId == null || userId <= 0) {
            throw new LoginRequiredException();
        }
        if (groupId <= 0 || diaryId <= 0) {
            throw new IllegalArgumentException("올바른 일기장·일기 번호가 아닙니다.");
        }
        if (!members.exists(groupId, userId) || diaries.findByIdAndGroupId(diaryId, groupId).isEmpty()) {
            throw new DiaryNotFoundException();
        }
    }
}
