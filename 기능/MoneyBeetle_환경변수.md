# 환경변수 (.env) 정의

> 💡 **API Key 유출 사고를 막고, 팀원 간 실행 환경을 맞추기 위한 문서입니다.** 로컬 및 배포 환경에서 필요한 환경변수(.env)의 목록, 쓰이는 곳, 로컬 개발 기본값을 정의합니다.

**읽는 법**

- 기준: 팀 프로젝트 `moneybeetle/`(`build.gradle`, `application.yaml`의 주석), 시스템 설계서의 "환경설정" 절, 정책 정의서.
- **상태** 칸: **필수** = 지금 서버를 띄우는 데 필요, **기능 구현 시** = 해당 기능을 만들 때 추가, **배포 시** = 서버 배포 때만 필요 (기능은 시스템 설계서의 가정 구성 기준).
- **비밀** 칸: ✅ = 유출되면 안 되는 값(비밀번호·키·토큰). 절대 저장소에 올리지 않습니다.
- 로컬 기본값은 **내 컴퓨터에서만 쓰는 값**입니다. 운영(prod)에 같은 값을 쓰지 않습니다.

---

## 1. 지켜야 할 규칙

| 번호 | 규칙 |
| --- | --- |
| 1 | `.env` 파일은 **저장소(Git)에 올리지 않는다.** 올리는 것은 키 이름만 적은 `.env.example`뿐이다. |
| 2 | 비밀번호·API 키·토큰은 코드, `application.yaml`, 문서, 이슈·PR 본문, 스크린샷에 **직접 적지 않는다.** |
| 3 | 비밀 값은 메신저·메일에 **평문으로 공유하지 않는다.** 필요하면 비밀번호 관리 도구나 1:1 전달을 쓴다. |
| 4 | 키가 한 번이라도 GitHub에 올라갔다면 **파일을 지우는 것으로 끝나지 않는다.** 그 키를 즉시 폐기하고 새로 발급받는다 (Git 이력에 남기 때문). |
| 5 | 환경변수 값은 **로그에 출력하지 않는다** (비밀번호·세션 ID·토큰은 로그에서 가린다, EP-05·DP-02). |
| 6 | 환경(local / dev / prod)마다 **값을 다르게** 쓴다. 특히 prod DB·키를 로컬에서 쓰지 않는다. |
| 7 | 키 이름은 `UPPER_SNAKE_CASE`로 쓴다 (예: `DB_PASSWORD`). |
| 8 | 새 환경변수를 추가하면 **이 문서와 `.env.example`을 같은 PR에서 함께 고친다.** |

> ⚠️ **현재 `moneybeetle/.gitignore`에 `.env`가 없습니다.** `.env`를 만들기 전에 `.gitignore`에 `.env`를 먼저 추가해야 합니다 (§6 체크리스트).

---

## 2. 환경변수 목록

### 2-1 필수 (지금 서버를 띄우는 데 필요)

| 키 | 쓰이는 곳 | 비밀 | 로컬 기본값 | 상태 |
| --- | --- | --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | 실행 프로필 선택 (`application-{local,dev,prod}.yml`) | | `local` | 필수 |
| `DB_URL` | `spring.datasource.url` — MySQL 접속 주소 | | `jdbc:mysql://localhost:3306/moneybeetle?serverTimezone=Asia/Seoul&characterEncoding=UTF-8` | 필수 |
| `DB_USERNAME` | `spring.datasource.username` — `create_database.sql`이 만드는 계정 | | `moneybeetle` | 필수 |
| `DB_PASSWORD` | `spring.datasource.password` | ✅ | 각자 정함 (아래 참고) | 필수 |

