# MoneyBeetle 역할 분담 (세부)

> 기준일 2026-11-07. 저장소 `bukkeu/my-home-finish`, GitHub 이슈 #2~#87·#95(이슈 번호는 이 저장소 기준, **담당자(Assignee) 지정 완료**), `docs/MoneyBeetle_API명세서.md`(API 148개), `moneybeetle-page/ROUTES.md`(화면 20개), `moneybeetle/src/main/resources/sql/schema.sql`(테이블 47개)
> 이슈 1개 = 작업 브랜치 1개 = PR 1개. 브랜치 이름은 Git 규칙(`feat/be-<이슈번호>-<설명>`)을 따릅니다.

---

## 0. 개정 요약

**이전 안의 문제**: 모의 청약이 서수영에게 몰려 있었습니다. 공통·회원·문의·관리자 운영까지 함께 맡아 이슈 27개(점수 157)였고, DBA ②는 10개(점수 48)였습니다.

**이번 안**: 모의 청약을 서수영의 중심 업무로 두고, 초반에 끝나는 공통 기반·보안만 더했습니다. 나머지(회원·인증·성장·홈·관리자 운영 등)는 신아림, 김예린, 이재성이 나눴습니다.

| 담당 | 이전 안 | 이번 안 |
| --- | --- | --- |
| 서수영 (SCM) | 27개 · 점수 157 | **16개 · 점수 93** (+고유 업무 10) |
| 이재성 (PM) | 13개 · 점수 70 | **20개 · 점수 98** (+일정 관리 10) |
| 신아림 (DBA ①) | 15개 · 점수 78 | **23개 · 점수 107** (+DB 3) |
| 김예린 (DBA ②) | 10개 · 점수 48 | **23개 · 점수 106** (+DB 3) |

