# Git 규칙 (브랜치·커밋·PR)

> 💡 브랜치 관리 전략, 커밋 메시지, PR 규칙과 Issue·PR 템플릿을 정의하는 문서입니다. FE와 BE는 한 저장소를 함께 쓰므로 규칙도 하나로 통일하고, 영역은 `fe` / `be` 말머리로 구분합니다.

**읽는 법**

- 팀: 여러 명(담당 SCM·DBA·PM), 한 저장소(FE + BE), GitHub Issues·PR로 관리
- 요구사항 ID(USR-03 등)는 요구사항 명세서의 번호입니다.

---

## 1. 브랜치 전략 (GitHub Flow)

**`main` / `dev` / 작업 브랜치** 세 단계로 씁니다. 작업물은 먼저 `dev`에 모아 확인하고, 안정되면 `main`으로 올립니다.

| 브랜치 | 역할 | 규칙 |
| --- | --- | --- |
| `main` | 시연·제출용(배포하지 않음, 로컬 발표). 언제든 실행 가능한 상태 | 직접 push 금지. `dev`에서 오는 PR로만 병합 |
| `dev` | 개발 통합 브랜치. 작업물을 모아 확인하는 곳 | 직접 push 금지. 작업 브랜치 PR로만 병합 |
| 작업 브랜치 | 기능·수정 하나당 하나 | **`dev`에서 만들고**, 병합 후 삭제 |
| `hotfix/…` | `main`의 긴급 버그 수정 | `main`에서 만들어 `main`으로 PR, 병합 후 `dev`에도 반영 |

```
작업 브랜치 ──PR──▶ dev ──PR(릴리스)──▶ main
```

### 1-1 브랜치 이름

```
<타입>/<영역>-<이슈번호>-<짧은-설명>
```

| 예시 | 설명 |
| --- | --- |
| `feat/be-12-login-api` | 백엔드 로그인 API (수업은 `feature/…`로 썼지만 커밋 타입과 맞춰 `feat/…`로 줄여 쓴다) |
| `feat/fe-12-login-page` | 프론트 로그인 화면 |
| `fix/be-30-cash-negative` | 캐시가 0 미만이 되는 버그 수정 |
| `docs/db-erd` | 문서 작업 (이슈 없으면 번호 생략) |

- 영문 소문자와 하이픈(`-`)만 씁니다.
- 영역: `fe`, `be`, 둘 다 건드리면 생략합니다.

### 1-2 작업 순서

1. GitHub에서 **이슈를 만든다.**
2. `dev`을 최신으로 받고 작업 브랜치를 만든다.
3. 작업하고 커밋한다 (작게, 자주).
4. push 후 **`dev`으로 PR**을 올리고 팀원 1명을 리뷰어로 지정한다.
5. 승인되면 **Squash and merge** 하고 브랜치를 삭제한다. (수업 `01_Git`에서는 병합 커밋으로 합쳤다. 작업 브랜치 → `dev`에 Squash를 쓰는 것은 한 이슈의 커밋 이력을 하나로 합치려는 팀 결정이다.)
6. 시연·제출 시점마다 **`dev` → `main` PR**을 올려 병합한다 (이때는 **Create a merge commit**).

```bash
git switch dev && git pull
git switch -c feat/be-12-login-api
# 작업·커밋
git push -u origin feat/be-12-login-api
```

`dev` → `main`은 Squash 하지 않고 병합 커밋으로 합칩니다. Squash 하면 두 브랜치의 이력이 어긋나 다음 병합 때 충돌이 납니다.

---

## 2. 커밋 메시지

```
<타입>(<영역>): <한글 요약> (#이슈번호)
```

- **타입은 영어, 요약은 한글**로 씁니다.
- 영역은 `fe`, `be`, `docs`, 생략 가능.
- 요약은 50자 안팎, 마침표 없이 씁니다.

