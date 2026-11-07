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
| `main` | 시연·제출·배포용. 언제든 실행 가능한 상태 | 직접 push 금지. `dev`에서 오는 PR로만 병합 |
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
| `feat/be-12-login-api` | 백엔드 로그인 API |
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
5. 승인되면 **Squash and merge** 하고 브랜치를 삭제한다.
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
| 승인 | **작성자를 뺀 팀원 1명의 승인** 필수. 본인 PR은 본인이 병합하지 않음 |
| 병합 방식 | 작업 브랜치 → `dev`은 Squash and merge, `dev` → `main`은 Create a merge commit |
| PR 대상 | 작업 브랜치는 `dev`으로 (`main`으로 바로 올리지 않음). `hotfix`만 `main` |
| 리뷰 기한 | 24시간 안에 확인 (어려우면 댓글로 미리 알림) |
| 병합 조건 | 빌드·테스트 통과 + 승인 + 충돌 없음 |
| 이슈 연결 | 본문에 `Closes #번호` → 병합 시 이슈 자동 종료 |
| 충돌 | 작업 브랜치에서 `dev`을 먼저 받아 해결한 뒤 push |
| 문서 | API·DB가 바뀌면 같은 PR에서 API명세서·DB설계서도 수정 |

### 리뷰 방법

- 요청 사항은 `[필수]` / `[제안]` / `[질문]`으로 구분해 적습니다.
- 사람이 아니라 코드에 대해 말합니다.

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
| PR 전 확인 | 서버를 띄워 화면이 열리는지 확인 (필요하면 스크린샷 첨부) | `./gradlew test` |

---

## 6. GitHub 설정 체크리스트

저장소 Settings → Branches에서 **`main`과 `dev` 두 브랜치 모두** 보호 규칙을 설정합니다. 기본(default) 브랜치는 `dev`으로 바꿔 두면 PR이 자동으로 `dev`을 향합니다.

- [ ] Require a pull request before merging
- [ ] Required approvals: **1**
- [ ] Require status checks to pass (CI 구성 후)
- [ ] 병합 방식은 Squash merge와 Merge commit을 허용 (Settings → General)
- [ ] Default branch를 `dev`으로 변경
- [ ] Automatically delete head branches