- `DB_USERNAME`·`DB_PASSWORD`는 `resources/sql/create_database.sql`로 만든 계정과 **같아야** 합니다. 비밀번호 값은 문서에 적지 않고 각자 로컬에서 정합니다. `.env.example`에는 `change-me`만 적습니다.
- 서버 시간대는 `Asia/Seoul`, 세션 만료 30분, 업로드 한도 2MB는 **환경에 따라 달라지지 않으므로** 환경변수가 아니라 `application.yaml`에 고정합니다 (§4).

### 2-2 기능 구현 시 추가

| 키 | 쓰이는 곳 | 비밀 | 로컬 기본값 | 관련 |
| --- | --- | --- | --- | --- |
| `MAIL_HOST` | 비밀번호 재설정 메일 발송 (`spring-boot-starter-mail`, `PasswordResetService`) | | `localhost` | USR-09, DP-12 |
| `MAIL_PORT` | 메일 서버 포트 | | `1025` (로컬 메일 확인 도구) | 〃 |
| `MAIL_USERNAME` | 메일 계정 | | 비워 둠 | 〃 |
| `MAIL_PASSWORD` | 메일 계정 비밀번호 (앱 비밀번호) | ✅ | 비워 둠 | 〃 |
| `MAIL_FROM` | 발신자 주소 | | `no-reply@moneybeetle.local` | 〃 |
| `GOOGLE_CLIENT_ID` | 구글 간편 로그인 (`spring-boot-starter-oauth2-client`, `OAuth2SuccessHandler`) | | 개인 테스트 앱의 값 | USR-07, DP-13 |
| `GOOGLE_CLIENT_SECRET` | 〃 | ✅ | 개인 테스트 앱의 값 | 〃 |
| `KAKAO_CLIENT_ID` | 카카오 간편 로그인 | | 개인 테스트 앱의 값 | 〃 |
| `KAKAO_CLIENT_SECRET` | 〃 | ✅ | 개인 테스트 앱의 값 | 〃 |
| `REMEMBER_ME_KEY` | 로그인 상태 유지(Remember Me) 쿠키 서명 키 (`SecurityConfig`) | ✅ | 로컬 전용 임의 문자열 | USR-06 |
| `FILE_STORAGE_TYPE` | 파일 저장 방식 선택 (`FileStorageService`): `local` 또는 `s3` | | `local` | DP-32 |
| `FILE_UPLOAD_DIR` | `FILE_STORAGE_TYPE=local`일 때 저장 폴더 (프로필·썸네일, `WebConfig`가 정적 경로로 연결) | | `./uploads` | DP-32 |

- 간편 로그인은 요구사항 우선순위가 **하**입니다. 구현 전에는 `GOOGLE_*`, `KAKAO_*`를 비워 둬도 됩니다. 단, 값이 비어 있는 채로 로그인 등록 설정을 켜면 서버가 뜨지 않을 수 있으므로, 구현할 때 "키가 없으면 해당 로그인을 끄는" 처리를 함께 넣습니다.
- 메일은 로컬에서 실제 메일을 보내지 않고 로컬 메일 확인 도구(예: Mailpit, `1025` 포트)나 로그 출력으로 확인합니다.
- `REMEMBER_ME_KEY`는 로컬과 운영 값이 **달라야** 합니다.

### 2-3 배포 시 (서버·CI)