| 타입 | 용도 | 예시 |
| --- | --- | --- |
| `feat` | 기능 추가 | `feat(be): 퀴즈 제출·채점 API 추가 (#34)` |
| `fix` | 버그 수정 | `fix(fe): 홈 화면 레벨 표시 오류 수정 (#41)` |
| `docs` | 문서 | `docs: DB 설계서 ERD 갱신` |
| `refactor` | 동작 변화 없는 구조 개선 | `refactor(be): 캐시 지급 로직 분리` |
| `style` | 포맷·공백 (동작 무관) | `style(fe): 린트 경고 정리` |
| `test` | 테스트 | `test(be): 가점 계산 단위 테스트` |
| `chore` | 빌드·설정·패키지 | `chore: Gradle 의존성 추가` |

필요하면 본문에 이유를 적습니다 (제목과 한 줄 띄움).

---

## 3. PR 규칙

| 항목 | 규칙 |
| --- | --- |
| 제목 | 커밋 제목과 같은 형식 (예: `feat(be): 로그인 API (#12)`) |
| 크기 | 한 PR = 한 이슈. 대략 400줄 이하 |
| 승인 | **코드 오너(SCM 서수영, 보조 PM 이재성) 중 작성자가 아닌 1명의 승인** 필수(`.github/CODEOWNERS`). 본인 PR은 본인이 병합하지 않음 |
| 병합 방식 | 작업 브랜치 → `dev`은 Squash and merge, `dev` → `main`은 Create a merge commit |
| 릴리스 PR 제목 | `dev` → `main` PR도 허용된 타입(`chore` 등)을 쓴다. 예: `chore: dev → main (보상 서비스·공통 레이아웃 반영)`. `release:` 같은 타입은 쓰지 않는다 |
| PR 대상 | 작업 브랜치는 `dev`으로 (`main`으로 바로 올리지 않음). `hotfix`만 `main`. `main`으로 온 PR은 `main-source` 체크가 출발 브랜치를 확인한다(6장) |
| 리뷰 기한 | 24시간 안에 확인 (어려우면 댓글로 미리 알림) |
| 병합 조건 | 빌드·테스트 통과 + 승인 + 충돌 없음 |
| 이슈 연결 | 본문에 `Closes #번호` → 병합 시 이슈 자동 종료 |
| 충돌 | 작업 브랜치에서 `dev`을 먼저 받아 해결한 뒤 push |
| 문서 | API·DB가 바뀌면 같은 PR에서 API명세서·DB설계서도 수정 |

### 리뷰 방법

- 요청 사항은 `[필수]` / `[제안]` / `[질문]`으로 구분해 적습니다.
- 사람이 아니라 코드에 대해 말합니다.

### AI 리뷰 (CodeRabbit)

PR을 올리면 CodeRabbit(`.coderabbit.yaml`)이 변경 요약과 줄 단위 리뷰 코멘트를 한국어로 남깁니다. AI 리뷰는 팀원 리뷰를 대신하지 않고, **승인(Approve)과 병합(Merge)은 팀원이 합니다.**

