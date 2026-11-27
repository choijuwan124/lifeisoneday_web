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

## [실습] 2. 파일 선택 취소 후 불필요한 업로드

- 구분: 정상 UI 검사 통과 후 의도적으로 오류를 주입했다.
- 기준 검사: `node --test frontend/tests/photo.test.mjs` 6건 통과. 새 사진 선택을 취소한 뒤 같은 일기 ID로 save해도 요청이 없고, 기존 사진 URL이 미리보기에 유지되는지 확인한다.
- 재현 커밋: `e1066c9414a46ee128e60b4f0614ae71c655503b`. 파일 입력 `cancel`에서 선택 파일 상태를 초기화하지 않도록 변경했다.
- 실행: `node --test frontend/tests/photo.test.mjs`.
- 실제 실패: 6건 중 2건. 취소 후 save가 `upload` 요청을 보냈다(기대 `[]`, 실제 `['upload']`). 기존 사진 교체 취소 검사에서도 원래 미리보기 `blob:1` 대신 취소한 파일 `blob:2`가 남았다.
- 원인: 파일 선택 창의 취소 이벤트가 dirty 선택 상태와 임시 미리보기 URL을 원래 서버 상태로 되돌리지 않았다.
- 수정 커밋: `8d47cc579d4c6e98f6e3638417bb84ee738e7d93`. 취소 이벤트가 선택 파일·URL을 정리하고 기존 사진 상태를 복원하도록 연결했다.
- 재검증: 같은 명령에서 6건 통과. 취소 후 PUT·DELETE가 없고, 기존 사진은 계속 표시되며, 취소한 임시 URL은 revoke된다.
- 범위: Node의 DOM·API 대역 검사. 파일 선택 대화상자를 띄운 실제 Chrome 검증은 아니다.

## [실습] 3. metadata 저장 실패 뒤 새 파일 잔류

- 구분: 정상 서비스 검사 통과 후 일부러 저장소 정리 호출을 제거했다.
- 기준 검사: `PhotoServiceTests` 5건 통과. 추가 파일 검사는 실제 임시 디렉터리, ImageIO로 만든 정상 PNG, 실제 `PhotoStorage`·`PhotoCleanupService`, 저장 실패를 던지는 Mockito `PhotoRepository` 대역을 사용한다.
- 최초 재현 커밋: `a6ef9a73dd38e3b801688e9992b9ce2474261d41`. DB 대역 오류 경로에서 새 키 정리를 생략했다. `PhotoServiceTests` 5건 중 1건이 `WantedButNotInvoked`로 실패했다.
- 실제 파일 재현 검사 추가 커밋: `6857655`. 정리 누락 상태에서 파일 검사를 실행해도 검사로 확인되도록 임시 디렉터리 검사를 별도 추가했다.
- 파일 재현 커밋: `71399b761945fd0c4170d3b049c89758bfb05e0a`.
- 실행: `.\backend\gradlew.bat -p backend test --tests '*PhotoServiceTests.realNewFileIsRemovedAfterRepositoryFailureAndOldFileAndMetadataRemain' --no-daemon --console=plain`.
- 실제 실패: 1건. `realNewFileIsRemovedAfterRepositoryFailureAndOldFileAndMetadataRemain`에서 임시 폴더가 기존 키 하나만 포함하기를 기대했지만 새 UUID PNG가 남아 파일 목록 검사가 실패했다.
- 원인: metadata 저장 예외 경로에서 새 저장 파일을 삭제하지 않았다.
- 수정 커밋: mock 실패 경로 `2f8b07d`, 실제 파일 경로 `662194f8e1217bad4368a86f2197854aacbc641f`. 저장 예외 때 새 키를 즉시 정리하도록 복구했다.
- 재검증: 같은 파일 검사 1건 통과. 기존 PNG 바이트·metadata 대역 값은 유지되고 임시 폴더에는 기존 파일만 남았다.
- 검증 한계: 저장소는 Mockito 대역이며 실제 MySQL 연결·롤백 검증은 아니다. 실제 사진 파일 저장·삭제는 임시 디렉터리에서 수행했다.

## [실습] 4. 미리보기 객체 URL 해제 누락

- 구분: 정상 미리보기 URL 검사 통과 후 실제 `URL.revokeObjectURL` 호출을 일부러 제거했다.
- 기준 검사: `node --test frontend/tests/photo.test.mjs` 7건 통과. 선택 교체·취소·저장·삭제·Viewer refresh·destroy 시 URL 수명과, 아직 표시하거나 취소 복원에 필요한 URL을 너무 일찍 해제하지 않는지 검사한다.
- 재현 커밋: `2bd805503076fef02aead0c15ea47d3baac1cea7`.
- 실행: `node --test frontend/tests/photo.test.mjs`.
- 실제 실패: 7건 중 6건. 선택 취소 기대 `['blob:1']`, 실제 `[]`; 기존 사진 교체 취소 기대 `['blob:2']`, 실제 `[]`; 로컬 선택 교체·삭제·destroy·Viewer refresh에서도 해제 배열이 비어 있어 각 기대값과 달랐다.
- 원인: UI의 URL 추적 집합에서 항목만 지우고 브라우저 URL 해제 API를 부르지 않았다.
- 수정 커밋: `c1c94b1e4ef92b8825dfae632c85dc5c820a6142`. 추적 중인 URL마다 실제 `URL.revokeObjectURL`을 호출한다.
- 재검증: 같은 명령에서 7건 통과. 현재 표시 중인 URL은 교체 전까지 유지하고, 취소·교체·삭제·destroy에서 더 이상 표시하지 않는 URL만 해제한다.
- 범위: Node의 URL API 대역 검사다. 실제 Chrome 메모리 프로파일 검증은 아니다.
