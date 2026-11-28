# 일기 소셜 UI 연동 디버깅 기록

담당: 민제
브랜치: `feat/diary-social-ui`
기준: `main`에서 분기. 병합은 수행하지 않음.

## 구현 범위

- `frontend/diary/diary-detail.html`: 기존 일기 내용과 작성자/액션 영역을 유지하면서 좋아요, 댓글, 사진 영역을 추가하고 `api.js → app.js → auth/logout.js → like.js → comment.js → photo.js → diary.js` 순으로 로드한다.
- `frontend/diary/diary-create.html`, `diary-edit.html`: 사진 선택 UI와 실패 후 재시도 상태 영역을 연결했다.
- `frontend/diary/diary.js`: 상세 화면의 좋아요/댓글/사진 위젯을 연결하고, 작성에서는 일기 POST 성공 후 발급된 ID로 사진을 저장한다. 수정에서는 일기 PUT 성공 후 사진을 저장한다. 사진 저장 실패 시 일기 본문을 다시 보내지 않고 사진만 재시도한다. 저장 중 중복 제출과 저장 완료 전 화면 이동을 막고, 페이지 종료 때 위젯/에디터를 해제한다.
- `frontend/diary/diary.css`: 반응형 배치, 긴 텍스트 줄바꿈, 포커스 표시, 사진 재시도 및 위젯 오류 안내를 추가했다.
- `frontend/tests/diary.test.mjs`: 기존 테스트와 DOM/API 모의 기반 회귀 테스트를 확장했다.

상세 사진 업로드의 서버 저장·권한 정책 및 서버의 사진 삭제 연동은 백엔드 변경 없이 검증할 수 없다. 그룹 멤버 검증은 기존 API 응답을 기준으로 UI에서 처리한다.

## 검증

프론트 회귀 테스트:

```sh
node --test frontend/tests/auth.test.mjs frontend/tests/diary.test.mjs frontend/tests/api-upload.test.mjs frontend/tests/like.test.mjs frontend/tests/comment.test.mjs frontend/tests/photo.test.mjs
```

결과: 59개 통과, 실패 0개. 테스트는 Node의 DOM/API 모의 환경에서 수행했다.

백엔드 빌드:

```powershell
$env:JAVA_HOME = '<Temurin JDK 21 경로>'
$env:GRADLE_USER_HOME = '<저장소 내 Gradle 캐시 경로>'
.\backend\gradlew.bat -p backend build --no-daemon --console=plain
```

결과: `compileJava`, `compileTestJava`, `bootJar`, `jar`, `assemble`은 성공했다. 테스트 77개 중 50개가 Mockito inline/Byte Buddy 에이전트 attach 오류(`InstrumentationImpl.appendToBootstrapClassLoaderSearch0`, `IllegalArgumentException`)로 실패했다. `-Djdk.attach.allowAttachSelf=true`와 Byte Buddy javaagent 지정도 시도했으나 같은 런타임 오류가 났다. 이 작업에서는 무관한 백엔드 테스트 설정을 변경하지 않았다.

MySQL 서비스 및 실제 백엔드 연결 설정을 사용할 수 없어 실제 DB 통합 확인은 수행하지 않았다. 로컬 mock 서버를 띄우고 브라우저 접속을 시도했지만 원격 in-app 브라우저에서 로컬 loopback에 연결되지 않았다. 따라서 실제 브라우저 렌더링 및 백엔드/DB 연동은 미검증이다. API 모의 기반 작성/수정 업로드 실패-재시도 smoke 확인은 완료했다.

## 의도적 오류 재현 및 수정

세 실습 모두 Node 테스트와 API 모의만 사용했으며 실제 데이터는 변경하지 않았다.

### 1. 상세 위젯에 groupId 누락

- 재현 커밋: `c7aec12` (`[실습] test: reproduce missing groupId in detail widget`)
- 재현 명령: `node --test --test-name-pattern "detail mounts like" frontend/tests/diary.test.mjs`
- 재현 결과: `undefined !== 4` (실패). 위젯에 URL에서 확인한 groupId 대신 undefined가 전달됐다.
- 수정 커밋: `eb44ea9` (`fix: pass group ID to detail widgets`)
- 같은 명령 재실행: 선택 테스트 1개 통과.

### 2. 사진 재시도에서 일기 POST 중복

- 재현 커밋: `b4018ca` (`[실습] test: reproduce duplicate diary POST on photo retry`)
- 재현 명령: `node --test --test-name-pattern "creation photo retry reuses" frontend/tests/diary.test.mjs`
- 재현 결과: 기대 POST 1회, 실제 2회 (`2 !== 1`). 사진 재시도 경로에서 일기 생성 요청까지 재실행됐다.
- 수정 커밋: `7a20314` (`fix: retry photo without recreating diary`)
- 같은 명령 재실행: 선택 테스트 1개 통과.

### 3. 사진 저장 완료 전 상세 화면 이동

- 재현 커밋: `1477858` (`[실습] test: reproduce early navigation before photo save`)
- 재현 명령: `node --test --test-name-pattern "duplicate create submits are blocked" frontend/tests/diary.test.mjs`
- 재현 결과: 사진 저장 Promise가 대기 중인데 상세 URL로 이동했다. 테스트가 이동이 없어야 한다고 기대했지만 실제 이동 URL은 `http://localhost/diary/diary-detail.html?groupId=4&diaryId=8`이었다.
- 수정 커밋: `29802c8` (`fix: wait for photo save before navigation`)
- 같은 명령 재실행: 선택 테스트 1개 통과.

## 후속 확인

- 실제 브라우저에서 반응형 화면 및 사진 업로드 재시도 확인
- MySQL이 연결된 백엔드 환경에서 생성/수정/상세 및 접근 제어 확인
- 사진 삭제 정책 및 서버 연동은 담당 서버 기능(#23)과 통합 시 확인