| 항목 | 규칙 |
| --- | --- |
| 리뷰 시작 | PR을 올리거나 푸시한 뒤 리뷰가 시작됐는지 확인한다. 자동으로 시작되지 않으면(Public 저장소 스타 10개 미만 등) PR 코멘트에 `@coderabbitai review` 한 줄만 적는다. CodeRabbit이 이미 리뷰한 커밋이 쌓이면(기본 5개) 푸시해도 자동 증분 리뷰가 잠시 멈출 수 있으니, 그때도 같은 코멘트로 요청한다 |
| 리뷰 대상 | `dev`·`main`을 향한 PR. Draft PR은 리뷰하지 않음 |
| AI 코멘트 처리 | 각 코멘트에 **반영 / 반영하지 않는 이유를 답글로 설명 / 질문** 셋 중 하나로 답한다. 반영·설명은 답글을 단 뒤, 질문은 AI의 답을 확인하고 납득한 뒤 스레드를 닫는다. 답 없이 Resolve하거나 무시하고 병합하지 않는다 |
| 병합 조건 | 리뷰 대상 변경은 **푸시한 최신 커밋**의 CodeRabbit 리뷰가 끝났고(진행 중이면 기다림, 시작되지 않았으면 요청한 뒤 기다림) 새로 달린 코멘트까지 위 방식으로 모두 처리 + 코드 오너 중 작성자가 아닌 1명 이상 승인(3장) + 빌드·테스트 통과. 경로 필터(`.coderabbit.yaml`의 `path_filters`)로 제외된 변경은 제외 사유를 확인하고 팀원이 직접 리뷰한다. 최신 커밋의 변경이 **모두** 제외돼 그 증분 리뷰가 건너뛰어졌으면, 이전에 리뷰된 변경의 CodeRabbit 리뷰와 코멘트 처리를 마친 뒤 제외된 최신 변경은 팀원이 직접 리뷰한다(최신 커밋 리뷰 완료 조건은 이 직접 리뷰가 대신한다). PR 전체가 **모두** 제외돼 CodeRabbit 리뷰가 없는 경우에는 이 팀원 직접 리뷰가 CodeRabbit 리뷰 완료 조건을 대신한다 |
| AI 제안 코드 | 내가 설명할 수 있는 코드만 커밋하고, 반영한 뒤에는 로컬에서 실행해 확인한다 |
| 비밀번호·키 지적 | 가장 먼저 처리한다. 커밋에서 지우는 것만으로는 부족하고 해당 비밀번호·키를 바꾼다 |
| 질문·반박 | 코멘트에 `@coderabbitai 질문` 형식으로 답글을 달면 된다. AI 말에 동의하지 않으면 이유를 답글로 남긴다 |
| 막힐 때 | PR에 `@coderabbitai help`, `@coderabbitai configuration`으로 현재 상태를 확인한다 |
| 설정 변경 | `.coderabbit.yaml`은 PR에서 바꾸면 그 PR 리뷰에 바로 적용된다. 팀원 1명 승인을 받고 바꾼다 |

팀원 리뷰어는 AI가 놓치기 쉬운 부분(요구사항·명세서와 맞는지, 다른 팀원 기능과 연결되는지, 팀이 정한 용어와 맞는지)을 중심으로 봅니다.

#### 사용 방법 (전원)

| 상황 | 하는 일 |
| --- | --- |
| PR을 올림 | 설치·설정은 필요 없다. PR 화면에서 CodeRabbit의 요약과 줄 단위 코멘트를 확인한다. 리뷰가 안 시작되면 `@coderabbitai review` 코멘트를 단다 |
| 코멘트를 받음 | 코멘트마다 반영 / 이유 설명 / 질문 중 하나로 답한다. 반영·이유 설명은 답글을 단 뒤 스레드를 닫고, **질문은 답을 확인하고 납득한 뒤에** 닫는다(질문을 올린 직후에는 열어 둔다). 반영한 뒤 푸시하면 새 커밋만 다시 리뷰된다. 푸시한 최신 커밋의 리뷰가 끝났는지 확인하고, 시작되지 않았으면 `@coderabbitai review`로 요청한 뒤 끝날 때까지 기다린다 |
| Claude Code를 씀 | 저장소 폴더에서 Claude Code를 열면 루트 `CLAUDE.md`(팀 규칙 요약)를 자동으로 읽는다. 새 작업은 "이슈 #번호를 `CLAUDE.md`와 `docs/` 규칙대로 구현해 줘", 리뷰 반영은 "PR #번호의 CodeRabbit 코멘트를 `CLAUDE.md`의 처리 순서대로 반영해 줘"라고 시킨다. 코멘트의 **Prompt for AI Agents** 부분을 복사해 붙여넣어도 된다 |
| Claude가 고친 뒤 | 로컬에서 실행·테스트로 확인하고, 설명할 수 있는 수정만 커밋한다. 승인·병합·스레드 Resolve는 사람이 한다 |
| 규칙을 바꾸고 싶음 | `CLAUDE.md`·`.coderabbit.yaml`·이 문서는 작업 브랜치 → `dev` PR로 바꾸고 팀원 1명의 승인을 받는다. 그 PR 리뷰에 바로 적용된다 |

