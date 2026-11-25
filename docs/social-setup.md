# 추가 기능 공통 준비 — 팀원 시작 안내

주완의 공통 준비 #18과 실습 #24에 대한 안내다.
좋아요·댓글·사진 기능 API와 실제 화면은 아직 각 조원이 개발해야 한다.

## 시작 순서

1. 현재 작업을 보존한 뒤 main으로 이동하고 `git pull --ff-only origin main` 실행.
2. [공통 계약](social-feature-contract.md)을 읽고 본인 이슈 확인.
3. 본인 DB에서 아래 세 SQL을 순서대로 적용.
4. 본인 작업 브랜치를 생성하거나 이어서 작업.
5. 파일 하나 작성·수정 → 검사 → 그 파일만 commit → push → 다음 파일.
6. 최종 PR을 주완이 검토하고, merge commit으로 병합.

| 사람 | 이슈 | 다음 작업 |
| --- | --- | --- |
| 현준 | [#19](https://github.com/choijuwan124/lifeisoneday_web/issues/19) | 좋아요 API, like.js·CSS |
| 용준 | [#20](https://github.com/choijuwan124/lifeisoneday_web/issues/20) | 댓글 API, comment.js·CSS |
| 림정 | [#21](https://github.com/choijuwan124/lifeisoneday_web/issues/21) | 사진 API, photo.js·CSS, 파일 정리 기능 |
| 민재 | [#22](https://github.com/choijuwan124/lifeisoneday_web/issues/22) | 화면 틀 준비 후 세 기능 병합 시 연결 |
| 주완 | [#23](https://github.com/choijuwan124/lifeisoneday_web/issues/23) | 최종 통합·삭제 연동·발표 준비 |

## DB 적용

기존 signup → group → diary 스키마가 적용된 DB를 선택하고 다음을 실행한다.

```text
backend/src/main/resources/like-schema.sql
backend/src/main/resources/comment-schema.sql
backend/src/main/resources/photo-schema.sql
```

Windows에서는 MySQL Workbench로 파일을 열고 본인의 lifeisoneday DB에 적용한다.
이미 새 테이블을 임의로 만들어뒀다면 먼저 컬럼·제약을 비교한다.
IF NOT EXISTS는 기존 테이블을 수정하지 않는다. 데이터나 테이블을 삭제해서 맞추지 않는다.
새 SQL은 기존 사용자·일기장·일기 행을 변경하지 않는다.

## 사진 저장·공통 함수

- 파일 5MiB, multipart 요청 전체 8MiB 제한. 앱 시작 시 새 설정을 읽도록 백엔드를 재시작한다.
- `app.upload.directory` 기본값은 백엔드 폴더 기준 `../.local/uploads`다.
- 다른 작업 폴더에서 실행한다면 `DIARY_UPLOAD_DIR` 또는 로컬 속성에 절대 경로를 설정한다.
- `.properties` 파일의 Windows 경로는 `C:/Users/.../.local/uploads`처럼 `/`를 사용한다.
- `.local/`은 Git 제외 경로다. 실제 사진을 frontend 또는 static 아래에 저장하지 않는다.
- `api.upload(path, formData)`는 PUT multipart 요청이며 세션 쿠키를 포함한다.
- `api.getBlob(path)`는 권한 검사를 거치는 이미지 API의 바이너리를 받는다.
- 파일 형식·픽셀·작성자 권한 검사와 실제 저장·교체는 림정이 구현한다.
- 일기 삭제 시 사진 파일 정리는 DB CASCADE만으로 해결되지 않으며 최종 통합 #23에서 확인한다.

## 자동 검사

Mac / Git Bash:

```bash
./backend/gradlew -p backend build
node --test frontend/tests/auth.test.mjs frontend/tests/diary.test.mjs frontend/tests/api-upload.test.mjs
```

Windows PowerShell에서는 Gradle 명령을 `.\backend\gradlew.bat -p backend build`로 바꾼다.
새 Java 검사에는 DB 없이 실제 임시 HTTP 서버를 시작하는 multipart 제한 검사도 포함된다.

### 실제 MySQL 제약 검사 (개발·실습 전용)

`scripts/test-social-schema.py`는 실행마다 무작위 이름의 새 DB를 생성하고 마지막에 그 DB만 삭제한다.
Python 3와 MySQL CLI, 테스트 DB 생성·삭제 권한이 필요하다.
MySQL 실제 제약 검사이므로 Java 대역 테스트와 구분한다. 기존 앱의 연결 파일은 자동으로 읽지 않는다.

Mac의 격리 MySQL 소켓 예:

```bash
MYSQL_BIN=/opt/homebrew/opt/mysql@8.4/bin/mysql MYSQL_TEST_SOCKET=/path/to/test.sock python3 scripts/test-social-schema.py
```

Windows에서는 본인의 테스트 서버용 MySQL client 설정 파일을 준비한다.
비밀번호를 넣은 설정 파일은 Git에 올리지 않는다.

```powershell
$env:MYSQL_BIN = "C:/Program Files/MySQL/MySQL Server 8.4/bin/mysql.exe"
$env:MYSQL_TEST_CONFIG = "C:/path/to/private-test-client.cnf"
python scripts/test-social-schema.py
```

`MYSQL_TEST_SOCKET`과 `MYSQL_TEST_CONFIG` 중 하나만 설정한다.
테스트는 중복 좋아요·사진 제한·외래키·삭제 CASCADE·탈퇴 후 기록 보존·SQL 재적용을 검사한다.

## 실습 기록

[주완의 오류 재현·수정 기록](debug/common.md)에 서로 다른 네 사례의 실패와 수정 커밋을 남겼다.
실습 결과를 우연히 발견한 오류나 아직 구현하지 않은 사진 기능의 통합 검증으로 표현하지 않는다.
각자 실제 개발 중 발견한 오류는 실제 원인·수정·재검증 결과로 별도 기록한다.