| 키 | 쓰이는 곳 | 비밀 | 로컬 기본값 | 둘 곳 |
| --- | --- | --- | --- | --- |
| `STORAGE_ENDPOINT` | S3 호환 오브젝트 스토리지 주소 (`FILE_STORAGE_TYPE=s3`) | | 쓰지 않음 | 서버 `.env` |
| `STORAGE_BUCKET` | 버킷 이름 (비공개 버킷) | | 쓰지 않음 | 서버 `.env` |
| `STORAGE_ACCESS_KEY` | 스토리지 접근 키 | ✅ | 쓰지 않음 | 서버 `.env` |
| `STORAGE_SECRET_KEY` | 〃 | ✅ | 쓰지 않음 | 서버 `.env` |
| `ADMIN_INITIAL_EMAIL` | 운영 서버의 **최초 관리자 계정** 이메일 (배포 때 한 번만 생성) | | `admin@moneybeetle.local` | 서버 `.env` |
| `ADMIN_INITIAL_PASSWORD` | 최초 관리자 비밀번호 | ✅ | 로컬 전용 값 | 서버 `.env` |
| `MYSQL_ROOT_PASSWORD` | Docker Compose의 MySQL 컨테이너 root 비밀번호 | ✅ | 각자 정함 | 서버 `.env`, 로컬 `.env` |
| `MYSQL_DATABASE` | Docker Compose MySQL이 만들 DB 이름 | | `moneybeetle` | 〃 |
| `DEPLOY_HOST` | 배포할 서버 주소 | | 쓰지 않음 | GitHub Secrets |
| `DEPLOY_USER` | 배포용 서버 계정 | | 쓰지 않음 | GitHub Secrets |
| `DEPLOY_SSH_KEY` | 배포용 SSH 개인 키 | ✅ | 쓰지 않음 | GitHub Secrets |

- GitHub Actions가 이미지를 GHCR에 올릴 때 쓰는 `GITHUB_TOKEN`은 GitHub이 자동으로 제공하므로 따로 만들지 않습니다.
- **관리자 계정 주의**: `resources/sql/99_seed.sql`에 관리자 계정을 넣는 설계라면, 그 파일에 비밀번호(해시 포함)가 들어갑니다. 로컬 개발용 계정만 seed에 두고, **운영 관리자는 `ADMIN_INITIAL_*`로 배포 때 만듭니다.**

---

## 3. 환경별 값 구분

| 항목 | local (각자 노트북) | dev (선택) | prod (운영) |
| --- | --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | `local` | `dev` | `prod` |
| DB | 내 컴퓨터의 MySQL (또는 Docker Compose 컨테이너) | 개발용 DB | 운영 DB (매일 백업) |
| 파일 저장 | `local` (`./uploads`) | 개발용 버킷 | 운영 버킷 (`s3`) |
| 메일 | 로컬 메일 확인 도구 | 테스트 메일 계정 | 실제 발송 계정 |
| 간편 로그인 키 | 개인 테스트 앱 | 개발용 앱 | 운영 앱 (콜백 주소가 다름) |
| 값을 두는 곳 | 내 `.env` | 서버 `.env` + GitHub Secrets | 서버 `.env` + GitHub Secrets |
| 관리자 계정 | seed의 개발용 계정 | seed의 개발용 계정 | `ADMIN_INITIAL_*`로 최초 1회 |

---

## 4. 환경변수가 아닌 고정 설정

팀원 모두, 모든 환경에서 같은 값이므로 `application.yaml`에 직접 씁니다 (`application.yaml` 주석 기준).

| 설정 | 값 | 근거 |
| --- | --- | --- |
| `spring.application.name` | `moneybeetle` | |
| `spring.jpa.hibernate.ddl-auto` | `none` (테이블은 `resources/sql` 스크립트로 만든다) | 학습·골격 |
| `server.servlet.session.timeout` | `30m` | DP-11 |
| `spring.servlet.multipart.max-file-size` | `2MB` | DP-32 |
| 시간대 | `Asia/Seoul` (서버·DB·Jackson) | DP-42 |
| `spring.jpa.show-sql`, `format_sql` | `local`에서만 켬 (`application-local.yml`) | |

---

## 5. 사용 방법

### 5-1 `.env.example` (저장소에 올리는 파일)

키 이름과 안전한 예시 값만 적습니다. 실제 비밀 값은 절대 넣지 않습니다.

