# 환경변수 (.env) 정의

> 💡 **API Key 유출 사고를 막고, 팀원 간 실행 환경을 맞추기 위한 문서입니다.** 로컬 환경(개발·발표)에서 필요한 환경변수(.env)의 목록, 쓰이는 곳, 로컬 개발 기본값을 정의합니다.
>
> 📌 **팀 결정 (2026-11-08)**: 이 프로젝트는 **배포하지 않고 로컬에서 발표**합니다. 그래서 서버·운영(prod)용 환경변수와 GitHub Secrets는 쓰지 않습니다. CI(GitHub Actions)는 DB 없이 빌드·테스트만 합니다.

**읽는 법**

- 기준: 팀 프로젝트 `moneybeetle/`(`build.gradle`, `application.yaml`의 주석), 시스템 설계서의 "환경설정" 절, 정책 정의서.
- **상태** 칸: **필수** = 지금 서버를 띄우는 데 필요, **기능 구현 시** = 해당 기능을 만들 때 추가.
- **비밀** 칸: ✅ = 유출되면 안 되는 값(비밀번호·키·토큰). 절대 저장소에 올리지 않습니다.
- 로컬 기본값은 **내 컴퓨터에서만 쓰는 값**입니다. 각자 정하고 다른 사람과 공유하지 않습니다.

---

## 1. 지켜야 할 규칙

| 번호 | 규칙 |
| --- | --- |
| 1 | `.env` 파일은 **저장소(Git)에 올리지 않는다.** 올리는 것은 키 이름만 적은 `.env.example`뿐이다. |
| 2 | 비밀번호·API 키·토큰은 코드, `application.yaml`, 문서, 이슈·PR 본문, 스크린샷에 **직접 적지 않는다.** |
| 3 | 비밀 값은 메신저·메일에 **평문으로 공유하지 않는다.** 필요하면 비밀번호 관리 도구나 1:1 전달을 쓴다. |
| 4 | 키가 한 번이라도 GitHub에 올라갔다면 **파일을 지우는 것으로 끝나지 않는다.** 그 키를 즉시 폐기하고 새로 발급받는다 (Git 이력에 남기 때문). |
| 5 | 환경변수 값은 **로그에 출력하지 않는다** (비밀번호·세션 ID·토큰은 로그에서 가린다, EP-05·DP-02). |
| 6 | 비밀 값은 **각자 자기 컴퓨터에서 정한다.** 다른 팀원의 DB 비밀번호·키를 그대로 가져다 쓰지 않는다. |
| 7 | 키 이름은 `UPPER_SNAKE_CASE`로 쓴다 (예: `DB_PASSWORD`). |
| 8 | 새 환경변수를 추가하면 **이 문서와 `.env.example`을 같은 PR에서 함께 고친다.** |

> ✅ 저장소 루트 `.gitignore`에 `.env`, `.env.*`가 들어 있고 `.env.example`만 예외로 올라갑니다 (루트 규칙이 `moneybeetle/.env`에도 적용됨).

---

## 2. 환경변수 목록

### 2-1 필수 (지금 서버를 띄우는 데 필요)

