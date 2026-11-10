# MoneyBeetle (머니비틀)

2030 사회초년생을 위한 금융 학습 LMS. 영상 강의와 퀴즈로 배우고, 가상 캐시로 모의투자·모의 청약을 연습한다.

> 폴더 구조와 파일 뼈대를 잡아 둔 상태입니다. 각 파일의 주석에 들어갈 내용과 담당자가 적혀 있으며, 기능은 이슈 단위로 채워 갑니다.
> 기능·데이터 기준은 저장소의 `docs/`(요구사항 명세서, 정책정의서, DB설계, API명세서, 화면설계)를 따릅니다.

## 저장소 구조

저장소 루트는 다음과 같이 구성됩니다.

```
my-home-finish/
├─ moneybeetle/       Spring Boot 백엔드 프로젝트
├─ moneybeetle-page/  화면 프로토타입 및 참고 자료
└─ docs/              요구사항·정책·API·DB·화면·역할 분담 문서
```

서버 실행과 Gradle 명령은 저장소 루트가 아니라 `moneybeetle/` 폴더에서 실행합니다.

## 기술 스택

Java 21 · Spring Boot 3.5 · Spring Data JPA · Spring Security(세션 로그인, 구글·카카오 간편 로그인) · Thymeleaf(서버 렌더링) · MySQL 8 · Gradle

## 실행 방법

### 준비 사항

- Java 21
- MySQL 8
- 저장소를 내려받은 뒤 `moneybeetle/` 폴더에서 아래 명령을 실행합니다.

### 1. 데이터베이스 준비

MySQL Workbench 등에서 `src/main/resources/sql/`의 SQL 파일을 다음 순서로 실행합니다.

1. `create_database.sql`: `money_beetle` 데이터베이스를 생성합니다. 같은 파일 아래쪽의 `CREATE USER` 예시를 사용할 경우 주석을 해제하고 비밀번호를 직접 정한 뒤 실행합니다.
2. `schema.sql`: 테이블을 생성합니다.
3. `sample-data.sql`: 로컬 개발·시연용 샘플 데이터를 넣습니다.

> **주의:** `schema.sql`은 기존 테이블을 삭제한 뒤 다시 생성하므로 해당 데이터베이스의 기존 데이터가 모두 사라집니다. 새 데이터베이스를 처음 구성할 때만 실행하세요. 이미 구성한 DB에는 `schema.sql`을 다시 실행하지 말고 필요한 `src/main/resources/sql/migration/` 파일만 실행합니다.

### 2. 환경변수 설정

`moneybeetle/` 폴더에서 `.env.example`을 복사해 `.env`를 만듭니다.

PowerShell:

```powershell
Copy-Item .env.example .env
```

Git Bash:

```bash
cp .env.example .env
```

생성한 `.env`에서 `DB_PASSWORD`를 로컬 MySQL 계정에 설정한 비밀번호로 바꿉니다. `DB_USERNAME`도 `create_database.sql`에서 만든 계정명과 같아야 합니다. 환경변수 전체 목록은 [환경변수 문서](../docs/MoneyBeetle_환경변수.md)를 참고하세요.

`.env`는 Git에 올리지 않습니다. IntelliJ를 사용하는 경우 Run/Debug Configurations의 Environment variables에 같은 값을 직접 설정해도 됩니다.

### 3. 서버 실행

PowerShell:

```powershell
.\gradlew.bat bootRun
```

Git Bash:

```bash
./gradlew bootRun
```

