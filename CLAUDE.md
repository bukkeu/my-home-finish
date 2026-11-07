# MoneyBeetle — Claude Code / CodeRabbit 작업 규칙

이 파일은 Claude Code와 CodeRabbit이 **같은 기준**으로 코드를 쓰고 리뷰하도록 팀 규칙을 요약한 것입니다. 자세한 내용과 근거는 `docs/`의 문서가 기준이며, 충돌하면 `docs/`가 우선합니다.

## 프로젝트

- 경제 학습 + 모의투자 + 모의 청약 서비스. 저장소: `moneybeetle/`(Spring 서버), `moneybeetle-page/`(HTML 프로토타입, 참고용), `docs/`(설계 문서).
- 스택: Java 21, Spring Boot 3.5, Spring Data JPA, Spring Security(세션 로그인), **Thymeleaf 서버 렌더링**, MySQL 8, Gradle.
- 작업 = GitHub 이슈 1개 = 브랜치 1개 = PR 1개. 이슈 번호는 저장소 `bukkeu/my-home-finish` 기준이고, 담당자는 이슈의 Assignee와 `docs/MoneyBeetle_역할분담.md`를 따른다.

## 기준 문서 (`docs/`)

| 무엇을 정하나 | 문서 |
| --- | --- |
| 기능·규칙·데이터 | `MoneyBeetle_요구사항_명세서.md`, `MoneyBeetle_정책정의서.md`, `MoneyBeetle_DB설계.md`, `MoneyBeetle_API명세서.md`, `MoneyBeetle_화면설계.md` |
| 코드 쓰는 방식 | `MoneyBeetle_코딩컨벤션.md` |
| 브랜치·커밋·PR | `MoneyBeetle_Git규칙.md` |
| 환경변수·비밀값 | `MoneyBeetle_환경변수.md` |
| 누가 무엇을 맡는가 | `MoneyBeetle_역할분담.md` |

기능·데이터는 명세서를, 코드 구조는 코딩컨벤션을 따른다. **API·DB를 바꾸면 같은 PR에서 API명세서·DB설계도 고친다.**

## 코드 규칙 (요약)

- **패키지는 기능(도메인)별**: `com.wanted.moneybeetle.domain.<도메인>/{controller, exception, model/{dao, dto, entity, service}}`, 공통은 `global`. 관리자·강사용 Controller·Service도 해당 도메인 안에 두고 `admin` 패키지를 따로 만들지 않는다.
- **요청 흐름**: Controller → Service → Repository(`model/dao`) → DB. Controller는 DTO만 다루고 `try-catch`와 업무 규칙을 두지 않는다. Controller가 Repository를 직접 부르지 않는다. 트랜잭션·Entity↔DTO 변환은 Service.
- **Entity**: `@Setter`·`@Data` 금지, 값 변경은 `changeXxx()` 같은 이름 있는 메서드, 생성은 정적 팩토리(`create(...)`), 기본 생성자는 `protected`. 테이블이 기준(`ddl-auto: none`)이며 Entity가 테이블에 맞춘다. NULL 가능 컬럼은 래퍼 타입. 수정은 `save()` 대신 변경 감지.
- **다른 도메인은 ID로 참조**: 다른 도메인의 Entity를 `@ManyToOne`으로 잇지 않는다. 다른 도메인의 Repository도 직접 쓰지 않고 그 도메인의 **Service 메서드만** 호출한다(`docs/MoneyBeetle_역할분담.md` §7 연동 약속).
- **규칙의 위치**: 현재 상태로 판단되는 규칙은 Entity, 입력값만으로 판단되는 규칙은 DTO, DB 조회·다른 도메인 정보가 필요한 규칙은 Policy. 규칙 위반은 `BusinessException`(도메인별 `ErrorCode`).
- **이름**: 클래스 PascalCase, `DTO`는 대문자(`SignupDTO`), 메서드 camelCase + 접두어(`find`=없을 수 있음, `get`=없으면 예외, `create`, `change`, `delete`, `check`/`is`/`has`), 상수·enum UPPER_SNAKE_CASE. 테이블·컬럼 snake_case 단수형, API URI는 소문자 복수형 kebab-case(`/api/v1/wrong-notes`), JSON 필드 camelCase, 템플릿·정적 파일 소문자 kebab-case.
- **인증·보안**: 세션 로그인, 비밀번호는 `PasswordEncoder`(BCrypt)로만 저장·변경, `/instructor/**`·`/admin/**` 접근 제한. 레벨 해금은 `@RequireLevel`.
- **삭제**: Hard Delete를 기본으로 쓰지 않고 DB 설계의 테이블별 방식(`deleted_at`, `is_hidden`, `is_public`)을 따른다.
- **화면**: Thymeleaf 템플릿은 `templates/<도메인>/<화면>.html`, 공통은 `fragments/layout`. 인라인 스크립트 대신 `static/js`. 들여쓰기 2칸(HTML·CSS·JS).
- **Java 스타일**: Google Java Style.
- **비밀값**: 비밀번호·키를 코드·`application.yaml`에 직접 쓰지 않는다. `.env`(Git 제외) + 환경변수로 읽는다(`docs/MoneyBeetle_환경변수.md`).

