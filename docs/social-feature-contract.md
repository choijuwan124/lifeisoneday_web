# 좋아요·댓글·사진 공통 계약

작성: 주완, 2026-11-26. 이 문서는 기능 구현 전에 확정하는 계약이다.
이 공통 준비 PR은 아래 기능 API 자체를 구현하지 않는다.

## 작업 순서와 파일 소유

주완 공통 준비 #18 → 현준 #19 / 용준 #20 / 림정 #21 동시 개발
→ 민재 화면 통합 #22 → 주완 최종 검사 #23.

| 이름 | 브랜치 | 작업 위치 |
| --- | --- | --- |
| 주완 | `feat/social-common` | 공통 계약, SQL, API 클라이언트, 업로드 설정·검사 |
| 현준 | `feat/likes` | Java `like/`, `frontend/like/like.js`, `like.css` |
| 용준 | `feat/comments` | Java `comment/`, `frontend/comment/comment.js`, `comment.css` |
| 림정 | `feat/photos` | Java `photo/`, `frontend/photo/photo.js`, `photo.css` |
| 민재 | `feat/diary-social-ui` | 기존 `frontend/diary/*.html`, `diary.js`, `diary.css` |
| 주완 | 최종 통합 브랜치 | 기존 일기·그룹 삭제와 사진 정리 연결, 전체 검증·발표 문서 |

Java 루트는 `backend/src/main/java/com/example/project013982/`다.
각 작업은 최신 main에서 시작한다. 민재는 화면 틀을 먼저 준비할 수 있지만 실제 연결은 세 기능 병합 후 한다.
공통 계약 변경이 필요하면 이슈에 변경 이유를 남기고 주완과 합의한 뒤 적용한다.
파일 하나 작성·수정 → 가능한 검사 → 그 파일만 commit → push → 다음 파일 순서를 지킨다.

## 공통 API와 권한

- 기본 경로 `D = /api/groups/{groupId}/diaries/{diaryId}`.
- 기존 `ApiResponse(success, message, data)`와 `LOGIN_USER_ID` 세션을 사용한다.
- 모든 API는 현재 멤버인지, 일기가 해당 그룹에 속하는지 검사한다.
- 미로그인 401, 잘못된 입력 400, 비멤버·없는 일기·ID 불일치 404, 타인 변경 403.
- 사용자·작성자 ID는 세션에서 결정한다. 요청 본문의 사용자 ID는 신뢰하지 않는다.
- 가입자만 참여하며 방을 나간 뒤에는 좋아요·댓글·사진에도 접근할 수 없다.
- 날짜는 기존과 같이 ISO LocalDateTime 문자열을 사용한다.
- `api.js`는 data가 있으면 data를, null이면 전체 응답을 반환한다. 기능 JS는 null 응답을 명시적으로 처리한다.
- 기능 JS는 `api.js`를 사용하고 다른 API 기본 주소나 로그인 방식을 만들지 않는다.

## 현준 — 좋아요

| 요청 | 의미 | HTTP / data |
| --- | --- | --- |
| GET `D/likes` | 총 개수·내 상태 | 200 / LikeStatus |
| PUT `D/likes/me` | 내가 좋아요 등록 (본문 없음) | 200 / LikeStatus |
| DELETE `D/likes/me` | 내가 좋아요 취소 | 200 / LikeStatus |

LikeStatus: `{diaryId, likeCount, likedByMe}`.
PUT 재요청은 중복 생성하지 않고, DELETE 재요청도 성공한다. DB UNIQUE 제약으로 동시 중복을 막는다.
변경 후 응답에는 최신 개수와 내 상태를 담는다. 화면은 서버 응답으로 갱신한다.
기존 멤버가 남긴 좋아요는 방 나가기만으로 삭제하지 않는다.

## 용준 — 댓글

| 요청 | 본문 | HTTP / data |
| --- | --- | --- |
| GET `D/comments` | 없음 | 200 / Comment 배열 |
| POST `D/comments` | `{content}` | 201 / Comment |
| PUT `D/comments/{commentId}` | `{content}` | 200 / Comment |
| DELETE `D/comments/{commentId}` | 없음 | 200 / null |

