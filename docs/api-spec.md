# API 명세

기본 주소: `http://localhost:8080` 또는 `http://127.0.0.1:8080`.
Java 21 / Spring Boot / JDBC / MySQL을 사용한다. 인증은 서버의 HTTP 세션과 `JSESSIONID` 쿠키로 처리한다.
프런트 주소는 동일 호스트의 5500 포트다. 요청에 `credentials: "include"`를 지정한다.

## 공통 응답

```json
{"success": true, "message": "처리되었습니다.", "data": {}}
```

실패는 `success: false`, `data: null`, 사용자에게 표시할 `message`를 반환한다.
`frontend/js/api.js`는 성공 시 `data`를 반환한다. `data`가 null이면 전체 응답을 반환한다.
실패 시 `error.message`, `error.status`를 가진 오류를 던진다. 401은 공통 메뉴에 인증 필요 이벤트를 알린다.
POST·PUT 본문은 `Content-Type: application/json`이다. 조회 응답은 브라우저에 캐시하지 않는다.

## 회원·인증

| 메서드 | 경로 | 본문 | 성공 |
| --- | --- | --- | --- |
| POST | `/api/auth/signup` | `username`, `password`, `nickname` | 201, data null |
| POST | `/api/auth/login` | `username`, `password` | 200, `{nickname}` |
| GET | `/api/auth/me` | 없음 | 200, `{id, nickname}` |
| POST | `/api/auth/logout` | 없음 | 200, data null |

- 아이디·닉네임 최대 50자, 비밀번호 최대 128자. 모두 필수다. 아이디·닉네임 양끝 공백을 제거한다.
- 중복 아이디·빈 값·틀린 로그인 정보는 400이다. 비밀번호는 PBKDF2 해시로 저장하며 응답하지 않는다.
- 로그인 성공 시 세션 ID를 재발급한다. `/me`는 세션을 새로 만들지 않으며 미로그인은 401이다.
- 로그아웃은 세션을 무효화한다. 미로그인 상태의 로그아웃도 성공한다.

## 단체 일기장

| 메서드 | 경로 | 본문 | 성공 |
| --- | --- | --- | --- |
| POST | `/api/groups` | `name`, 선택 `description` | 201, Group |
| GET | `/api/groups` | 없음 | 200, 참여 중인 Group 배열 |
| GET | `/api/groups/{groupId}` | 없음 | 200, Group |
| DELETE | `/api/groups/{groupId}` | 없음 | 200, data null |

Group: `id`, `name`, `description`, `ownerId`, `createdAt`.

- 이름은 필수, 양끝 공백 제거 후 최대 100자. 설명은 최대 500자이며 비어 있으면 null이다.
- 생성자는 OWNER 멤버로 자동 등록한다. 목록은 생성 시각·ID 내림차순이다.
- 조회는 현재 멤버만 가능하다. 삭제는 방장만 가능하다.
- 삭제하면 해당 일기장의 **모든 일기와 멤버 참여 정보**를 함께 삭제한다. 사용자 계정과 다른 일기장은 유지한다.
- 삭제는 DB 트랜잭션으로 처리한다. 중간 실패 시 앞선 삭제도 되돌린다.
- 미로그인 401, 잘못된 값 400, 존재하지 않거나 비멤버인 일기장 404, 일반 멤버의 삭제 403.

## 멤버·친구 초대·방 나가기

| 메서드 | 경로 | 본문 | 성공 |
| --- | --- | --- | --- |
| GET | `/api/groups/{groupId}/members` | 없음 | 200, Member 배열 |
| POST | `/api/groups/{groupId}/members` | `username` | 201, 추가한 Member |
| DELETE | `/api/groups/{groupId}/members/me` | 없음 | 200, data null |

Member: `userId`, `nickname`, `role` (`OWNER` 또는 `MEMBER`), `joinedAt`.

- 현재 멤버 누구나 가입한 친구를 아이디로 초대할 수 있다. 수락 절차 없이 즉시 MEMBER로 추가한다.
- 멤버 목록은 방장 우선, 참여 시각·ID 오름차순이다.
- 중복 초대 409, 미가입 아이디·비멤버·없는 일기장 404, 미로그인 401, 잘못된 입력 400.
- 방장은 나갈 수 없어 403을 반환한다. 방장 위임은 지원하지 않는다.
- 일반 멤버는 본인의 참여 정보만 삭제한다. **기존에 작성한 일기는 남아 있지만 탈퇴자는 접근할 수 없다.**

## 일기

| 메서드 | 경로 | 본문 | 성공 |
| --- | --- | --- | --- |
| POST | `/api/groups/{groupId}/diaries` | `title`, `content` | 201, Diary |
| GET | `/api/groups/{groupId}/diaries` | 없음 | 200, Diary 배열 |
| GET | `/api/groups/{groupId}/diaries/{diaryId}` | 없음 | 200, Diary |
| PUT | `/api/groups/{groupId}/diaries/{diaryId}` | `title`, `content` | 200, Diary |
| DELETE | `/api/groups/{groupId}/diaries/{diaryId}` | 없음 | 200, data null |

Diary: `id`, `groupId`, `authorId`, `authorNickname`, `title`, `content`, `createdAt`, `updatedAt`, `canEdit`, `canDelete`.

- 제목은 양끝 공백 제거 후 1~200자, 본문은 공백만 입력할 수 없으며 최대 10,000자다. 본문 줄바꿈은 보존한다.
- 작성자는 세션에서 결정한다. 클라이언트의 `authorId`를 사용하지 않는다.
- 목록은 작성 시각·ID 내림차순이다. 날짜는 시간대 오프셋 없는 ISO LocalDateTime 문자열이다.
- 조회·작성은 현재 멤버, 수정·삭제는 **현재 멤버이면서 작성자**인 경우만 허용한다.
- 방장도 타인의 개별 일기는 수정·삭제할 수 없다. 일기장 전체 삭제와 별개 정책이다.
- 미로그인 401, 비멤버·없는 일기·그룹과 일기 ID 불일치 404, 타인 수정·삭제 403, 입력 오류 400.
- `canEdit`, `canDelete`는 현재 요청 사용자 기준이다. 버튼 숨김과 별개로 서버에서도 권한을 검사한다.

## 화면 연결

`index.html → auth/login.html → group/group-list.html → group/group-detail.html`

그룹 상세에서 `member/member-manage.html`, `diary/diary-list.html`, `diary/diary-create.html`로 이동한다.
일기 화면은 `groupId`, 상세·수정 화면은 추가로 `diaryId` 쿼리 파라미터를 유지한다.
비로그인 상태로 보호 화면에 접근하면 로그인 화면으로 이동한다.

예상하지 못한 서버 오류는 500과 일반 오류 메시지로 응답한다. 오류 응답에 비밀번호·DB 접속 정보를 노출하지 않는다.
