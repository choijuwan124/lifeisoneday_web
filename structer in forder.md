lifeisoneday_web/
├── frontend/
│   ├── index.html
│   ├── auth/
│   │   ├── login.html
│   │   └── signup.html
│   ├── group/
│   │   ├── group-list.html
│   │   ├── group-create.html
│   │   └── group-detail.html
│   ├── diary/
│   │   ├── diary-create.html
│   │   ├── diary-detail.html
│   │   └── diary-edit.html
│   ├── css/
│   │   ├── common.css
│   │   ├── auth.css
│   │   ├── group.css
│   │   ├── diary.css
│   │   └── member.css
│   └── js/
│       ├── api.js
│       ├── auth.js
│       ├── group.js
│       ├── diary.js
│       └── member.js
│
├── backend/
│   ├── build.gradle
│   ├── settings.gradle
│   └── src/
│       ├── main/
│       │   ├── java/
│       │   │   └── com/
│       │   │       └── lifeisoneday/
│       │   │           └── web/
│       │   │               ├── LifeIsOneDayApplication.java
│       │   │               ├── config/
│       │   │               │   ├── CorsConfig.java
│       │   │               │   └── SecurityConfig.java
│       │   │               ├── common/
│       │   │               │   └── GlobalExceptionHandler.java
│       │   │               ├── user/
│       │   │               │   ├── User.java
│       │   │               │   ├── UserRepository.java
│       │   │               │   └── UserService.java
│       │   │               ├── auth/
│       │   │               │   ├── AuthController.java
│       │   │               │   ├── AuthService.java
│       │   │               │   ├── SignupRequest.java
│       │   │               │   └── LoginRequest.java
│       │   │               ├── group/
│       │   │               │   ├── DiaryGroup.java
│       │   │               │   ├── DiaryGroupRepository.java
│       │   │               │   ├── GroupController.java
│       │   │               │   ├── GroupService.java
│       │   │               │   ├── CreateGroupRequest.java
│       │   │               │   └── GroupResponse.java
│       │   │               ├── member/
│       │   │               │   ├── GroupMember.java
│       │   │               │   ├── GroupMemberRepository.java
│       │   │               │   ├── MemberController.java
│       │   │               │   ├── MemberService.java
│       │   │               │   ├── InviteMemberRequest.java
│       │   │               │   └── MemberResponse.java
│       │   │               └── diary/
│       │   │                   ├── Diary.java
│       │   │                   ├── DiaryRepository.java
│       │   │                   ├── DiaryController.java
│       │   │                   ├── DiaryService.java
│       │   │                   ├── CreateDiaryRequest.java
│       │   │                   ├── UpdateDiaryRequest.java
│       │   │                   └── DiaryResponse.java
│       │   └── resources/
│       │       ├── application.yml
│       │       └── application-local.yml
│       └── test/
│
├── docs/
│   ├── feature-spec.md
│   ├── api-spec.md
│   ├── erd.md
│   └── team-guide.md
│
├── .gitignore
└── main.md
