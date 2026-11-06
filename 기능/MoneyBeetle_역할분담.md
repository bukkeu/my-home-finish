# MoneyBeetle 역할 분담 (세부)

> 기준일 2026-11-06. 저장소 `MY-HOME`(`origin/main` b63f108, 브랜치 `main`·`dev`·`sy`), GitHub 이슈 #1~#72, `기능/MoneyBeetle_API명세서.md`(API 148개), `moneybeetle-page/ROUTES.md`(화면 20개), `moneybeetle/src/main/resources/sql/create_table.sql`(테이블 47개)
> **담당 확정**: PM 이재성 = **모의투자**, SCM 서수영 = **모의 청약**. DBA ①·②는 이름 미정.
> 이슈 1개 = 작업 브랜치 1개 = PR 1개. 브랜치 이름은 Git 규칙(`feat/be-<이슈번호>-<설명>`)을 따릅니다.

---

## 0. 개정 요약

**이전 안의 문제**: 모의 청약이 서수영에게 몰려 있었습니다. 공통·회원·문의·관리자 운영까지 함께 맡아 이슈 27개(점수 157)였고, DBA ②는 10개(점수 48)였습니다.

**이번 안**: 모의 청약을 서수영의 중심 업무로 두고, 초반에 끝나는 공통 기반·보안만 더했습니다. 나머지(회원·인증·성장·홈·관리자 운영 등)는 DBA ①·②와 이재성이 나눴습니다.

| 담당 | 이전 안 | 이번 안 |
| --- | --- | --- |
| 서수영 (SCM) | 27개 · 점수 157 | **11개 · 점수 78** (+고유 업무 10) |
| 이재성 (PM) | 13개 · 점수 70 | **17개 · 점수 87** (+일정 관리 10) |
| DBA ① | 15개 · 점수 78 | **19개 · 점수 95** (+DB 3) |
| DBA ② | 10개 · 점수 48 | **19개 · 점수 95** (+DB 3) |

