# 팀 작업 안내
실제 기능 담당은 [프로젝트 소개](../main.md), 작업 범위와 순서는 [계획표](../plan)를 기준으로 한다.
Windows 팀원은 아래 명령을 **Git Bash**에서 실행한다. 맥 터미널도 동일하다.

## 처음 받을 때
GitHub 저장소 초대를 수락하고 본인 계정으로 로그인한다. 처음 한 번만 clone한다.

```bash
git clone https://github.com/choijuwan124/lifeisoneday_web.git
cd lifeisoneday_web
git config user.name "본인 이름"
git config user.email "본인 GitHub 이메일"
```

이미 폴더가 있으면 다시 clone하지 말고 그 폴더의 터미널을 연다.
IntelliJ는 `backend`, VS Code는 프로젝트 또는 `frontend` 폴더를 연다.
실행 설정은 [로컬 개발 안내](local-development.md)를 따른다.

## 새 작업 시작
`git status`를 먼저 보고, 저장하지 않은 변경이 있으면 현재 작업 브랜치에서 먼저 정리한다.
다른 사람 작업을 없애려고 reset --hard나 강제 push를 사용하지 않는다.

```bash
git status
git switch main
git pull --ff-only origin main
git switch -c feat/my-next-feature
```

`feat/my-next-feature`는 작업 내용을 나타내는 새 브랜치 이름으로 바꾼다.
진행 중인 작업을 이어갈 때는 그 브랜치로 `git switch`한다. 매번 새 브랜치를 만들지 않는다.

## 파일 하나씩 개발
**여러 파일을 한꺼번에 만든 다음 커밋을 쪼개지 않는다.**
AI에게도 아래 순서를 지키도록 전달한다.

1. 현재 코드와 API 명세를 읽고 이번에 만들 파일 하나를 정한다.
2. 그 파일만 작성·수정하고 가능한 컴파일·구문·동작 검사를 한다.
3. `git diff`로 변경 내용을 확인한다.
4. 그 파일 경로를 지정해 add → commit → push한다.
5. push가 성공한 뒤 다음 파일을 시작한다.
6. 기능 연결 후 전체 빌드·회귀 테스트·브라우저 시연을 확인한다.

예시(실제로 변경한 파일 경로와 메시지로 바꿔서 실행):
```bash
git diff
git add frontend/diary/diary.js
git commit -m "fix: 일기 저장 실패 안내 개선"
git push -u origin HEAD
```

커밋 전에 `git diff --cached --stat`으로 한 파일만 올라가는지 확인한다.
같은 파일을 나중에 다시 고쳤다면 새 수정 커밋을 남긴다. 정상적인 개발 기록이다.
커밋 메시지는 따옴표를 끝까지 닫는다. `dquote>`가 나오면 Ctrl+C로 취소하고 명령을 다시 입력한다.

## PR과 병합
1. GitHub 저장소에서 **Pull requests → New pull request** 선택.
2. base는 `main`, compare는 본인 작업 브랜치로 지정.
3. 구현 내용과 검증 결과를 적고 PR 생성. 링크를 조장에게 전달.
4. 조장은 변경 파일·권한 처리·빌드·실행 결과·충돌 여부를 확인.
5. **Create a merge commit**으로 병합. 수업용 파일별 커밋을 유지하기 위해 squash하지 않는다.
6. 각자 변경 중인 작업을 먼저 정리한 뒤 `git switch main` → `git pull --ff-only origin main` 실행.
7. 백엔드를 재시작하고 프런트 새로고침. 새 SQL이 있으면 안내된 순서로 적용.

충돌이 있으면 어느 변경을 유지할지 확인한 뒤 해결한다. 다른 사람 코드를 통째로 덮어쓰지 않는다.
DB 비밀번호·개인 설정·`.local/`·발표 계정 정보는 커밋하지 않는다.

## 회의·발표에서 보여줄 기록
- 기능 추가 순서와 담당 파일
- 파일별 commit, 기능별 branch, PR 검토와 merge
- 실제 오류를 발견하고 수정한 커밋
- 전체 기능 시연과 테스트 결과

커밋 개수만 늘리기 위한 빈 커밋이나 의미 없는 파일 변경은 만들지 않는다.