`CLAUDE.md`는 이 문서와 코딩컨벤션의 **요약**입니다. 내용이 다르면 `docs/`를 기준으로 하고, `docs/`를 바꾸면 `CLAUDE.md`도 같은 PR에서 맞춥니다.

---

## 4. Issue·PR 템플릿

`.github/` 폴더에 있어 GitHub에서 자동으로 불러옵니다.

| 파일 | 용도 |
| --- | --- |
| `.github/pull_request_template.md` | PR 작성 양식 |
| `.github/ISSUE_TEMPLATE/feature.md` | 기능 이슈 |
| `.github/ISSUE_TEMPLATE/bug.md` | 버그 이슈 |

### 라벨

| 라벨 | 의미 |
| --- | --- |
| `feat` `bug` `docs` `chore` | 작업 종류 |
| `fe` `be` | 영역 |
| `P1` `P2` `P3` | 우선순위 (P1이 가장 급함) |

---

## 5. FE·BE별 추가 규칙

| 구분 | FE (Thymeleaf 템플릿 · JavaScript) | BE (Spring Boot) |
| --- | --- | --- |
| 코드 스타일 | 들여쓰기 2칸(HTML·CSS·JS), 인라인 스크립트 대신 `static/js` 사용 | Google Java Style |
| 네이밍 | 템플릿·정적 파일 소문자 kebab-case, JS 변수·함수 camelCase | 클래스 PascalCase, 패키지 소문자 |
| PR 전 확인 | 서버를 띄워 화면이 열리는지 확인 (필요하면 스크린샷 첨부) | `moneybeetle/` 폴더에서 `./gradlew test` (저장소 루트에는 Gradle 래퍼가 없다) |

---

## 6. GitHub 설정 체크리스트

저장소 Settings → Branches에서 **`main`과 `dev` 두 브랜치 모두** 보호 규칙을 설정합니다. 기본(default) 브랜치는 `dev`으로 바꿔 두면 PR이 자동으로 `dev`을 향합니다.

- [ ] Require a pull request before merging
- [ ] Required approvals: **1**
- [ ] Require review from Code Owners: `.github/CODEOWNERS`의 서수영(`tjtndud2`)·이재성(`godgeul-glitch`) 중 한 명이 승인해야 병합된다. 서수영의 PR은 이재성이 승인한다
- [ ] Do not allow bypassing the above settings: 팀원 모두 저장소 admin이라 이것을 켜야 규칙을 건너뛰지 못한다
- [ ] `main`만: Require status checks에 `main-source` 추가. `.github/workflows/main-source-check.yml`이 PR의 출발 브랜치가 `dev` 또는 `hotfix/…`인지 확인해, 다른 브랜치에서 `main`으로 온 PR은 병합되지 않는다
- [ ] Require status checks to pass (CI 구성 후)
- [ ] 병합 방식은 Squash merge와 Merge commit을 허용 (Settings → General)
- [ ] Default branch를 `dev`으로 변경
- [ ] Automatically delete head branches
- [ ] CodeRabbit 설치 확인: Organization은 Settings → GitHub Apps, 개인 계정은 Settings → Applications → Installed GitHub Apps에 `coderabbitai`가 있고 이 저장소가 선택되어 있다
- [ ] 저장소가 Public인지, `application.yaml`·`.env`에 비밀번호가 올라가 있지 않은지 확인 (`.env`는 `.gitignore`에 포함)