- **점수**는 작업량 추정치입니다(API 수 + 구현 난이도 가산, 화면은 프로토타입 크기 기준). 계산 기준은 맨 아래 부록에 있습니다.
- 서수영은 고유 업무(문서·Git·이슈, 릴리스 PR)가 있고 모의 청약에 **미확정 규칙**(당첨 규칙 임시안, 가점표)이 남아 있어서, 점수를 다른 사람보다 조금 낮게 두었습니다.
- 모의 청약(점수 56)과 CI·문서만 맡기면 서수영의 점수가 64로, 나머지 세 명이 각각 100 이상이 되어 불균형이 커집니다. 그래서 초반에 끝나는 공통 기반(#1, #3)을 더했습니다.

---

## 1. 한눈에 보기

| 담당 | 담당자 | 맡는 영역 | 이슈 | 점수 | API |
| --- | --- | --- | --- | --- | --- |
| **SCM** | **서수영** | **모의 청약** + 공통 기반·보안 + CI·배포 + 문서·Git·이슈 | 11 | 78 | 16 |
| **PM** | **이재성** | **모의투자** + 통합 검색 + 마이페이지 틀 + 중복 요청 방지 | 17 | 87 | 39 |
| **DBA ①** | (미정) | 강의·퀴즈·강사 + 문의 + 사용자·강사 관리 | 19 | 95 | 53 |
| **DBA ②** | (미정) | 회원·인증 + 성장·알림·홈·경제 용어 + 관리자 틀·운영 + 공통 레이아웃 | 19 | 95 | 40 |

- 이슈 #2(DB 스키마 SQL)는 DBA 두 명이 구역을 나눠 맡습니다(점수는 각각 3으로 반영).
- API 합계는 148개이고 모든 API가 한 번씩 배정되어 있습니다. API 수가 DBA ①에 많은 것은 강의·문의·강사 관리 API가 많기 때문이고, 화면과 난이도는 점수에 반영했습니다.

### 담당자별 고유 업무 (코드 외)

| 역할 | 고유 업무 |
| --- | --- |
| **PM 이재성** | 기획·실행·테스트·완료 전체 흐름과 일정 관리, 회의 주관, 의견 조율, 병목 파악·해결. 미확정 사항(#72 XP 값, 청약 당첨 규칙 등)의 결정 조율 |
| **SCM 서수영** | 프로젝트 문서 변경 추적·버전 관리, Git/GitHub 브랜치 전략·이슈 설계, 문서·개발 컨벤션 설계, `dev → main` 릴리스 PR |
| **DBA ① · ②** | DB 설계·관리, 스키마 버전 관리, SQL 최적화. 코드 담당자가 테이블을 바꾸려면 DBA에게 요청 |

**DB 관리 구분**: DBA ① = 회원·인증·문의·운영·강의·퀴즈 테이블 23개, DBA ② = 성장·알림·경제 용어 8개 + 청약 4개 + 모의투자 12개 = 24개. 시드는 구역을 나눠 함께 관리합니다.

---

## 2. 서수영 (SCM) — 모의 청약 · 공통 기반 · CI/배포

**패키지**: `domain/housing`, `global/**`, `domain/auth/config`(SecurityConfig)
**테이블(코드 담당 4)**: subscription_account, subscription_deposit, housing_complex, housing_application
**요구사항**: SUB-01~11, ADM-36~39, NFR-01~04, NFR-12~14
**11개 · 점수 78 (+10) · API 16개**

| 이슈 | 작업 | API | 점수 | 핵심 내용 · 확정 사항 |
| --- | --- | --- | --- | --- |
| #1 | 공통 응답·예외 처리·기본 설정 구성 | — | 8 | `ApiResponse`·`PageResponse`·`ErrorCode`·`GlobalExceptionHandler`·서버 시작 클래스(`@SpringBootApplication`)·`application.yaml`. BaseTimeEntity는 필요한 컬럼만 가진 공통 클래스로 분리. **가장 먼저**(없으면 서버가 뜨지 않음) |
| #3 | 인증·권한·레벨 해금 공통 처리 | — | 6 | `SecurityConfig`(세션·BCrypt), `/instructor/**`·`/admin/**` 접근 제한, `@RequireLevel`·`LevelCheckInterceptor`(API명세서에서 레벨이 표시된 API에만. 청약통장·당첨 결과는 제외) |
| #26 | 청약 캘린더·공고 상세 API | 63,64 | 3 | 청약 캘린더·공고 상세 (Lv.12). 신청 기간·일정은 서버 KST 시각으로 계산 |
| #27 | 청약통장 납입 API | 65~67 | 4 | 청약통장 조회·납입·내역. **레벨 조건 없음**. 하루 1회, 캐시 차감은 DBA ② `RewardService.spend`. `Idempotency-Key` 적용 |
| #28 | 가점 계산·청약 신청·당첨 결과 API | 68~71 | 9 | 가점 계산기·신청·내 신청 내역·당첨 결과. 신청 때 가점·납입 회차·인정금액을 스냅샷으로 저장 |
| #46 | 가상 단지 공고 관리 API | 142~145 | 4 | 공고 등록·수정·삭제·목록. 신청자가 있으면 삭제 대신 비공개, 유형·공급 세대 수 변경 제한 |
| #47 | 신청자 목록·당첨자 산정·결과 발표 API | 146~148 | 11 | 신청자 목록·당첨자 산정·결과 발표. **당첨 규칙은 프로토타입 기준 임시안**(커트라인 방식, 가점표 공공 85점·민영 68점)이며 추후 확정 |
| #69 | 환경변수·CI·배포 구성 | — | 6 | `.env`·`.gitignore`·`application-*.yml`, GitHub Actions 빌드·테스트, Docker 배포. 환경변수 문서 기준 |
| #57 | 모의 청약 화면 (화면) | — | 13 | 모의 청약 화면(캘린더·통장·가점 계산기·신청·결과). Lv.12 미만 블러, 신청 기간 밖 신청 버튼 비활성화 |
| #62 | 관리자 모의 청약 운영 화면 (화면) | — | 12 | 관리자 청약 운영(공고·신청자·산정·발표). 발표 뒤 사용자 화면에 결과 노출 |
| #72 | 경험치 활동 보상 기준 확정 (문서) | — | 2 | 활동별 XP 확정 → 정책 정의서 SP-22·`reward_rule` 시드·관련 이슈 본문 반영. 값 결정은 PM이 조율 |

**다른 사람에게 줘야 하는 것**: `HousingService.openAccount(memberId)`(DBA ② 가입), `HousingService.findMyApplications(memberId)`(DBA ② 마이페이지 성장 탭), `HousingService.getDashboardSummary()`(DBA ② 관리자 첫 화면), `FileStorageService.save(file, dir)`(DBA ①·②), `LevelCheckInterceptor`·`SecurityConfig`
**서수영이 쓰는 것**: DBA ② `GrowthService.getLevel`, `RewardService.spend`, `NotificationService.send`

---

## 3. 이재성 (PM) — 모의투자 · 통합 검색 · 마이페이지 틀

**패키지**: `domain/trading`, `global/security/IdempotencyFilter`(서수영 검토), `fragments/mypage` 틀
**테이블(코드 담당 12)**: trading_season, season_turn, sector, stock, stock_price, news, news_tag, trade_reason, season_participant, holding, trade_order, ranking_award
**요구사항**: INV-01~18, ADM-08~28, ADM-31~35, NFR-05, NFR-07
**참고 코드**: `moneybeetle-page/new1/App.jsx`(사용자), `new1/Admin.jsx`(관리자), `DemoTrading-code/`
**17개 · 점수 87 (+10) · API 39개**

| 이슈 | 작업 | API | 점수 | 핵심 내용 · 확정 사항 |
| --- | --- | --- | --- | --- |
| #14 | 통합 검색 API | 28 | 3 | 강의(DBA ①)·문의(DBA ①)·종목(본인) 검색 메서드를 모아 결과 제공, 자동완성 |
| #22 | 모의투자 홈·시즌 참가 API | 53,54 | 3 | 모의투자 홈·시즌 참가. **참가해도 캐시는 차감하지 않음**, Lv.8부터 참가 (팀 결정) |
| #23 | 종목·시세·뉴스 조회 API | 55~58 | 4 | 분야·종목·가상 시세·뉴스 조회. 같은 턴 안에서는 가격 고정 |
| #24 | 매수·매도 주문 API | 59~61 | 7 | 매수·매도. 한 트랜잭션(NFR-05), 이전 턴 가격·휴장 거절, 매매 이유 필수, 거래 내역, `Idempotency-Key` 적용 |
| #25 | 시즌 랭킹 조회 API | 62 | 2 | 시즌 랭킹 조회(공개된 뒤에만) |
| #39 | 시즌 생성·설정·상태 관리 API | 114~117 | 4 | 시즌 생성·설정·상태. 시작일(월) → 14일·12턴 자동 생성, 이월 비율·상한 |
| #40 | 분야·종목·기업정보 관리 API | 121~126 | 6 | 분야 15개 × 종목 30개, 기업정보 수정 |
| #41 | 턴별 가격 입력·CSV 업로드/다운로드 API | 127~130 | 6 | 턴별 가격표 입력, CSV 내려받기·올리기, ±30%·0원 경고, 지난 턴 잠금 |
| #42 | 가상 뉴스 등록·관리 API | 131~134 | 4 | 턴당 뉴스 2~3개, 관련 분야·종목, 지난 턴은 오타 수정만 |
| #43 | 턴 달력·다음 턴 진행 API | 118~120 | 6 | 턴 달력, 다음 턴 점검(30종목 가격·뉴스 빈칸), 진행 기록. 되돌릴 수 없음 |
| #44 | 참가자 관리·랭킹 미리보기 API | 135~137 | 3 | 참가자 목록·상세(순자산·수익률·보유·거래), 랭킹 미리보기 |
| #45 | 시즌 정산·랭킹 공개·보상·통계 API | 138~141 | 7 | 정산 실행(이월금·최종 자산), 랭킹 공개·보상, 시즌 통계. 관리자 첫 화면용 요약 메서드 제공 |
| #67 | 중복 요청 방지(Idempotency-Key) | — | 3 | `IdempotencyFilter`(`global/security`에 두되 서수영 검토). 키 24시간 보관, **같은 키에 다른 내용이면 409 거절**(`IDEMPOTENCY_KEY_CONFLICT`). 퀴즈 제출·매수·매도·청약통장 납입에 적용 |
| #53 | 마이페이지 화면 (화면) | — | 7 | 마이페이지 **틀**(`member/mypage.html`). 탭 내용(`settings`·`learning`·`growth`)은 각 담당이 fragment로 제공 |
| #55 | 통합 검색 화면 (화면) | — | 4 | 통합 검색 화면(`home/search.html`), 상단 검색창 자동완성(`mb-search.js`) |
| #56 | 모의투자 화면 (화면) | — | 9 | 모의투자 화면(참고: `new1/App.jsx`). Lv.8 미만 블러, 주문은 실제 데이터로 동작 |
| #61 | 관리자 모의투자 운영 화면 (화면) | — | 9 | 관리자 모의투자 운영(시즌·종목·가격·뉴스·턴·정산·통계). 누락 데이터가 있으면 다음 턴 버튼 비활성화 |

**다른 사람에게 줘야 하는 것**: `RankingService.findAwards(memberId)`(DBA ② Drawer·마이페이지), `TradingService.getDashboardSummary()`(DBA ② 관리자 첫 화면), `IdempotencyFilter`(전원)
**이재성이 쓰는 것**: DBA ② `RewardService.getBalance`·`spend`, `NotificationService.send`, DBA ① `CourseService.search`·`InquiryService.search`

---

## 4. DBA ① — 강의 · 퀴즈 · 강사 · 문의

**패키지**: `domain/course`, `domain/quiz`, `domain/inquiry`, `AdminMemberController`(#36)
**테이블(코드 담당 14)**: category, course, course_review, lesson, learning_progress, lesson_watch, scrap, quiz_question, quiz_choice, quiz_attempt, quiz_answer, wrong_answer_note, inquiry, inquiry_reply
**DB 관리**: 회원·인증·문의·운영·강의·퀴즈 테이블 23개, 시드의 카테고리·강의·퀴즈
**요구사항**: LRN-01~09, QST-01~06, TCH-01~09, ADM-02~04, ADM-06~07, NFR-15
**19개 · 점수 95 (+3) · API 53개**

| 이슈 | 작업 | API | 점수 | 핵심 내용 · 확정 사항 |
| --- | --- | --- | --- | --- |
| #15 | 카테고리·강의 목록·강의 상세 API | 29~31 | 3 | 카테고리·소주제·강의 목록·강의 상세 (허가된 강의만, 영상 최대 3개, 기준일/출처) |
| #16 | 영상 시청·이어서 보기·시청 기록 API | 32~35 | 4 | 영상 재생·시청 위치 저장·시청 기록·학습 완료 목록. 홈 "이어서 볼 강의" 메서드 제공 |
| #17 | 강의 스크랩 API | 36~38 | 3 | 스크랩·해제·목록 |
| #18 | 테스트 퀴즈(OX) API | 39,40 | 2 | 테스트 퀴즈(OX 5문제) 문제·채점. 불이익 없음 |
| #19 | 퀴즈 제출·채점·학습 완료 처리 | 41~43 | 6 | 4지선다 퀴즈 제출·채점·학습 완료. **통과 기준 70점 이상**(확정). 보상은 DBA ② `RewardService.grant`, `Idempotency-Key` 적용 |
| #20 | 오답노트 API | 44~46 | 3 | 오답노트 조회·다시 풀기·삭제 |
| #21 | 강의 문의 게시판 API | 47~52 | 6 | 강의 문의 게시판(작성·수정·삭제·답글). 금칙어 검사, 비공개글 권한, 하루 10개 제한 |
| #29 | 강사 가입 신청·강의 생성/수정/삭제 API | 12, 72~76 | 5 | 강사 가입 신청(API 12)·내 강의 목록·강의 생성·수정·삭제. 신청은 `member` 도메인이므로 DBA ② 서비스와 협의 |
| #30 | 영상·요약글·퀴즈 등록 API | 77~80 | 9 | 썸네일·영상·요약글·퀴즈 저장. **영상은 링크가 아니라 파일 업로드**(확정, `FileStorageService`) |
| #31 | 검수 요청·내 강의 문의 API | 81,82 | 2 | 검수 요청, 내 강의 문의(`GET /instructor/inquiries`) |
| #33 | 카테고리 관리 API | 85~88 | 4 | 카테고리·소주제 관리 |
| #34 | 강의 검수·공개 관리 API | 89~94 | 6 | 강의 허가·반려(사유)·공개·비공개·삭제. 학습 기록이 있으면 삭제 대신 비공개. 알림은 DBA ② `NotificationService` |
| #36 | 사용자·강사 관리 API | 101~105 | 5 | 사용자·강사 목록, 계정 정지·해제, 강사 권한, 강사 가입 신청·승인 (`AdminMemberController`) |
| #37 | 문의 게시판 관리 API | 106,107 | 2 | 문의 게시판 관리(조회·숨김·해제) |
| #51 | 강의 목록·강의 상세·영상 시청 화면 (화면) | — | 9 | 강의 목록·상세·영상 시청 화면, 이어서 보기, 스크랩 버튼 |
| #52 | 테스트 퀴즈·퀴즈·결과 화면 (화면) | — | 4 | 테스트 퀴즈·퀴즈·결과 화면 |
| #54 | 문의 게시판 화면 (화면) | — | 4 | 문의 게시판 화면 |
| #58 | 강사 로그인·가입·대시보드 화면 (화면) | — | 13 | 강사 로그인·가입·대시보드(내 강의, Q&A 탭) |
| #59 | 강사 강의 생성 화면 (화면) | — | 5 | 강의 생성 화면(분류·정보·영상·요약글·퀴즈·검수 요청) |

**다른 사람에게 줘야 하는 것**: `CourseService.hasLearningHistory(memberId)`(DBA ② 홈·로그인 분기), `CourseService.findContinueWatching / findLearningSummary(memberId)`(DBA ② 홈·Drawer), `CourseService.search(q)`·`InquiryService.search(q)`(이재성 검색)
**DBA ①이 쓰는 것**: DBA ② `RewardService.grant`·`NotificationService.send`·`ForbiddenWordService.check`·`AdminLogService.write`, 서수영 `FileStorageService.save`

---

## 5. DBA ② — 회원·인증 · 성장 · 홈 · 관리자 틀

**패키지**: `domain/member`, `domain/auth`(config 제외), `domain/growth`, `domain/notification`, `domain/home`, `domain/term`, `domain/operation`, `templates/fragments/layout`·`static/**`
**테이블(코드 담당 14)**: member, instructor_profile, social_account, terms, member_terms_agreement, password_reset_token, system_setting, admin_log, forbidden_word, attendance, cash_history, exp_history, level_policy, reward_rule, notification, economic_term, term_schedule
**DB 관리**: 성장·알림·경제 용어 8개 + 청약 4개 + 모의투자 12개 = 24개, 시드의 레벨 정책·보상 규칙·분야·종목·가격
**요구사항**: USR-01~14, HOM-01~06, GRW-01~08, ADM-01, ADM-05, ADM-29~30, NFR-06, NFR-08~10, NFR-13
**19개 · 점수 95 (+3) · API 40개**

| 이슈 | 작업 | API | 점수 | 핵심 내용 · 확정 사항 |
| --- | --- | --- | --- | --- |
| #4 | 회원가입 API | 1~5 | 5 | 약관·이메일·닉네임 중복 확인·가입·본인인증. 가입 직후 서수영 `HousingService.openAccount()` 호출 |
| #5 | 로그인·로그아웃 API | 6,7 | 4 | 로그인·로그아웃. Controller 없이 Security 폼 로그인 + 성공·실패 핸들러, 5회 실패 10분 잠금, 출석 호출 |
| #6 | 내 정보·설정 수정 API | 13~15, 18 | 6 | 내 정보·설정 수정·프로필 이미지·다른 회원 공개 프로필 (Drawer 프로필 응답 포함) |
| #7 | 아이디 찾기·비밀번호 재설정·변경·회원 탈퇴 API | 9~11, 16,17 | 4 | 아이디 찾기·비밀번호 재설정(메일)·비밀번호 변경·회원 탈퇴(비활성화) |
| #8 | 본인인증·간편 로그인 | 8 | 2 | 본인인증·간편 로그인(구글·카카오)은 실제 연동 없이 **가상 방식**으로 구현 (팀 결정, 우선순위 하) |
| #9 | 캐시·경험치 지급 공통(RewardService) | 22,23 | 8 | `RewardService` 캐시·경험치 지급/차감, 같은 활동 중복 지급 차단, 원장. **메서드 이름만 먼저 공개**. XP 값은 #72 확정 후 반영 |
| #10 | 레벨업·칭호·레벨업 캐시 | 24 | 3 | 레벨업·칭호. **최대 Lv.20, 칭호 구간별 필요 XP**(고시원 150·원룸 200·투룸 300·아파트 400·내 집 600). 누적 Lv.8=1,250, Lv.12=2,450 |
| #11 | 출석 체크·연속 출석 캐시 | 21 | 3 | 출석(하루 1회), 연속 5일부터 매일 캐시 |
| #12 | 홈 화면·오늘의 경제 용어 API | 19,20 | 4 | 홈 화면 API(오늘의 용어·이어서 볼 강의·학습 기록·트레이닝 바로가기) |
| #13 | 알림 API | 25~27 | 4 | 알림 목록·읽음·모두 읽음, Lv.8·Lv.12 해금 알림 |
| #32 | 관리자 로그인·대시보드 API | 83,84 | 3 | 관리자 로그인·대시보드 API. 첫 화면 요약은 각 담당 Service의 요약 메서드를 받아 표시 |
| #35 | 경제 용어·오늘의 용어 지정 API | 95~100 | 6 | 경제 용어 등록·수정·삭제, 날짜별 5개 지정 |
| #38 | 보상 규칙·캐시 수동 조정·서비스 설정·작업 기록 API | 108~113 | 6 | 보상 규칙 조회·수정, 캐시 수동 지급·회수(사유 필수), 서비스 설정, 관리자 작업 기록(`AdminLogService`) |
| #66 | 금칙어 검사 | — | 3 | `forbidden_word` 기반 금칙어 검사 서비스(`ForbiddenWordService`). 닉네임·문의·Q&A·강의 제목·요약글에 적용 |
| #68 | 예약 작업(탈퇴 정보 정리·만료 토큰 삭제) | — | 3 | 탈퇴 30일 뒤 개인정보 삭제, 만료 토큰 삭제 (`@EnableScheduling`) |
| #48 | Thymeleaf 공통 레이아웃·정적 파일 정리 (화면) | — | 10 | Thymeleaf 공통 레이아웃(`fragments/layout`), `static/` 정리, 역할별 메뉴·레벨 잠금 표시. 다른 화면의 기반 |
| #49 | 로그인·회원가입·계정 찾기 화면 (화면) | — | 4 | 로그인·회원가입·계정 찾기 화면 |
| #50 | 홈 화면·햄버거 메뉴·알림 (화면) | — | 7 | 홈 화면(vr1/vr2)·햄버거 메뉴·알림. 서비스 소개 메인(`index.html`)도 여기서 맡음(별도 이슈 없음) |
| #60 | 관리자 로그인·대시보드·운영 메뉴 화면 (화면) | — | 10 | 관리자 로그인·대시보드 **틀**·운영 메뉴. 탭(`?view=`) 내용은 각 담당이 fragment로 제공 |

**다른 사람에게 줘야 하는 것**: `RewardService.grant / spend / getBalance`, `GrowthService.getLevel(memberId)`, `NotificationService.send(memberId, type, title, link)`, `AttendanceService.checkToday(memberId)`, `ForbiddenWordService.check(text)`, `AdminLogService.write(...)`
**DBA ②가 쓰는 것**: DBA ① `CourseService.*`, 서수영 `HousingService.*`, 이재성 `RankingService.findAwards`·`TradingService.getDashboardSummary`

---

## 6. 화면 담당표 (moneybeetle-page → templates)

| 화면 (.dc.html) | 향후 URL | 템플릿 | 이슈 | 담당 |
| --- | --- | --- | --- | --- |
| Main | `/` | `index.html` | #50에 포함(별도 이슈 없음) | DBA ② |
| UserLogin / UserSignup | `/login`, `/signup` | `auth/login.html`, `member/signup.html` | #49 | DBA ② |
| UserHome-vr1 / vr2 | `/user/home` | `home/home.html` (학습 이력 유무로 분기) | #50 | DBA ② |
| UserLectureCategory | `/user/lectures?cat=` | `course/category.html` | #51 | DBA ① |
| UserLectureDetail | `/user/lecture-detail` | `course/detail.html` | #51, #52 | DBA ① |
| UserMyPage | `/user/my-page` | `member/mypage.html` (틀 + 탭 3개) | #53 | 이재성 틀 |
| UserSearch | `/user/search?q=` | `home/search.html` | #55 | 이재성 |
| UserInquiry | `/user/inquiry` | `inquiry/inquiry.html` | #54 | DBA ① |
| UserDemoTrading | `/user/demo-trading` | `trading/trading.html` | #56 | **이재성** |
| UserDemoHousingSubscription | `/user/demo-housing-subscription` | `housing/housing.html` | #57 | **서수영** |
| InstructorLogin / Signup / Dashboard | `/instructor/*` | `auth/instructor-login.html`, `member/instructor-signup.html`, `instructor/dashboard.html` | #58 | DBA ① |
| InstructorCourseCreate | `/instructor/course-create` | `instructor/course-create.html` | #59 | DBA ① |
| AdminLogin / AdminDashboard | `/admin/login`, `/admin/dashboard?view=` | `auth/admin-login.html`, `admin/dashboard.html` (틀 + 탭) | #60 | DBA ② 틀 |
| AdminTradingManagement | `/admin/trading-management` | `admin/trading.html` | #61 | **이재성** |
| AdminHousingManagement | `/admin/housing-management` | `admin/housing.html` | #62 | **서수영** |
| (공통) 헤더·Drawer·알림 | — | `fragments/layout/*`, `static/**` | #48 | DBA ② |

**틀 + 탭(fragment)으로 나눠 쓰는 화면**

| 화면 | 틀 | 탭 / 영역 | 탭 담당 |
| --- | --- | --- | --- |
| 마이페이지 | 이재성 | `mypage/settings` (설정·비밀번호·탈퇴·내 문의) | DBA ② |
| | | `mypage/learning` (시청 기록·학습 완료·스크랩·오답노트) | DBA ① |
| | | `mypage/growth` (캐시·경험치·레벨표·수상 내역·청약 신청 내역) | DBA ② (수상 내역은 이재성, 청약 신청 내역은 서수영이 데이터 제공) |
| 관리자 대시보드 | DBA ② | `?view=members` 회원·강사 / `?view=board` 문의 | DBA ① |
| | | `?view=courses` 카테고리·강의 검수 | DBA ① |
| | | `?view=terms` 경제 용어 / `?view=growth` 보상·캐시 / `?view=settings` 설정·작업 기록 | DBA ② |
| 헤더·Drawer | DBA ② | 프로필·연속 출석·경험치·캐시·수상 내역, 알림 종, 검색창 | DBA ② (수상 내역은 이재성 데이터) |

---

## 7. 연동 약속 (서로 부르는 메서드)

다른 도메인의 Repository는 직접 쓰지 않고 아래 Service 메서드만 호출합니다. **각 제공자는 1단계에서 메서드 이름·파라미터만 먼저 만들어 올립니다**(내용은 `TODO`).

| 제공자 | 메서드 | 호출하는 사람 | 언제 |
| --- | --- | --- | --- |
| DBA ② | `RewardService.grant(memberId, type, refId)` | DBA ① | 학습 완료·퀴즈 통과 |
| DBA ② | `RewardService.spend(...)` / `getBalance(memberId)` | 이재성, 서수영 | 시즌 참가 잔액 확인, 청약통장 납입 |
| DBA ② | `GrowthService.getLevel(memberId)` | 서수영 | 레벨 해금 인터셉터 |
| DBA ② | `NotificationService.send(memberId, type, title, link)` | DBA ①, 이재성, 서수영 | 검수 결과, 턴 진행·랭킹 공개, 청약 결과 |
| DBA ② | `AttendanceService.checkToday(memberId)` | DBA ② | 로그인 성공 |
| DBA ② | `ForbiddenWordService.check(text)` | DBA ①, DBA ② | 문의·닉네임·강의 글 |
| DBA ② | `AdminLogService.write(adminId, action, targetId, before, after, reason)` | 전원 | 관리자 중요 작업 |
| 서수영 | `HousingService.openAccount(memberId)` | DBA ② | 회원가입 완료 |
| 서수영 | `HousingService.findMyApplications(memberId)` | DBA ② | 마이페이지 성장 탭 |
| 서수영 | `HousingService.getDashboardSummary()` | DBA ② | 관리자 첫 화면 |
| 서수영 | `FileStorageService.save(file, dir)` | DBA ①, DBA ② | 썸네일·영상 파일, 프로필 이미지 |
| 이재성 | `RankingService.findAwards(memberId)` | DBA ② | Drawer 수상 내역, 마이페이지 |
| 이재성 | `TradingService.getDashboardSummary()` | DBA ② | 관리자 첫 화면 |
| 이재성 | `IdempotencyFilter` (`Idempotency-Key` 헤더) | 전원 | 퀴즈 제출·매수·매도·청약통장 납입 |
| DBA ① | `CourseService.hasLearningHistory(memberId)` | DBA ② | 로그인 후 홈 vr1/vr2 분기 |
| DBA ① | `CourseService.findContinueWatching / findLearningSummary(memberId)` | DBA ② | 홈, Drawer |
| DBA ① | `CourseService.search(q)` / `InquiryService.search(q)` | 이재성 | 통합 검색 |

---

## 8. 같이 쓰는 파일 — 수정 권한

| 파일 | 고치는 사람 | 다른 사람은 |
| --- | --- | --- |
| `global/**`, `SecurityConfig`, `CommonErrorCode` | 서수영 | 이슈로 요청 |
| `global/security/IdempotencyFilter` | 이재성 | 서수영이 검토 |
| `application.yaml`, `build.gradle`, `.github/**`, Docker | 서수영 | 이슈로 요청 (환경변수는 `MoneyBeetle_환경변수.md`) |
| `sql/create_table.sql` 회원·강의·문의·운영 구역 | DBA ① | 컬럼 변경은 DBA ①에게 요청 |
| `sql/create_table.sql` 성장·청약·모의투자 구역 | DBA ② | 컬럼 변경은 DBA ②에게 요청(청약은 서수영, 모의투자는 이재성이 요청) |
| 시드 데이터 | DBA ①(회원·강의 구역) / DBA ②(성장·모의투자 구역) | — |
| `fragments/layout/*`, `static/**` | DBA ② | 서수영·이재성은 자기 화면 영역만 |
| `member/mypage.html` | 이재성(틀) | 자기 탭 fragment만 수정 |
| `admin/dashboard.html` | DBA ②(틀) | 자기 탭 fragment만 수정 |
| `static/js/mb-config.js` | DBA ② | 레벨·해금·출석 값. 청약 납입 값은 서수영이 요청 |
| `기능/*.md` | 기능 담당자가 수정, 서수영이 변경 이력 추적 | API·DB가 바뀌면 같은 PR에서 수정 |

---

## 9. 작업 순서 (단계)

| 단계 | 서수영 | 이재성 | DBA ① | DBA ② |
| --- | --- | --- | --- | --- |
| **1. 기반** | #1, #3, #69 | #67 | #2(회원·강의 구역) | #2(성장·청약·모의투자 구역), #48, #9 |
| **2. 핵심 흐름** | #26, #27 | #39, #40, #41, #22 | #15, #16, #18, #19 | #4, #5, #10, #11, #12, #49, #50 |
| **3. 기능 확장** | #28, #57, #72 | #43, #42, #23, #24, #56, #53 | #17, #20, #21, #29, #30, #51, #52 | #6, #7, #13, #35, #66, #68 |
| **4. 관리자·마무리** | #46, #47, #62 | #25, #44, #45, #61, #14, #55 | #31, #33, #34, #36, #37, #54, #58, #59 | #8, #32, #38, #60 |

- 1단계가 `dev`에 다 들어가야 2단계를 시작합니다. 특히 서수영의 **#1**(서버 시작 클래스·설정)과 DBA ②의 **#9**(연동 메서드 공개)가 늦어지면 모두가 막힙니다.
- 서수영의 **#3**(Security)이 끝나야 DBA ②가 로그인(#5)을 마무리할 수 있습니다. 2단계 초반에 먼저 올려 주세요.
- 2단계 끝에서 확인할 흐름: ① 가입 → 로그인 → 홈 → 강의 → 퀴즈 통과 → 캐시·경험치 지급 ② 관리자 시즌 생성 → 가격 입력 → 모의투자 참가 ③ 가입 → 청약통장 자동 개설 → 납입

---

## 10. 현재 깃·이슈 상태 (2026-11-06)

| 항목 | 상태 |
| --- | --- |
| 이슈 | 열린 이슈 67개(#1~#62, #66~#69, #72), **담당자 지정 0개**. 작성자는 `tjtndud2` 62개, `paaaraaaeaaa` 4개(#66~#69), `godgeul-glitch` 1개(#72) |
| 최근 확정 사항 | #8 본인인증·간편 로그인은 **가상**, #10 최대 Lv.20·칭호 구간별 필요 XP(첫 칭호 **고시원**), #19 통과 **70점**, #22 시즌 참가 시 **캐시 차감 없음**, #30 영상은 **파일 업로드**, #47 청약 당첨 규칙은 **임시안**(추후 확정), #72 XP 값 확정 필요 |
| 브랜치 | `main`, `dev`, `sy`. `dev`·`sy`는 `main`과 갈라져 있음(각각 main에 없는 커밋 4개, main에 있는 커밋 5개) |
| 열린 PR | #71 `sy → main`, #73 `dev → main` (각각 파일 4개: `.coderabbit.yaml`, `.idea/misc.xml`, `moneybeetle/.gitignore`, `SecurityConfig.java`). Git 규칙은 `기능 브랜치 → dev → main`인데 #71은 `main`으로 직접 향함 |
| 코드 | `moneybeetle/`는 골격(대부분 주석만). `./gradlew compileJava`는 성공, `bootRun`은 **서버 시작 클래스 없음**으로 실패(#1에서 해결) |
| 저장소 | `.idea/` 파일이 `main`에 올라가 있음(IDE 설정, 제외 필요). 루트 `.gitignore`는 로컬에서 추가했으나 아직 원격에 없음 |
| SQL | 원격에는 `sql/01~04`·`99_seed.sql`과 `create_table.sql`이 함께 있고, 로컬 작업은 `sql/create_table.sql` 한 파일로 합치는 중 |

---

## 11. 팀 확인 필요

| 항목 | 내용 | 정할 것 |
| --- | --- | --- |
| DBA 이름 | DBA ①, ② 미정 | 이름 확정 후 "(미정)" 교체 |
| 서수영 업무량 | 점수 78로 가장 낮음. 모의 청약은 규칙이 미확정이라 범위가 늘 수 있음 | 청약 규칙 확정 후 필요하면 다른 담당에게 일부 이동 |
| Main 소개 화면 | 화면 20개 중 `Main`만 이슈가 없음 | #50에 포함할지, 새 이슈를 만들지 |
| 이슈 담당자 | 담당자 지정 0개 | 이 문서 §2~§5 기준으로 GitHub Assignee 지정 |
| `member`·`auth` 패키지 | `SecurityConfig`(서수영)와 로그인 핸들러·회원 API(DBA ②)가 같은 `auth`·`member`에 섞임 | 파일 단위 소유(§8)로 합의하거나, `auth/config`만 서수영으로 분리 |
| `#29`·`#31` | 강사 가입 신청(`member`)·내 강의 문의(`inquiry`)가 강의 이슈에 포함 | 이슈를 쪼갤지, 서비스 호출로 처리할지 |
| 코드 파일의 담당 주석 | `moneybeetle/` 파일 머리말·README의 담당 표기가 예전 기준 | 이번 담당으로 수정 |
| 브랜치 흐름 | `sy → main` 직접 PR, `dev`·`sy`와 `main`이 갈라짐 | `dev` 기준으로 정리, PR #71·#73 처리 |
| SQL 파일 구조 | 번호 파일 vs 단일 파일 | 하나로 확정 후 §8 표기 맞춤 |

---

## 부록. 작업량 점수 기준

- **BE 이슈**: 담당 API 수 + 난이도 가산. 가산: 공통 기반 +8, 보안 +6, 보상 엔진 +5, 퀴즈 채점 +3, 영상 파일 업로드 +4, 주문 트랜잭션 +4, 턴 점검 +3, 정산·이월금 +3, 가점·신청 스냅샷 +5, 당첨 산정(임시 규칙) +8, CI·배포 +6 등
- **FE 이슈**: 프로토타입 화면 파일 크기(KB) ÷ 10 + 가산(모의투자·모의 청약 화면 +3, 관리자 운영 화면 +2)
- **고유 업무·DB 가산**: PM 일정 관리 +10, SCM 문서·Git·이슈 +10, DBA DB 관리 +3(#2 구역 몫)
- 점수는 **상대 비교용 추정치**입니다. 실제 작업을 해 보며 이슈를 옮겨도 됩니다.

| 이슈 | 점수 | 이슈 | 점수 | 이슈 | 점수 | 이슈 | 점수 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| #1 | 8 | #3 | 6 | #4 | 5 | #5 | 4 |
| #6 | 6 | #7 | 4 | #8 | 2 | #9 | 8 |
| #10 | 3 | #11 | 3 | #12 | 4 | #13 | 4 |
| #14 | 3 | #15 | 3 | #16 | 4 | #17 | 3 |
| #18 | 2 | #19 | 6 | #20 | 3 | #21 | 6 |
| #22 | 3 | #23 | 4 | #24 | 7 | #25 | 2 |
| #26 | 3 | #27 | 4 | #28 | 9 | #29 | 5 |
| #30 | 9 | #31 | 2 | #32 | 3 | #33 | 4 |
| #34 | 6 | #35 | 6 | #36 | 5 | #37 | 2 |
| #38 | 6 | #39 | 4 | #40 | 6 | #41 | 6 |
| #42 | 4 | #43 | 6 | #44 | 3 | #45 | 7 |
| #46 | 4 | #47 | 11 | #48 | 10 | #49 | 4 |
| #50 | 7 | #51 | 9 | #52 | 4 | #53 | 7 |
| #54 | 4 | #55 | 4 | #56 | 9 | #57 | 13 |
| #58 | 13 | #59 | 5 | #60 | 10 | #61 | 9 |
| #62 | 12 | #66 | 3 | #67 | 3 | #68 | 3 |
| #69 | 6 | #72 | 2 |  |  |  |  |
