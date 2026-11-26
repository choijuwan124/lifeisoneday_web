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
