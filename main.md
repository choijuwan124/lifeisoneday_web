# Life Is One Day

공유 일기장 웹사이트 조별 프로젝트입니다. 사용자는 개인 일기를 작성하고, 친구를 초대해 단체 일기장을 함께 사용할 수 있습니다.

## 팀 구성

| 분야 | 담당 |
| --- | --- |
| Frontend | 현준, 민재 |
| Backend | 림정, 용준 |

## 기능 정의

- 로그인 / 회원가입
- 일기 작성, 수정, 삭제, 조회
- 단체 일기장 생성, 삭제, 조회
- 친구 초대
- 단체 일기장(방) 나가기

## 기술 스택

- Frontend: HTML, CSS, JavaScript
- Backend: Spring Boot, MySQL
- 협업: Git, GitHub

## 프로젝트 구조

```text
.
├── frontend/                  # 화면 및 브라우저 동작 코드
├── backend/                   # Spring Boot API 서버
└── docs/                      # 기획·API·DB 문서
```

팀원별 첫 작업과 Git 사용 순서는 [팀 개발 시작 안내](docs/team-work-guide.md)를 확인하세요.

## 브랜치 및 작업 규칙

1. `main` 브랜치에는 직접 push하지 않습니다.
2. 작업 전에는 `git pull origin main`으로 최신 코드를 받습니다.
3. 기능별 브랜치를 만들어 작업합니다. 예: `feature/login`, `feature/diary-crud`
4. 작업을 마치면 Pull Request(PR)를 만들고, 팀원 확인 후 `main`에 병합합니다.
5. 커밋 메시지는 `feat: 로그인 화면 추가`, `fix: 일기 삭제 오류 수정`처럼 작성합니다.

## 시작 방법

### Frontend

`frontend/index.html`을 브라우저에서 열거나 VS Code의 Live Server로 실행합니다.

### Backend

`backend/src/main/resources/application.properties`에 MySQL 연결 설정을 추가한 뒤 `backend` 폴더에서 실행합니다.

```bash
./gradlew bootRun
```