- **점수**는 작업량 추정치입니다(API 수 + 구현 난이도 가산, 화면은 프로토타입 크기 기준). 계산 기준은 맨 아래 부록에 있습니다.
- 서수영은 고유 업무(문서·Git·이슈, 릴리스 PR)가 있고 모의 청약에 **미확정 규칙**(당첨 규칙 임시안, 가점표)이 남아 있어서, 점수를 다른 사람보다 조금 낮게 두었습니다.
- 모의 청약(점수 56)과 CI·문서만 맡기면 서수영의 점수가 64로, 나머지 세 명이 각각 100 이상이 되어 불균형이 커집니다. 그래서 초반에 끝나는 공통 기반(#2, #4)을 더했습니다.

---

## 1. 한눈에 보기

| 담당 | 담당자 | 맡는 영역 | 이슈 | 점수 | API |
| --- | --- | --- | --- | --- | --- |
| **SCM** | **서수영** (`tjtndud2`) | **모의 청약** + 공통 기반·보안 + CI·배포·Actuator + README·브랜치 정리 + 문서·Git·이슈 | 16 | 93 | 16 |
| **PM** | **이재성** (`godgeul-glitch`) | **모의투자** + 통합 검색 + 마이페이지 틀 + 중복 요청 방지 + 통합 점검 | 20 | 98 | 39 |
| **DBA ①** | **신아림** (`arim0317-dot`) | 강의·퀴즈·강사 + 문의 + 사용자·강사 관리 | 23 | 107 | 53 |
| **DBA ②** | **김예린** (`paaaraaaeaaa`) | 회원·인증 + 성장·알림·홈·경제 용어 + 관리자 틀·운영 + 공통 레이아웃 | 23 | 106 | 40 |

- 이슈 #3(DB 스키마 SQL)와 #87(인덱스·쿼리 점검)은 DBA 두 명이 구역을 나눠 맡습니다(GitHub 담당자도 두 명, 점수는 #3은 각각 3, #87은 각각 2로 반영).
- GitHub 담당자(Assignee)는 이 문서의 배정과 같게 지정되어 있습니다. 담당을 옮길 때는 이 문서와 GitHub 담당자를 같이 고칩니다.
- API 합계는 148개이고 모든 API가 한 번씩 배정되어 있습니다. API 수가 DBA ①에 많은 것은 강의·문의·강사 관리 API가 많기 때문이고, 화면과 난이도는 점수에 반영했습니다.

### FE / BE 구분

우리 팀은 **기능(도메인) 단위로 담당**합니다. 한 사람이 자기 기능의 BE(서버)와 FE(화면)를 모두 맡고, 기능 하나가 끝나면 화면까지 열려야 합니다(세로로 자르기). 대신 아래처럼 FE와 BE를 이슈·폴더·약속으로 분명히 나눕니다.

**담당자별 BE / FE 이슈** (점수는 부록 기준, 기타 = 문서·시드·브랜치·점검 이슈)

| 담당 | BE 이슈 | FE 이슈 | 기타 |
| --- | --- | --- | --- |
| 서수영 | 9개 · 54점<br>#2 #4 #27 #28 #29 #47 #48 #67 #84 | 3개 · 29점<br>#58 #63 #70 | #68 #74 #83 #85 |
| 이재성 | 13개 · 58점<br>#15 #23~#26 #40~#46 #65 | 4개 · 29점<br>#54 #56 #57 #62 | #72 #76 #86 |
| 신아림 | 14개 · 60점<br>#16~#22 #30 #31 #32 #34 #35 #37 #38 | 6개 · 39점<br>#52 #53 #55 #59 #60 #71 | #73 #87 #95 |
| 김예린 | 15개 · 64점<br>#5~#14 #33 #36 #39 #64 #66 | 5개 · 35점<br>#49 #50 #51 #61 #69 | #75 #87 #95 |

**폴더·이슈 소유** — 자기 이슈가 아닌 쪽의 파일은 직접 고치지 않고 이슈로 요청합니다(§8). 아래 경로는 모두 **저장소 루트** 기준이고, 저장소 루트에는 Gradle 래퍼가 없어서 명령은 `moneybeetle/` 폴더에서 실행합니다.

| 구분 | 폴더 | 이슈 라벨·브랜치 | 끝났다는 확인 |
| --- | --- | --- | --- |
| BE | `moneybeetle/src/main/java/**`, `moneybeetle/src/main/resources/sql/**`, `moneybeetle/src/main/resources/application.yaml`, `moneybeetle/src/test/**` | `be` · `feat/be-…` | `moneybeetle/` 폴더에서 테스트 통과(Git Bash는 `./gradlew test`, PowerShell은 `.\gradlew.bat test`), API가 명세서와 같음 |
| FE | `moneybeetle/src/main/resources/templates/**`, `moneybeetle/src/main/resources/static/**` (공통 틀 `fragments/layout`·`static/**`은 김예린, 마이페이지·관리자 대시보드 탭은 §6 표대로) | `fe` · `feat/fe-…` | 서버를 띄워 화면이 열리고 PC·모바일에서 동작 확인 |
| 문서·설정 | `docs/**`, `.github/**`, `.coderabbit.yaml`, `CLAUDE.md` | `docs`·`chore` | 관련 문서가 같은 PR에서 고쳐짐 |

**FE와 BE가 만나는 약속**

1. **화면 요청** — Controller(BE)가 View 이름과 Model 이름을 정하고, 템플릿(FE)이 그 이름으로 그립니다. 이름과 DTO는 FE 이슈의 "관련 BE 이슈"와 화면설계 §10.2(Model 표)에 적고, 바뀌면 같은 PR에서 고칩니다. 모든 화면이 쓰는 공통 값은 `layout`(`LayoutDTO`)입니다.
2. **데이터 요청(fetch)** — API 명세서가 계약입니다. 화면 JS는 `static/js/common.js`의 `MB.api()`로 부르고, 명세서에 없는 필드에 기대지 않습니다. 실패하면 `MB.api()`는 `{ code, message, fields }`를 던지며, `fields`는 입력칸별 오류(`@Valid` 실패, 없으면 `null`)라서 입력칸 아래 문구에 씁니다. 서버에 닿지 못한 네트워크 실패도 같은 모양(`code`는 `NETWORK_ERROR`, `fields`는 `null`)으로 던지므로 화면은 응답 오류와 같은 방식으로 처리합니다.
3. **한쪽이 먼저 끝날 때** — 약속한 이름으로 `Fake` 데이터(코딩컨벤션 §11)를 쓰고, 값이 없어도(null) 화면이 깨지지 않게 그립니다. 나중에 진짜 값으로 바꿉니다.
4. **권한·레벨** — 화면은 `sec:authorize`(`MEMBER`·`INSTRUCTOR`·`ADMIN`)와 잠금 표시로 보여 주기만 하고, 실제 차단은 BE(`SecurityConfig`, `LevelCheckInterceptor`)가 합니다.
5. **한 이슈가 양쪽을 건드릴 때** — 자기 이슈의 BE와 FE는 한 사람이 같은 브랜치 흐름으로 하되, PR은 BE와 FE를 이슈별로 나눕니다(`feat/be-…`, `feat/fe-…`). 한 PR = 한 이슈 규칙은 그대로입니다. 한 이슈에 BE와 FE 작업이 함께 들어 있다면 이렇게 나눕니다.
   1. 기존 이슈는 **추적용**으로 열어 둡니다. 본문에 `- [ ] #BE이슈`, `- [ ] #FE이슈` 체크리스트를 적습니다(GitHub 하위 이슈 기능을 써도 됩니다).
   2. BE 이슈(`be` 라벨, `feat/be-…`)와 FE 이슈(`fe` 라벨, `feat/fe-…`)를 새로 만들고, 각 본문에 `Refs #추적이슈`를 적어 연결합니다. FE 이슈의 "관련 BE 이슈"에 BE 이슈 번호를 적습니다.
   3. 각 PR에는 자기 작업 이슈의 `Closes #번호` 하나만 적습니다. 추적 이슈는 PR에 `Closes`로 걸지 않고, 두 작업 이슈가 모두 닫히면 직접 닫습니다.

**리뷰 기준** — FE PR은 화면을 직접 열어 확인한 방법(스크린샷 또는 확인 순서)을 본문에 적고, BE PR은 테스트와 명세서 일치를 봅니다. 가능하면 FE PR은 연결된 BE 이슈 담당자가, BE PR은 연결된 FE 이슈 담당자가 리뷰합니다.

### 담당자별 고유 업무 (코드 외)

| 역할 | 고유 업무 |
| --- | --- |
| **PM 이재성** | 기획·실행·테스트·완료 전체 흐름과 일정 관리, 회의 주관, 의견 조율, 병목 파악·해결. 미확정 사항(#68 XP 값, #76 캐시·청약통장 기준, 청약 당첨 규칙 등)의 결정 조율, 통합 점검(#86) |
| **SCM 서수영** | 프로젝트 문서 변경 추적·버전 관리, Git/GitHub 브랜치 전략·이슈 설계, 문서·개발 컨벤션 설계, `dev → main` 릴리스 PR, 브랜치 정리·보호(#85), README(#83) |
| **DBA ① 신아림 · ② 김예린** | DB 설계·관리, 스키마 버전 관리, SQL 최적화. 코드 담당자가 테이블을 바꾸려면 DBA에게 요청 |

**DB 관리 구분**: 신아림 (DBA ①) = 회원·인증·문의·운영·강의·퀴즈 테이블 23개, 김예린 (DBA ②) = 성장·알림·경제 용어 8개 + 청약 4개 + 모의투자 12개 = 24개. 시드는 구역을 나눠 함께 관리합니다.

---

## 2. 서수영 (SCM) — 모의 청약 · 공통 기반 · CI/배포

**패키지**: `domain/housing`, `global/**`, `domain/auth/config`(SecurityConfig)
**테이블(코드 담당 4)**: subscription_account, subscription_deposit, housing_complex, housing_application
**요구사항**: SUB-01~11, ADM-36~39, NFR-01~04, NFR-12~14
**16개 · 점수 93 (+10) · API 16개**

| 이슈 | 작업 | API | 점수 | 핵심 내용 · 확정 사항 |
| --- | --- | --- | --- | --- |
| #2 | 공통 응답·예외 처리·기본 설정 구성 | — | 8 | `ApiResponse`·`PageResponse`·`ErrorCode`·`GlobalExceptionHandler`·서버 시작 클래스(`@SpringBootApplication`)·`application.yaml`. BaseTimeEntity는 필요한 컬럼만 가진 공통 클래스로 분리. **가장 먼저**(없으면 서버가 뜨지 않음) |
| #4 | 인증·권한·레벨 해금 공통 처리 | — | 6 | `SecurityConfig`(세션·BCrypt), `/instructor/**`·`/admin/**` 접근 제한, `@RequireLevel`·`LevelCheckInterceptor`(API명세서에서 레벨이 표시된 API에만. 청약통장·당첨 결과는 제외) |
| #27 | 청약 캘린더·공고 상세 API | 63,64 | 3 | 청약 캘린더·공고 상세 (Lv.12). 신청 기간·일정은 서버 KST 시각으로 계산 |
| #28 | 청약통장 납입 API | 65~67 | 4 | 청약통장 조회·납입·내역. **레벨 조건 없음**. 하루 1회, 캐시 차감은 김예린 `RewardService.spend`. `Idempotency-Key` 적용 |
| #29 | 가점 계산·청약 신청·당첨 결과 API | 68~71 | 9 | 가점 계산기·신청·내 신청 내역·당첨 결과. 신청 때 가점·납입 회차·인정금액을 스냅샷으로 저장 |
| #47 | 가상 단지 공고 관리 API | 142~145 | 4 | 공고 등록·수정·삭제·목록. 신청자가 있으면 삭제 대신 비공개, 유형·공급 세대 수 변경 제한 |
| #48 | 신청자 목록·당첨자 산정·결과 발표 API | 146~148 | 11 | 신청자 목록·당첨자 산정·결과 발표. **당첨 규칙은 프로토타입 기준 임시안**(커트라인 방식, 가점표 공공 85점·민영 68점)이며 추후 확정 |
| #67 | 환경변수·CI·배포 구성 | — | 6 | `.env`·`.gitignore`·`application-*.yml`, GitHub Actions 빌드·테스트, Docker 배포. 환경변수 문서 기준 |
| #58 | 모의 청약 화면 (화면) | — | 13 | 모의 청약 화면(캘린더·통장·가점 계산기·신청·결과). Lv.12 미만 블러, 신청 기간 밖 신청 버튼 비활성화 |
| #63 | 관리자 모의 청약 운영 화면 (화면) | — | 12 | 관리자 청약 운영(공고·신청자·산정·발표). 발표 뒤 사용자 화면에 결과 노출 |
| #68 | 경험치 활동 보상 기준 확정 (문서) | — | 2 | 활동별 XP 확정 → 정책 정의서 SP-22·`reward_rule` 시드·관련 이슈 본문 반영. 값 결정은 PM이 조율 |
| #70 | 마이페이지 나의 청약통장 탭 (화면) | — | 4 | 마이페이지 "나의 청약통장" 탭(잔액·납입·내역). **레벨 조건 없음**, `Idempotency-Key`. `fragments/mypage/housing` |
| #74 | 샘플 데이터 — 청약 단지·공고 | — | 3 | 시연용 공공·민영 단지·공고(네 단계 날짜 구성, 커트라인, 이미지). `housing/store.js` 기준 |
| #83 | README 실행 가이드·담당 표기 정리 (문서) | — | 3 | 로컬 실행 방법(`.env.example` → DB → `bootRun`), 폴더 구조, 규칙 요약, 코드 파일 머리말·README의 옛 담당 표기 수정 |
| #84 | Actuator·로그 설정 | — | 3 | `health`·`info`만 노출, 프로필별 로그 레벨·보관, 비밀번호·토큰 로그 금지, 요청 ID(MDC), Docker·Nginx 헬스 체크 |
| #85 | 브랜치 정리·보호 규칙 설정 | — | 2 | `sy`·`test` 등 남은 브랜치 정리, `main`·`dev` 보호(PR 필수·승인 1명), `main`은 `dev`에서만 병합 |

**다른 사람에게 줘야 하는 것**: `HousingService.openAccount(memberId)`(김예린 가입), `HousingService.getDashboardSummary()`(김예린 관리자 첫 화면), `FileStorageService.save(file, dir)`(**신아림**·**김예린**), `LevelCheckInterceptor`·`SecurityConfig`
**서수영이 쓰는 것**: 김예린 `GrowthService.getLevel`, `RewardService.spend`, `NotificationService.send`

---

## 3. 이재성 (PM) — 모의투자 · 통합 검색 · 마이페이지 틀

**패키지**: `domain/trading`, `global/security/IdempotencyFilter`(서수영 검토), `fragments/mypage` 틀
**테이블(코드 담당 12)**: trading_season, season_turn, sector, stock, stock_price, news, news_tag, trade_reason, season_participant, holding, trade_order, ranking_award
**요구사항**: INV-01~18, ADM-08~28, ADM-31~35, NFR-05, NFR-07
**참고 코드**: `moneybeetle-page/new1/App.jsx`(사용자), `new1/Admin.jsx`(관리자), `DemoTrading-code/`
**20개 · 점수 98 (+10) · API 39개**

| 이슈 | 작업 | API | 점수 | 핵심 내용 · 확정 사항 |
| --- | --- | --- | --- | --- |
| #15 | 통합 검색 API | 28 | 3 | 강의(신아림)·문의(신아림)·종목(본인) 검색 메서드를 모아 결과 제공, 자동완성 |
| #23 | 모의투자 홈·시즌 참가 API | 53,54 | 3 | 모의투자 홈·시즌 참가. **참가해도 캐시는 차감하지 않음**, Lv.8부터 참가 (팀 결정) |
| #24 | 종목·시세·뉴스 조회 API | 55~58 | 4 | 분야·종목·가상 시세·뉴스 조회. 같은 턴 안에서는 가격 고정 |
| #25 | 매수·매도 주문 API | 59~61 | 7 | 매수·매도. 한 트랜잭션(NFR-05), 이전 턴 가격·휴장 거절, 매매 이유 필수, 거래 내역, `Idempotency-Key` 적용 |
| #26 | 시즌 랭킹 조회 API | 62 | 2 | 시즌 랭킹 조회(공개된 뒤에만) |
| #40 | 시즌 생성·설정·상태 관리 API | 114~117 | 4 | 시즌 생성·설정·상태. 시작일(월) → 14일·12턴 자동 생성, 이월 비율·상한 |
| #41 | 분야·종목·기업정보 관리 API | 121~126 | 6 | 분야 15개 × 종목 30개, 기업정보 수정 |
| #42 | 턴별 가격 입력·CSV 업로드/다운로드 API | 127~130 | 6 | 턴별 가격표 입력, CSV 내려받기·올리기, ±30%·0원 경고, 지난 턴 잠금 |
| #43 | 가상 뉴스 등록·관리 API | 131~134 | 4 | 턴당 뉴스 2~3개, 관련 분야·종목, 지난 턴은 오타 수정만 |
| #44 | 턴 달력·다음 턴 진행 API | 118~120 | 6 | 턴 달력, 다음 턴 점검(30종목 가격·뉴스 빈칸), 진행 기록. 되돌릴 수 없음 |
| #45 | 참가자 관리·랭킹 미리보기 API | 135~137 | 3 | 참가자 목록·상세(순자산·수익률·보유·거래), 랭킹 미리보기 |
| #46 | 시즌 정산·랭킹 공개·보상·통계 API | 138~141 | 7 | 정산 실행(이월금·최종 자산), 랭킹 공개·보상, 시즌 통계. 관리자 첫 화면용 요약 메서드 제공 |
| #65 | 중복 요청 방지(Idempotency-Key) | — | 3 | `IdempotencyFilter`(`global/security`에 두되 서수영 검토). 키 24시간 보관, **같은 키에 다른 내용이면 409 거절**(`IDEMPOTENCY_KEY_CONFLICT`). 퀴즈 제출·매수·매도·청약통장 납입·강의 영상 저장(API 78)에 적용 |
| #54 | 마이페이지 화면 (화면) | — | 7 | **담당 범위는 틀뿐**: 마이페이지 **틀**(`member/mypage.html`)과 틀의 Controller·API 연동(탭 전환, 탭별 fragment 연결). 탭 내용은 각 담당이 fragment로 만든다: `settings`(설정·프로필 이미지·비밀번호·탈퇴)는 **김예린(#69)**, `learning`(스크랩·오답노트 포함)은 신아림(#71), `growth`는 김예린(#69), `housing`은 서수영(#70). 스크랩·오답노트·설정은 #54에서 구현하지 않고 각 fragment를 틀에 연결만 한다 |
| #56 | 통합 검색 화면 (화면) | — | 4 | 통합 검색 화면(`home/search.html`), 상단 검색창 자동완성(`mb-search.js`) |
| #57 | 모의투자 화면 (화면) | — | 9 | 모의투자 화면(참고: `new1/App.jsx`). Lv.8 미만 블러, 주문은 실제 데이터로 동작 |
| #62 | 관리자 모의투자 운영 화면 (화면) | — | 9 | 관리자 모의투자 운영(시즌·종목·가격·뉴스·턴·정산·통계). 누락 데이터가 있으면 다음 턴 버튼 비활성화 |
| #72 | 샘플 데이터 — 종목·가격·뉴스 | — | 5 | 시연용 분야 15·종목 30·12턴 가격표·턴당 뉴스·매매 이유. `new1/App.jsx` 기준 |
| #76 | 캐시 보상·청약통장 납입·이월금 기준 확정 (문서) | — | 2 | 출석·학습 완료·레벨업 캐시, 청약통장 납입, 이월금, 랭킹 보상 값 확정 → 정책 정의서·시드·`mb-config.js` 반영. 결정 조율은 PM |
| #86 | 통합 점검·시연 시나리오 리허설 | — | 4 | 시연 시나리오 4개(학습·모의투자·청약·강사/관리자)를 `dev`에서 끝까지 실행, 문제는 담당자 이슈로 정리. 기능이 `dev`에 모인 뒤 진행 |

**다른 사람에게 줘야 하는 것**: `RankingService.findAwards(memberId)`(김예린 Drawer·마이페이지), `TradingService.getDashboardSummary()`(김예린 관리자 첫 화면), `IdempotencyFilter`(전원)
**이재성이 쓰는 것**: 김예린 `RewardService.getBalance`·`spend`, `NotificationService.send`, 신아림 `CourseService.search`·`InquiryService.search`

---

## 4. 신아림 (DBA ①) — 강의 · 퀴즈 · 강사 · 문의

**패키지**: `domain/course`, `domain/quiz`, `domain/inquiry`, `AdminMemberController`(#37)
**테이블(코드 담당 14)**: category, course, course_review, lesson, learning_progress, lesson_watch, scrap, quiz_question, quiz_choice, quiz_attempt, quiz_answer, wrong_answer_note, inquiry, inquiry_reply
**DB 관리**: 회원·인증·문의·운영·강의·퀴즈 테이블 23개, 시드의 카테고리·강의·퀴즈
**요구사항**: LRN-01~09, QST-01~06, TCH-01~09, ADM-02~04, ADM-06~07, NFR-15
**23개 · 점수 107 (+3) · API 53개**

| 이슈 | 작업 | API | 점수 | 핵심 내용 · 확정 사항 |
| --- | --- | --- | --- | --- |
| #16 | 카테고리·강의 목록·강의 상세 API | 29~31 | 3 | 카테고리·소주제·강의 목록·강의 상세 (허가된 강의만, 영상 최대 3개, 기준일/출처) |
| #17 | 영상 시청·이어서 보기·시청 기록 API | 32~35 | 4 | 영상 재생·시청 위치 저장·시청 기록·학습 완료 목록. 홈 "이어서 볼 강의" 메서드 제공 |
| #18 | 강의 스크랩 API | 36~38 | 3 | 스크랩·해제·목록 |
| #19 | 테스트 퀴즈(OX) API | 39,40 | 2 | 테스트 퀴즈(OX 5문제) 문제·채점. 불이익 없음 |
| #20 | 퀴즈 제출·채점·학습 완료 처리 | 41~43 | 6 | 4지선다 퀴즈 제출·채점·학습 완료. **통과 기준 70점 이상**(확정). 보상은 김예린 `RewardService.grant`, `Idempotency-Key` 적용 |
| #21 | 오답노트 API | 44~46 | 3 | 오답노트 조회·다시 풀기·삭제 |
| #22 | 강의 문의 게시판 API | 47~52 | 6 | 강의 문의 게시판(작성·수정·삭제·답글). 금칙어 검사, 비공개글 권한, 하루 10개 제한 |
| #30 | 강사 가입 신청·강의 생성/수정/삭제 API | 12, 72~76 | 5 | 강사 가입 신청(API 12)·내 강의 목록·강의 생성·수정·삭제. 신청은 `member` 도메인이므로 김예린 서비스와 협의 |
| #31 | 영상·요약글·퀴즈 등록 API | 77~80 | 9 | 썸네일·영상·요약글·퀴즈 저장. **영상은 링크가 아니라 파일 업로드**(확정, `FileStorageService`) |
| #32 | 검수 요청·내 강의 문의 API | 81,82 | 2 | 검수 요청, 내 강의 문의(`GET /instructor/inquiries`) |
| #34 | 카테고리 관리 API | 85~88 | 4 | 카테고리·소주제 관리 |
| #35 | 강의 검수·공개 관리 API | 89~94 | 6 | 강의 허가·반려(사유)·공개·비공개·삭제. 학습 기록이 있으면 삭제 대신 비공개. 알림은 김예린 `NotificationService` |
| #37 | 사용자·강사 관리 API | 101~105 | 5 | 사용자·강사 목록, 계정 정지·해제, 강사 권한, 강사 가입 신청·승인 (`AdminMemberController`) |
| #38 | 문의 게시판 관리 API | 106,107 | 2 | 문의 게시판 관리(조회·숨김·해제) |
| #52 | 강의 목록·강의 상세·영상 시청 화면 (화면) | — | 9 | 강의 목록·상세·영상 시청 화면, 이어서 보기, 스크랩 버튼 |
| #53 | 테스트 퀴즈·퀴즈·결과 화면 (화면) | — | 4 | 테스트 퀴즈·퀴즈·결과 화면 |
| #55 | 문의 게시판 화면 (화면) | — | 4 | 문의 게시판 화면 |
| #59 | 강사 로그인·가입·대시보드 화면 (화면) | — | 13 | 강사 로그인·가입·대시보드(내 강의, Q&A 탭) |
| #60 | 강사 강의 생성 화면 (화면) | — | 5 | 강의 생성 화면(분류·정보·영상·요약글·퀴즈·검수 요청) |
| #71 | 마이페이지 학습 기록·Q&A 모음 탭 (화면) | — | 4 | 시청 기록·학습 완료·스크랩(#18)·오답노트(#21)·내 문의(Q&A 모음) 탭, 강의로 이동. `fragments/mypage/learning`. 스크랩·오답노트는 신아림의 API라서 이 이슈에서 함께 맡는다(점수는 그대로) |
| #73 | 샘플 데이터 — 강의·영상·퀴즈 | — | 4 | 시연용 카테고리 2·소주제·샘플 강의·영상 경로·요약글·OX 5문제·4지선다 퀴즈 |
| #87 | 인덱스·쿼리 점검 (김예린과 공동) | — | 2 | 회원·강의·문의·운영 구역의 조회 실행 계획(EXPLAIN)·인덱스 점검. DB 설계서와 `schema.sql`을 같은 PR에서 수정 |
| #95 | SQL 파일을 `schema.sql` + `sample-data.sql`로 정리 (김예린과 공동, 사후 기록) | — | 2 | 수업 형식으로 SQL 구조 정리(PR #91~#93 병합 완료). 후속: DB 이름·접속 계정 정리 |

**다른 사람에게 줘야 하는 것**: `CourseService.hasLearningHistory(memberId)`(김예린 홈·로그인 분기), `CourseService.findContinueWatching / findLearningSummary(memberId)`(김예린 홈·Drawer), `CourseService.search(q)`·`InquiryService.search(q)`(이재성 검색)
**신아림이 쓰는 것**: 김예린 `RewardService.grant`·`NotificationService.send`·`ForbiddenWordService.check`·`AdminLogService.write`, 서수영 `FileStorageService.save`

---

## 5. 김예린(DBA ②) — 회원·인증 · 성장 · 홈 · 관리자 틀

**패키지**: `domain/member`, `domain/auth`(config 제외), `domain/growth`, `domain/notification`, `domain/home`, `domain/term`, `domain/operation`, `templates/fragments/layout`·`static/**`
**테이블(코드 담당 14)**: member, instructor_profile, social_account, terms, member_terms_agreement, password_reset_token, system_setting, admin_log, forbidden_word, attendance, cash_history, exp_history, level_policy, reward_rule, notification, economic_term, term_schedule
**DB 관리**: 성장·알림·경제 용어 8개 + 청약 4개 + 모의투자 12개 = 24개, 시드의 레벨 정책·보상 규칙·분야·종목·가격
**요구사항**: USR-01~14, HOM-01~06, GRW-01~08, ADM-01, ADM-05, ADM-29~30, NFR-06, NFR-08~10, NFR-13
**23개 · 점수 106 (+3) · API 40개**

| 이슈 | 작업 | API | 점수 | 핵심 내용 · 확정 사항 |
| --- | --- | --- | --- | --- |
| #5 | 회원가입 API | 1~5 | 5 | 약관·이메일·닉네임 중복 확인·가입·본인인증. 가입 직후 서수영 `HousingService.openAccount()` 호출 |
| #6 | 로그인·로그아웃 API | 6,7 | 4 | 로그인·로그아웃. Controller 없이 Security 폼 로그인 + 성공·실패 핸들러, 5회 실패 10분 잠금, 출석 호출 |
| #7 | 내 정보·설정 수정 API | 13~15, 18 | 6 | 내 정보·설정 수정·프로필 이미지·다른 회원 공개 프로필 (Drawer 프로필 응답 포함) |
| #8 | 아이디 찾기·비밀번호 재설정·변경·회원 탈퇴 API | 9~11, 16,17 | 4 | 아이디 찾기·비밀번호 재설정(메일)·비밀번호 변경·회원 탈퇴(비활성화) |
| #9 | 본인인증·간편 로그인 | 8 | 2 | 본인인증·간편 로그인(구글·카카오)은 실제 연동 없이 **가상 방식**으로 구현 (팀 결정, 우선순위 하) |
| #10 | 캐시·경험치 지급 공통(RewardService) | 22,23 | 8 | `RewardService` 캐시·경험치 지급/차감, 같은 활동 중복 지급 차단, 원장. **메서드 이름만 먼저 공개**. XP 값은 #68 확정 후 반영 |
| #11 | 레벨업·칭호·레벨업 캐시 | 24 | 3 | 레벨업·칭호. **최대 Lv.20, 칭호 구간별 필요 XP**(고시원 150·원룸 200·투룸 300·아파트 400·내 집 600). 누적 Lv.8=1,250, Lv.12=2,450 |
| #12 | 출석 체크·연속 출석 캐시 | 21 | 3 | 출석(하루 1회), 연속 5일부터 매일 캐시 |
| #13 | 홈 화면·오늘의 경제 용어 API | 19,20 | 4 | 홈 화면 API(오늘의 용어·이어서 볼 강의·학습 기록·트레이닝 바로가기) |
| #14 | 알림 API | 25~27 | 4 | 알림 목록·읽음·모두 읽음, Lv.8·Lv.12 해금 알림 |
| #33 | 관리자 로그인·대시보드 API | 83,84 | 3 | 관리자 로그인·대시보드 API. 첫 화면 요약은 각 담당 Service의 요약 메서드를 받아 표시 |
| #36 | 경제 용어·오늘의 용어 지정 API | 95~100 | 6 | 경제 용어 등록·수정·삭제, 날짜별 5개 지정 |
| #39 | 보상 규칙·캐시 수동 조정·서비스 설정·작업 기록 API | 108~113 | 6 | 보상 규칙 조회·수정, 캐시 수동 지급·회수(사유 필수), 서비스 설정, 관리자 작업 기록(`AdminLogService`) |
| #64 | 금칙어 검사 | — | 3 | `forbidden_word` 기반 금칙어 검사 서비스(`ForbiddenWordService`). 닉네임·문의·Q&A·강의 제목·요약글에 적용 |
| #66 | 예약 작업(탈퇴 정보 정리·만료 토큰 삭제) | — | 3 | 탈퇴 30일 뒤 개인정보 삭제, 만료 토큰 삭제 (`@EnableScheduling`) |
| #49 | Thymeleaf 공통 레이아웃·정적 파일 정리 (화면) | — | 10 | Thymeleaf 공통 레이아웃(`fragments/layout`), `static/` 정리, 역할별 메뉴·레벨 잠금 표시. 다른 화면의 기반 |
| #50 | 로그인·회원가입·계정 찾기 화면 (화면) | — | 4 | 로그인·회원가입·계정 찾기 화면 |
| #51 | 홈 화면·햄버거 메뉴·알림 (화면) | — | 7 | 홈 화면(vr1/vr2)·햄버거 메뉴·알림. 서비스 소개 메인(`index.html`)도 여기서 맡음(별도 이슈 없음) |
| #61 | 관리자 로그인·대시보드·운영 메뉴 화면 (화면) | — | 10 | 관리자 로그인·대시보드 **틀**·운영 메뉴. 탭(`?view=`) 내용은 각 담당이 fragment로 제공 |
| #69 | 마이페이지 설정·캐시 내역·레벨 탭 (화면) | — | 4 | "캐시 내역" 탭·프로필 요약(레벨·칭호·경험치·연속 출석·수상 내역)과 **"설정" 탭**(설정 수정·프로필 이미지·비밀번호 변경·회원 탈퇴, #7·#8 API). 수상 내역은 이재성이 데이터 제공. `fragments/mypage/growth`, `fragments/mypage/settings` (설정 탭은 #54에서 옮겨 온 것이라 점수는 그대로 두고 진행하며 조정) |
| #75 | 샘플 데이터 — 경제 용어·알림 | — | 3 | 시연용 경제 용어 사전·날짜별 5개·개발용 알림 샘플 |
| #87 | 인덱스·쿼리 점검 (신아림과 공동) | — | 2 | 성장·알림·경제 용어·청약·모의투자 구역의 조회 실행 계획·인덱스 점검 |
| #95 | SQL 파일을 `schema.sql` + `sample-data.sql`로 정리 (신아림과 공동, 사후 기록) | — | 2 | 수업 형식으로 SQL 구조 정리(PR #91~#93 병합 완료). 후속: DB 이름·접속 계정 정리 |

**다른 사람에게 줘야 하는 것**: `RewardService.grant / spend / getBalance`, `GrowthService.getLevel(memberId)`, `NotificationService.send(memberId, type, title, link)`, `AttendanceService.checkToday(memberId)`, `ForbiddenWordService.check(text)`, `AdminLogService.write(...)`
**김예린이 쓰는 것**: 신아림 `CourseService.*`, 서수영 `HousingService.openAccount`·`getDashboardSummary`, 이재성 `RankingService.findAwards`·`TradingService.getDashboardSummary`

---

## 6. 화면 담당표 (moneybeetle-page → templates)

| 화면 (.dc.html) | 향후 URL | 템플릿 | 이슈 | 담당 |
| --- | --- | --- | --- | --- |
| Main | `/` | `index.html` | #51에 포함(별도 이슈 없음) | **김예린** |
| UserLogin / UserSignup | `/login`, `/signup` | `auth/login.html`, `member/signup.html` | #50 | **김예린** |
| UserHome-vr1 / vr2 | `/user/home` | `home/home.html` (학습 이력 유무로 분기) | #51 | **김예린** |
| UserLectureCategory | `/user/lectures?cat=` | `course/category.html` | #52 | **신아림** |
| UserLectureDetail | `/user/lecture-detail` | `course/detail.html` | #52, #53 | **신아림** |
| UserMyPage | `/user/my-page` | `member/mypage.html` (틀 + 탭 3개) | #54 | 이재성 틀|
| UserSearch | `/user/search?q=` | `home/search.html` | #56 | **이재성** |
| UserInquiry | `/user/inquiry` | `inquiry/inquiry.html` | #55 | **신아림** |
| UserDemoTrading | `/user/demo-trading` | `trading/trading.html` | #57 | **이재성** |
| UserDemoHousingSubscription | `/user/demo-housing-subscription` | `housing/housing.html` | #58 | **서수영** |
| InstructorLogin / Signup / Dashboard | `/instructor/*` | `auth/instructor-login.html`, `member/instructor-signup.html`, `instructor/dashboard.html` | #59 | **신아림** |
| InstructorCourseCreate | `/instructor/course-create` | `instructor/course-create.html` | #60 | **신아림** |
| AdminLogin / AdminDashboard | `/admin/login`, `/admin/dashboard?view=` | `auth/admin-login.html`, `admin/dashboard.html` (틀 + 탭) | #61 | 김예린 틀 |
| AdminTradingManagement | `/admin/trading-management` | `admin/trading.html` | #62 | **이재성** |
| AdminHousingManagement | `/admin/housing-management` | `admin/housing.html` | #63 | **서수영** |
| (공통) 헤더·Drawer·알림 | — | `fragments/layout/*`, `static/**` | #49 | **김예린** |

**틀 + 탭(fragment)으로 나눠 쓰는 화면**

| 화면 | 틀 | 탭 / 영역 | 탭 담당 |
| --- | --- | --- | --- |
| 마이페이지 | 이재성(틀, #54) | `mypage/settings` (설정·프로필 이미지·비밀번호·탈퇴) (#69) | **김예린** |
| | | `mypage/learning` (시청 기록·학습 완료·스크랩·오답노트·Q&A 모음) (#71) | **신아림** |
| | | `mypage/growth` (캐시·경험치·레벨표·수상 내역) (#69) | **김예린** (수상 내역은 이재성이 데이터 제공) |
| | | `mypage/housing` (나의 청약통장·납입·내역) (#70). 청약 신청 내역은 청약 화면(#58)에서 보여 준다 | **서수영** |
| 관리자 대시보드 | 김예린 | `?view=members` 회원·강사 / `?view=board` 문의 | **신아림** |
| | | `?view=courses` 카테고리·강의 검수 | **신아림** |
| | | `?view=terms` 경제 용어 / `?view=growth` 보상·캐시 / `?view=settings` 설정·작업 기록 | **김예린** |
| 헤더·Drawer | 김예린 | 프로필·연속 출석·경험치·캐시·수상 내역, 알림 종, 검색창 | **김예린** (수상 내역은 이재성 데이터) |

---

## 7. 연동 약속 (서로 부르는 메서드)

다른 도메인의 Repository는 직접 쓰지 않고 아래 Service 메서드만 호출합니다. **각 제공자는 1단계에서 메서드 이름·파라미터만 먼저 만들어 올립니다**(내용은 `TODO`).

| 제공자 | 메서드 | 호출하는 사람 | 언제 |
| --- | --- | --- | --- |
| 김예린 | `RewardService.grant(memberId, type, refId)` | 신아림 | 학습 완료·퀴즈 통과 |
| 김예린 | `RewardService.spend(...)` / `getBalance(memberId)` | 이재성, 서수영 | 시즌 참가 잔액 확인, 청약통장 납입 |
| 김예린 | `GrowthService.getLevel(memberId)` | 서수영 | 레벨 해금 인터셉터 |
| 김예린 | `NotificationService.send(memberId, type, title, link)` | 신아림, 이재성, 서수영 | 검수 결과, 턴 진행·랭킹 공개, 청약 결과 |
| 김예린 | `AttendanceService.checkToday(memberId)` | 김예린 | 로그인 성공 |
| 김예린 | `ForbiddenWordService.check(text)` | 신아림, 김예린 | 문의·닉네임·강의 글 |
| 김예린 | `AdminLogService.write(adminId, action, targetId, before, after, reason)` | 전원 | 관리자 중요 작업 |
| 김예린 | `InstructorService.apply(memberId, ...)` / `approve(applicationId)` | 신아림 | 강사 가입 신청(#30), 관리자 강사 승인(#37). `instructor_profile`은 김예린 `member` 도메인 소유 |
| 서수영 | `HousingService.openAccount(memberId)` | 김예린 | 회원가입 완료 |
| 서수영 | `HousingService.getDashboardSummary()` | 김예린 | 관리자 첫 화면 |
| 서수영 | `FileStorageService.save(file, dir)` | 신아림, 김예린 | 썸네일·영상 파일, 프로필 이미지 |
| 이재성 | `RankingService.findAwards(memberId)` | 김예린 | Drawer 수상 내역, 마이페이지 |
| 이재성 | `TradingService.getDashboardSummary()` | 김예린 | 관리자 첫 화면 |
| 이재성 | `IdempotencyFilter` (`Idempotency-Key` 헤더) | 전원 | 퀴즈 제출·매수·매도·청약통장 납입·강의 영상 저장(API 78) |
| 신아림 | `CourseService.hasLearningHistory(memberId)` | 김예린 | 로그인 후 홈 vr1/vr2 분기 |
| 신아림 | `CourseService.findContinueWatching / findLearningSummary(memberId)` | 김예린 | 홈, Drawer |
| 신아림 | `CourseService.search(q)` / `InquiryService.search(q)` | 이재성 | 통합 검색 |

---

## 8. 같이 쓰는 파일 — 수정 권한

| 파일 | 고치는 사람 | 다른 사람은 |
| --- | --- | --- |
| `global/**`, `domain/auth/config/**`(`SecurityConfig`·`LevelCheckInterceptor`), `CommonErrorCode` | 서수영 | 이슈로 요청 |
| `domain/auth/**`(로그인 핸들러 등, config 제외), `domain/member/**` | 김예린 | 서수영은 `auth/config`만 수정 |
| `global/security/IdempotencyFilter` | 이재성 | 서수영이 검토 |
| `application.yaml`, `build.gradle`, `.github/**`, Docker | 서수영 | 이슈로 요청 (환경변수는 `MoneyBeetle_환경변수.md`) |
| `sql/schema.sql` 신아림 담당 테이블 23개: member, instructor_profile, social_account, terms, member_terms_agreement, password_reset_token, system_setting, admin_log, forbidden_word, inquiry, inquiry_reply, category, course, course_review, lesson, learning_progress, lesson_watch, scrap, quiz_question, quiz_choice, quiz_attempt, quiz_answer, wrong_answer_note | 신아림 | 컬럼 변경은 신아림에게 요청 (파일 안은 알파벳순이라 구역 구분이 없음) |
| `sql/schema.sql` 김예린 담당 테이블 24개: attendance, cash_history, exp_history, level_policy, reward_rule, notification, economic_term, term_schedule, subscription_account, subscription_deposit, housing_complex, housing_application, trading_season, season_turn, sector, stock, stock_price, news, news_tag, trade_reason, season_participant, holding, trade_order, ranking_award | 김예린 | 컬럼 변경은 김예린에게 요청(청약은 서수영, 모의투자는 이재성이 요청) |
| `sql/sample-data.sql` 시드 | 파일 맨 아래에 자기 구역 주석(`-- [이름] 구역`)을 만들고 그 아래에만 추가: 회원·강의·퀴즈 구역 신아림(#73, 이미 있음), 성장·알림·경제 용어 구역 김예린(#75, 레벨 정책·보상 규칙 포함), 모의투자 시드 이재성(#72), 청약 시드 서수영(#74). 한 파일이라 충돌이 나기 쉬우니 자기 구역만 고친다 | 컬럼이 맞는지는 해당 구역 DBA가 검토 |
| `fragments/layout/*`, `static/**` | 김예린 | 서수영·이재성은 자기 화면 영역만 |
| `member/mypage.html` | 이재성(틀) | 자기 탭 fragment만 수정 |
| `admin/dashboard.html` | 김예린(틀) | 자기 탭 fragment만 수정 |
| `static/js/mb-config.js` | 김예린 | 레벨·해금·출석 값. 청약 납입 값은 서수영이 요청 |
| `docs/*.md` | 기능 담당자가 수정, 서수영이 변경 이력 추적 | API·DB가 바뀌면 같은 PR에서 수정 |

---

## 9. 작업 순서 (단계)

| 단계 | 서수영 | 이재성 | 신아림 | 김예린 |
| --- | --- | --- | --- | --- |
| **1. 기반** | #2, #4, #67, #84 | #65 | #3(회원·강의 구역) | #3(성장·청약·모의투자 구역), #49, #10 |
| **(문서)** | #68, #83 | #76 | — | — |
| **2. 핵심 흐름** | #27, #28, #74 | #40, #41, #42, #23, #72 | #16, #17, #19, #20, #73 | #5, #6, #11, #12, #13, #50, #51, #75 |
| **3. 기능 확장** | #29, #58, #70 | #44, #43, #24, #25, #57, #54 | #18, #21, #22, #30, #31, #52, #53, #71 | #7, #8, #14, #36, #64, #66, #69 |
| **4. 관리자·마무리** | #47, #48, #63, #85 | #26, #45, #46, #62, #15, #56 | #32, #34, #35, #37, #38, #55, #59, #60 | #9, #33, #39, #61 |
| **5. 점검** | — | #86(통합 점검·시연) | #87(구역 ①) | #87(구역 ②) |

- 1단계가 `dev`에 다 들어가야 2단계를 시작합니다. 특히 서수영의 **#2**(서버 시작 클래스·설정)과 김예린의 **#10**(연동 메서드 공개)가 늦어지면 모두가 막힙니다. 샘플 데이터(#72~#75)는 해당 테이블이 확정된 뒤 시연 전까지 넣으면 됩니다.
- 서수영의 **#4**(Security)이 끝나야 김예린가 로그인(#6)을 마무리할 수 있습니다. 2단계 초반에 먼저 올려 주세요.
- 2단계 끝에서 확인할 흐름: ① 가입 → 로그인 → 홈 → 강의 → 퀴즈 통과 → 캐시·경험치 지급 ② 관리자 시즌 생성 → 가격 입력 → 모의투자 참가 ③ 가입 → 청약통장 자동 개설 → 납입

---

## 10. 현재 깃·이슈 상태 (2026-11-07)

| 항목 | 상태 |
| --- | --- |
| 이슈 | `bukkeu/my-home-finish`의 이슈 #2~#87·#95(81개). #3(DB 스키마 SQL)은 닫힘, 나머지 열림. **담당자(Assignee) 지정 완료**(이 문서 배정과 같음, #3·#87·#95는 DBA 두 명). #83~#87은 2026-11-07, #95는 11-08에 추가 |
| PR 번호 | 이슈와 PR이 번호를 같이 씀: PR은 #1, #77~#82 |
| 최근 확정 사항 | #9 본인인증·간편 로그인은 **가상**, #11 최대 Lv.20·칭호 구간별 필요 XP(첫 칭호 **고시원**), #20 통과 **70점**, #23 시즌 참가 시 **캐시 차감 없음**, #31 영상은 **파일 업로드**(1개 500MB 이하·mp4, DP-33), #48 청약 당첨 규칙은 **임시안**(추후 확정), #68 XP 값·#76 캐시·청약통장 기준 확정 필요 |
| 브랜치 | 원격에 `main`, `dev`, `sy`, `test`, `feat/be-3-schema-course`, `fix/issue-auto-close`. 작업은 `feat/…` → `dev` → `main` 흐름(Git 규칙) |
| 코드 | `moneybeetle/`는 골격(대부분 주석만). `./gradlew compileJava`는 성공하지만 `MoneyBeetleApplication`에 `@SpringBootApplication`·main이 없고 `application.yaml`이 비어 있어 `bootRun`은 실패(#2에서 해결) |
| 저장소 | `.idea/`는 `.gitignore`로 제외됨. 설계 문서는 `docs/`에 있음 |
| SQL | `sql/create_database.sql`(DB 생성, 계정은 각자 로컬에서 만듦), `sql/schema.sql`(테이블 47개), `sql/sample-data.sql`(시드) 세 파일 구성 |

---

## 11. 정리한 결정과 남은 확인 사항

### 이번에 정한 것

| 항목 | 결정 | 반영 |
| --- | --- | --- |
| 이슈 담당자 | GitHub Assignee를 이 문서 배정대로 지정함(서수영 `tjtndud2`, 이재성 `godgeul-glitch`, 신아림 `arim0317-dot`, 김예린 `paaaraaaeaaa`). #3·#87은 DBA 두 명 | GitHub 이슈 전체 |
| Main 소개 화면 | 별도 이슈 없이 #51에 포함(김예린) | #51 |
| `member`·`auth` 패키지 | **파일 단위로 소유**: `auth/config`(SecurityConfig·LevelCheckInterceptor)는 서수영, 로그인 핸들러·회원 API는 김예린. 서로 다른 쪽 파일은 이슈로 요청 | §8 |
| #30 강사 가입 신청 | 이슈를 쪼개지 않음. 신아림이 API를 만들고 신청·승인 처리는 김예린 `InstructorService`를 호출(#37 승인도 같음) | §7 |
| #32 내 강의 문의 | 문의(`inquiry`) 도메인이 신아림 담당이라 그대로 둠 | — |
| 코드 파일 머리말·README의 옛 담당 표기 | 새 이슈 #83에서 정리 | #83 |
| 브랜치 흐름 | `sy`·`test` 등 정리와 `main`·`dev` 보호는 새 이슈 #85에서 처리 | #85 |
| SQL 파일 구조 | 수업(14일차 JDBC 팀 프로젝트)과 같은 형식: 실행 순서 `create_database.sql` → `schema.sql` → `sample-data.sql`. 시드는 구역별로 담당이 작성하고 DBA가 검토 | §8 |
| 추가한 이슈 | #83 README·담당 표기, #84 Actuator·로그, #85 브랜치 정리·보호, #86 통합 점검·시연, #87 인덱스·쿼리 점검 | §2~§5 |

### 남은 확인 사항

| 항목 | 내용 | 정할 것 |
| --- | --- | --- |
| 청약 규칙 | 당첨 규칙은 임시안, 가점표 확정 필요 | 확정 후 서수영 업무량 재조정(점수 93, 가장 낮음) |
| 경험치·캐시 값 | #68, #76에서 확정 | 이재성(PM)이 결정 조율 |
| 점수 기준 | 점수는 상대 비교용 추정치 | 실제 진행하며 이슈를 옮기고, 옮길 때는 이 문서와 GitHub 담당자를 같이 수정 |

---

## 부록. 작업량 점수 기준

- **BE 이슈**: 담당 API 수 + 난이도 가산. 가산: 공통 기반 +8, 보안 +6, 보상 엔진 +5, 퀴즈 채점 +3, 영상 파일 업로드 +4, 주문 트랜잭션 +4, 턴 점검 +3, 정산·이월금 +3, 가점·신청 스냅샷 +5, 당첨 산정(임시 규칙) +8, CI·배포 +6 등
- **FE 이슈**: 프로토타입 화면 파일 크기(KB) ÷ 10 + 가산(모의투자·모의 청약 화면 +3, 관리자 운영 화면 +2)
- **고유 업무·DB 가산**: PM 일정 관리 +10, SCM 문서·Git·이슈 +10, DBA DB 관리 +3(#3 구역 몫). 새로 추가한 #83~#87은 작업 시간 기준으로 3·3·2·4·4점
- 점수는 **상대 비교용 추정치**입니다. 실제 작업을 해 보며 이슈를 옮겨도 됩니다.

| 이슈 | 점수 | 이슈 | 점수 | 이슈 | 점수 | 이슈 | 점수 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| #2 | 8 | #4 | 6 | #5 | 5 | #6 | 4 |
| #7 | 6 | #8 | 4 | #9 | 2 | #10 | 8 |
| #11 | 3 | #12 | 3 | #13 | 4 | #14 | 4 |
| #15 | 3 | #16 | 3 | #17 | 4 | #18 | 3 |
| #19 | 2 | #20 | 6 | #21 | 3 | #22 | 6 |
| #23 | 3 | #24 | 4 | #25 | 7 | #26 | 2 |
| #27 | 3 | #28 | 4 | #29 | 9 | #30 | 5 |
| #31 | 9 | #32 | 2 | #33 | 3 | #34 | 4 |
| #35 | 6 | #36 | 6 | #37 | 5 | #38 | 2 |
| #39 | 6 | #40 | 4 | #41 | 6 | #42 | 6 |
| #43 | 4 | #44 | 6 | #45 | 3 | #46 | 7 |
| #47 | 4 | #48 | 11 | #49 | 10 | #50 | 4 |
| #51 | 7 | #52 | 9 | #53 | 4 | #54 | 7 |
| #55 | 4 | #56 | 4 | #57 | 9 | #58 | 13 |
| #59 | 13 | #60 | 5 | #61 | 10 | #62 | 9 |
| #63 | 12 | #64 | 3 | #65 | 3 | #66 | 3 |
| #67 | 6 | #68 | 2 | #69 | 4 | #70 | 4 |
| #71 | 4 | #72 | 5 | #73 | 4 | #74 | 3 |
| #75 | 3 | #76 | 2 | #83 | 3 | #84 | 3 |
| #85 | 2 | #86 | 4 | #87 | 4 (각 2) | #95 | 4 (각 2) |
