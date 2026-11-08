# MoneyBeetle (머니비틀)

2030 사회초년생을 위한 금융 학습 LMS. 영상 강의와 퀴즈로 배우고, 가상 캐시로 모의투자·모의 청약을 연습한다.

> 폴더 구조와 파일 뼈대를 잡아 둔 상태입니다. 각 파일의 주석에 들어갈 내용과 담당자가 적혀 있으며, 기능은 이슈 단위로 채워 갑니다.
> 기능·데이터 기준은 저장소의 `docs/`(요구사항 명세서, 정책정의서, DB설계, API명세서, 화면설계)를 따릅니다.

## 기술 스택

Java 21 · Spring Boot 3.5 · Spring Data JPA · Spring Security(세션 로그인, 구글·카카오 간편 로그인) · Thymeleaf(서버 렌더링) · MySQL 8 · Gradle

## 실행 방법

1. MySQL에서 `src/main/resources/sql`의 스크립트를 순서대로 실행한다: `create_database.sql` → `schema.sql` → `sample-data.sql`
   - ⚠️ `schema.sql`은 기존 테이블을 **삭제하고 다시 만든다**. 이미 넣어 둔 개발 데이터가 있으면 모두 사라지니 처음 한 번만 실행한다.
   - `sample-data.sql`은 로컬 개발용 시드라서 운영 서버에는 넣지 않는다.
   - 이미 DB를 만들어 둔 사람은 `schema.sql`을 다시 실행하지 말고 `src/main/resources/sql/migration/`의 파일만 실행한다(값 변경이 있을 때만 파일이 추가된다). 지금은 `2026-11-08_trading_season_carryover_default.sql`(이월 비율 기본값 100.00)이 있다.
2. DB 접속 정보 등 비밀 값은 `.env`(Git 제외)와 환경변수로 넣는다. 키 목록은 `docs/MoneyBeetle_환경변수.md`를 본다.
3. 서버를 띄운다. (⚠️ 시작 클래스와 설정(`application.yaml`)이 이슈 #2에서 올라온 뒤부터 실행할 수 있다. 그전에는 `./gradlew bootRun`이 안 된다.)

```bash
./gradlew bootRun
```

PR 전에는 `./gradlew test`를 실행한다.

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
| SCM 서수영 | `housing`(모의 청약), `global` 공통 기반·보안 |
| PM 이재성 | `trading`(모의투자), 통합 검색, 마이페이지 틀, 중복 요청 방지 |
| DBA ① 신아림 | `course`, `quiz`, `inquiry`, 사용자·강사 관리 |
| DBA ② 김예린 | `auth`, `member`, `growth`, `notification`, `home`, `term`, `operation`, 관리자 틀, 공통 레이아웃 |

최신 배정과 이슈 번호는 `docs/MoneyBeetle_역할분담.md`가 기준입니다.

## 충돌 방지 규칙

1. 자기 도메인 패키지 밖의 파일은 고치지 않는다. 필요하면 이슈로 담당자에게 요청한다.
2. 다른 도메인의 Entity를 `@ManyToOne`으로 잇거나 Repository를 직접 쓰지 않는다. 다른 도메인은 ID로 참조하고 그 도메인의 Service 메서드를 부른다.
   - 캐시·경험치 지급/차감 : `growth.RewardService` / 알림 : `notification.NotificationService` / 관리자 기록 : `operation.AdminLogService`
3. 에러 코드는 도메인별 enum(`XxxErrorCode`)에 추가한다. `CommonErrorCode`는 SCM만 고친다.
4. SQL은 `resources/sql`의 `schema.sql`(테이블)과 `sample-data.sql`(시드)로 나누고 DBA가 관리한다. 테이블 변경은 DBA에게 요청한다. `schema.sql`을 다시 실행하면 데이터가 지워지므로, 이미 만든 DB에 변경만 적용해야 할 때에 한해 DBA가 `sql/migration/<날짜>_<설명>.sql`(ALTER 등)을 추가하고 같은 변경을 `schema.sql`에도 반영한다. 시드는 파일 맨 아래 자기 구역 주석 아래에만 추가한다.
5. 마이페이지·관리자 대시보드는 틀과 탭 내용(`templates/fragments/...`, 각 담당)을 나눈다.
6. API·DB를 바꾸면 같은 PR에서 API명세서·DB설계도 고친다.

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
