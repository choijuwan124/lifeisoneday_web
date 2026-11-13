# Life Is One Day
친구끼리만 공유하는 단체 일기장 웹 프로젝트. 가입한 친구를 초대해 일기를 함께 읽고, 자신이 쓴 글을 관리한다.
Git 수업 과제로 파일별 커밋 → 브랜치 push → PR 검토 → merge 과정을 기록했다.

## 구현 기능

- 아이디·비밀번호·닉네임 회원가입, 로그인 상태 확인, 로그인·로그아웃
- 단체 일기장 생성·목록·상세·방장 전용 삭제
- 가입한 친구 아이디로 초대, 멤버 목록, 일반 멤버 방 나가기
- 일기 작성·목록·상세·작성자 전용 수정·삭제
- 비멤버 접근 차단, 탈퇴 후 기록 보존, 오류·빈 목록 안내, 모바일 화면
- 공통 로그인 메뉴와 보호 화면 이동, 발표용 데이터 생성

개별 일기는 작성자만 수정·삭제한다. 방장이 일기장 전체를 삭제하면 모든 멤버의 일기도 함께 삭제된다.
일기장 삭제 전에는 확인창을 표시한다. 친구 초대는 별도 수락 없이 즉시 참여하는 방식이다.

## 팀 담당

| 담당 | 구현 범위 |
| --- | --- |
| 주완(조장) | 공통 설정, 로그인·로그아웃, 통합·오류 수정, 일기장 삭제, 문서·발표 준비 |
| 현준 | 회원가입 API·화면 |
| 민재 | 단체 일기장 데이터·생성·목록·상세 |
| 용준 | 멤버 데이터·목록·친구 초대·방 나가기 |
| 림정 | 일기 작성·조회·수정·삭제 |

기존 프런트/백엔드 분담 대신 기능 단위로 협업했다.

## 기술과 구조

- Java 21, Spring Boot, Gradle Wrapper, JDBC/JdbcTemplate, MySQL
- HTML, CSS, JavaScript — 프런트 프레임워크 없음
- 세션 쿠키 인증, PBKDF2 비밀번호 해시, Git/GitHub
- 백엔드는 IntelliJ에서 `backend` 폴더를 Gradle 프로젝트로 연다.

```text
backend/src/main/java/com/example/project013982/
  auth/      회원가입·로그인·현재 사용자·로그아웃 API
  user/      사용자 저장 및 비밀번호 처리
  group/     단체 일기장 생성·조회·삭제
  member/    친구 초대·참여·방 나가기
  diary/     일기 CRUD
  common/    응답·오류 처리
  config/    CORS
backend/src/main/resources/       DB 설정 예시와 SQL 3개
frontend/
  auth/ group/ member/ diary/     기능별 화면
  js/api.js                      공통 API 호출
  js/app.js                      공통 메뉴·세션 확인
  css/style.css                  공통 스타일
  tests/                         프런트 회귀 테스트
scripts/                         Mac DB 관리·발표 데이터 생성
docs/                            실행·API·ERD·협업·발표·검증 자료
```

계획 초기의 `com/lifeisoneday/web`, `frontend/common` 대신 위 실제 경로를 사용한다.

## 실행

1. Java 21, MySQL 준비. 테스트·발표 데이터 생성은 Node.js 20 이상 사용.
2. `application-local.properties.example`을 `application-local.properties`로 복사해 본인 DB 연결 정보 입력.
3. MySQL에서 DB 생성·선택 후 `signup-schema.sql → group-schema.sql → diary-schema.sql` 실행.
4. 백엔드 실행:
   - Mac/Git Bash: `./backend/gradlew -p backend bootRun`
   - Windows PowerShell: `.\backend\gradlew.bat -p backend bootRun`
5. VS Code Live Server 5500 포트에서 `frontend/index.html` 열기. 파일 더블클릭 방식은 지원하지 않음.

프런트와 API는 같은 호스트를 사용한다. localhost와 127.0.0.1은 쿠키가 다르므로 섞어 쓰지 않는다.
비밀번호가 들어간 로컬 설정과 `.local/`은 Git에 올리지 않는다.

자세한 설정과 오류 해결: [로컬 실행 안내](docs/local-development.md).

## 테스트·발표

```bash
./backend/gradlew -p backend build
node --test frontend/tests/auth.test.mjs frontend/tests/diary.test.mjs
node scripts/seed-demo.mjs
```

Windows에서는 첫 명령을 `.\backend\gradlew.bat -p backend build`로 바꾼다.
발표 데이터 명령은 백엔드가 실행 중일 때 사용한다. 계정 정보는 `.local/demo-credentials.json`에만 저장된다.

- [전체 검증 결과와 수동 점검](docs/verification.md)
- [발표 순서·시연 대본](docs/presentation-guide.md)
- [API 명세](docs/api-spec.md)
- [ERD와 데이터 보존 정책](docs/erd.md)
- [개발 단계·완료 상태](plan)
- [파일별 커밋·PR 협업 안내](docs/team-work-guide.md)

현재 완성 범위는 로컬 과제 시연이다. 인터넷 배포, 이메일 초대·수락, 방장 위임, 비밀번호 재설정·회원 탈퇴는 포함하지 않는다.
