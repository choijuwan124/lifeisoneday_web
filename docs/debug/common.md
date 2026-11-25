# 주완 — 공통 준비 오류 재현 실습

날짜: 2026-11-26. 작업 이슈 #18, 실습 이슈 #24.
아래 사례는 정상 구현·테스트를 기준으로 **일부러 오류를 주입한 실습**이다.
실제 개발 중 우연히 발견한 오류로 발표하지 않는다. main에는 수정·재검증 후 병합한다.
각 코드 파일을 수정하고 검사·commit·push한 뒤 다음 파일을 작업했다.

## 1. FormData의 Content-Type 오류 — 완료

- 정상 기준: `a83159d`, 업로드 테스트 7개 통과.
- 재현 커밋: `29f9416` — 업로드에 `Content-Type: application/json`을 강제로 설정.
- 실행: `node --test frontend/tests/api-upload.test.mjs`
- 실제 결과: 7개 중 2개 실패. 두 호스트의 multipart boundary 검사 모두 실패.
- 실패 출력: `actual: 'application/json'`, `expected: /^multipart\/form-data; boundary=/`.
- 원인: FormData는 multipart 헤더와 본문의 boundary가 일치해야 하는데 JSON 헤더로 덮어썼다.
- 수정 커밋: `7543fd8` — 수동 Content-Type 제거, fetch가 경계를 생성하도록 처리.
- 재검증: 같은 7개 테스트 모두 통과. 파일 이름·본문 보존 검사도 통과.
- 범위: Node의 실제 Request/FormData 직렬화 검사이며 림정의 사진 API 업로드 검증은 아직 아니다.

## 2. 업로드 세션 쿠키 누락 — 완료

- 재현 커밋: `55d0bb7` — 업로드 옵션에 `credentials: omit`을 넣어 공통 include 설정을 덮어씀.
- 실행: `node --test frontend/tests/api-upload.test.mjs`
- 실제 결과: 7개 중 1개 실패. `actual: 'omit'`, `expected: 'include'`.
- 원인: FormData 전송은 정상이지만 로그인 세션 쿠키를 보내지 않는 요청이 구성됐다.
- 수정 커밋: `65a7123` — 덮어쓰기를 제거하고 공통 쿠키 설정을 유지.
- 재검증: 같은 7개 테스트 모두 통과.
- 범위: 요청의 credentials 설정 검증이다. 실제 브라우저의 쿠키 정책까지 검증했다고 주장하지 않는다.

## 3. 좋아요 UNIQUE 제약 누락 — 완료

- 정상 기준: `2bfd3f8`, 실제 MySQL 8.4 테스트 7개 통과.
- 재현 커밋: `986f18e` — `(diary_id, user_id)` UNIQUE 제약 제거.
- 실행: `MYSQL_BIN=<mysql 경로> MYSQL_TEST_SOCKET=<격리 서버 소켓> python3 scripts/test-social-schema.py`.
- 실제 결과: 7개 중 1개 실패. 동일 일기·사용자의 두 번째 INSERT가 성공했다.
- 실패 출력: `invalid SQL operation was accepted: INSERT INTO diary_likes(diary_id,user_id) VALUES (100,1)`.
- 원인: 애플리케이션 확인만으로는 중복을 DB에서 보장할 수 없다. UNIQUE가 빠지면 중복 행이 허용된다.
- 수정 커밋: `1f8dc25` — UNIQUE 제약 복구. 같은 7개 검사 모두 통과했고 중복 INSERT가 MySQL 1062로 거부됨.
- 범위: 매 실행마다 새 격리 DB를 생성·삭제했다. 기존 테이블에 IF NOT EXISTS를 재실행해 제약이 생긴다고 가정하지 않았다.

## 다음 실습

4. 요청 전체 업로드 제한 오류 — 진행 예정

테스트 원본은 `frontend/tests/api-upload.test.mjs`에 있다.
각 재현·수정 커밋은 GitHub의 commit 화면 또는 `git show <커밋>`으로 비교할 수 있다.
