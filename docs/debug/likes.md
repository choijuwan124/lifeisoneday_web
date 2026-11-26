# 좋아요 검증과 화면 연결

작업 이슈는 [#19](https://github.com/choijuwan124/lifeisoneday_web/issues/19)이고 작업 브랜치는 `feat/likes`다.

- 조회는 `GET /api/groups/{groupId}/diaries/{diaryId}/likes`.
- 등록과 취소는 같은 경로 뒤에 `/me`를 붙여 `PUT`과 `DELETE`로 호출한다.
- 성공 응답은 `{diaryId, likeCount, likedByMe}`를 담은 공통 응답이다.

## 민재 화면 연결

일기 상세 HTML에 컨테이너와 스타일을 추가하고, 공통 API 스크립트 뒤와 `diary.js` 앞에 좋아요 스크립트를 로드한다.

```html
<link rel="stylesheet" href="../like/like.css">
<div id="diary-like-root"></div>
<script defer src="../like/like.js"></script>
```

상세 화면에서 일기장과 일기 ID를 정한 뒤 호출한다.

```js
const widget = await window.LikeUI.mount({
  root: document.getElementById("diary-like-root"),
  groupId,
  diaryId
});
```

서버 상태를 다시 조회할 때 `await widget.refresh()`를 호출한다. 화면을 해제할 때 `widget.destroy()`를 호출한다.
같은 root에 다시 연결하면 이전 위젯의 이벤트와 요소가 정리된다.

## 기본 검사

```powershell
.\backend\gradlew.bat -p backend build
node --test frontend/tests/auth.test.mjs frontend/tests/diary.test.mjs frontend/tests/api-upload.test.mjs frontend/tests/like.test.mjs
git diff --check
```

Gradle 전체 빌드와 Node 검사 24건이 통과했다. 서비스 검사는 저장소 대역을,
컨트롤러 검사는 MockMvc와 저장소 대역을 사용한다. 위젯 검사는 Node에서 DOM과 API 대역을 사용한다.

## 실제 환경 문제

- 시스템 PATH에 Java와 Node가 없어 Gradle이 `JAVA_HOME is not set`으로 중단됐다.
  Git 제외 폴더에 Java 21과 Node 22를 준비한 뒤 전체 빌드와 Node 검사를 다시 실행했다.
- 격리 MySQL 8.4.13의 실행 경로에 한글이 포함되면 Windows에서 서버 시작 시
  `Can't find error-message file 'C:\share\english\errmsg.sys'`와
  `Failed to set datadir to 'C:\data\'`가 출력됐다.
  격리 데이터 경로를 임시 ASCII 드라이브 문자로 매핑한 뒤 127.0.0.1:33317에서 기동했다.

## 실제 MySQL과 HTTP 검사

새 격리 DB에서 `SHOW TABLES`를 확인한 뒤 signup → group → diary → like → comment → photo 스키마를 순서대로 적용했다.
`SHOW CREATE TABLE diary_likes`에서 `uk_diary_likes_diary_user`와 두 외래키를 확인했다.
검사 스크립트는 실행할 때마다 새 시험 계정과 일기장을 만들므로 격리 DB에 연결한 앱으로 실행한다.

```powershell
.\backend\gradlew.bat -p backend bootJar
$env:LIKES_TEST_PORT = '18082'
node --test scripts/test-likes-http.mjs
```

위 포트에서 격리 MySQL에 연결한 최신 JAR를 새로 기동한 뒤 Node 명령을 실행한다.
실제 HTTP 세션과 MySQL을 사용한다. 화면 실습의 Node 검사는 DOM 대역을 사용한다.

## [실습] 1. 반복 등록의 중복 키 오류

- 재현 `c48244e`, 수정 `f45c261`. 의도적으로 중복 키 처리만 제거하고 MySQL UNIQUE는 유지했다.
- 위 HTTP 검사에서 같은 세션으로 같은 일기에 PUT을 두 번 보냈다. 둘 다 200이어야 했으나 첫 요청은 200, 두 번째는 500이었다.
- 실패 출력 `PUT /api/groups/3/diaries/5/likes/me: 서버 오류가 발생했습니다.`, `500 !== 200`.
- 원인은 MySQL이 두 번째 INSERT를 UNIQUE 위반으로 거부하고 그 오류가 500으로 전달된 것이다.
  오류 코드 1062와 해당 제약 이름이 일치하는 경우만 재등록 성공으로 처리하도록 복구했다.
- 동일 HTTP 검사 3건 재통과. 순차 PUT 2회와 삭제 직후 병렬 PUT 8회가 모두 200이었고 최종 GET 개수는 1이었다.

## [실습] 2. 다른 일기의 좋아요까지 합산

- 재현 `12ceca4`, 수정 `a3799b4`. 개수 집계의 일기 조건을 의도적으로 같은 일기장 조건으로 바꿨다.
- 격리 MySQL에서 같은 일기장 안의 두 시험 일기에 좋아요 2개와 1개를 각각 등록했다.
  `node --test --test-name-pattern 'likes stay scoped' scripts/test-likes-http.mjs`로 실제 HTTP 응답을 검사했다.
- 첫 일기의 개수는 2여야 했으나 3이었다. 실패 출력은 `+ likeCount: 3` / `- likeCount: 2`였다.
- 원인은 개수 쿼리가 같은 일기장의 다른 일기까지 더한 것이다. `WHERE diary_id = ?`로 범위를 복구했다.
- 동일 검사 재통과. 두 일기의 개수가 분리됐고, 한 사용자의 반복 취소 이후에도 다른 사용자의 좋아요가 남았다.
