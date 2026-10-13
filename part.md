~~~~markdown
# 친구 공유 일기장 프로젝트 개발 분담

## 개발 순서

```text
공통 프로젝트 설정
→ 회원가입 / 로그인
→ 단체 일기장 생성 / 목록 / 상세
→ 멤버 관리 / 친구 초대 / 방 나가기
→ 일기 작성 / 조회 / 수정 / 삭제
→ 전체 연결 / 오류 처리 / 디자인 / 테스트
→ 발표
```

```text
주완 공통 설정 완료
→ 현준 회원 기능 개발
→ 민재 단체 일기장 기능 개발
→ 용준 멤버 기능 개발
→ 림정 일기 기능 개발
→ 주완 전체 통합 및 테스트
```

---

## 주완 — 공통 설정 / 문서 / 통합

```text
frontend/
└── common/
    ├── index.html
    ├── api.js
    └── common.css

backend/
├── build.gradle
└── src/main/
    ├── java/com/lifeisoneday/web/
    │   ├── config/
    │   │   └── CorsConfig.java
    │   └── common/
    │       ├── ApiResponse.java
    │       └── GlobalExceptionHandler.java
    └── resources/
        ├── application.yml
        └── application-local.yml

docs/
├── feature-spec.md
├── api-spec.md
├── erd.md
└── team-guide.md

.gitignore
main.md
```

```text
브랜치: chore/project-setup
```

---

## 현준 — 회원가입 / 로그인

```text
frontend/
└── auth/
    ├── login.html
    ├── signup.html
    ├── auth.js
    └── auth.css

backend/src/main/java/com/lifeisoneday/web/
├── config/
│   └── SecurityConfig.java
├── user/
│   ├── User.java
│   ├── UserRepository.java
│   └── UserService.java
└── auth/
    ├── AuthController.java
    ├── AuthService.java
    ├── SignupRequest.java
    └── LoginRequest.java
```

```text
브랜치: feat/auth
```

---

## 민제 — 단체 일기장 생성 / 조회 / 삭제

```text
frontend/
└── group/
    ├── group-list.html
    ├── group-create.html
    ├── group-detail.html
    ├── group.js
    └── group.css

backend/src/main/java/com/lifeisoneday/web/group/
├── DiaryGroup.java
├── DiaryGroupRepository.java
├── GroupController.java
├── GroupService.java
├── CreateGroupRequest.java
└── GroupResponse.java
```

```text
브랜치: feat/group
```

---

## 용준 — 멤버 목록 / 친구 초대 / 방 나가기

```text
frontend/
└── member/
    ├── member-manage.html
    ├── member.js
    └── member.css

backend/src/main/java/com/lifeisoneday/web/member/
├── GroupMember.java
├── GroupMemberRepository.java
├── MemberController.java
├── MemberService.java
├── InviteMemberRequest.java
└── MemberResponse.java
```

```text
브랜치: feat/member
```

---

## 림정 — 일기 작성 / 조회 / 수정 / 삭제

```text
frontend/
└── diary/
    ├── diary-list.html
    ├── diary-create.html
    ├── diary-detail.html
    ├── diary-edit.html
    ├── diary.js
    └── diary.css

backend/src/main/java/com/lifeisoneday/web/diary/
├── Diary.java
├── DiaryRepository.java
├── DiaryController.java
├── DiaryService.java
├── CreateDiaryRequest.java
├── UpdateDiaryRequest.java
└── DiaryResponse.java
```

```text
브랜치: feat/diary
```

---

## 공통 규칙

```text
main 브랜치에서 직접 작업하지 않는다.
작업 시작 전 main 최신 코드를 pull 한다.
각자 본인 폴더와 파일에서 작업한다.
공통 파일 수정은 주완에게 먼저 공유한다.
application-local.yml과 DB 비밀번호는 GitHub에 올리지 않는다.
기능 완료 후 commit → push → Pull Request → main Merge 순서로 진행한다.
```
~~~~