```properties
# 실행 환경
SPRING_PROFILES_ACTIVE=local

# DB (create_database.sql로 만든 계정과 같아야 함)
DB_URL=jdbc:mysql://localhost:3306/moneybeetle?serverTimezone=Asia/Seoul&characterEncoding=UTF-8
DB_USERNAME=moneybeetle
DB_PASSWORD=change-me

# 메일 (비밀번호 재설정) - 기능 구현 시
MAIL_HOST=localhost
MAIL_PORT=1025
MAIL_USERNAME=
MAIL_PASSWORD=
MAIL_FROM=no-reply@moneybeetle.local

# 간편 로그인 (우선순위 하) - 기능 구현 시
GOOGLE_CLIENT_ID=
GOOGLE_CLIENT_SECRET=
KAKAO_CLIENT_ID=
KAKAO_CLIENT_SECRET=

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

### 5-4 서버(배포)에서

- 서버의 `.env` 파일에 값을 두고 Docker Compose가 읽게 합니다. 서버 `.env`의 권한은 소유자만 읽을 수 있게 합니다.
- GitHub Actions에서 쓰는 값은 저장소 Settings → Secrets and variables → Actions에 등록합니다.

---

## 6. 새 팀원·새 환경 체크리스트

- [ ] `moneybeetle/.gitignore`에 `.env`가 있는지 확인한다 (없으면 추가).
- [ ] MySQL 8을 설치하고 `resources/sql/create_database.sql` → `01_member.sql` … → `99_seed.sql` 순서로 실행한다 (FK 때문에 번호 순).
- [ ] `.env.example`을 복사해 `.env`(또는 IntelliJ 환경변수)를 만들고 `DB_PASSWORD`를 채운다.
- [ ] `SPRING_PROFILES_ACTIVE=local`로 서버를 띄워 로그인 화면이 열리는지 확인한다.
- [ ] `git status`에 `.env`가 보이지 않는지 확인한다.
- [ ] 푸시 전에 `git diff`로 키·비밀번호가 섞이지 않았는지 확인한다.

---

## 7. 키가 유출되었을 때

1. **즉시 그 키를 폐기하고 새로 발급**한다 (DB 비밀번호 변경, OAuth 클라이언트 시크릿 재발급, 스토리지 키 교체).
2. 담당자(SCM)와 팀에 알린다.
3. 새 키를 서버 `.env`·GitHub Secrets·각자의 `.env`에 반영한다.
4. Git에서 지우는 것(이력 정리)은 **그다음** 일이다. 이미 공개된 키는 지워도 유효하므로 1번이 먼저다.
5. 원인(어디에 적었는지)을 확인하고 위 규칙 1~5를 다시 점검한다.

---

## 8. 팀 확인 필요

| 항목 | 현재 상태 | 정할 것 |
| --- | --- | --- |
| `.gitignore` | `moneybeetle/.gitignore`에 `.env` 항목이 없음 | `.env`, `.env.*`(단 `.env.example`은 제외) 추가 |
| 메일 발송 방식 | 정책(10분 유효 1회용 토큰)은 있으나 SMTP 서비스 미정 | 사용할 메일 서비스(예: Gmail SMTP 앱 비밀번호) |
| 간편 로그인 | 우선순위 하. 구글·카카오 앱 등록 필요 | 구현 시점, 개인 테스트 앱을 쓸지 팀 공용 앱을 쓸지 |
| 파일 저장 | 로컬 폴더와 S3 호환 스토리지 (시스템 설계서의 가정) | 운영에서 쓸 스토리지 서비스, 영상 파일 업로드 허용 여부 |
| 관리자 계정 | `99_seed.sql`에 관리자 계정 포함 예정 | 운영 계정을 환경변수로 만들지, seed에서 제외할지 |
| 환경변수 키 이름 | 이 문서의 이름은 제안 | 팀 합의 후 확정 (Spring 기본 이름 `SPRING_DATASOURCE_URL` 등을 쓸 수도 있음) |
| 비밀 값 전달 방법 | 미정 | 팀원 간 공유 방법 (비밀번호 관리 도구 등) |
