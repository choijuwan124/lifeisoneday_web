# 로컬 실행과 통합 확인

백엔드는 Java 21, 프런트는 HTML/CSS/JavaScript, DB는 MySQL을 사용한다.
프로젝트 루트에서 명령어를 실행한다. 아래 실행 방법은 로그인·그룹·멤버 기능용이다.

## 주완 Mac에 설정된 개발 DB

MySQL 8.4를 별도 설치했고 프로젝트 전용 데이터 폴더와 포트를 사용한다.

- 실행 파일: `/opt/homebrew/opt/mysql@8.4/bin/`
- 데이터: `.local/mysql/`
- 주소: `127.0.0.1:3307`
- 데이터베이스: `lifeisoneday`
- 앱 전용 DB 사용자: `lifeisoneday_app`
- 앱 연결 정보: `backend/src/main/resources/application-local.properties`
- 로컬 DB 관리 정보: `.local/mysql-client.cnf`

개인 연결 정보와 `.local/`은 Git에서 제외된다. 비밀번호를 팀 채팅이나 GitHub에 올리지 않는다.
기존 시스템 MySQL 데이터와 별개이므로 이 프로젝트 DB는 아래 스크립트로 관리한다.

```bash
bash scripts/dev-mysql-mac.sh start
bash scripts/dev-mysql-mac.sh status
```

DB를 종료할 때:

```bash
bash scripts/dev-mysql-mac.sh stop
```

종료해도 데이터는 남는다. `brew services start mysql@8.4`는 다른 데이터 경로를 사용하므로
이 프로젝트의 시작 명령어 대신 사용하지 않는다.

## 서버 실행

첫 번째 터미널에서 백엔드 실행:

```bash
./backend/gradlew -p backend bootRun
```

두 번째 터미널에서 프런트 실행(Mac, Python 3 설치 환경):

```bash
python3 -m http.server 5500 --bind 127.0.0.1 --directory frontend
```

브라우저에서 `http://localhost:5500/`을 연다.
`http://127.0.0.1:5500/`도 지원한다. 두 주소는 로그인 쿠키가 다르므로 이용 중 주소를 섞지 않는다.
각 서버는 해당 터미널에서 Ctrl+C로 종료한다.

## Windows 팀원 최초 DB 설정

1. Java 21과 MySQL을 준비하고 MySQL 서버를 실행한다.
2. `application-local.properties.example`을 같은 폴더의 `application-local.properties`로 복사한다.
3. 본인 MySQL 주소·포트·계정·비밀번호를 로컬 파일에 입력한다. 주완의 3307 설정을 그대로 복사하지 않는다.
4. MySQL Workbench에서 `lifeisoneday` 데이터베이스를 만들고 선택한다.
5. `signup-schema.sql` → `group-schema.sql` 순서로 적용한다.

```sql
CREATE DATABASE IF NOT EXISTS lifeisoneday CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE lifeisoneday;
```

위 DB를 선택한 상태에서 `backend/src/main/resources/`의 SQL 파일 두 개를 실행한다.
기존 테이블이 있다면 먼저 컬럼과 제약이 일치하는지 확인한다. 기존 데이터를 삭제하지 않는다.

Windows PowerShell의 백엔드 실행 명령어:

```powershell
.\backend\gradlew.bat -p backend bootRun
```

프런트는 VS Code Live Server의 5500 포트로 `frontend/index.html`을 열어도 된다.
프런트를 파일 더블클릭(`file://`)으로 실행하지 않는다.

## 자동 테스트

Mac 또는 Git Bash:

```bash
./backend/gradlew -p backend build
node --test frontend/tests/auth.test.mjs
```

PowerShell:

```powershell
.\backend\gradlew.bat -p backend build
node --test frontend/tests/auth.test.mjs
```

Java 테스트는 DB 대역을 사용한 인증·세션 검사이며, Node 테스트는 프런트 응답 처리 회귀 검사다.
실제 MySQL·브라우저 통합 검사는 아래 흐름으로 별도 확인한다.

## 계정 두 개로 전체 흐름 확인

1. 서로 다른 브라우저 프로필 또는 일반 창·시크릿 창에서 A와 B를 각각 가입한다.
2. 틀린 비밀번호가 거부되는지 확인한 뒤 두 계정을 로그인한다.
3. A가 단체 일기장을 만들고 새로고침 후에도 상세 정보가 남는지 확인한다.
4. 초대 전 B가 그 일기장에 접근할 수 없는지 확인한다.
5. A가 B의 아이디로 초대하고, 중복 초대가 거부되는지 확인한다.
6. B의 목록에 해당 일기장이 나타나고 멤버 목록을 조회할 수 있는지 확인한다.
7. A(방장)는 나갈 수 없고, B는 나간 뒤 목록과 상세 접근이 차단되는지 확인한다.
8. 홈에서 로그아웃하고 그룹 API 접근이 거부되는지 확인한다.
9. 재로그인 후 A의 일기장이 유지되는지 확인한다.

정상 로그인 후에는 일기장 목록으로 이동한다.
로그아웃 요청이 실패하면 현재 화면에 오류를 표시하고 재시도를 허용한다.

## 오류를 만났을 때

- `DataSource` URL 오류: 로컬 연결 파일의 이름·위치·입력값 확인.
- DB 접속 실패: MySQL 실행 상태, 포트, 계정 권한 확인.
- CORS 오류: 프런트 주소가 localhost 또는 127.0.0.1의 5500 포트인지 확인.
- API 401: 로그인 후 재시도. 다른 로컬 호스트 주소로 바꿨다면 그 주소에서 다시 로그인.
- 서버 500: 백엔드 로그와 SQL 적용 여부 확인. 개인 비밀번호를 로그 공유에 포함하지 않는다.