Comment: `{id, diaryId, authorId, authorNickname, content, createdAt, updatedAt, canEdit, canDelete}`.
댓글은 오래된 순 `(created_at ASC, id ASC)`으로 표시한다. 빈 목록은 `[]`.
본문은 양끝 공백 제거 후 1~1000자이며 공백만 입력할 수 없다. HTML로 실행하지 않는다.
현재 멤버이면서 작성자인 사람만 수정·삭제할 수 있다. 방장에게 타인의 댓글 변경 권한은 없다.
방 나가기는 기존 댓글을 삭제하지 않는다. 댓글 ID와 일기 ID도 함께 검사한다.

## 림정 — 사진

파일 제한은 JPG/PNG, **5 MiB = 5,242,880 bytes**, 일기당 1장이다.
공통 multipart 설정은 파일 5MB, 요청 전체 8MB로 한다. 요청 크기는 multipart 경계·헤더 공간을 포함한다.

| 요청 | 본문 | HTTP / data |
| --- | --- | --- |
| GET `D/photo` | 없음 | 200 / Photo 또는 null |
| PUT `D/photo` | multipart/form-data, `file` 필드 1개 | 200 / Photo (신규·교체 동일) |
| GET `D/photo/content` | 없음 | 200 / 이미지 바이너리 |
| DELETE `D/photo` | 없음 | 200 / null (사진이 없어도 성공) |

Photo: `{id, diaryId, contentType, sizeBytes, createdAt, updatedAt, contentPath}`.
`contentPath`는 `D/photo/content` 형식의 상대 API 경로다. 실제 저장 경로·storage_key는 응답하지 않는다.
사진이 없는 일기의 metadata는 null, content는 404다. 일기 접근 권한은 사진 유무 검사보다 먼저 검사한다.
조회는 현재 멤버만, 첨부·교체·삭제는 현재 멤버이면서 일기 작성자만 가능하다.

- 비어 있는 파일·필드 누락 400, 허용하지 않는 실제 이미지 형식 415, 용량 초과 413.
- 확장자와 요청 Content-Type만 믿지 말고 이미지 해석 결과도 검사한다. 이미지 픽셀 크기 상한도 정해 과도한 메모리 사용을 막는다.
- 서버에서 UUID 기반 파일명을 만들고 저장 루트 내부 경로만 허용한다. 원본 파일명을 경로로 사용하지 않는다.
- `app.upload.directory`에 저장하며 정적 리소스로 공개하지 않는다. 이미지 응답도 인증·권한 검사를 거친다.
- 이미지 응답은 적절한 Content-Type, `Cache-Control: no-store`, `X-Content-Type-Options: nosniff`를 사용한다.
- 교체 전 새 파일 검사가 끝나야 한다. DB 저장 실패 시 새 파일을 정리하고 기존 파일·메타데이터를 유지한다.
- DB 트랜잭션 성공 후 이전 파일을 정리한다. 파일 삭제 실패는 재시도 가능한 정리 대상으로 기록한다.
- 일기당 UNIQUE 제약을 사용하고 동시 교체 시 일기 행 잠금 등으로 경쟁을 제어한다.

## 민재 — 화면 연결 계약

각 기능 JS는 자기 root 내부에 UI를 만들고 기존 HTML을 직접 변경하지 않는다.
root는 DOM Element이며 ID 문자열이 아니다. CSS는 `.like-widget`, `.comment-widget`, `.photo-widget` 아래로 범위를 제한한다.

| 화면 영역 | HTML 컨테이너 ID | 호출 함수 |
| --- | --- | --- |
| 상세 좋아요 | `diary-like-root` | `await LikeUI.mount({root, groupId, diaryId})` |
| 상세 댓글 | `diary-comment-root` | `await CommentUI.mount({root, groupId, diaryId})` |
| 상세 사진 | `diary-photo-root` | `await PhotoUI.mountViewer({root, groupId, diaryId})` |
| 작성·수정 사진 | `diary-photo-editor-root` | `await PhotoUI.mountEditor({root, groupId, diaryId})` |