| 키 | 쓰이는 곳 | 비밀 | 로컬 기본값 | 상태 |
| --- | --- | --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | 실행 프로필 선택. `local` 이면 `application.yaml` 의 `local` 구역(SQL 로그)이 켜진다. `.env` 에 적어도 적용된다(`spring.profiles.active: ${SPRING_PROFILES_ACTIVE:}`, #114). CI 는 설정하지 않아 SQL 로그가 꺼진다 | | `local` | 선택 |
| `DB_URL` | `spring.datasource.url` — MySQL 접속 주소 | | `jdbc:mysql://localhost:3306/money_beetle?serverTimezone=Asia/Seoul&characterEncoding=UTF-8` | 필수 |
| `DB_USERNAME` | `spring.datasource.username` — 로컬 MySQL에 각자 만든 계정 (`create_database.sql` 맨 아래 주석 참고) | | `moneybeetle` | 필수 |
| `DB_PASSWORD` | `spring.datasource.password` | ✅ | 각자 정함 (아래 참고) | 필수 |

- `DB_USERNAME`·`DB_PASSWORD`는 로컬 MySQL에 만든 계정(`resources/sql/create_database.sql` 맨 아래 주석의 `CREATE USER` 예시)과 **같아야** 합니다. DB 이름은 `money_beetle`입니다. 비밀번호 값은 문서에 적지 않고 각자 로컬에서 정합니다. `.env.example`에는 `change-me`만 적습니다.
- 서버 시간대는 `Asia/Seoul`, 세션 만료 30분, 업로드 한도(프로필 이미지 2MB, 강의 영상 500MB)는 **환경에 따라 달라지지 않으므로** 환경변수가 아니라 `application.yaml`에 고정합니다 (§4).

### 2-2 기능 구현 시 추가

| 키 | 쓰이는 곳 | 비밀 | 로컬 기본값 | 관련 |
| --- | --- | --- | --- | --- |
| `MAIL_HOST` | 비밀번호 재설정·가입 본인인증 메일 발송 (`spring-boot-starter-mail`, `PasswordResetService`, `IdentityVerificationService`) | | `localhost` | USR-09, DP-12, USR-05, DP-44 |
| `MAIL_PORT` | 메일 서버 포트 | | `1025` (로컬 메일 확인 도구) | 〃 |
| `MAIL_USERNAME` | 메일 계정 | | 비워 둠 | 〃 |
| `MAIL_PASSWORD` | 메일 계정 비밀번호 (앱 비밀번호) | ✅ | 비워 둠 | 〃 |
| `MAIL_FROM` | 발신자 주소 | | `no-reply@moneybeetle.local` | 〃 |
| `APP_BASE_URL` | 재설정 메일 속 링크의 서버 주소 (`{APP_BASE_URL}/password-reset?token=...`) | | `http://localhost:8080` | 〃 |
| `GOOGLE_CLIENT_ID` | 구글 간편 로그인 (`spring-boot-starter-oauth2-client`, `OAuth2SuccessHandler`) | | 개인 테스트 앱의 값 | USR-07, DP-13 |
| `GOOGLE_CLIENT_SECRET` | 〃 | ✅ | 개인 테스트 앱의 값 | 〃 |
| `REMEMBER_ME_KEY` | 로그인 상태 유지(Remember Me) 쿠키 서명 키 (`SecurityConfig`) | ✅ | 로컬 전용 임의 문자열 | USR-06 |
| `FILE_STORAGE_TYPE` | 파일 저장 방식. 배포하지 않아 로컬 폴더(`local`)만 쓰므로 지금은 읽지 않음 | | `local` | DP-32 |
| `FILE_UPLOAD_DIR` | 업로드 파일 저장 폴더 (`file.upload-dir`, `FileStorageService` 저장, `WebConfig`가 `/uploads/**`로 연결). 시연용 샘플 영상은 `moneybeetle/uploads/videos/sample-1~3.mp4`(저장소에 포함, #73)라서 기본값 `./uploads`로 `moneybeetle` 폴더에서 실행해야 시드 영상이 재생된다. 다른 폴더를 쓰면 `videos/` 폴더를 그곳에 복사한다 | | `./uploads` | DP-32·33 |

- 간편 로그인(#9)은 **실제 OAuth** 로 구현했습니다. 키(`GOOGLE_*`)가 없어도 서버는 뜨고, **ID 와 시크릿이 모두 있어야 켜지고, 하나라도 비어 있으면 간편 로그인 전체가 꺼집니다**(`OAuthClientConfig`). 값이 비어 있는 채로 등록하지 않으므로 서버가 못 뜨는 일은 없습니다.
- 키 발급과 등록 (각자 개인 테스트 앱):
  - 구글: Google Cloud 콘솔 → API 및 서비스 → 사용자 인증 정보 → OAuth 클라이언트 ID(웹 애플리케이션). 승인된 리디렉션 URI 에 `http://localhost:8080/login/oauth2/code/google` 를 등록합니다. 범위는 `profile`, `email` 입니다.
  - 포트나 주소가 다르면 `{주소}/login/oauth2/code/google` 로 등록합니다. 받은 값은 `.env` 에만 넣고 코드·깃에는 올리지 않습니다.
  - 제공자가 이메일을 주지 않거나 확인되지 않은 이메일이면 그 로그인은 막힙니다(`OAUTH_FAILED`).
- 메일은 로컬에서 실제 메일을 보내지 않고 로컬 메일 확인 도구(예: Mailpit, `1025` 포트)나 로그 출력으로 확인합니다.
- `REMEMBER_ME_KEY`는 로컬 전용 임의 문자열을 각자 정합니다.

### 2-3 배포용 값 (쓰지 않음)

배포하지 않으므로(팀 결정 2026-11-08) 오브젝트 스토리지(`STORAGE_*`), 운영 최초 관리자(`ADMIN_INITIAL_*`), Docker Compose MySQL(`MYSQL_*`), 배포 서버 접속(`DEPLOY_*`) 값은 **만들지 않습니다.** 관리자 계정은 `sample-data.sql`의 로컬 개발용 계정을 그대로 씁니다.

---

## 3. 환경별 값 구분

| 항목 | local (각자 노트북 · 발표 노트북) | CI (GitHub Actions) |
| --- | --- | --- |
| DB | 내 컴퓨터의 MySQL 8 (`money_beetle`) | 없음 (DB 없이 테스트) |
| 파일 저장 | 로컬 폴더 `./uploads` | 없음 |
| 메일 | 로컬 메일 확인 도구 | 없음 |
| 간편 로그인 키 | 개인 테스트 앱 | 없음 |
| 값을 두는 곳 | 내 `moneybeetle/.env` 또는 IntelliJ 환경변수 | 없음 (Secrets 불필요) |
| 관리자 계정 | `sample-data.sql`의 개발용 계정 | 없음 |

---

## 4. 환경변수가 아닌 고정 설정

팀원 모두, 모든 환경에서 같은 값이므로 `application.yaml`에 직접 씁니다 (`application.yaml` 주석 기준).

| 설정 | 값 | 근거 |
| --- | --- | --- |
| `spring.application.name` | `moneybeetle` | |
| `spring.jpa.hibernate.ddl-auto` | `none` (테이블은 `resources/sql` 스크립트로 만든다) | 학습·골격 |
| `server.servlet.session.timeout` | `30m` | DP-11 |
| `spring.servlet.multipart.max-file-size` | `500MB` (강의 영상 한도. 프로필 이미지 2MB는 `FileStorageService`가 용도별로 따로 검사한다). **구현 예정**: 설정과 `FileStorageService`는 #2, 영상 검증은 #31. 그전에는 Spring Boot 기본값(파일 1MB)이다 | DP-32, DP-33 |
| `spring.servlet.multipart.max-request-size` | `510MB` (영상은 한 요청에 한 개씩 올리므로 파일 한 개 한도보다 조금 크게. 영상 한 개당 업로드는 API명세서 #78 "영상 파일 올리기") | DP-33 |
| 시간대 | `Asia/Seoul` (서버·DB·Jackson) | DP-42 |
| `spring.jpa.show-sql`, `spring.jpa.properties.hibernate.format_sql` | `local` 프로필에서만 켬 (`application.yaml` 아래의 `on-profile: local` 구역, 기본은 `show-sql: false`, #114). `format_sql`은 `properties.hibernate` 아래에 써야 적용된다 | 학습 (09 module07) |
| `management.endpoints.web.exposure.include` | `health,info` 만 웹에 연다. `health` 는 UP/DOWN 만(`show-details: never`), 메일 상태는 확인에서 뺀다(`management.health.mail.enabled: false`). `/actuator/health`·`/actuator/info` 는 누구나, 나머지 actuator 주소는 열지 않는다 (#84) | 시스템 설계 "모니터링" |
| `logging.*` | 콘솔은 줄마다 `[요청 ID|m=회원 ID]`(`logging.pattern.correlation`, 비로그인은 회원 ID 칸이 빈다). `local` 프로필은 우리 코드 `DEBUG`, 파일 `logs/moneybeetle.log` 에 JSON 한 줄(`requestId`·`memberId` 포함, `logging.structured.format.file: logstash`), 10MB 마다 나누고 7일 보관. `logs/` 는 Git 제외 (#84) | 시스템 설계 "로그", EP-05 |

---

## 5. 사용 방법

### 5-1 `.env.example` (저장소에 올리는 파일)

키 이름과 안전한 예시 값만 적습니다. 실제 비밀 값은 절대 넣지 않습니다.

```properties
# 실행 환경
SPRING_PROFILES_ACTIVE=local

# DB (로컬 MySQL에 만든 계정과 같아야 함, DB 이름은 money_beetle)
DB_URL=jdbc:mysql://localhost:3306/money_beetle?serverTimezone=Asia/Seoul&characterEncoding=UTF-8
DB_USERNAME=moneybeetle
DB_PASSWORD=change-me

# 메일 (비밀번호 재설정·가입 본인인증) - 기능 구현 시
MAIL_HOST=localhost
MAIL_PORT=1025
MAIL_USERNAME=
MAIL_PASSWORD=
MAIL_FROM=no-reply@moneybeetle.local

# 간편 로그인 (#9). 키가 없으면 꺼진다 (비워 둬도 서버는 뜬다)
GOOGLE_CLIENT_ID=
GOOGLE_CLIENT_SECRET=

# 로그인 상태 유지 키 (로컬 전용 임의 문자열)
REMEMBER_ME_KEY=change-me-local-only

# 파일 저장
FILE_STORAGE_TYPE=local
FILE_UPLOAD_DIR=./uploads
```

### 5-2 `application.yaml`에서 읽는 방법

`${KEY}`로 읽고, 비밀이 아닌 값에만 기본값(`:` 뒤)을 둡니다. **비밀 값에는 기본값을 넣지 않습니다.**

```yaml
spring:
  datasource:
    url: ${DB_URL}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}          # 기본값 없음: 설정 안 하면 서버가 뜨지 않아 실수를 바로 알 수 있다
  mail:
    host: ${MAIL_HOST:localhost}
    port: ${MAIL_PORT:1025}
```

### 5-3 로컬에서 값 넣기 (둘 중 하나)

1. **IntelliJ 실행 설정**: Run/Debug Configurations → Environment variables에 `DB_PASSWORD=...` 형식으로 입력합니다. 파일이 프로젝트 밖(IDE 설정)에 있어서 실수로 올릴 위험이 가장 적습니다.
2. **`.env` 파일**: `moneybeetle/` 폴더에 `.env.example`을 복사해 `.env`로 만들고 값을 채웁니다. Spring Boot가 `.env`를 읽도록 `application.yaml`에 `spring.config.import: optional:file:.env[.properties]`를 추가합니다. (`.gitignore`에 `.env`가 있는지 **먼저** 확인합니다.)

### 5-4 발표 노트북에서

- 배포 서버는 없습니다(팀 결정 2026-11-08). 발표 노트북도 개발할 때와 같은 방법(5-3)으로 `.env`를 만들어 실행합니다.
- CI(GitHub Actions)는 DB 없이 테스트하므로 GitHub Secrets에 값을 등록하지 않습니다.

---

## 6. 새 팀원·새 환경 체크리스트

- [x] 루트 `.gitignore`에 `.env`, `.env.*`(`.env.example` 제외)가 있다 (#67).
- [ ] MySQL 8을 설치하고 `resources/sql/create_database.sql` → `schema.sql` → `sample-data.sql` 순서로 실행한다 (시드는 로컬 개발용).
- [ ] `.env.example`을 복사해 `.env`(또는 IntelliJ 환경변수)를 만들고 `DB_PASSWORD`를 채운다.
- [ ] 서버를 띄워(`./gradlew bootRun` 또는 IntelliJ) DB 오류 없이 시작되는지 확인한다.
- [ ] `git status`에 `.env`가 보이지 않는지 확인한다.
- [ ] 푸시 전에 `git diff`로 키·비밀번호가 섞이지 않았는지 확인한다.

---

## 7. 키가 유출되었을 때

1. **즉시 그 키를 폐기하고 새로 발급**한다 (DB 비밀번호 변경, OAuth 클라이언트 시크릿 재발급).
2. 담당자(SCM)와 팀에 알린다.
3. 새 키를 각자의 `.env`(또는 IntelliJ 환경변수)에 반영한다.
4. Git에서 지우는 것(이력 정리)은 **그다음** 일이다. 이미 공개된 키는 지워도 유효하므로 1번이 먼저다.
5. 원인(어디에 적었는지)을 확인하고 위 규칙 1~5를 다시 점검한다.

---

## 8. 팀 확인 필요

| 항목 | 현재 상태 | 정할 것 |
| --- | --- | --- |
| `.gitignore` | ✅ 루트 `.gitignore`에 `.env`, `.env.*`, `!.env.example` 추가 (#67) | 없음 |
| 메일 발송 방식 | 정책(10분 유효 1회용 토큰)은 있으나 SMTP 서비스 미정 | 사용할 메일 서비스(예: Gmail SMTP 앱 비밀번호) |
| 간편 로그인 | 우선순위 하. 구글 앱 등록 필요 | 구현 시점, 개인 테스트 앱을 쓸지 팀 공용 앱을 쓸지 |
| 파일 저장 | ✅ 로컬 폴더 `./uploads`로 확정 (배포하지 않음, 팀 결정 2026-11-08). 영상 업로드 500MB·mp4 확정 (DP-33) | 없음 |
| 관리자 계정 | ✅ `sample-data.sql`의 로컬 개발용 계정을 그대로 씀 (배포하지 않음) | 없음 |
| 환경변수 키 이름 | 이 문서의 이름은 제안 | 팀 합의 후 확정 (Spring 기본 이름 `SPRING_DATASOURCE_URL` 등을 쓸 수도 있음) |
| 비밀 값 전달 방법 | 미정 | 팀원 간 공유 방법 (비밀번호 관리 도구 등) |
