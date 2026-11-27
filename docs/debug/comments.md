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

| 검사 | 범위 | 결과 |
| --- | --- | --- |
| `frontend/tests/comment.test.mjs` | Node의 DOM·API 대역 | 11건 통과 |
| 기존 프런트 검사 포함 전체 Node 검사 | Node 대역 | 35건 통과 |
| `CommentServiceTests` | 저장소 대역 (Mockito) | 15건 통과 — 아래 "검사 환경" 참고 |
| `CommentControllerTests` | MockMvc + 저장소 대역 | Gradle 실행 대기 |
| Gradle 전체 빌드 | 기존 로그인·일기·좋아요 검사 포함 | Gradle 실행 대기 |
| `scripts/test-comments-http.mjs` | 실제 MySQL + 실제 HTTP 세션 | 앱 실행 대기 |
| 실브라우저 연결 | 민재의 상세 화면 | 미검증 (민재 #22에서 연결) |

### 검사 환경

- 작업 환경에서 Maven Central·Gradle 배포 서버 접속이 차단되어 프로젝트 Gradle 빌드를 아직 실행하지 못했다.
- 그동안 Ubuntu 패키지의 Spring 4.3·Mockito 2 jar로 댓글 Java 파일을 예비 컴파일했다.
  서비스 검사 15건은 이 예비 환경에서 실행해 통과했다. 프로젝트의 Spring Boot 4 빌드 결과가 아니므로 Gradle로 다시 확인한다.
- 컨트롤러는 예비 컴파일만 했다. MockMvc 검사는 Spring 6 이상의 jakarta 서블릿이 필요해 Gradle에서만 실행할 수 있다.
- 격리 MySQL 8.0.46(별도 데이터 폴더, 포트 33318, 무작위 이름 DB)에 signup → group → diary → like → comment → photo 순서로 스키마를 적용했다.
  적용 전 `SHOW TABLES`는 비어 있었고, `SHOW CREATE TABLE diary_comments`로 CHECK·외래키·정렬 인덱스를 확인했다.
- jsdom과 실제 Chromium에서 API 대역으로 위젯을 띄워 HTML 모양 댓글이 글자로 표시되는지, 데스크톱·375px 화면 배치를 확인했다. 실제 서버와 연결한 브라우저 검사는 아니다.