전역 객체는 `window.LikeUI`, `window.CommentUI`, `window.PhotoUI`다.
LikeUI·CommentUI·사진 Viewer의 반환값은 `{refresh(): Promise<void>, destroy(): void}`다.
사진 Editor는 `{save(diaryId): Promise<Photo|null>, isDirty(): boolean, destroy(): void}`를 반환한다.
작성 화면의 초기 diaryId는 null이다. Editor는 선택·미리보기만 하고 save 전에는 업로드하지 않는다.
수정 화면은 기존 사진을 조회하고, 변경이 없으면 save에서 업로드·삭제 요청을 보내지 않는다.
save는 새 파일을 PUT, 명시적 삭제 선택은 DELETE로 처리하며 실패 시 오류를 던진다.
중복 mount를 방지하고 객체 URL·이벤트는 destroy에서 정리한다.

스크립트 순서: `api.js → app.js → logout.js → like/comment/photo.js → diary.js` (defer).
현준·용준·림정은 사용 예시를 PR에 남기고, 민재가 실제 HTML·diary.js에 연결한다.

### 일기 저장과 사진 실패

1. 민재: 제목·본문으로 기존 일기 POST/PUT을 실행한다.
2. 응답의 diaryId로 사진 Editor의 save(diaryId)를 호출한다.
3. 사진까지 성공하면 상세로 이동한다.
4. 일기는 저장됐지만 사진이 실패했다면 ‘일기는 저장됨 / 사진 업로드 실패’를 구분해 표시한다.
5. 생성된 diaryId를 유지하고 사진만 재시도한다. 일기 POST를 반복하지 않는다.
6. 페이지를 닫았다면 생성된 일기의 수정 화면에서 다시 사진을 추가할 수 있게 안내한다.

파일 전송은 `api.upload(path, formData)`를 사용한다 (PUT, 쿠키 포함).
FormData의 `file` 필드에 File을 넣고 Content-Type은 수동으로 지정하지 않는다.
사진 읽기는 `api.getBlob(contentPath)`로 받아 객체 URL로 표시하고 교체·화면 해제 때 revoke한다.
인증·403·404 오류는 위젯별 메시지로 안내하고, 공통 API의 401 이벤트 처리를 유지한다.

## 주완 — 삭제 연동과 마이그레이션

SQL 적용 순서: signup → group → diary → like → comment → photo-schema.sql.
새 테이블은 `diary_likes`, `diary_comments`, `diary_photos`다.
세 테이블의 diary_id FK는 ON DELETE CASCADE로 일기 삭제 시 함께 정리한다.
기존 GroupService는 일기 → 일기장 순서로 삭제하므로 DB의 자식 행도 정리된다.
**DB CASCADE는 사진 파일을 삭제하지 않는다.** 다음 작업은 사진 구현 후 주완의 #23에서 연결한다.

- 림정은 diaryId별 storage_key 수집과 안전한 파일 정리 메서드를 제공한다.
- 주완은 일기·일기장 삭제 트랜잭션 전에 사진 정리 대상을 수집하고, DB 성공 이후 파일을 정리한다.
- DB 실패 시 기존 사진 파일은 유지한다. 파일 정리 실패는 재시도 대상으로 기록한다.
- #23에서 DB·파일 모두 검증하기 전에는 사진 삭제 연동을 완료로 표시하지 않는다.

`CREATE TABLE IF NOT EXISTS`는 이미 있는 테이블의 제약을 바꾸지 않는다.
기존 테이블이 있다면 정의를 확인하고 별도 마이그레이션을 검토한다. 사용자 데이터를 초기화하지 않는다.

## 기록과 완료 기준

주완은 공통 전송·세션·제약·용량 설정의 여러 실패를 독립적인 실습으로 재현한다.
의도적 실습은 `debug-exercise` 레이블과 `[실습]` 커밋으로 표시한다. 실제 발견한 오류는 별도로 기록한다.
실패 로그·원인·수정·재검증을 남기며 실패한 상태를 main에 병합하지 않는다.
공통 준비 완료는 팀원이 이 문서·SQL·공통 함수를 사용할 수 있는 상태다. 세 기능 자체의 완성은 #19~#23의 별도 작업이다.