## Git 규칙 (요약)

- 브랜치 흐름: **작업 브랜치 → `dev` → `main`**. `main`·`dev`에 직접 push하지 않고 PR로만 병합. 작업 브랜치는 `dev`에서 만든다. (`hotfix/…`만 `main`에서 시작)
- 브랜치 이름: `<타입>/<영역>-<이슈번호>-<짧은-설명>` (예: `feat/be-12-login-api`). 영문 소문자와 하이픈만.
- 커밋 메시지: `<타입>(<영역>): <한글 요약> (#이슈번호)` — 타입은 영어(`feat` `fix` `docs` `refactor` `style` `test` `chore`), 요약은 한글, 50자 안팎, 마침표 없음.
- PR: 한 PR = 한 이슈, 대략 400줄 이하. 본문에 `Closes #번호`. 작성자를 뺀 팀원 1명 승인 필수, 본인 PR은 본인이 병합하지 않는다. 작업 브랜치 → `dev`는 Squash and merge, `dev` → `main`은 Create a merge commit.
- BE는 PR 전에 `./gradlew test`, FE는 서버를 띄워 화면을 확인한다.

## PR 리뷰 코멘트 처리 (CodeRabbit·팀원 공통)

Claude Code에게 PR 코멘트를 읽고 반영하라고 하면 아래 순서를 지킨다.

1. 코멘트(CodeRabbit의 "Prompt for AI Agents" 포함)를 하나씩 읽고, **반영 / 반영하지 않는 이유 설명 / 질문** 셋 중 하나로 분류한다.
2. 반영하는 것은 코드를 고친 뒤 **로컬에서 실행·테스트로 확인**한다. 내가 설명할 수 있는 수정만 커밋한다.
3. 비밀번호·키 노출 지적은 **가장 먼저** 처리한다. 커밋에서 지우는 것만으로는 부족하고 해당 비밀번호·키를 바꾸도록 알린다.
4. 반영하지 않을 때는 이유를 코멘트 답글로 남긴다. 질문은 AI·리뷰어의 답을 확인하고 납득한 뒤 스레드를 닫는다. **답 없이 Resolve하거나 무시하고 병합하지 않는다.**
5. 새 커밋을 푸시하면 CodeRabbit이 새 커밋만 다시 리뷰한다. 자동으로 시작되지 않으면 PR에 `@coderabbitai review`만 적은 코멘트를 단다.
6. **승인(Approve)과 병합(Merge)은 사람이 한다.** Claude Code는 병합·승인·스레드 Resolve 같은 되돌리기 어려운 동작을 하기 전에 사용자에게 확인한다.

## 하지 않는 것

- 요청받지 않은 리팩터링, 이슈 범위를 벗어난 파일 수정.
- 담당이 다른 도메인의 파일 수정. 필요하면 이슈로 요청한다(`docs/MoneyBeetle_역할분담.md` §8).
- `main`·`dev`에 직접 push, force push, 훅 우회.