서버가 정상적으로 시작되면 브라우저에서 [http://localhost:8080](http://localhost:8080)에 접속합니다.
지금은 시작 화면(`templates/index.html`)이 주석만 있는 상태라 빈 화면이 보이는 것이 정상입니다. 시작 화면은 이슈 #51에서 만듭니다.

### 4. 테스트

PR을 올리기 전에 `moneybeetle/` 폴더에서 테스트를 실행합니다.

PowerShell:

```powershell
.\gradlew.bat test
```

Git Bash:

```bash
./gradlew test
```

## 패키지 구조

```
com.wanted.moneybeetle
├─ global                  공통
│  ├─ config               MoneyBeetleApplication, ContextConfig, JpaConfig, WebConfig, IndexController
│  ├─ common               ApiResponse, PageResponse, BaseTimeEntity
│  ├─ exception            ErrorCode, CommonErrorCode, BusinessException, GlobalExceptionHandler
│  ├─ security             RequireLevel, LevelCheckInterceptor(레벨 해금 확인), IdempotencyFilter(중복 요청 방지)
│  └─ file                 FileStorageService(파일 업로드)
└─ domain
   ├─ auth                 로그인·인증·비밀번호 재설정   (config, handler 포함)
   ├─ member               회원·강사·약관
   ├─ inquiry              문의
   ├─ operation            설정·관리자 기록·금칙어
   ├─ course               강의·영상·스크랩·후기·학습 진행
   ├─ quiz                 퀴즈·오답노트
   ├─ growth               출석·캐시·경험치·레벨        (RewardService)
   ├─ home                 홈·통합 검색
   ├─ term                 오늘의 경제 용어
   ├─ notification         알림                        (NotificationService)
   ├─ housing              모의 청약
   └─ trading              모의투자·주문·랭킹·시즌·정산
```

도메인 안은 같은 모양입니다: `controller` / `exception` / `model/{dao, dto, entity, service}`
(필요 없는 폴더는 두지 않습니다. `auth`만 `config`, `handler`가 더 있습니다.)

관리자·강사용 Controller·Service도 해당 도메인 안에 둡니다(예: `course/controller/AdminCourseController`, `InstructorCourseController`). `admin` 패키지는 따로 만들지 않습니다.

## 리소스 구조

```
src/main/resources
├─ application.yaml        DB·세션·업로드·시간대 설정 (비밀 값은 환경변수로)
├─ sql
│  ├─ create_database.sql  DB 생성
│  ├─ schema.sql           테이블 생성
│  └─ sample-data.sql      시드 (구역별로 담당이 작성)
├─ templates               Thymeleaf 템플릿 (templates/<도메인>/<화면>.html)
│  ├─ index.html, error/(403·404·500)
│  ├─ auth, member, home, course, quiz, housing, trading, inquiry
│  ├─ instructor          강사 화면
│  ├─ admin               관리자 화면 틀
│  └─ fragments
│     ├─ layout           header, sidebar, notice (공통)
│     ├─ mypage           마이페이지 탭 (learning, growth, settings)
│     └─ admin            관리자 탭 (members, courses, growth)
└─ static
   ├─ css/common.css
   ├─ js                  common.js, config.js, drawer.js, notify.js
   └─ images              logo.png, cash-coin.png, lock.png
```

- 화면에서 쓰는 설정값의 기준은 서버(`system_setting` 테이블)입니다. `config.js`는 서버 값이 없을 때만 기본값을 씁니다.
- 레벨·칭호 계산은 화면 표시용이고, 실제 레벨 해금 판단은 서버(`LevelCheckInterceptor`)가 합니다. (`LevelCheckInterceptor`와 등록을 맡을 `WebConfig`는 아직 빈 파일이라 이슈 #2·#4에서 구현할 예정입니다.)

## 담당

| 역할 | 담당 도메인 |
| --- | --- |
| SCM 서수영 | `housing`(모의 청약), `global` 공통 기반·보안, `auth/config`의 `SecurityConfig` |
| PM 이재성 | `trading`(모의투자), 통합 검색(#15·#56), 마이페이지 틀(#54), `IdempotencyFilter`(#65) |
| DBA ① 신아림 | `course`, `quiz`, `inquiry`, 사용자·강사 관리 API(#37의 `AdminMemberController` 포함) |
| DBA ② 김예린 | `auth`(config 제외), `member`(단, `AdminMemberController` 제외), `growth`, `notification`, 홈(#13), `term`, `operation`, 관리자 틀, 공통 레이아웃 |

위 표는 기본 담당 영역을 요약한 것입니다. 실제 파일·기능의 담당자는 `docs/MoneyBeetle_역할분담.md`와 해당 GitHub 이슈의 담당자를 함께 기준으로 합니다. 같은 도메인 안에서도 이슈에 따라 담당자가 달라질 수 있습니다.

- `AdminMemberController`는 도메인 경로가 `member`이지만 이슈 #37 담당자인 DBA ① 신아림이 맡습니다.
- 홈 API(#13)는 DBA ② 김예린, 통합 검색 API(#15)와 검색 화면(#56)은 PM 이재성이 맡습니다. 따라서 `HomeController`의 홈·검색 기능 주석은 각각 구분합니다.
- `auth/config/SecurityConfig`와 공통 기반 파일은 SCM 서수영, `auth`의 로그인·회원 인증 기능은 DBA ② 김예린이 맡습니다.
- `global/security/IdempotencyFilter`는 경로와 달리 이슈 #65 담당자인 PM 이재성이 맡습니다.

## 충돌 방지 규칙

1. 자기 도메인 패키지 밖의 파일은 고치지 않는다. 필요하면 이슈로 담당자에게 요청한다.
2. 다른 도메인의 Entity를 `@ManyToOne`으로 잇거나 Repository를 직접 쓰지 않는다. 다른 도메인은 ID로 참조하고 그 도메인의 Service 메서드를 부른다.
   - 캐시·경험치 지급/차감 : `growth.RewardService` / 알림 : `notification.NotificationService` / 관리자 기록 : `operation.AdminLogService`
3. 에러 코드는 도메인별 enum(`XxxErrorCode`)에 추가한다. `CommonErrorCode`는 SCM만 고친다.
4. SQL은 `resources/sql`의 `schema.sql`(테이블)과 `sample-data.sql`(시드)로 나누고 DBA가 관리한다. 테이블 변경은 DBA에게 요청한다. `schema.sql`을 다시 실행하면 데이터가 지워지므로, 이미 만든 DB에 변경만 적용해야 할 때에 한해 DBA가 `sql/migration/<날짜>_<설명>.sql`(ALTER 등)을 추가하고 같은 변경을 `schema.sql`에도 반영한다. 시드는 파일 맨 아래 자기 구역 주석 아래에만 추가한다.
5. 마이페이지·관리자 대시보드는 틀과 탭 내용(`templates/fragments/...`, 각 담당)을 나눈다.
6. API·DB를 바꾸면 같은 PR에서 API명세서·DB설계도 고친다.

## 브랜치·커밋·PR 규칙

- 작업 전 GitHub 이슈를 만들고, 최신 `dev`에서 이슈별 작업 브랜치를 만듭니다.
- **이슈 1개 = 작업 브랜치 1개 = PR 1개**를 원칙으로 합니다.
- 브랜치 이름은 `feat/be-<이슈번호>-<설명>`, `feat/fe-<이슈번호>-<설명>`, `docs/<이슈번호>-<설명>`처럼 작업 종류·영역·이슈 번호를 표시합니다.
- 작업 PR은 `dev`를 대상으로 올립니다. `dev`와 `main`에 직접 push하지 않습니다.
- PR에는 팀원 1명을 리뷰어로 지정하고 빌드·테스트 및 CodeRabbit 리뷰를 확인합니다. 승인 후 작업 브랜치를 `dev`에 Squash and merge 합니다.
- 시연·제출 시점에는 `dev`에서 `main`으로 별도 PR을 올리고 merge commit 방식으로 병합합니다.
- PR 본문에는 작업이 완료되어 이슈를 닫아도 될 때 `Closes #이슈번호`, 추적만 필요할 때 `Refs #이슈번호`를 사용합니다.

세부 규칙은 [Git 규칙 문서](../docs/MoneyBeetle_Git규칙.md)를 따릅니다.

## 문서

저장소 `docs/` 폴더:

| 문서 | 내용 |
| --- | --- |
| `MoneyBeetle_요구사항_명세서.md`, `MoneyBeetle_정책정의서.md` | 기능·규칙 |
| `MoneyBeetle_DB설계.md` | 테이블·컬럼 |
| `MoneyBeetle_API명세서.md` | API |
| `MoneyBeetle_화면설계.md` | 화면 |
| `MoneyBeetle_시스템설계.md`, `MoneyBeetle_업무분석.md` | 구조·업무 분석 |
| `MoneyBeetle_코딩컨벤션.md` | 코드 작성 방식 |
| `MoneyBeetle_Git규칙.md` | 브랜치·커밋·PR |
| `MoneyBeetle_환경변수.md` | 환경변수·비밀 값 |
| `MoneyBeetle_역할분담.md` | 담당·이슈 배정 |
