# 팀 개발 시작 안내

## 1. 담당과 첫 작업

| 담당 | 브랜치 | 첫 구현 범위 |
| --- | --- | --- |
| 현준 (Frontend) | `feature/login-signup-ui` | 로그인·회원가입 화면 및 입력 검증 |
| 민재 (Frontend) | `feature/diary-group-ui` | 일기 목록·작성 화면, 단체 일기장 화면 |
| 림정 (Backend) | `feature/auth-user-api` | 회원가입·로그인 API, 사용자 데이터 구조 |
| 용준 (Backend) | `feature/diary-group-api` | 일기·단체 일기장·초대·방 나가기 API와 데이터 구조 |

> 기능이 겹치거나 역할을 바꾸게 되면, 작업을 시작하기 전에 단체 채팅방에서 먼저 알립니다.

## 2. 팀원이 처음 할 명령어

Git이 설치되어 있다면 터미널에서 실행합니다.

```bash
git clone https://github.com/choijuwan124/lifeisoneday_web.git
cd lifeisoneday_web
git switch 브랜치이름
```

예시: 현준

```bash
git switch feature/login-signup-ui
```

## 3. 매번 작업하는 순서

```bash
git pull origin main
git add .
git commit -m "feat: 구현한 기능 설명"
git push origin 브랜치이름
```

GitHub에서 **Compare & pull request**를 눌러 PR을 만들고, 조장 또는 팀원이 확인한 뒤 `main`으로 병합합니다.

## 4. 꼭 지킬 것

- `main` 브랜치에서 직접 수정하거나 push하지 않습니다.
- 비밀번호·DB 접속 정보·API 키는 커밋하지 않습니다.
- 다른 사람 파일을 수정해야 하면 먼저 알립니다.
- 오류가 나면 혼자 오래 붙잡지 말고 오류 화면이나 메시지를 공유합니다.

## 5. 다음 회의에서 정할 항목

- 화면 디자인과 페이지 목록
- MySQL 테이블: 사용자, 일기, 단체 일기장, 멤버/초대
- 로그인 방식과 API 요청·응답 형식
- 프런트와 백엔드를 연결하는 시점
