# 댓글 검증과 화면 연결

작업 이슈 [#20](https://github.com/choijuwan124/lifeisoneday_web/issues/20), 작업 브랜치 `feat/comments`, 담당 용준.

## API 요약

기본 경로 `D = /api/groups/{groupId}/diaries/{diaryId}`. 모든 응답은 `ApiResponse(success, message, data)`.

| 요청 | 본문 | 성공 |
| --- | --- | --- |
| GET `D/comments` | 없음 | 200 / Comment 배열 (없으면 `[]`) |
| POST `D/comments` | `{"content": "..."}` | 201 / Comment |
| PUT `D/comments/{commentId}` | `{"content": "..."}` | 200 / 수정된 Comment |
| DELETE `D/comments/{commentId}` | 없음 | 200 / `null` |

Comment: `{id, diaryId, authorId, authorNickname, content, createdAt, updatedAt, canEdit, canDelete}`.
정렬은 `created_at ASC, id ASC`. 본문은 양끝 공백 제거 후 1~1000자(MySQL `CHAR_LENGTH`와 같은 코드 포인트 기준).

| 상황 | 응답 |
| --- | --- |
| 미로그인 | 401 |
| 잘못된 ID 형식·0 이하·범위 초과, 본문 누락·공백·문자열 아님·1001자 이상, 잘못된 JSON | 400 |
| 비멤버, 방 나간 멤버, 없는 일기, 다른 그룹의 일기, 다른 일기의 댓글, 없는 댓글 | 404 |
| 같은 일기에 접근할 수 있지만 다른 사람의 댓글 수정·삭제 (방장 포함) | 403 |

작성자 ID는 `LOGIN_USER_ID` 세션에서만 정한다. 본문의 `authorId`·`userId`는 무시한다.
UPDATE·DELETE는 `id`, `diary_id`, `author_id`를 모두 조건에 넣고 변경 행 수가 1이 아니면 404로 처리한다.

## 민재 화면 연결

상세 HTML에 스타일과 컨테이너를 추가하고, 스크립트는 `api.js → app.js → logout.js → like/comment/photo.js → diary.js` 순서(defer)로 로드한다.

```html
<link rel="stylesheet" href="../comment/comment.css">
<div id="diary-comment-root"></div>
<script defer src="../comment/comment.js"></script>
```

`diary.js`에서 groupId·diaryId를 정한 뒤 호출한다.

```js
const commentWidget = await window.CommentUI.mount({
  root: document.getElementById("diary-comment-root"), // ID 문자열이 아니라 DOM 요소
  groupId,
  diaryId
});
// 서버 목록 다시 조회: await commentWidget.refresh();
// 화면 해제: commentWidget.destroy();
```

- 위젯은 root 안에 제목·목록·빈 목록 안내·입력창을 만든다. 같은 root에 다시 mount하면 이전 위젯과 이벤트가 정리된다.
- 첫 조회 실패 시 위젯 안에 오류와 "댓글 다시 불러오기" 버튼이 나온다. 401은 기존 `api.js`가 `auth:required` 이벤트를 보낸다.
- 스타일은 `.comment-widget` 아래로만 적용되므로 기존 `diary.css`를 바꿀 필요가 없다.

## 기본 검사

```powershell
.\backend\gradlew.bat -p backend build
node --test frontend/tests/auth.test.mjs frontend/tests/diary.test.mjs frontend/tests/api-upload.test.mjs frontend/tests/like.test.mjs frontend/tests/comment.test.mjs
git diff --check
```

실제 Chrome 입력 검사: `node --test scripts/test-comments-browser.mjs`.
Node 22 이상과 Chrome/Chromium이 필요하며, 기본 설치 경로가 아니면 `CHROME_BIN`을 지정한다.
이 검사는 별도 임시 브라우저 프로필과 API 대역을 사용하고 종료 시 정리한다.

| 검사 | 범위 | 결과 |
| --- | --- | --- |
| `frontend/tests/comment.test.mjs` | Node의 DOM·API 대역 | 13건 통과 |
| 기존 프런트 검사 포함 전체 Node 검사 | Node 대역 | 37건 통과 |
| `CommentServiceTests` | 프로젝트 Gradle·Mockito 저장소 대역 | 15건 통과 |
| `CommentControllerTests` | 프로젝트 Gradle·MockMvc + 저장소 대역 | 10건 통과 |
| Gradle 전체 빌드 | 기존 로그인·일기·좋아요 검사 포함 | 성공, Java 62건 통과 |
| `scripts/test-comments-http.mjs` | 격리 MySQL + 실제 HTTP 세션 | 7건 통과 |
| `scripts/test-likes-http.mjs` | 같은 격리 앱의 좋아요 회귀 검사 | 3건 통과 |
| `scripts/test-comments-browser.mjs` | 실제 Chrome 입력 + API 대역 | 3건 통과 |
| 실브라우저 연결 | 민재의 상세 화면 | 미검증 (민재 #22에서 연결) |

### 검사 환경

아래 네 항목은 용준의 최초 개발 환경 기록이다. 2026-11-27 주완의 검토·보완 환경에서
프로젝트의 실제 Gradle 전체 빌드와 격리 MySQL·HTTP 검사를 추가 실행해 위 표로 갱신했다.
최종 검사 총 112건(Java 62 + Node 37 + HTTP 10 + Chrome 3)이 통과했다.

- 작업 환경에서 Maven Central·Gradle 배포 서버 접속이 차단되어 프로젝트 Gradle 빌드를 아직 실행하지 못했다.
- 그동안 Ubuntu 패키지의 Spring 4.3·Mockito 2 jar로 댓글 Java 파일을 예비 컴파일했다.
  서비스 검사 15건은 이 예비 환경에서 실행해 통과했다. 프로젝트의 Spring Boot 4 빌드 결과가 아니므로 Gradle로 다시 확인한다.
- 컨트롤러는 예비 컴파일만 했다. MockMvc 검사는 Spring 6 이상의 jakarta 서블릿이 필요해 Gradle에서만 실행할 수 있다.
- 격리 MySQL 8.0.46(별도 데이터 폴더, 포트 33318, 무작위 이름 DB)에 signup → group → diary → like → comment → photo 순서로 스키마를 적용했다.
  적용 전 `SHOW TABLES`는 비어 있었고, `SHOW CREATE TABLE diary_comments`로 CHECK·외래키·정렬 인덱스를 확인했다.
- jsdom과 실제 Chromium에서 API 대역으로 위젯을 띄워 HTML 모양 댓글이 글자로 표시되는지, 데스크톱·375px 화면 배치를 확인했다. 실제 서버와 연결한 브라우저 검사는 아니다.

## 오류 재현 실습

아래 사례는 정상 구현·검사가 통과한 상태에서 **일부러 오류를 주입한 실습**이다. 실제 개발 중 발견한 오류로 발표하지 않는다.
실습마다 오류 주입 → 검사 실패 확인 → `[실습]` 재현 커밋 → 수정 → 같은 검사 재통과 → 수정 커밋 순서로 진행했다.
용준이 실습 2~4를 먼저 수행했고, 주완이 실제 프로젝트 Gradle 환경에서 실습 1을 보완했다.
실제 검토 중 발견한 화면 결함은 아래 별도 절에 구분해서 기록한다.

### [실습] 1. 1000자 경계 오류 — 주완 보완 완료

- 정상 기준: 프로젝트 Gradle의 `CommentServiceTests` 15건 통과.
- 재현 `a0a97e5`, 수정 `ab0f886`. 서버 길이 조건을 의도적으로 `> 1000`에서 `>= 1000`으로 바꿨다.
- 실행: `./backend/gradlew -p backend test --tests '*CommentServiceTests' --console=plain`.
  Windows에서는 `.\backend\gradlew.bat -p backend test --tests '*CommentServiceTests' --console=plain`.
- 실제 실패: 15건 중 경계 검사와 코드 포인트 검사 2건 실패.
  1000자 ASCII·이모지가 정상 입력인데 `IllegalArgumentException: 댓글은 1000자 이하로 입력해 주세요.`가 발생했다.
- 수정: 초과(`>`) 조건으로 복구. 같은 15건 모두 통과했고 999·1000자 허용, 1001자 거부를 확인했다.
- 범위: 서비스 검사이며 의도적 실습이다. 최종 수정 상태는 이후 실제 HTTP 경계 검사에서도 통과했다.

### [실습] 2. 수정 성공 후 화면 미갱신

- 재현 `4c8f20a`, 수정 `5d18f00`. 수정 성공 처리에서 서버 응답 대신 기존 목록을 그대로 쓰도록 `comment.js`를 의도적으로 바꿨다.
- 실행: `node --test frontend/tests/comment.test.mjs` (DOM·API 대역).
- 기대: PUT 응답의 본문 `서버가 정리한 댓글`과 `수정됨 2026-11-27 10:30`이 화면에 표시된다.
- 실제: 11건 중 2건 실패. 서버는 수정에 성공했지만 화면에 이전 본문이 남았다.
- 실패 출력: `+ '원래 댓글'` / `- '서버가 정리한 댓글'` (수정 실패 후 재시도 검사도 `+ '원래 댓글'` / `- '새 내용'`).
- 원인: 수정 성공 후 편집 상태만 닫고, 목록의 해당 댓글을 응답 객체로 바꾸지 않았다.
- 수정: 같은 ID의 댓글을 PUT 응답 객체로 교체해 본문·`updatedAt`을 서버 값으로 표시. 재검증 11건 통과.
- 범위: Node DOM 대역 검사. 실제 서버의 PUT 응답 형태는 컨트롤러·HTTP 검사에서 따로 확인한다.

### [실습] 3. 삭제 성공 응답 처리 오류

- 재현 `3897f2a`, 수정 `1f66586`. 삭제 응답을 Comment 객체로 가정하고, 아니면 실패로 처리하도록 `comment.js`를 의도적으로 바꿨다.
- 실행: `node --test frontend/tests/comment.test.mjs`. API 대역은 `api.js`처럼 `data:null`이면 전체 `ApiResponse`를 반환한다.
- 기대: DELETE 요청 후 댓글이 사라지고 빈 목록 안내가 표시된다.
- 실제: 11건 중 1건 실패. DELETE 요청은 전송됐지만(서버에서는 삭제 성공) 화면에 댓글이 남았다. 실패 출력 `1 !== 0`.
- 원인: 공통 `api.js`는 `body?.data ?? body`를 반환하므로 삭제 성공 결과는 `{success:true, message, data:null}`이다. 이를 Comment로 검사해 성공을 실패로 판단했다.
- 수정: 삭제는 반환값을 사용하지 않고, 요청이 오류 없이 끝나면 성공으로 보고 해당 댓글을 목록에서 제거. 재검증 11건 통과.
- 추가 확인: 실제 `frontend/js/api.js`와 서버 형태의 fetch 대역(jsdom)으로 `api.delete` 반환값 `{"success":true,"message":"댓글을 삭제했습니다.","data":null}`을 확인했고, 삭제 후 목록 0건·빈 목록 안내 표시·오류 문구 없음을 확인했다. 실제 서버 DELETE 응답은 HTTP 검사에서 확인한다.

### [실습] 4. 작성 실패 시 입력 내용 소실

- 재현 `bd9e5ec`, 수정 `b43ee56`. POST 요청을 보내기 전에 입력창을 비우도록 `comment.js`를 의도적으로 바꿨다.
- 실행: `node --test frontend/tests/comment.test.mjs`. API 대역이 첫 POST에서 `서버 오류가 발생했습니다.`를 던진다.
- 기대: 실패 후에도 입력한 `보존될 댓글`이 남고, 버튼이 다시 활성화되며, 같은 내용으로 재시도할 수 있다.
- 실제: 11건 중 2건 실패. 실패 출력 `'' !== '보존될 댓글'`, 요청 중 입력 유지 검사도 `'' !== '  새 댓글  '`.
- 원인: 서버 성공을 확인하기 전에 입력을 지워, 실패하면 사용자가 쓴 내용을 되돌릴 방법이 없었다.
- 수정: 응답이 올바른 Comment인지 확인한 뒤에만 입력을 비우고, 실패 시 `finally`에서 입력창·버튼을 다시 활성화. 재검증 11건 통과.
  재시도한 POST 두 번 모두 본문이 `{"content":"보존될 댓글"}`이었고 두 번째 요청 성공 후 목록에 표시됐다.
- 범위: Node DOM 대역 검사.

## 주완의 실제 검토 발견 및 수정

아래는 오류를 주입하지 않고 PR 검토에서 발견한 문제다. 위 의도적 실습과 구분한다.

### 이모지 입력이 500개에서 잘리는 문제

- 재현 검사 `efe5cf4`, 수정 `5a7bea5`.
- 작성·수정 textarea의 `maxlength=1000`은 UTF-16 단위인데 서버와 JS 검증은 코드 포인트를 센다.
- 실제 Chrome의 입력 기능으로 이모지 1000개를 보내자 값에는 500개만 남았다.
  1001개 입력도 500개로 잘려 일부 내용만 제출됐다. 브라우저 검사 3건 모두 실패했다.
- native maxlength를 제거하고 기존 코드 포인트 검증으로 제출을 검사하도록 수정했다.
- 동일 Chrome 검사 3건 통과: 작성·수정 1000개 모두 전송, 1001개는 입력을 유지하고 오류 안내 후 전송하지 않음.
- 범위: 실제 브라우저 입력 + API 대역. 민재의 실제 상세 페이지 연결을 검증한 것은 아니다.

### 작성 응답과 목록 갱신이 겹칠 때 중복·순서 오류

- 재현 검사 `0662b63`, 수정 `5a7bea5`.
- POST 응답이 대기 중일 때 refresh가 이미 저장된 댓글을 반환하면 기존 코드는 같은 ID를 다시 추가했다.
- 추가 검사에서 기대 `[새 댓글, 나중 댓글]`, 실제 `[새 댓글, 나중 댓글, 새 댓글]`로 실패했다.
- 새 댓글 응답보다 늦은 댓글의 GET이 먼저 도착하면 `[나중 작성, 먼저 작성]`으로 표시되는 검사도 실패했다.
- ID 기준으로 기존 항목과 병합하고 `createdAt`, `id` 오름차순으로 정리했다.
- 추가 2건을 포함한 댓글 Node 검사 13건 모두 통과했다. 실제 DB에 중복 행이 생긴다는 의미는 아니다.

최종 코드는 실습 오류가 모두 수정된 상태다. 원시 실행 로그는 주완 로컬의 Git 제외 경로에 보관했다.
