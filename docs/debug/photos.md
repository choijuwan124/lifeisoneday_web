# 사진 기능 검사·실습 기록

사진 제한은 JPG/JPEG·PNG, 파일 최대 5 MiB(5,242,880 bytes), 최대 20,000,000 pixels다. 픽셀 상한은 이미지 전체를 디코딩하기 전에 크기를 확인하는 기준이다. 파일 확장자와 multipart Content-Type은 저장 형식 판정에 사용하지 않고, 이미지 리더가 확인한 실제 포맷을 저장한다.

저장 디렉터리는 `app.upload.directory`이며 기본값은 백엔드 실행 기준 `../.local/uploads`다. UUID 파일명만 허용한다. API metadata에는 `storageKey`와 실제 경로를 내보내지 않는다. 바이너리는 멤버십 확인 후 반환하고 `Cache-Control: no-store`, `X-Content-Type-Options: nosniff`를 설정한다.

## 화면 연결 — 민재

상세 HTML에는 다음 영역과 스타일을 추가한다. 스크립트는 공통 로더 순서에 맞춰 `api.js` 다음, `diary.js` 전에 둔다.

```html
<link rel="stylesheet" href="../photo/photo.css">
<div id="diary-photo-root"></div>
<div id="diary-photo-editor-root"></div>
<script defer src="../photo/photo.js"></script>
```

상세 화면:

```js
const viewer = await PhotoUI.mountViewer({
  root: document.getElementById("diary-photo-root"), groupId, diaryId
});
await viewer.refresh();
viewer.destroy();
```

작성·수정 화면:

```js
const editor = await PhotoUI.mountEditor({
  root: document.getElementById("diary-photo-editor-root"), groupId, diaryId: diaryId ?? null
});
// 기존 일기 POST/PUT 성공 후 반환된 ID로 사진만 저장한다.
await editor.save(savedDiary.id);
if (editor.isDirty()) { /* 사진 저장이 실패하면 같은 savedDiary.id로 재시도 */ }
editor.destroy();
```

작성 중에는 로컬 미리보기만 만든다. `save(savedDiary.id)` 전에는 업로드하지 않는다. 사진 업로드 실패 시 이미 저장된 일기를 다시 POST하지 않고 같은 ID로 `save`를 재호출한다. 사진 삭제는 삭제 버튼을 누른 뒤 `save`했을 때만 서버에 반영된다. 사진 선택 취소는 원래 사진 상태로 되돌아간다.

## 파일 삭제 연동 — 주완

`PhotoService.storageKeysForDiaryDeletion(diaryId)` 또는 `storageKeysForGroupDeletion(groupId)`를 **삭제 DB 트랜잭션 안에서 DB CASCADE 전에** 호출해 키 목록을 보관한다. DB 커밋이 성공한 다음 `cleanupAfterDeletionCommit(keys)`를 호출한다. DB 롤백이면 파일 정리를 호출하지 않는다. 그룹 삭제는 그룹 행 → 일기 행 순서로 잠그고, 사진 키 조회는 일기 행을 ID 순서로 잠근다. 사진 업로드·교체·삭제는 일기 행을 먼저 잠근 뒤 멤버십 행을 잠근다.

교체·삭제 후 파일 정리에 실패하면 `app.upload.directory/.cleanup-pending`에 UUID 키를 기록한다. 애플리케이션 시작 때 자동 재시도하고, 필요하면 `PhotoCleanupService.retryPending()`을 다시 호출한다. 큐에는 경로가 아닌 서버 생성 키만 들어간다. metadata cascade가 실제 파일을 지우지는 않으므로 #23에서 실제 삭제 통합·격리 DB 확인을 완료하기 전에는 일기·그룹 삭제 연동 완료로 표시하지 않는다.

## 검사 범위

| 검사 | 범위 | 상태 |
| --- | --- | --- |
| `PhotoStorageTests` | 실제 Java ImageIO JPEG/PNG, 5 MiB 경계, 손상·위조·픽셀 상한, 임시 파일 | 통과 |
| `PhotoServiceTests` | Mockito 저장소 대역의 권한·DB 오류 정리·트랜잭션 callback | 통과; 실제 DB 롤백이 아님 |
| `PhotoControllerTests` | MockMvc 세션·JSON·multipart·바이너리 헤더·상태 | 통과 |
| `frontend/tests/photo.test.mjs` | Node DOM/API 대역의 선택·취소·교체·재시도·URL·destroy | 통과 |
| 전체 Gradle 빌드 | 기존 기능 포함 Java 74건 | 통과 |
| 전체 Node 회귀 검사 | 기존 기능 포함 42건 | 통과 |
| `scripts/test-photos-http.mjs` | 격리 MySQL + 앱 세션 쿠키 + 실제 HTTP 이미지 파일 | 미실행; 이 PC의 격리 DB/app이 준비되지 않음 |
| 실제 Chrome 파일 선택·미리보기 | 실제 브라우저 UI | 미실행; API 대역 Node 검사만 수행 |
| 민재의 일기 페이지 연결 | 전체 앱 브라우저 통합 | 미실행; #22에서 연결 후 확인 필요 |

HTTP 실검사는 폐기 가능한 테스트 DB와 임시 업로드 폴더에 연결된 앱에서만 실행한다.

## [실습] 1. 정확히 5 MiB 사진 거부

- 구분: 정상 구현 후 의도적으로 오류를 주입한 실습이다.
- 기준 검사: `PhotoStorageTests` 4건 통과. 정확히 5 MiB인 유효 PNG와 5 MiB+1 byte를 각각 검사한다.
- 재현 커밋: `72791b5d89e2ee0e423cc3b173c03c5105d41875`. 파일 크기 조건을 `>= MAX_BYTES`로 변경했다.
- 실행: `.\backend\gradlew.bat -p backend test --tests '*PhotoStorageTests' --no-daemon --console=plain`.
- 실제 실패: 4건 중 1건. `PhotoStorageTests.acceptsExactFiveMiBAndRejectsOneByteMore`에서 정확히 5 MiB를 업로드할 때 `PhotoTooLargeException` 발생.
- 원인: 허용 경계값과 초과값을 모두 같은 오류 조건에 넣었다.
- 수정 커밋: `a13dc3cd4e234359b9b5cabf285d846a17f68775`. 조건을 `> MAX_BYTES`로 복원했다.
- 재검증: 같은 명령에서 4건 통과. 5 MiB 허용, 5 MiB+1 거부.
- 범위: 실제 생성된 PNG 바이트와 임시 파일을 사용한 Java 단위 검사. 실제 HTTP 요청 검증은 아니다.
