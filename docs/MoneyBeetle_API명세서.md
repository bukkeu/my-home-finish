# MoneyBeetle_API명세서

# API 명세서

> 💡 **프론트엔드와 백엔드와의 데이터 통신 규약을 정의하는 페이지입니다.**
본격적으로 개발에 앞서 기획 및 초기 설계된 내용 기반으로 초안을 먼저 작성한 뒤
실제 개발을 진행하면서 수정된 내용을 실시간으로 최신화하고 팀원들에게 알려주세요.
> 
> - 포함 내용
>     - **Endpoint:** /api/v1/posts 형태의 URI
>     - **Method:** GET, POST, PUT, DELETE
>     - **Request / Response 구조**

**읽는 법**

- 화면은 Thymeleaf 서버 렌더링이고, 이 문서의 API는 화면 안의 `fetch`가 쓰는 JSON입니다 (1-5 참고).
- 기준 문서: 요구사항 명세서, 정책 정의서, DB 설계. “관련” 칸의 ID는 요구사항·정책 번호입니다.
- 총 **148개 API** (사용자 71, 강사 11, 관리자 66)
- 초안입니다. 개발하면서 바뀐 내용은 이 문서에 바로 반영해 주세요.

---

## 1. 공통 규약

### 1-1 기본 정보

| 항목 | 내용 |
| --- | --- |
| Base URL | `/api/v1` (로컬 `http://localhost:8080/api/v1`) |
| 데이터 형식 | `Content-Type: application/json; charset=UTF-8` (파일 업로드만 `multipart/form-data`) |
| 인증 | 세션 쿠키(`JSESSIONID`). 30분 동안 요청이 없으면 만료 (DP-11) |
| 권한 | 비회원 / 회원 / 강사 / 관리자. 서버 필터에서 매번 확인 (DP-14). `/api/v1/instructor/**`는 강사, `/api/v1/admin/**`는 관리자 |
| 해금 | 모의투자 API는 Lv.8, 모의 청약 API는 Lv.12 미만이면 `403 LEVEL_LOCKED` (SP-24) |
| 시간 | 모든 날짜·시간은 KST, `yyyy-MM-dd` / `yyyy-MM-ddTHH:mm:ss` (DP-42) |
| 이름 규칙 | URI는 소문자 복수형 kebab-case(`/wrong-notes`), JSON 필드는 camelCase(`memberId`) |
| 금액 | 캐시는 정수. 비율(수익률·변동률)은 소수 둘째 자리까지의 숫자(%) |
| 중복 요청 | 퀴즈 제출, 주문, 청약 납입, 강의 영상·요약글 저장(API 78)은 `Idempotency-Key` 헤더로 한 번만 처리 (DP-37). 키는 로그인 회원별로 구분하고 24시간 보관한다. 같은 키·같은 내용이 다시 오면 처음 결과(상태 코드·본문)를 그대로 돌려주고 응답 헤더 `Idempotent-Replayed: true`를 붙인다. 같은 키·다른 내용이면 `IDEMPOTENCY_KEY_CONFLICT`, 처음 요청이 아직 처리 중이면 `DUPLICATE_REQUEST`. 거절된 4xx 응답은 보관하지 않아 같은 키로 다시 시도할 수 있다. 5xx 응답은 처리 여부를 알 수 없어 보관하고 같은 키로 다시 오면 그 응답을 돌려준다(이미 저장된 주문·납입이 다시 실행되지 않게). 5xx를 받은 화면은 자동으로 다시 보내지 않고, 내역(거래 내역·청약통장·퀴즈 결과)을 다시 조회해 처리됐는지 먼저 확인한 뒤 처리되지 않았을 때만 새 키로 시도한다(새 키는 기존 기록이 적용되지 않아 그대로 다시 실행된다). 응답을 만들지 못하고 필터 밖으로 예외·Error가 전파된 경우도 처리 여부를 알 수 없으므로 5xx와 같게 500(`INTERNAL_ERROR`) 결과로 보관한다(같은 키로 다시 와도 재실행되지 않고, "처리 중"으로 남아 `DUPLICATE_REQUEST`가 계속되지도 않는다). 헤더가 없으면 막지 않고 그대로 처리한다. 키는 100자까지이고(UUID 36자), 넘으면 처리하지 않고 400 `IDEMPOTENCY_KEY_TOO_LONG` (`IdempotencyFilter`, #65) |
| 요청 ID | 모든 응답에 `X-Request-Id` 헤더(8자리)를 붙인다. 화면에서 오류를 알릴 때 이 값을 함께 남기면 서버 로그에서 같은 요청을 찾을 수 있다. 요청에 영문·숫자·하이픈 36자 이내의 `X-Request-Id` 를 보내면 그 값을 그대로 쓴다 (`RequestLogFilter`, #84) |
| 서버 상태 확인 | `GET /actuator/health` → `{ "status": "UP" }`, `GET /actuator/info` → 앱 이름. 로그인 없이 호출하며 `/api/v1` 응답 형식(`success`·`data`)을 쓰지 않는다. 그 외 actuator 주소는 열지 않는다 (발표 전 로컬 점검용, #84) |

### 1-2 응답 형식

성공

```json
{
  "success": true,
  "data": { },
  "error": null
}
```

실패

```json
{
  "success": false,
  "data": null,
  "error": { "code": "NOT_ENOUGH_CASH", "message": "현금이 부족해요", "fields": null }
}
```

- 아래 상세 명세의 Response는 `data` 안의 내용만 적었습니다.
- 입력값 오류는 `error.fields`에 `[{ "field": "nickname", "message": "이미 사용 중인 닉네임이에요" }]` 형태로 입력칸별 안내를 담습니다 (EP-01).
  - 요청 본문 검증(`@Valid`) 실패는 400 `INVALID_INPUT` 이고 `fields`에 칸마다 한 줄씩 담습니다. 여러 칸을 비교하는 검사를 `isXxx()` 메서드에 붙이면 `field`는 그 이름(예: `isPasswordConfirmed()` → `passwordConfirmed`)이고, 클래스에 붙인 검사는 `field`가 `null` 입니다.
  - 본문 JSON 형식 오류, 필수 파라미터 누락, 파라미터 타입 오류(예: `?page=abc`)도 400 `INVALID_INPUT` 이지만 `fields`는 `null` 입니다.
  - 입력값 오류가 아닌 모든 실패 응답의 `fields`는 `null` 입니다 (#142).
- `message`는 화면에 그대로 보여 줄 수 있는 문구입니다. 기술 용어와 상세 원인은 넣지 않습니다 (EP-05).

### 1-3 페이지네이션

목록 API는 `page`(0부터), `size`(기본 20, 최대 100)를 받고 아래 형태로 돌려줍니다.

```json
{ "items": [ ], "page": 0, "size": 20, "totalElements": 42, "totalPages": 3 }
```

### 1-4 HTTP 상태 코드

| 코드 | 의미 | 쓰는 경우 |
| --- | --- | --- |
| 200 OK | 성공 | 조회·수정 |
| 201 Created | 생성됨 | 등록·제출·주문 |
| 204 No Content | 성공(본문 없음) | 삭제·상태 변경 |
| 400 Bad Request | 입력 오류 | 형식·규칙 위반, 잔액 부족 |
| 401 Unauthorized | 인증 필요 | 비로그인, 세션 만료 |
| 403 Forbidden | 권한 없음 | 역할·레벨 미달, 정지 계정 |
| 404 Not Found | 없음 | 없는 데이터·비공개 |
| 409 Conflict | 상태 충돌 | 중복, 기간 밖, 이미 처리됨, 지난 턴 |
| 423 Locked | 잠김 | 로그인 5회 실패 |
| 429 Too Many Requests | 한도 초과 | 문의 하루 10개 |
| 500 Internal Server Error | 서버 오류 | 모두 되돌리고 공통 오류 안내 |

### 1-5 화면과 API의 관계

화면은 **Thymeleaf가 서버에서 만들어 응답**합니다(서버 렌더링). 이 문서의 API(`/api/v1/**`)는 화면을 만드는 요청이 아니라, **화면 안의 JavaScript(`fetch`)가 부르는 JSON 요청**입니다(예: 이메일 중복 확인, 알림 읽음, 퀴즈 제출). 화면 주소와 화면 흐름은 화면 설계 문서를 따릅니다.

| 구분 | 요청 | 응답 | 처리 |
| --- | --- | --- | --- |
| 화면 요청 | `GET /user/home` 등 화면 주소 | HTML (`ModelAndView`) | 서버가 Model에 담은 값으로 템플릿을 그림 |
| 데이터 요청 | `/api/v1/**` | JSON (`ApiResponse`, `@ResponseBody`) | 화면 안 JS가 호출 |
| 쓰기 후 화면 이동 | `POST` 폼 제출 | 302 redirect (PRG) | 새로고침해도 요청이 반복되지 않게 함 |
| 로그인·로그아웃 | 아래 참고 | Security 필터가 처리 | Controller 메서드 없음 |

- **로그인·로그아웃**(No. 6, 7, 83)은 Spring Security가 처리합니다. 요청은 폼(`application/x-www-form-urlencoded`)으로 받고, 성공·실패 뒤의 이동과 메시지는 `AuthSuccessHandler`·`AuthFailHandler`가 정합니다. 아래 상세의 JSON 응답 예시는 **로그인 뒤 화면에 전달되는 값**(역할, 출석 결과, 이동 주소)을 뜻합니다.
- **CSRF**: 보호를 켜 둡니다. 폼은 토큰이 자동으로 들어가고, `fetch`로 `POST`·`PATCH`·`DELETE`를 보낼 때는 `X-CSRF-TOKEN` 헤더에 토큰을 담습니다.
- 화면 요청의 오류는 에러 화면(`403`·`404`·`500`)으로, `/api` 요청의 오류는 위 1-2의 JSON으로 응답합니다.

---

## 2. API 목록

### 회원·인증

| No | Method | Endpoint | 이름 | 권한 | 관련 |
| --- | --- | --- | --- | --- | --- |
| 1 | `GET` | `/api/v1/agreements` | 약관 목록 | 비회원 | USR-03 |
| 2 | `GET` | `/api/v1/auth/check-email` | 이메일 중복 확인 | 비회원 | USR-01, SP-01 |
| 3 | `GET` | `/api/v1/auth/check-nickname` | 닉네임 중복·규칙 확인 | 비회원 | USR-01, SP-02, DP-21 |
| 4 | `POST` | `/api/v1/auth/signup` | 회원가입 | 비회원 | USR-01~04, SP-01~05, SUB-04 |
| 5-1 | `POST` | `/api/v1/auth/identity-verification/request` | 본인인증 코드 요청 | 비회원 | USR-05, EP-02 |
| 5 | `POST` | `/api/v1/auth/identity-verification` | 본인인증 (코드 확인) | 비회원 | USR-05, EP-02 |
| 6 | `POST` | `/api/v1/auth/login` | 로그인 | 비회원 | USR-06, DP-10~11, GRW-01 |
| 7 | `POST` | `/api/v1/auth/logout` | 로그아웃 | 회원 | USR-06 |
| 8 | `GET` | `/api/v1/auth/oauth/{provider}` | 간편 로그인 시작 | 비회원 | USR-07, DP-13 |
| 8-1 | `GET` | `/api/v1/auth/oauth-signup` | 간편 로그인 가입 정보 확인 | 비회원 | USR-07, DP-13 |
| 8-2 | `POST` | `/api/v1/auth/oauth-signup` | 간편 로그인 가입 완료 | 비회원 | USR-07, DP-13 |
| 9 | `POST` | `/api/v1/auth/find-email` | 아이디(이메일) 찾기 | 비회원 | USR-08 |
| 10 | `POST` | `/api/v1/auth/password-reset/request` | 비밀번호 재설정 요청 | 비회원 | USR-09, DP-12 |
| 11 | `POST` | `/api/v1/auth/password-reset/confirm` | 비밀번호 재설정 | 비회원 | USR-09, DP-12 |
| 12 | `POST` | `/api/v1/auth/instructor-signup` | 강사 가입 신청 | 비회원 | USR-10, ADM-06 |
| 13 | `GET` | `/api/v1/members/me` | 내 정보 (메뉴 프로필) | 회원 | HOM-03~05, USR-12 |
| 14 | `PATCH` | `/api/v1/members/me` | 설정 수정 | 회원 | USR-12, SP-02 |
| 15 | `POST` | `/api/v1/members/me/profile-image` | 프로필 이미지 변경 | 회원 | USR-12, DP-32 |
| 16 | `PATCH` | `/api/v1/members/me/password` | 비밀번호 변경 | 회원 | USR-13, SP-03 |
| 17 | `DELETE` | `/api/v1/members/me` | 회원 탈퇴 | 회원 | USR-14, DP-06 |
| 18 | `GET` | `/api/v1/members/{memberId}/profile` | 다른 회원 공개 프로필 | 회원 | DP-03, HOM-06 |

### 홈·성장

| No | Method | Endpoint | 이름 | 권한 | 관련 |
| --- | --- | --- | --- | --- | --- |
| 19 | `GET` | `/api/v1/home` | 홈 화면 | 회원 | HOM-01~02, LRN-05~06 |
| 20 | `GET` | `/api/v1/economic-terms/today` | 오늘의 경제 용어 | 회원 | HOM-02, SP-45~46 |
| 21 | `GET` | `/api/v1/members/me/attendance` | 출석 현황 | 회원 | GRW-01~02, HOM-05 |
| 22 | `GET` | `/api/v1/members/me/cash-histories` | 캐시 내역 | 회원 | GRW-07, SP-15 |
| 23 | `GET` | `/api/v1/members/me/exp-histories` | 경험치 내역 | 회원 | GRW-03~04 |
| 24 | `GET` | `/api/v1/levels` | 레벨·칭호 표 | 회원 | GRW-04~05, GRW-08 |
| 25 | `GET` | `/api/v1/notifications` | 알림 목록 | 회원 | GRW-08 |
| 26 | `PATCH` | `/api/v1/notifications/{notificationId}/read` | 알림 읽음 | 회원 | - |
| 27 | `PATCH` | `/api/v1/notifications/read-all` | 알림 모두 읽음 | 회원 | - |
| 28 | `GET` | `/api/v1/search` | 통합 검색 | 회원 | LRN-01, INV-06 |

### 강의 학습

| No | Method | Endpoint | 이름 | 권한 | 관련 |
| --- | --- | --- | --- | --- | --- |
| 29 | `GET` | `/api/v1/categories` | 카테고리·소주제 | 회원 | LRN-01 |
| 30 | `GET` | `/api/v1/courses` | 강의 목록 | 회원 | LRN-01, SP-31 |
| 31 | `GET` | `/api/v1/courses/{courseId}` | 강의 상세 | 회원 | LRN-02, LRN-04, SP-41 |
| 32 | `GET` | `/api/v1/courses/{courseId}/lessons/{lessonId}` | 영상 재생 정보 | 회원 | LRN-03, LRN-05 |
| 33 | `PUT` | `/api/v1/courses/{courseId}/lessons/{lessonId}/progress` | 시청 위치 저장 | 회원 | LRN-05~06, SP-38 |
| 34 | `GET` | `/api/v1/members/me/watch-histories` | 시청 기록 | 회원 | LRN-06 |
| 35 | `GET` | `/api/v1/members/me/completed-courses` | 학습 완료 목록 | 회원 | LRN-06~07 |
| 36 | `POST` | `/api/v1/courses/{courseId}/scrap` | 스크랩 | 회원 | LRN-08, SP-39 |
| 37 | `DELETE` | `/api/v1/courses/{courseId}/scrap` | 스크랩 해제 | 회원 | LRN-08 |
| 38 | `GET` | `/api/v1/members/me/scraps` | 스크랩 목록 | 회원 | LRN-08, USR-11 |

### 퀴즈

| No | Method | Endpoint | 이름 | 권한 | 관련 |
| --- | --- | --- | --- | --- | --- |
| 39 | `GET` | `/api/v1/courses/{courseId}/test-quiz` | 테스트 퀴즈 문제 | 회원 | QST-01, SP-33 |
| 40 | `POST` | `/api/v1/courses/{courseId}/test-quiz/grade` | 테스트 퀴즈 채점 | 회원 | QST-01, SP-33 |
| 41 | `GET` | `/api/v1/courses/{courseId}/quiz` | 퀴즈 문제 | 회원 | QST-02, SP-35 |
| 42 | `POST` | `/api/v1/courses/{courseId}/quiz/attempts` | 퀴즈 제출·채점 | 회원 | QST-02~05, LRN-07, GRW-03~06, SP-34~36, NFR-06 |
| 43 | `GET` | `/api/v1/quiz-attempts/{attemptId}` | 퀴즈 결과 | 회원 (본인) | QST-03, DP-37 |
| 44 | `GET` | `/api/v1/members/me/wrong-notes` | 오답노트 | 회원 | QST-06, SP-36 |
| 45 | `POST` | `/api/v1/members/me/wrong-notes/retry` | 오답 다시 풀기 채점 | 회원 | QST-06 |
| 46 | `DELETE` | `/api/v1/members/me/wrong-notes/{wrongNoteId}` | 오답 삭제 | 회원 | SP-36 |

### 문의

| No | Method | Endpoint | 이름 | 권한 | 관련 |
| --- | --- | --- | --- | --- | --- |
| 47 | `GET` | `/api/v1/inquiries` | 문의 목록 | 회원 | LRN-09, SP-40 |
| 48 | `POST` | `/api/v1/inquiries` | 문의 작성 | 회원 | LRN-09, DP-30, DP-21 |
| 49 | `GET` | `/api/v1/inquiries/{inquiryId}` | 문의 상세 | 작성자·강사·관리자 | LRN-09, SP-40, DP-22 |
| 50 | `PATCH` | `/api/v1/inquiries/{inquiryId}` | 문의 수정 | 작성자 | LRN-09 |
| 51 | `DELETE` | `/api/v1/inquiries/{inquiryId}` | 문의 삭제 | 작성자·관리자 | ADM-07 |
| 52 | `POST` | `/api/v1/inquiries/{inquiryId}/replies` | 답글 작성 | 작성자·강사(내 강의)·관리자 | TCH-09, LRN-09 |

### 모의투자

| No | Method | Endpoint | 이름 | 권한 | 관련 |
| --- | --- | --- | --- | --- | --- |
| 53 | `GET` | `/api/v1/trading/home` | 모의투자 홈 | 회원 Lv.8 | INV-01~02, INV-05, INV-14, NFR-12 |
| 54 | `POST` | `/api/v1/trading/seasons/{seasonId}/participations` | 시즌 참가 | 회원 Lv.8 | INV-03~04, SP-51~52, DP-40 |
| 55 | `GET` | `/api/v1/trading/sectors` | 분야 목록 | 회원 Lv.8 | INV-06 |
| 56 | `GET` | `/api/v1/trading/stocks` | 오늘의 가상 시세 | 회원 Lv.8 | INV-06, INV-10 |
| 57 | `GET` | `/api/v1/trading/stocks/{stockId}` | 종목 상세 | 회원 Lv.8 | INV-07 |
| 58 | `GET` | `/api/v1/trading/news` | 가상 뉴스 | 회원 Lv.8 | INV-08 |
| 59 | `GET` | `/api/v1/trading/trade-reasons` | 매매 이유 선택지 | 회원 Lv.8 | INV-11 |
| 60 | `POST` | `/api/v1/trading/orders` | 매수·매도 | 회원 Lv.8 | INV-09~13, SP-53~58, DP-36, DP-40 |
| 61 | `GET` | `/api/v1/trading/orders` | 내 거래 내역 | 회원 Lv.8 | INV-15 |
| 62 | `GET` | `/api/v1/trading/seasons/{seasonId}/rankings` | 시즌 랭킹 | 회원 Lv.8 | INV-16~18, SP-62 |

### 모의 청약

| No | Method | Endpoint | 이름 | 권한 | 관련 |
| --- | --- | --- | --- | --- | --- |
| 63 | `GET` | `/api/v1/housing/complexes` | 청약 캘린더 | 회원 Lv.12 | SUB-01~02, NFR-12 |
| 64 | `GET` | `/api/v1/housing/complexes/{complexId}` | 공고 상세 | 회원 Lv.12 | SUB-03, SUB-08, SUB-10, SP-74 |
| 65 | `GET` | `/api/v1/housing/account` | 내 청약통장 | 회원 | SUB-04, SP-70 |
| 66 | `POST` | `/api/v1/housing/account/deposits` | 청약통장 납입 | 회원 | SUB-05, SP-70, DP-35, DP-40 |
| 67 | `GET` | `/api/v1/housing/account/deposits` | 납입 내역 | 회원 | SUB-05 |
| 68 | `POST` | `/api/v1/housing/score-preview` | 가점 계산기 | 회원 Lv.12 | SUB-06, SP-71 |
| 69 | `POST` | `/api/v1/housing/complexes/{complexId}/applications` | 청약 신청 | 회원 Lv.12 | SUB-07~08, SP-72~73 |
| 70 | `GET` | `/api/v1/housing/applications/me` | 내 신청 내역 | 회원 Lv.12 | SUB-09, SUB-11 |
| 71 | `GET` | `/api/v1/housing/applications/{applicationId}/result` | 당첨 결과 | 회원 (본인) | SUB-11 |

### 강사

| No | Method | Endpoint | 이름 | 권한 | 관련 |
| --- | --- | --- | --- | --- | --- |
| 72 | `GET` | `/api/v1/instructor/courses` | 내 강의 목록 | 강사 | TCH-06 |
| 73 | `POST` | `/api/v1/instructor/courses` | 강의 생성 | 강사 | TCH-01 |
| 74 | `GET` | `/api/v1/instructor/courses/{courseId}` | 강의 편집 정보 | 강사 (본인 강의) | TCH-01~05 |
| 75 | `PUT` | `/api/v1/instructor/courses/{courseId}` | 강의 수정 | 강사 (본인 강의) | TCH-06, SP-31 |
| 76 | `DELETE` | `/api/v1/instructor/courses/{courseId}` | 강의 삭제 | 강사 (본인 강의) | TCH-06, SP-32 |
| 77 | `POST` | `/api/v1/instructor/courses/{courseId}/thumbnail` | 썸네일 업로드 | 강사 | TCH-01 |
| 78 | `PUT` | `/api/v1/instructor/courses/{courseId}/lessons` | 영상·요약글 저장 | 강사 | TCH-02~03, SP-30, DP-31, DP-33 |
| 79 | `PUT` | `/api/v1/instructor/courses/{courseId}/test-quiz` | 테스트 퀴즈 저장 | 강사 | TCH-04 |
| 80 | `PUT` | `/api/v1/instructor/courses/{courseId}/quiz` | 퀴즈 저장 | 강사 | TCH-05 |
| 81 | `POST` | `/api/v1/instructor/courses/{courseId}/review-request` | 검수 요청 | 강사 | TCH-07, DP-34 |
| 82 | `GET` | `/api/v1/instructor/inquiries` | 내 강의 문의 | 강사 | TCH-08 |

### 관리자

| No | Method | Endpoint | 이름 | 권한 | 관련 |
| --- | --- | --- | --- | --- | --- |
| 83 | `POST` | `/api/v1/admin/auth/login` | 관리자 로그인 | 비회원 | ADM-01, DP-10 |
| 84 | `GET` | `/api/v1/admin/dashboard` | 대시보드 | 관리자 | ADM-08 |
| 85 | `GET` | `/api/v1/admin/categories` | 카테고리 목록 | 관리자 | ADM-02 |
| 86 | `POST` | `/api/v1/admin/categories` | 카테고리 등록 | 관리자 | ADM-02 |
| 87 | `PUT` | `/api/v1/admin/categories/{categoryId}` | 카테고리 수정 | 관리자 | ADM-02 |
| 88 | `DELETE` | `/api/v1/admin/categories/{categoryId}` | 카테고리 삭제 | 관리자 | ADM-02 |
| 89 | `GET` | `/api/v1/admin/courses` | 강의 목록(검수) | 관리자 | ADM-03~04 |
| 90 | `GET` | `/api/v1/admin/courses/{courseId}` | 검수 상세 | 관리자 | ADM-03 |
| 91 | `POST` | `/api/v1/admin/courses/{courseId}/approve` | 강의 허가 | 관리자 | ADM-03, DP-43 |
| 92 | `POST` | `/api/v1/admin/courses/{courseId}/reject` | 강의 반려 | 관리자 | ADM-03, SP-31 |
| 93 | `PATCH` | `/api/v1/admin/courses/{courseId}/visibility` | 공개·비공개 | 관리자 | ADM-04 |
| 94 | `DELETE` | `/api/v1/admin/courses/{courseId}` | 강의 삭제 | 관리자 | ADM-04, SP-32 |
| 95 | `GET` | `/api/v1/admin/economic-terms` | 경제 용어 목록 | 관리자 | ADM-05 |
| 96 | `POST` | `/api/v1/admin/economic-terms` | 경제 용어 등록 | 관리자 | ADM-05 |
| 97 | `PUT` | `/api/v1/admin/economic-terms/{termId}` | 경제 용어 수정 | 관리자 | ADM-05 |
| 98 | `DELETE` | `/api/v1/admin/economic-terms/{termId}` | 경제 용어 삭제 | 관리자 | ADM-05 |
| 99 | `GET` | `/api/v1/admin/term-schedules` | 날짜별 용어 일정 | 관리자 | ADM-05 |
| 100 | `PUT` | `/api/v1/admin/term-schedules/{date}` | 오늘의 용어 지정 | 관리자 | ADM-05, SP-46~47 |
| 101 | `GET` | `/api/v1/admin/members` | 사용자·강사 목록 | 관리자 | ADM-06 |
| 102 | `PATCH` | `/api/v1/admin/members/{memberId}/status` | 계정 정지·해제 | 관리자 | ADM-06, SP-06, DP-43 |
| 103 | `PATCH` | `/api/v1/admin/members/{memberId}/role` | 강사 권한 부여·회수 | 관리자 | ADM-06, SP-07 |
| 104 | `GET` | `/api/v1/admin/instructor-applications` | 강사 가입 신청 목록 | 관리자 | ADM-06 |
| 105 | `POST` | `/api/v1/admin/instructor-applications/{memberId}/approve` | 강사 승인 | 관리자 | ADM-06 |
| 106 | `GET` | `/api/v1/admin/inquiries` | 문의 게시판 관리 | 관리자 | ADM-07 |
| 107 | `PATCH` | `/api/v1/admin/inquiries/{inquiryId}/hidden` | 문의 숨김·해제 | 관리자 | ADM-07, DP-22 |
| 108 | `GET` | `/api/v1/admin/reward-rules` | 보상 규칙 조회 | 관리자 | ADM-29 |
| 109 | `PUT` | `/api/v1/admin/reward-rules/{ruleId}` | 보상 규칙 수정 | 관리자 | ADM-29, NFR-13 |
| 110 | `POST` | `/api/v1/admin/members/{memberId}/cash-adjustments` | 캐시 수동 지급·회수 | 관리자 | ADM-30, SP-17, DP-43 |
| 111 | `GET` | `/api/v1/admin/settings` | 서비스 설정 | 관리자 | NFR-13, ADM-11 |
| 112 | `PUT` | `/api/v1/admin/settings/{key}` | 서비스 설정 변경 | 관리자 | NFR-13 |
| 113 | `GET` | `/api/v1/admin/admin-logs` | 관리자 작업 기록 | 관리자 | NFR-14, DP-43 |

### 관리자 · 모의투자 운영

| No | Method | Endpoint | 이름 | 권한 | 관련 |
| --- | --- | --- | --- | --- | --- |
| 114 | `GET` | `/api/v1/admin/trading/seasons` | 시즌 목록 | 관리자 | ADM-09~10 |
| 115 | `POST` | `/api/v1/admin/trading/seasons` | 시즌 생성 | 관리자 | ADM-09, ADM-12, SP-50 |
| 116 | `PATCH` | `/api/v1/admin/trading/seasons/{seasonId}/settings` | 시즌 설정 | 관리자 | ADM-11 |
| 117 | `PATCH` | `/api/v1/admin/trading/seasons/{seasonId}/status` | 시즌 상태 변경 | 관리자 | ADM-10, SP-65 |
| 118 | `GET` | `/api/v1/admin/trading/seasons/{seasonId}/turns` | 턴 달력·진행 기록 | 관리자 | ADM-13, ADM-15 |
| 119 | `GET` | `/api/v1/admin/trading/seasons/{seasonId}/turns/next-check` | 다음 턴 점검 | 관리자 | ADM-14 |
| 120 | `POST` | `/api/v1/admin/trading/seasons/{seasonId}/turns/next` | 다음 턴 진행 | 관리자 | ADM-14~15, SP-59, DP-43 |
| 121 | `GET` | `/api/v1/admin/trading/sectors` | 분야 목록 | 관리자 | ADM-16 |
| 122 | `POST` | `/api/v1/admin/trading/sectors` | 분야 등록 | 관리자 | ADM-16 |
| 123 | `PUT` | `/api/v1/admin/trading/sectors/{sectorId}` | 분야 수정 | 관리자 | ADM-16 |
| 124 | `GET` | `/api/v1/admin/trading/stocks` | 종목 목록 | 관리자 | ADM-17 |
| 125 | `POST` | `/api/v1/admin/trading/stocks` | 종목 등록 | 관리자 | ADM-17~18 |
| 126 | `PUT` | `/api/v1/admin/trading/stocks/{stockId}` | 종목·기업정보 수정 | 관리자 | ADM-17~18 |
| 127 | `GET` | `/api/v1/admin/trading/seasons/{seasonId}/prices` | 턴별 가격표 | 관리자 | ADM-19, ADM-22 |
| 128 | `PUT` | `/api/v1/admin/trading/seasons/{seasonId}/prices` | 턴별 가격 저장 | 관리자 | ADM-19~20, ADM-23~24, SP-60 |
| 129 | `GET` | `/api/v1/admin/trading/seasons/{seasonId}/prices/export` | 가격표 CSV 내려받기 | 관리자 | ADM-21 |
| 130 | `POST` | `/api/v1/admin/trading/seasons/{seasonId}/prices/import` | 가격표 CSV 올리기 | 관리자 | ADM-21 |
| 131 | `GET` | `/api/v1/admin/trading/seasons/{seasonId}/news` | 뉴스 목록 | 관리자 | ADM-25~26 |
| 132 | `POST` | `/api/v1/admin/trading/seasons/{seasonId}/news` | 뉴스 등록 | 관리자 | ADM-25~26 |
| 133 | `PUT` | `/api/v1/admin/trading/news/{newsId}` | 뉴스 수정 | 관리자 | ADM-27, SP-60 |
| 134 | `DELETE` | `/api/v1/admin/trading/news/{newsId}` | 뉴스 삭제 | 관리자 | ADM-25 |
| 135 | `GET` | `/api/v1/admin/trading/seasons/{seasonId}/participants` | 참가자 목록 | 관리자 | ADM-28 |
| 136 | `GET` | `/api/v1/admin/trading/participants/{participantId}` | 참가자 상세 | 관리자 | ADM-28, DP-04 |
| 137 | `GET` | `/api/v1/admin/trading/seasons/{seasonId}/rankings/preview` | 랭킹 미리보기 | 관리자 | ADM-31 |
| 138 | `POST` | `/api/v1/admin/trading/seasons/{seasonId}/settle` | 정산 실행 | 관리자 | ADM-32, SP-61, SP-63, DP-40, DP-43 |
| 139 | `POST` | `/api/v1/admin/trading/seasons/{seasonId}/rankings/publish` | 랭킹 공개 | 관리자 | ADM-33, DP-43 |
| 140 | `POST` | `/api/v1/admin/trading/seasons/{seasonId}/rewards` | 랭킹 보상 지급 | 관리자 | ADM-34, SP-64 |
| 141 | `GET` | `/api/v1/admin/trading/seasons/{seasonId}/statistics` | 시즌 통계 | 관리자 | ADM-35 |

### 관리자 · 모의 청약 운영

| No | Method | Endpoint | 이름 | 권한 | 관련 |
| --- | --- | --- | --- | --- | --- |
| 142 | `GET` | `/api/v1/admin/housing/complexes` | 단지 목록 | 관리자 | ADM-36 |
| 143 | `POST` | `/api/v1/admin/housing/complexes` | 공고 등록 | 관리자 | ADM-36 |
| 144 | `PUT` | `/api/v1/admin/housing/complexes/{complexId}` | 공고 수정 | 관리자 | ADM-36, SP-76 |
| 145 | `DELETE` | `/api/v1/admin/housing/complexes/{complexId}` | 공고 삭제 | 관리자 | ADM-36 |
| 146 | `GET` | `/api/v1/admin/housing/complexes/{complexId}/applications` | 신청자 목록 | 관리자 | ADM-37 |
| 147 | `POST` | `/api/v1/admin/housing/complexes/{complexId}/compute` | 당첨자 산정 | 관리자 | ADM-38, SP-75 |
| 148 | `POST` | `/api/v1/admin/housing/complexes/{complexId}/announce` | 결과 발표 | 관리자 | ADM-39 |

---

## 3. 상세 명세

### 회원·인증

#### 1. 약관 목록

| 항목 | 내용 |
| --- | --- |
| Endpoint | `/api/v1/agreements` |
| Method | `GET` |
| 권한 | 비회원 |
| 관련 | USR-03 |
| 설명 | 가입 화면에 보여 줄 필수·선택 약관을 조회한다. |

**Request**

- 없음

**Response**

```json
{
  "agreements": [
    { "termsId": 1, "title": "서비스 이용약관", "isRequired": true, "version": "1.0", "content": "..." },
    { "termsId": 3, "title": "마케팅 정보 수신", "isRequired": false, "version": "1.0", "content": "..." }
  ]
}
```

#### 2. 이메일 중복 확인

| 항목 | 내용 |
| --- | --- |
| Endpoint | `/api/v1/auth/check-email` |
| Method | `GET` |
| 권한 | 비회원 |
| 관련 | USR-01, SP-01 |
| 설명 | 대소문자 구분 없이 중복을 확인한다. |

**Request**

- Query: [email=minji@gmail.com](mailto:email=minji@gmail.com)

**Response**

```json
{ "available": false }
```

#### 3. 닉네임 중복·규칙 확인

| 항목 | 내용 |
| --- | --- |
| Endpoint | `/api/v1/auth/check-nickname` |
| Method | `GET` |
| 권한 | 비회원 |
| 관련 | USR-01, SP-02, DP-21 |
| 설명 | 2~12자, 한글·영문·숫자, 금칙어 여부까지 확인한다. |

**Request**

- Query: nickname=민지

**Response**

```json
{ "available": true }
```

**Error**: `NICKNAME_INVALID`, `FORBIDDEN_WORD`

#### 4. 회원가입

| 항목 | 내용 |
| --- | --- |
| Endpoint | `/api/v1/auth/signup` |
| Method | `POST` |
| 권한 | 비회원 |
| 관련 | USR-01~04, SP-01~05, SUB-04 |
| 설명 | 가입과 동시에 청약통장을 만든다. 같은 브라우저 세션에서 그 이메일의 본인인증(API 5)을 마친 뒤에만 가입할 수 있고, 인증 결과는 회원에 저장된다(`is_identity_verified`). 인증하지 않았거나 인증한 이메일과 다르면 `IDENTITY_NOT_VERIFIED`. |

**Request**

```json
{
  "email": "minji@gmail.com",
  "password": "beetle1234",
  "passwordConfirm": "beetle1234",
  "nickname": "민지",
  "birthDate": "1999-04-12",
  "gender": "FEMALE",
  "agreedTermsIds": [1, 2, 3]
}
```

**Response**

```json
201 Created
{ "memberId": 1, "nickname": "민지" }
```

**Error**: `EMAIL_INVALID`, `EMAIL_DUPLICATED`, `NICKNAME_INVALID`, `NICKNAME_DUPLICATED`, `PASSWORD_MISMATCH`, `PASSWORD_INVALID`, `BIRTH_DATE_INVALID`, `AGE_UNDER_14`, `TERMS_NOT_FOUND`, `REQUIRED_TERMS_NOT_AGREED`, `FORBIDDEN_WORD`, `IDENTITY_NOT_VERIFIED`

- `EMAIL_DUPLICATED`·`NICKNAME_DUPLICATED`는 두 요청이 동시에 같은 값으로 가입해 중복 확인을 함께 통과해도 DB 고유 제약(`uk_member_email`, `uk_member_nickname`)에서 걸러져 같은 409로 응답한다.
- 청약통장 개설은 서수영의 `HousingService.openAccount` 연동 뒤에 붙는다(이슈 #5 남은 작업).

#### 5-1. 본인인증 코드 요청

| 항목 | 내용 |
| --- | --- |
| Endpoint | `/api/v1/auth/identity-verification/request` |
| Method | `POST` |
| 권한 | 비회원 |
| 관련 | USR-05, EP-02 |
| 설명 | 가입하려는 이메일로 6자리 인증번호를 보낸다(5분 안에 입력). 메일은 요청과 다른 스레드에서 보내 응답이 바로 돌아가고, 메일 서버 실패는 1회 자동 재시도한 뒤 서버 로그에만 남긴다(EP-02). 같은 이메일로는 60초에 한 번만 보낼 수 있다(브라우저·세션이 달라도 같다). 접속 주소(IP) 하나는 1분에 10번까지만 요청할 수 있다(이메일을 바꿔 가며 여러 주소로 메일을 보내게 하는 것을 막는다. 요청의 접속 주소만 쓰고 `X-Forwarded-For` 는 믿지 않으며, 한도는 서버 메모리에 있어 서버를 다시 켜면 지워진다). 이미 가입된 이메일이면 보내지 않는다. 새로 요청하면 이전 번호는 쓸 수 없다. 진행 상태는 HTTP 세션에 담아 두며 DB 테이블은 쓰지 않는다. |

**Request**

```json
{ "email": "minji@gmail.com" }
```

**Response**

- 202 Accepted (본문 없음)

**Error**: `EMAIL_INVALID`, `EMAIL_DUPLICATED`, `IDENTITY_CODE_TOO_SOON`, `IDENTITY_REQUEST_LIMITED`, `IDENTITY_MAIL_UNAVAILABLE`

#### 5. 본인인증 (코드 확인)

| 항목 | 내용 |
| --- | --- |
| Endpoint | `/api/v1/auth/identity-verification` |
| Method | `POST` |
| 권한 | 비회원 |
| 관련 | USR-05, EP-02 |
| 설명 | 이메일로 받은 6자리 번호가 맞으면 이 세션에서 그 이메일을 인증된 것으로 표시한다. 표시는 30분 동안 가입(API 4)에 쓸 수 있고 가입이 끝나면 지워진다. 번호를 5번 틀리면 그 번호는 버려지고 다시 받아야 한다(`IDENTITY_CODE_EXPIRED`). 5분이 지났거나 번호를 받은 적이 없거나 다른 이메일의 번호이면 `IDENTITY_CODE_EXPIRED`로 같게 답한다. |

**Request**

```json
{ "email": "minji@gmail.com", "code": "123456" }
```

**Response**

```json
{ "verified": true }
```

**Error**: `IDENTITY_CODE_INVALID`, `IDENTITY_CODE_EXPIRED`

#### 6. 로그인

| 항목 | 내용 |
| --- | --- |
| Endpoint | `/api/v1/auth/login` |
| Method | `POST` |
| 권한 | 비회원 |
| 관련 | USR-06, DP-10~11, GRW-01 |
| 설명 | 성공하면 세션을 만들고 오늘 첫 접속이면 출석을 기록한다. 5회 연속 실패 시 1분 잠금(이후 5회 실패마다 5분). 로그인은 Spring Security 가 처리하는 **폼 로그인**이라 요청이 JSON 이 아니라 **폼 형식**(`application/x-www-form-urlencoded`)이다. 데이터를 바꾸는 POST 라서 CSRF 토큰(`_csrf` 폼 값 또는 헤더)도 함께 보낸다. 응답은 JSON 이다. |

**Request**

```
POST /api/v1/auth/login
Content-Type: application/x-www-form-urlencoded

email=minji@gmail.com&password=beetle1234&next=/user/lectures/3
```

- `email`은 대소문자·앞뒤 공백을 구분하지 않는다. `next`는 선택이고, 로그인 뒤 이동할 사이트 안 경로(`/`로 시작)만 받는다.
- JSON 본문(`{"email": ...}`)은 읽지 않아 `LOGIN_FAILED` 로 응답한다.

**Response**

```json
{
  "memberId": 1, "nickname": "민지", "role": "MEMBER",
  "redirectUrl": "/user/home",
  "attendance": { "checkedToday": true, "newlyChecked": true, "streak": 5, "rewardCash": 10000 }
}
```

- `role`은 실제로 부여된 권한이다. 회원 정보상 강사여도 관리자가 승인하기 전에는 `MEMBER` 로 로그인된다(SP-07).
- `redirectUrl`: `next` → 로그인 전에 가려던 화면 → 역할별 첫 화면(`/user/home`, `/instructor/dashboard`, `/admin/dashboard`) 순이다.
- `attendance`는 일반 회원(`MEMBER`)이 로그인할 때 오늘 출석을 기록한 결과다. `newlyChecked`는 오늘 처음 기록했는지, `rewardCash`는 이번 출석으로 받은 캐시(연속 5일째부터, 이미 출석했거나 보상 규칙이 꺼져 있으면 0)다. 강사·관리자 로그인이거나 출석 기록이 실패하면 `null` 이고, 출석이 실패해도 로그인은 성공한다.

**Error**: `LOGIN_FAILED`(401), `LOGIN_LOCKED`(423), `ACCOUNT_SUSPENDED`(403), `ACCOUNT_WITHDRAWN`(403)

- 없는 이메일과 틀린 비밀번호는 같은 `LOGIN_FAILED` 로 응답한다. 계정이 있는지 알려 주지 않기 위해서다.
- 비밀번호가 틀린 경우만 실패 횟수를 올리고, 5번째 실패 응답부터 `LOGIN_LOCKED` 이다. 잠금·정지·탈퇴는 비밀번호보다 먼저 확인한다.

#### 7. 로그아웃

| 항목 | 내용 |
| --- | --- |
| Endpoint | `/api/v1/auth/logout` |
| Method | `POST` |
| 권한 | 회원 |
| 관련 | USR-06 |
| 설명 | 세션을 끝낸다. 로그인과 같이 Spring Security 가 처리하고 CSRF 토큰이 필요하다. |

**Request**

- 없음 (`Accept: application/json` 으로 보내는 `fetch` 요청)

**Response**

- 204 No Content
- 브라우저가 직접 보낸 폼 요청(사이드바 로그아웃 버튼)은 204 대신 첫 화면(`/`)으로 이동한다.

#### 8. 간편 로그인 시작

| 항목 | 내용 |
| --- | --- |
| Endpoint | `/api/v1/auth/oauth/{provider}` |
| Method | `GET` |
| 권한 | 비회원 |
| 관련 | USR-07, DP-13 |
| 설명 | 구글·카카오 인증 화면으로 보낸다(실제 OAuth, 2026-11-10 가상에서 변경). provider = `google` 또는 `kakao`. 환경변수에 그 제공자의 키가 없으면 `OAUTH_PROVIDER_UNAVAILABLE`(서버는 정상으로 뜨고 이 로그인만 꺼진다). 제공자 인증이 끝나면 제공자가 `/login/oauth2/code/{provider}`(Spring Security)로 돌려보내고, 서버가 이메일·이름만 받아 아래처럼 처리한 뒤 화면을 **이동(302)** 시킨다. 이 흐름은 브라우저가 주소를 따라가는 것이라 JSON 으로 답하지 않는다. ① 이미 연결된 회원 → 로그인(이메일 로그인과 같은 세션 로그인, 일반 회원이면 오늘 출석 기록)하고 역할별 첫 화면(`/user/home` 등)으로. ② 처음이고 이메일이 새것 → `/signup/social`(추가 정보 입력, API 8-1·8-2). ③ 막힌 경우 → `/login?oauthError={오류 코드}`. 오류 코드는 `EMAIL_DUPLICATED`(같은 이메일로 이미 가입. 이메일·비밀번호로 가입한 계정에 자동으로 연결하지 않으므로 이메일로 로그인하게 안내), `ACCOUNT_WITHDRAWN`, `ACCOUNT_SUSPENDED`, `OAUTH_FAILED`(동의 취소, 인증 오류, 제공자가 이메일을 주지 않았거나 확인되지 않은 이메일). 제공자가 **확인한 이메일만** 받는다(구글 `email_verified`, 카카오 `is_email_verified`). 인증 코드는 한 번만 쓸 수 있어 자동 재시도하지 않고 사용자가 처음부터 다시 시도한다. |

**Request**

- Path: provider=google | kakao

**Response**

- 302 Found → `/oauth2/authorization/{provider}` → 제공자 로그인 화면

**Error**: `OAUTH_PROVIDER_UNAVAILABLE`

#### 8-1. 간편 로그인 가입 정보 확인

| 항목 | 내용 |
| --- | --- |
| Endpoint | `/api/v1/auth/oauth-signup` |
| Method | `GET` |
| 권한 | 비회원 (간편 로그인으로 처음 온 세션) |
| 관련 | USR-07, DP-13 |
| 설명 | 간편 로그인으로 처음 온 사람의 제공자 정보를 돌려준다. 가입 화면(`/signup/social`)이 이메일·이름을 미리 채우는 용도이며, 제공자 쪽 사용자 ID 는 주지 않는다. 정보는 HTTP 세션에 담겨 있고(세션 만료 30분), 없으면 `OAUTH_SESSION_EXPIRED`. |

**Response**

```json
{ "provider": "GOOGLE", "email": "minji@gmail.com", "nickname": "민지" }
```

**Error**: `OAUTH_SESSION_EXPIRED`

#### 8-2. 간편 로그인 가입 완료

| 항목 | 내용 |
| --- | --- |
| Endpoint | `/api/v1/auth/oauth-signup` |
| Method | `POST` |
| 권한 | 비회원 (간편 로그인으로 처음 온 세션) |
| 관련 | USR-07, DP-13, SUB-04 |
| 설명 | 나머지 가입 정보를 받아 가입하고 **바로 로그인 상태**로 만든다. 이메일은 요청으로 받지 않고 세션에 담긴, 제공자가 확인한 값을 쓴다. 비밀번호는 없다(간편 로그인으로만 쓰는 계정, `password_hash` NULL). 본인인증(이메일 코드, API 5)은 거치지 않고 인증 완료(`is_identity_verified`)로 저장한다(DP-44). 닉네임·생년월일·약관 규칙은 API 4 와 같고, 회원·약관 동의·간편 로그인 연결(`social_account`)과 청약통장이 한 트랜잭션으로 저장된다. 가입이 거절되면(닉네임 중복 등) 세션 정보는 남아 고쳐서 다시 보낼 수 있다. |

**Request**

```json
{ "nickname": "민지", "birthDate": "1999-04-12", "gender": "FEMALE", "agreedTermsIds": [1, 2, 3] }
```

**Response**

```json
201 Created
{ "memberId": 1, "nickname": "민지" }
```

**Error**: `OAUTH_SESSION_EXPIRED`, `EMAIL_DUPLICATED`, `NICKNAME_INVALID`, `NICKNAME_DUPLICATED`, `BIRTH_DATE_INVALID`, `AGE_UNDER_14`, `TERMS_NOT_FOUND`, `REQUIRED_TERMS_NOT_AGREED`, `FORBIDDEN_WORD`, `OAUTH_FAILED`(같은 제공자 계정이 동시에 가입)

#### 9. 아이디(이메일) 찾기

| 항목 | 내용 |
| --- | --- |
| Endpoint | `/api/v1/auth/find-email` |
| Method | `POST` |
| 권한 | 비회원 |
| 관련 | USR-08 |
| 설명 | 닉네임과 생년월일로 이메일 일부를 가려서 알려 준다. 탈퇴·정지 회원은 알려 주지 않고 `MEMBER_NOT_FOUND`다. |

**Request**

```json
{ "nickname": "민지", "birthDate": "1999-04-12" }
```

**Response**

```json
{ "maskedEmail": "mi***@gmail.com" }
```

**Error**: `MEMBER_NOT_FOUND`

#### 10. 비밀번호 재설정 요청

| 항목 | 내용 |
| --- | --- |
| Endpoint | `/api/v1/auth/password-reset/request` |
| Method | `POST` |
| 권한 | 비회원 |
| 관련 | USR-09, DP-12 |
| 설명 | 본인 확인 메일을 보낸다. 인증은 10분 유효, 1회용이다. 가입 여부를 숨기려고 없는 이메일이어도 같은 202로 답한다. 새로 요청하면 이전 메일의 링크는 쓸 수 없다. 메일의 링크는 `{APP_BASE_URL}/password-reset?token=...`. |

**Request**

```json
{ "email": "minji@gmail.com" }
```

**Response**

- 202 Accepted

#### 11. 비밀번호 재설정

| 항목 | 내용 |
| --- | --- |
| Endpoint | `/api/v1/auth/password-reset/confirm` |
| Method | `POST` |
| 권한 | 비회원 |
| 관련 | USR-09, DP-12 |
| 설명 | 메일의 토큰으로 새 비밀번호를 설정한다. 없는 토큰은 만료와 같게 `TOKEN_EXPIRED`로 답한다. 사용한 토큰은 매일 새벽(03:00 KST) 정리 작업이 지우므로(DP-12), 정리되기 전에 같은 링크를 다시 쓰면 `TOKEN_USED`, 정리된 뒤에는 없는 토큰과 같게 `TOKEN_EXPIRED`로 답한다. |

**Request**

```json
{ "token": "f3a9...", "newPassword": "beetle5678", "newPasswordConfirm": "beetle5678" }
```

**Response**

- 204 No Content

**Error**: `TOKEN_EXPIRED`, `TOKEN_USED`, `PASSWORD_MISMATCH`, `PASSWORD_INVALID`

#### 12. 강사 가입 신청

| 항목 | 내용 |
| --- | --- |
| Endpoint | `/api/v1/auth/instructor-signup` |
| Method | `POST` |
| 권한 | 비회원 |
| 관련 | USR-10, ADM-06 |
| 설명 | 강사 회원가입 페이지. 승인 대기(PENDING)로 저장되고 관리자가 승인하면 강사 권한이 생긴다. 학습자 가입(API 4)과 같게, 같은 브라우저 세션에서 그 이메일의 본인인증(API 5-1·5)을 마친 뒤에만 신청할 수 있고 인증하지 않았거나 인증한 이메일과 다르면 `IDENTITY_NOT_VERIFIED`. 구현은 `IdentityVerificationService.requireVerified`(저장 전)·`clear`(성공 뒤)를 부른다(#30, DP-44). |

**Request**

```json
{
  "email": "teacher@moneybeetle.kr", "password": "teach1234", "passwordConfirm": "teach1234",
  "displayName": "박서연", "field": "청약, ETF", "agreedTermsIds": [1, 2]
}
```

**Response**

```json
201 Created
{ "memberId": 31, "approvalStatus": "PENDING" }
```

**Error**: `EMAIL_DUPLICATED`, `PASSWORD_MISMATCH`, `IDENTITY_NOT_VERIFIED`

#### 13. 내 정보 (메뉴 프로필)

| 항목 | 내용 |
| --- | --- |
| Endpoint | `/api/v1/members/me` |
| Method | `GET` |
| 권한 | 회원 |
| 관련 | HOM-03~05, USR-12 |
| 설명 | 햄버거 메뉴 맨 위 프로필과 설정 화면에 쓴다. |

**Request**

- 없음

**Response**

```json
{
  "memberId": 1, "email": "minji@gmail.com", "nickname": "민지", "profileImageUrl": null,
  "birthDate": "1999-04-12", "gender": "FEMALE", "role": "MEMBER",
  "level": 8, "title": "투룸", "exp": 1120, "expToNextLevel": 230,
  "cash": 12400,
  "attendance": { "streak": 3, "daysToReward": 2 },
  "learning": { "watchedLessons": 14, "completedCourses": 5 },
  "unlocks": { "trading": true, "housing": false, "housingLevel": 12 }
}
```

#### 14. 설정 수정

| 항목 | 내용 |
| --- | --- |
| Endpoint | `/api/v1/members/me` |
| Method | `PATCH` |
| 권한 | 회원 |
| 관련 | USR-12, SP-02 |
| 설명 | 닉네임, 생년월일, 성별을 바꾼다. 보내지 않은(null) 항목은 그대로 둔다. 닉네임이 바뀔 때만 형식·금칙어·중복을 확인하고, 생년월일은 과거 날짜·만 14세 이상이어야 한다. 이메일은 바꿀 수 없다. |

**Request**

```json
{ "nickname": "민지짱", "birthDate": "1999-04-12", "gender": "FEMALE" }
```

**Response**

```json
{ "memberId": 1, "nickname": "민지짱" }
```

**Error**: `NICKNAME_INVALID`, `NICKNAME_DUPLICATED`, `FORBIDDEN_WORD`, `BIRTH_DATE_INVALID`, `AGE_UNDER_14`

#### 15. 프로필 이미지 변경

| 항목 | 내용 |
| --- | --- |
| Endpoint | `/api/v1/members/me/profile-image` |
| Method | `POST` |
| 권한 | 회원 |
| 관련 | USR-12, DP-32 |
| 설명 | multipart/form-data. 2MB 이하 jpg·png. |

**Request**

- Form: file=(이미지 파일)

**Response**

```json
{ "profileImageUrl": "/uploads/3f2c9a1e-....png" }
```

**Error**: `FILE_TOO_LARGE`, `FILE_TYPE_NOT_ALLOWED`

#### 16. 비밀번호 변경

| 항목 | 내용 |
| --- | --- |
| Endpoint | `/api/v1/members/me/password` |
| Method | `PATCH` |
| 권한 | 회원 |
| 관련 | USR-13, SP-03 |
| 설명 | 현재 비밀번호 확인 후 변경. |

**Request**

```json
{ "currentPassword": "beetle1234", "newPassword": "beetle5678", "newPasswordConfirm": "beetle5678" }
```

**Response**

- 204 No Content

**Error**: `PASSWORD_WRONG`, `PASSWORD_MISMATCH`, `PASSWORD_INVALID`

#### 17. 회원 탈퇴

| 항목 | 내용 |
| --- | --- |
| Endpoint | `/api/v1/members/me` |
| Method | `DELETE` |
| 권한 | 회원 |
| 관련 | USR-14, DP-06 |
| 설명 | 계정을 비활성화(WITHDRAWN)하고 현재 로그인 세션을 끝낸다. 같은 계정으로 다른 기기에 열어 둔 세션도 로그인한 요청마다 계정 상태를 확인해서, 다음 요청에서 `UNAUTHORIZED`(401)로 끝낸다(정지된 계정도 같다). 개인정보는 30일 뒤 파기. 관리자 계정은 `FORBIDDEN`. |

**Request**

```json
{ "password": "beetle1234" }
```

**Response**

- 204 No Content

**Error**: `PASSWORD_WRONG`, `FORBIDDEN`

#### 18. 다른 회원 공개 프로필

| 항목 | 내용 |
| --- | --- |
| Endpoint | `/api/v1/members/{memberId}/profile` |
| Method | `GET` |
| 권한 | 회원 |
| 관련 | DP-03, HOM-06 |
| 설명 | 닉네임, 프로필 이미지, 칭호, 레벨, 랭킹 수상 내역만 보여 준다. 이메일·생년월일·성별은 내려주지 않는다. 탈퇴·정지 회원은 `MEMBER_NOT_FOUND`. (`awards`는 모의투자 도메인이 수상 내역 조회를 공개하기 전까지 빈 목록) |

**Request**

- Path: memberId=2

**Response**

```json
{
  "memberId": 2, "nickname": "복리왕", "profileImageUrl": null, "level": 15, "title": "아파트",
  "awards": [ { "seasonName": "시즌 5", "rankingType": "ASSET", "rank": 1 } ]
}
```

**Error**: `MEMBER_NOT_FOUND`

### 홈·성장

#### 19. 홈 화면

| 항목 | 내용 |
| --- | --- |
| Endpoint | `/api/v1/home` |
| Method | `GET` |
| 권한 | 회원 |
| 관련 | HOM-01~02, LRN-05~06 |
| 설명 | 오늘의 경제 용어, 이어서 볼 강의, 학습 기록, 트레이닝 바로가기를 한 번에 준다. |

**Request**

- 없음

**Response**

```json
{
  "todayTerms": [ { "economicTermId": 3, "term": "복리", "description": "이자에 이자가 붙는 방식" } ],
  "continueCourse": { "courseId": 7, "title": "청약통장 기초와 가점 계산", "lessonId": 21, "lessonTitle": "가점은 어떻게 계산할까", "positionSeconds": 312 },
  "learning": { "watchedLessons": 14, "completedCourses": 5 },
  "training": {
    "trading": { "unlocked": true, "requiredLevel": 8 },
    "housing": { "unlocked": false, "requiredLevel": 12 }
  }
}
```

- `todayTerms` 는 오늘(KST) 지정된 용어 5개로, 같은 날에는 모든 사용자에게 같은 값이다(API 20과 같은 값, 사용 중인 용어가 5개 미만이면 있는 만큼, 하나도 없으면 빈 배열).
- `continueCourse` 는 가장 최근에 본 진행 중 강의다. 이어서 볼 강의가 없으면 `null`이고, 강의는 있지만 그 강의에서 본 영상 기록이 없으면 `lessonId`·`lessonTitle`·`positionSeconds` 가 `null`이다. 마지막 영상은 최근 시청 기록 1,000건 안에서 찾는다.
- `learning` 은 본 영상 수와 학습 완료한 강의 수다.
- `training.*.unlocked` 는 내 레벨이 `requiredLevel` 이상일 때 `true`다.

#### 20. 오늘의 경제 용어

| 항목 | 내용 |
| --- | --- |
| Endpoint | `/api/v1/economic-terms/today` |
| Method | `GET` |
| 권한 | 회원 |
| 관련 | HOM-02, SP-45~46 |
| 설명 | 오늘(KST) 지정된 5개. 지정이 없으면 등록 순서대로 자동 선정. |

**Request**

- 없음

**Response**

```json
{ "date": "2026-11-05", "terms": [ { "economicTermId": 3, "term": "복리", "description": "..." } ] }
```

#### 21. 출석 현황

| 항목 | 내용 |
| --- | --- |
| Endpoint | `/api/v1/members/me/attendance` |
| Method | `GET` |
| 권한 | 회원 |
| 관련 | GRW-01~02, HOM-05 |
| 설명 | 연속 출석 일수와 캐시 지급까지 남은 일수. |

**Request**

- 없음

**Response**

```json
{ "streak": 3, "daysToReward": 2, "rewardCash": 10000, "checkedToday": true }
```

#### 22. 캐시 내역

| 항목 | 내용 |
| --- | --- |
| Endpoint | `/api/v1/members/me/cash-histories` |
| Method | `GET` |
| 권한 | 회원 |
| 관련 | GRW-07, SP-15 |
| 설명 | 적립·사용 내역 최신순. |

**Request**

- Query: page=0&size=20&type=EARN|USE (생략 시 전체)

**Response**

```json
{
  "cash": 12400,
  "items": [
    { "cashHistoryId": 88, "historyType": "EARN", "title": "강의 완료 · 금리와 채권 가격", "amount": 3000, "balanceAfter": 12400, "createdAt": "2026-11-02T14:10:00" },
    { "cashHistoryId": 87, "historyType": "USE", "title": "청약통장 납입", "amount": -1000, "balanceAfter": 9400, "createdAt": "2026-11-02T09:00:00" }
  ],
  "page": 0, "size": 20, "totalElements": 42
}
```

#### 23. 경험치 내역

| 항목 | 내용 |
| --- | --- |
| Endpoint | `/api/v1/members/me/exp-histories` |
| Method | `GET` |
| 권한 | 회원 |
| 관련 | GRW-03~04 |
| 설명 | 경험치 지급 내역. |

**Request**

- Query: page=0&size=20

**Response**

```json
{
  "level": 8, "exp": 1120,
  "items": [ { "expHistoryId": 12, "sourceType": "COURSE_COMPLETE", "title": "금리와 채권 가격", "amount": 100, "levelAfter": 8, "createdAt": "2026-11-02T14:10:00" } ],
  "page": 0, "size": 20, "totalElements": 12
}
```

#### 24. 레벨·칭호 표

| 항목 | 내용 |
| --- | --- |
| Endpoint | `/api/v1/levels` |
| Method | `GET` |
| 권한 | 회원 |
| 관련 | GRW-04~05, GRW-08 |
| 설명 | 레벨별 필요 경험치, 칭호, 해금 기능. |

**Request**

- 없음

**Response**

```json
{ "levels": [ { "level": 1, "requiredExp": 0, "title": "고시원", "levelupCash": 0 }, { "level": 8, "requiredExp": 1250, "title": "투룸", "levelupCash": 80000, "unlocks": ["TRADING"] } ] }
```

#### 25. 알림 목록

| 항목 | 내용 |
| --- | --- |
| Endpoint | `/api/v1/notifications` |
| Method | `GET` |
| 권한 | 회원 |
| 관련 | GRW-08 |
| 설명 | 안 읽은 알림 수와 목록. 최신순이다. 로그인한 회원 자신의 알림만 준다. |

**Request**

- Query: page=0&size=20
    - `page` 기본값은 0이고 0 미만이면 0으로 본다. `size` 기본값은 20이고 1 미만이면 1, 50을 넘으면 50으로 맞춘다(최대 50).

**Response**

```json
{
  "unreadCount": 2,
  "items": [ { "notificationId": 5, "type": "INQUIRY_REPLY", "title": "문의에 답변이 등록됐어요", "linkUrl": "/user/inquiry/3", "isRead": false, "createdAt": "2026-11-05T10:00:00" } ]
}
```

#### 26. 알림 읽음

| 항목 | 내용 |
| --- | --- |
| Endpoint | `/api/v1/notifications/{notificationId}/read` |
| Method | `PATCH` |
| 권한 | 회원 |
| 관련 | - |
| 설명 | 알림 하나를 읽음 처리. |

**Request**

- Path: notificationId=5

**Response**

- 204 No Content

#### 27. 알림 모두 읽음

| 항목 | 내용 |
| --- | --- |
| Endpoint | `/api/v1/notifications/read-all` |
| Method | `PATCH` |
| 권한 | 회원 |
| 관련 | - |
| 설명 | 내 알림을 모두 읽음 처리. |

**Request**

- 없음

**Response**

- 204 No Content

#### 28. 통합 검색

| 항목 | 내용 |
| --- | --- |
| Endpoint | `/api/v1/search` |
| Method | `GET` |
| 권한 | 회원 |
| 관련 | LRN-01, INV-06 |
| 설명 | 강의, 경제 용어, 종목(해금 시)을 키워드로 찾는다. DB 키워드 검색. |

**Request**

- Query: keyword=청약

**Response**

```json
{
  "courses": [ { "courseId": 7, "title": "청약통장 기초와 가점 계산", "categoryName": "부동산 › 청약" } ],
  "terms": [ { "economicTermId": 11, "term": "청약 가점", "description": "..." } ],
  "stocks": []
}
```

### 강의 학습

#### 29. 카테고리·소주제

| 항목 | 내용 |
| --- | --- |
| Endpoint | `/api/v1/categories` |
| Method | `GET` |
| 권한 | 회원 |
| 관련 | LRN-01 |
| 설명 | 카테고리(자본·부동산) → 소주제 트리. |

**Request**

- 없음

**Response**

```json
{ "categories": [ { "categoryId": 1, "name": "자본", "children": [ { "categoryId": 11, "name": "월급 관리" }, { "categoryId": 12, "name": "ETF·분산 투자" } ] } ] }
```

#### 30. 강의 목록

| 항목 | 내용 |
| --- | --- |
| Endpoint | `/api/v1/courses` |
| Method | `GET` |
| 권한 | 회원 |
| 관련 | LRN-01, SP-31 |
| 설명 | 허가 + 공개 강의만. 소주제별 조회. |

**Request**

- Query: categoryId=12&page=0&size=20

**Response**

```json
{
  "items": [
    { "courseId": 2, "title": "ETF로 시작하는 분산 투자", "instructorName": "박서연", "thumbnailUrl": null, "lessonCount": 3, "status": "IN_PROGRESS", "isScrapped": false }
  ],
  "page": 0, "size": 20, "totalElements": 4
}
```

#### 31. 강의 상세

| 항목 | 내용 |
| --- | --- |
| Endpoint | `/api/v1/courses/{courseId}` |
| Method | `GET` |
| 권한 | 회원 |
| 관련 | LRN-02, LRN-04, SP-41 |
| 설명 | 강의 소개, 영상 목록(최대 3개)과 요약글, 진행 상태, 스크랩 여부. |

**Request**

- Path: courseId=7

**Response**

```json
{
  "courseId": 7, "title": "청약통장 기초와 가점 계산", "description": "...",
  "category": { "categoryId": 21, "name": "청약", "parentName": "부동산" },
  "instructor": { "memberId": 31, "displayName": "박서연" },
  "infoBaseDate": "2026-10-01", "infoSource": "청약홈",
  "lessons": [
    { "lessonId": 20, "sortOrder": 1, "title": "청약통장이 뭐예요?", "durationSeconds": 480, "summary": "...", "isCompleted": true },
    { "lessonId": 21, "sortOrder": 2, "title": "가점은 어떻게 계산할까", "durationSeconds": 840, "summary": "...", "isCompleted": false }
  ],
  "progress": { "status": "IN_PROGRESS", "lastLessonId": 21, "lastPositionSeconds": 312, "completedAt": null },
  "quiz": { "testQuizCount": 5, "quizCount": 10, "passScore": 70, "isQuizOpen": false },
  "isScrapped": true
}
```

**Error**: `COURSE_NOT_PUBLIC`

#### 32. 영상 재생 정보

| 항목 | 내용 |
| --- | --- |
| Endpoint | `/api/v1/courses/{courseId}/lessons/{lessonId}` |
| Method | `GET` |
| 권한 | 회원 |
| 관련 | LRN-03, LRN-05 |
| 설명 | 재생 주소와 이어보기 위치. |

**Request**

- Path: courseId=7, lessonId=21

**Response**

```json
{ "lessonId": 21, "title": "가점은 어떻게 계산할까", "videoUrl": "https://...", "durationSeconds": 840, "positionSeconds": 312 }
```

**Error**: `COURSE_NOT_PUBLIC`, `LESSON_NOT_FOUND`

#### 33. 시청 위치 저장

| 항목 | 내용 |
| --- | --- |
| Endpoint | `/api/v1/courses/{courseId}/lessons/{lessonId}/progress` |
| Method | `PUT` |
| 권한 | 회원 |
| 관련 | LRN-05~06, SP-38 |
| 설명 | 재생 중 주기적으로(예: 15초) 저장. 끝까지 보면 completed=true. |

**Request**

```json
{ "positionSeconds": 600, "watchedSeconds": 590, "completed": false }
```

**Response**

- 204 No Content

#### 34. 시청 기록

| 항목 | 내용 |
| --- | --- |
| Endpoint | `/api/v1/members/me/watch-histories` |
| Method | `GET` |
| 권한 | 회원 |
| 관련 | LRN-06 |
| 설명 | 최근 본 영상 순. |

**Request**

- Query: page=0&size=20

**Response**

```json
{ "items": [ { "lessonId": 21, "lessonTitle": "가점은 어떻게 계산할까", "courseId": 7, "courseTitle": "청약통장 기초와 가점 계산", "isCompleted": false, "lastWatchedAt": "2026-11-05T09:12:00" } ] }
```

#### 35. 학습 완료 목록

| 항목 | 내용 |
| --- | --- |
| Endpoint | `/api/v1/members/me/completed-courses` |
| Method | `GET` |
| 권한 | 회원 |
| 관련 | LRN-06~07 |
| 설명 | 학습 완료한 강의. |

**Request**

- Query: page=0&size=20

**Response**

```json
{ "items": [ { "courseId": 3, "title": "월급 관리 첫걸음", "completedAt": "2026-11-01T20:30:00" } ] }
```

#### 36. 스크랩

| 항목 | 내용 |
| --- | --- |
| Endpoint | `/api/v1/courses/{courseId}/scrap` |
| Method | `POST` |
| 권한 | 회원 |
| 관련 | LRN-08, SP-39 |
| 설명 | 강의를 스크랩한다. |

**Request**

- Path: courseId=7

**Response**

- 201 Created

**Error**: `ALREADY_SCRAPPED`

#### 37. 스크랩 해제

| 항목 | 내용 |
| --- | --- |
| Endpoint | `/api/v1/courses/{courseId}/scrap` |
| Method | `DELETE` |
| 권한 | 회원 |
| 관련 | LRN-08 |
| 설명 | 스크랩을 해제(실제 삭제)한다. |

**Request**

- Path: courseId=7

**Response**

- 204 No Content

#### 38. 스크랩 목록

| 항목 | 내용 |
| --- | --- |
| Endpoint | `/api/v1/members/me/scraps` |
| Method | `GET` |
| 권한 | 회원 |
| 관련 | LRN-08, USR-11 |
| 설명 | 마이페이지 스크랩 목록. 최근 스크랩 순. 지금 사용자에게 보이는 강의(허가 + 공개 + 삭제 안 됨)만 보여 준다. 재검수 중·비공개·삭제된 강의는 숨기지만 스크랩 기록은 남아서, 다시 허가·공개되면 목록에 돌아온다(SP-31). |

**Request**

- Query: page=0&size=20

**Response**

```json
{ "items": [ { "courseId": 7, "title": "청약통장 기초와 가점 계산", "categoryName": "부동산 › 청약", "scrappedAt": "2026-11-03T12:00:00" } ] }
```

### 퀴즈

#### 39. 테스트 퀴즈 문제

| 항목 | 내용 |
| --- | --- |
| Endpoint | `/api/v1/courses/{courseId}/test-quiz` |
| Method | `GET` |
| 권한 | 회원 |
| 관련 | QST-01, SP-33 |
| 설명 | OX 5문제. 정답은 보내지 않는다. |

**Request**

- Path: courseId=7

**Response**

```json
{ "questions": [ { "questionId": 101, "content": "청약통장은 1인 1개만 만들 수 있다.", "choices": [ { "choiceId": 1, "content": "O" }, { "choiceId": 2, "content": "X" } ] } ] }
```

#### 40. 테스트 퀴즈 채점

| 항목 | 내용 |
| --- | --- |
| Endpoint | `/api/v1/courses/{courseId}/test-quiz/grade` |
| Method | `POST` |
| 권한 | 회원 |
| 관련 | QST-01, SP-33 |
| 설명 | 정답과 해설을 돌려준다. 점수·경험치·오답노트에 영향 없음, 기록하지 않음. 다 풀면 영상이 열린다. |

**Request**

```json
{ "answers": [ { "questionId": 101, "choiceId": 1 } ] }
```

**Response**

```json
{
  "correctCount": 4,
  "results": [ { "questionId": 101, "isCorrect": true, "correctChoiceId": 1, "explanation": "..." } ],
  "lessonsUnlocked": true
}
```

#### 41. 퀴즈 문제

| 항목 | 내용 |
| --- | --- |
| Endpoint | `/api/v1/courses/{courseId}/quiz` |
| Method | `GET` |
| 권한 | 회원 |
| 관련 | QST-02, SP-35 |
| 설명 | 4지선다 5~15문제. 영상을 모두 본 뒤 열린다. 정답은 보내지 않는다. |

**Request**

- Path: courseId=7

**Response**

```json
{ "passScore": 70, "questions": [ { "questionId": 201, "content": "청약 가점 항목이 아닌 것은?", "choices": [ { "choiceId": 11, "content": "무주택 기간" }, { "choiceId": 12, "content": "연 소득" } ] } ] }
```

**Error**: `QUIZ_LOCKED`

#### 42. 퀴즈 제출·채점

| 항목 | 내용 |
| --- | --- |
| Endpoint | `/api/v1/courses/{courseId}/quiz/attempts` |
| Method | `POST` |
| 권한 | 회원 |
| 관련 | QST-02~05, LRN-07, GRW-03~06, SP-34~36, NFR-06 |
| 설명 | 서버에서 채점하고 오답을 오답노트에 저장한다. 최초 통과 때만 학습 완료 + 경험치·캐시 지급(레벨업 포함, 한 트랜잭션). 같은 요청은 한 번만 처리. |

**Request**

```json
Header: Idempotency-Key: 7c1e...
{ "answers": [ { "questionId": 201, "choiceId": 12 }, { "questionId": 202, "choiceId": null } ] }
```

**Response**

```json
201 Created
{
  "attemptId": 55, "score": 80, "correctCount": 8, "questionCount": 10, "isPassed": true,
  "results": [ { "questionId": 201, "isCorrect": true, "correctChoiceId": 12, "explanation": "..." } ],
  "rewards": { "firstPass": true, "exp": 120, "cash": 3500, "levelUp": { "from": 7, "to": 8, "title": "투룸", "levelupCash": 80000, "unlocked": ["TRADING"] } }
}
```

**Error**: `QUIZ_LOCKED`, `DUPLICATE_REQUEST`

#### 43. 퀴즈 결과

| 항목 | 내용 |
| --- | --- |
| Endpoint | `/api/v1/quiz-attempts/{attemptId}` |
| Method | `GET` |
| 권한 | 회원 (본인) |
| 관련 | QST-03, DP-37 |
| 설명 | 새로고침·중복 제출 시 기존 결과를 다시 보여 준다. |

**Request**

- Path: attemptId=55

**Response**

- (퀴즈 제출 응답과 같은 구조)

**Error**: `FORBIDDEN`, `NOT_FOUND`

#### 44. 오답노트

| 항목 | 내용 |
| --- | --- |
| Endpoint | `/api/v1/members/me/wrong-notes` |
| Method | `GET` |
| 권한 | 회원 |
| 관련 | QST-06, SP-36 |
| 설명 | 카테고리·강의별로 모아 본다. categoryId(하위 소주제 포함)·courseId는 선택. 허가·공개·삭제 안 된 강의의 오답만 보여 준다(DH-01). 최근 순. |

**Request**

- Query: categoryId=21&courseId=7&page=0&size=20

**Response**

```json
{
  "items": [ { "wrongNoteId": 9, "courseId": 7, "courseTitle": "청약통장 기초와 가점 계산", "categoryName": "부동산 › 청약",
    "questionId": 205, "content": "...", "myChoice": "40,000원", "correctChoice": "33,840원", "explanation": "...", "createdAt": "2026-11-02T14:00:00" }   ],
  "page": 0, "size": 20, "totalElements": 1, "totalPages": 1
}
```

#### 45. 오답 다시 풀기 채점

| 항목 | 내용 |
| --- | --- |
| Endpoint | `/api/v1/members/me/wrong-notes/retry` |
| Method | `POST` |
| 권한 | 회원 |
| 관련 | QST-06 |
| 설명 | 오답노트 문제만 다시 풀고 채점 결과를 본다. 기록·보상 없음. 요청한 문항 중 내 오답노트에 있고 공개된 강의의 문제만 채점한다. |
**Request**

```json
{ "answers": [ { "questionId": 205, "choiceId": 31 } ] }
```

**Response**

```json
{ "results": [ { "questionId": 205, "isCorrect": true, "correctChoiceId": 31, "explanation": "..." } ] }
```

#### 46. 오답 삭제

| 항목 | 내용 |
| --- | --- |
| Endpoint | `/api/v1/members/me/wrong-notes/{wrongNoteId}` |
| Method | `DELETE` |
| 권한 | 회원 |
| 관련 | SP-36 |
| 설명 | 오답노트에서 지운다(실제 삭제). 본인 오답만 지워지며, 없거나 남의 ID여도 204(존재 여부를 알리지 않음). |
**Request**

- Path: wrongNoteId=9

**Response**

- 204 No Content

### 문의

#### 47. 문의 목록

| 항목 | 내용 |
| --- | --- |
| Endpoint | `/api/v1/inquiries` |
| Method | `GET` |
| 권한 | 회원 |
| 관련 | LRN-09, SP-40 |
| 설명 | courseId가 있으면 그 강의 문의, 없으면 사이트 문의. 회원은 내 글만, 강사는 내 강의 글, 관리자는 전체가 보인다. 관리자가 아니면 숨긴 글은 목록에서 빠진다(상세는 안내 문구로 표시). |


- Query: courseId=7&status=WAITING|ANSWERED&page=0&size=20

**Response**

```json
{
  "items": [ { "inquiryId": 3, "courseId": 7, "category": null, "title": "인정금액이 잔액보다 적게 나와요", "status": "ANSWERED", "writerNickname": "민지", "createdAt": "2026-11-03T10:00:00" } ],
  "page": 0, "size": 20, "totalElements": 1
}
```

#### 48. 문의 작성

| 항목 | 내용 |
| --- | --- |
| Endpoint | `/api/v1/inquiries` |
| Method | `POST` |
| 권한 | 회원 |
| 관련 | LRN-09, DP-30, DP-21 |
| 설명 | 강의 문의(courseId) 또는 사이트 문의(category) 중 **하나만** 지정(둘 다 있거나 둘 다 없으면 400). 강의 문의는 공개 강의만. 제목 50자, 내용 1,000자, 하루 10개(Asia/Seoul 자정 기준, 삭제한 글도 셈). |

```json
{ "courseId": 7, "category": null, "title": "부양가족에 부모님도 포함되나요?", "content": "..." }
```

**Response**

```json
201 Created
{ "inquiryId": 12 }
```

**Error**: `INQUIRY_EMPTY`, `INQUIRY_TOO_LONG`, `INQUIRY_DAILY_LIMIT`, `FORBIDDEN_WORD`

#### 49. 문의 상세

| 항목 | 내용 |
| --- | --- |
| Endpoint | `/api/v1/inquiries/{inquiryId}` |
| Method | `GET` |
| 권한 | 작성자·강사·관리자 |
| 관련 | LRN-09, SP-40, DP-22 |
| 설명 | 답글 포함(오래된 순). 숨김 글·답글은 관리자 외에는 “관리자에 의해 숨김 처리된 글이에요”로 표시. 강사 권한은 삭제되지 않은 내 강의의 문의만. 답글 writer.role: 강의 강사 INSTRUCTOR, 문의 작성자 MEMBER, 그 외 ADMIN. |

- Path: inquiryId=3

**Response**

```json
{
  "inquiryId": 3, "courseId": 7, "title": "...", "content": "...", "status": "ANSWERED", "isHidden": false,
  "writer": { "memberId": 1, "nickname": "민지" }, "createdAt": "2026-11-03T10:00:00",
  "replies": [ { "replyId": 4, "writer": { "memberId": 31, "nickname": "박서연", "role": "INSTRUCTOR" }, "content": "...", "createdAt": "2026-11-03T11:00:00" } ]
}
```

**Error**: `FORBIDDEN`, `NOT_FOUND`

#### 50. 문의 수정

| 항목 | 내용 |
| --- | --- |
| Endpoint | `/api/v1/inquiries/{inquiryId}` |
| Method | `PATCH` |
| 권한 | 작성자 |
| 관련 | LRN-09 |
| 설명 | 답글이 달리기 전에만 수정 가능. |

**Request**

```json
{ "title": "...", "content": "..." }
```

**Response**

- 204 No Content

**Error**: `INQUIRY_ALREADY_ANSWERED`, `FORBIDDEN_WORD`

#### 51. 문의 삭제

| 항목 | 내용 |
| --- | --- |
| Endpoint | `/api/v1/inquiries/{inquiryId}` |
| Method | `DELETE` |
| 권한 | 작성자·관리자 |
| 관련 | ADM-07 |
| 설명 | Soft Delete(deleted_at). |

**Request**

- Path: inquiryId=3

**Response**

- 204 No Content

#### 52. 답글 작성

| 항목 | 내용 |
| --- | --- |
| Endpoint | `/api/v1/inquiries/{inquiryId}/replies` |
| Method | `POST` |
| 권한 | 작성자·강사(내 강의)·관리자 |
| 관련 | TCH-09, LRN-09 |
| 설명 | 강사·관리자 답글이면 문의 상태를 ANSWERED로 바꾸고 작성자에게 알림(type INQUIRY_REPLY, linkUrl /user/inquiry/{inquiryId}). 작성자 본인의 추가 댓글은 상태를 바꾸지 않음. |

```json
{ "content": "부모님도 함께 살면 포함돼요." }
```

**Response**

```json
201 Created
{ "replyId": 5 }
```

**Error**: `FORBIDDEN`, `FORBIDDEN_WORD`

### 모의투자

#### 53. 모의투자 홈

| 항목 | 내용 |
| --- | --- |
| Endpoint | `/api/v1/trading/home` |
| Method | `GET` |
| 권한 | 회원 Lv.8 |
| 관련 | INV-01~02, INV-05, INV-14, NFR-12 |
| 설명 | 현재 시즌·턴, 남은 현금, 순자산, 보유 종목. 해금 레벨 미달이면 403. 시즌은 진행 중·휴장 중·정산 중·종료 중 시작일이 가장 최근인 것(정산 중·종료에도 열리고 `season.status`로 "정산 중"·"종료"를 안내한다. 준비 중 시즌뿐이면 `NO_RUNNING_SEASON`). `calendar`는 시작일부터 14일이고 `state`는 `DONE`(지난 턴)·`CURRENT`(현재 턴)·`UPCOMING`(남은 턴)·`HOLIDAY`(일요일, `turnNo` null). `nextTurnAt`은 다음 턴 거래일 0시(마지막 턴이면 null). 순자산 = 현금 + 보유 종목 평가금액(현재 턴 가격, 가격이 없으면 평균 단가), 수익률은 소수 둘째 자리 반올림(SP-61). 이 시즌에 참가하지 않았으면 `participation`은 null, `holdings`는 빈 목록 (#23) |

**Request**

- 없음

**Response**

```json
{
  "season": { "seasonId": 7, "name": "시즌 7", "status": "RUNNING", "currentTurn": 5, "totalTurns": 12, "endDate": "2026-11-14", "nextTurnAt": "2026-11-06T00:00:00",
    "calendar": [ { "date": "2026-11-02", "turnNo": 1, "state": "DONE" }, { "date": "2026-11-08", "turnNo": null, "state": "HOLIDAY" } ] },
  "participation": { "participantId": 120, "investAmount": 4820000, "cash": 2939000, "netAsset": 5134000, "returnRate": 6.5 },
  "holdings": [ { "stockId": 1, "stockName": "한빛반도체", "quantity": 12, "avgPrice": 79000, "evaluation": 1014000, "returnRate": 7.0 } ],
  "notice": "모든 투자·청약 화면은 가상 실습이며 실제 금융 거래가 아니에요"
}
```

**Error**: `LEVEL_LOCKED`, `NO_RUNNING_SEASON`

#### 54. 시즌 참가

| 항목 | 내용 |
| --- | --- |
| Endpoint | `/api/v1/trading/seasons/{seasonId}/participations` |
| Method | `POST` |
| 권한 | 회원 Lv.8 |
| 관련 | INV-03~04, SP-51~52, DP-40 |
| 설명 | 시즌당 1회, 첫 턴 종료 전까지(진행 중이고 현재 턴이 1일 때, 시작 전 시즌도 마감으로 본다). 투자 가능 금액 = 모은 캐시(참가할 때의 캐시 잔액) + 이월금(이 회원의 가장 최근 다른 시즌 `carryover_out`)으로 확정(이후 불변). **참가해도 회원 캐시는 빼지 않는다** (PM 결정, #23). 로그인 정보가 없으면 401 `UNAUTHORIZED` |

**Request**

- Path: seasonId=7

**Response**

```json
201 Created
{ "participantId": 120, "savedCash": 3200000, "carryoverIn": 1620000, "investAmount": 4820000 }
```

**Error**: `LEVEL_LOCKED`, `ALREADY_JOINED`, `JOIN_CLOSED`, `NOT_FOUND`(없는 시즌), `UNAUTHORIZED`

#### 55. 분야 목록

| 항목 | 내용 |
| --- | --- |
| Endpoint | `/api/v1/trading/sectors` |
| Method | `GET` |
| 권한 | 회원 Lv.8 |
| 관련 | INV-06 |
| 설명 | 15개 분야와 소속 종목. 사용 중인 분야를 정렬 순서대로, 분야마다 사용 중인 종목을 종목 번호 순으로 보여 준다(#24). |

**Request**

- 없음

**Response**

```json
{ "sectors": [ { "sectorId": 1, "name": "반도체", "stocks": [ { "stockId": 1, "name": "한빛반도체" }, { "stockId": 8, "name": "미래칩스" } ] } ] }
```

#### 56. 오늘의 가상 시세

| 항목 | 내용 |
| --- | --- |
| Endpoint | `/api/v1/trading/stocks` |
| Method | `GET` |
| 권한 | 회원 Lv.8 |
| 관련 | INV-06, INV-10 |
| 설명 | 현재 턴 가격과 등락률. 지금 보이는 시즌(진행 중·휴장 중·정산 중·종료 중 시작일이 가장 최근인 것) 기준이고 정산 중·종료에도 마지막 턴까지 계속 보여 주며(주문만 `SEASON_CLOSED`로 막힌다), 다음 턴 이후 가격은 미리 입력돼 있어도 나오지 않는다. 기본 정렬은 분야 정렬 순서 → 종목 번호, `sort=popular`(인기)는 이번 시즌 거래 수가 많은 순(같으면 종목 번호, 제안). `sectorId`, `keyword`(종목 이름 일부)로 거를 수 있다. 가격이 아직 입력되지 않은 종목은 `price`·`changeRate`가 null, 1턴은 `changeRate`가 null (#24) |

**Request**

- Query: sort=popular&sectorId=&keyword=

**Response**

```json
{ "turnNo": 5, "stocks": [ { "stockId": 1, "name": "한빛반도체", "sectorName": "반도체", "price": 84500, "changeRate": 2.8 } ] }
```

**Error**: `NO_RUNNING_SEASON`

#### 57. 종목 상세

| 항목 | 내용 |
| --- | --- |
| Endpoint | `/api/v1/trading/stocks/{stockId}` |
| Method | `GET` |
| 권한 | 회원 Lv.8 |
| 관련 | INV-07 |
| 설명 | 현재 가격, 1턴부터 현재 턴까지 가격 흐름, 기업정보, 내 보유 수량. `myHolding`은 로그인 회원이 이 시즌에 참가하지 않았거나 그 종목이 없으면 null. 사용 안 함 종목은 `NOT_FOUND` (#24) |

**Request**

- Path: stockId=1

**Response**

```json
{
  "stockId": 1, "name": "한빛반도체", "sectorName": "반도체", "turnNo": 5, "price": 84500, "changeRate": 2.8,
  "priceHistory": [ { "turnNo": 1, "price": 76800 }, { "turnNo": 5, "price": 84500 } ],
  "company": { "intro": "...", "business": "...", "revenue": "1조 2,000억", "operatingProfit": "1,450억" },
  "myHolding": { "quantity": 12, "avgPrice": 79000 }
}
```

**Error**: `NOT_FOUND`, `NO_RUNNING_SEASON`

#### 58. 가상 뉴스

| 항목 | 내용 |
| --- | --- |
| Endpoint | `/api/v1/trading/news` |
| Method | `GET` |
| 권한 | 회원 Lv.8 |
| 관련 | INV-08 |
| 설명 | 현재 턴까지 공개된 뉴스, 최근 턴부터. `turnNo`를 주면 그 턴만(현재 턴보다 뒤면 빈 목록), `stockId`를 주면 그 종목이 관련 종목으로 걸린 뉴스만(종목 뉴스 탭). 관리자 메모는 나오지 않는다 (#24) |

**Request**

- Query: turnNo=5&stockId=1

**Response**

```json
{ "items": [ { "newsId": 31, "turnNo": 5, "title": "한빛반도체, 대형 메모리 공급 계약", "content": "...", "sectors": ["반도체"], "stocks": ["한빛반도체"] } ] }
```

**Error**: `NO_RUNNING_SEASON`

#### 59. 매매 이유 선택지

| 항목 | 내용 |
| --- | --- |
| Endpoint | `/api/v1/trading/trade-reasons` |
| Method | `GET` |
| 권한 | 회원 Lv.8 |
| 관련 | INV-11 |
| 설명 | 주문할 때 고르는 선택지. |

**Request**

- 없음

**Response**

```json
{ "reasons": [ { "reasonId": 1, "label": "뉴스를 보고" }, { "reasonId": 2, "label": "나눠 담으려고" } ] }
```

#### 60. 매수·매도

| 항목 | 내용 |
| --- | --- |
| Endpoint | `/api/v1/trading/orders` |
| Method | `POST` |
| 권한 | 회원 Lv.8 |
| 관련 | INV-09~13, SP-53~58, DP-36, DP-40 |
| 설명 | 그 턴의 고정 가격으로 즉시 체결. turnNo가 현재 턴과 다르면 거절. 현금·보유 수량·거래 내역을 한 트랜잭션으로. |

**Request**

```json
Header: Idempotency-Key: 9b2f...
{ "stockId": 1, "orderType": "BUY", "quantity": 6, "turnNo": 5, "reasonId": 1 }
```

**Response**

```json
201 Created
{ "orderId": 901, "price": 84500, "amount": 507000, "cash": 2432000, "holding": { "stockId": 1, "quantity": 18, "avgPrice": 80833 } }
```

**Error**: `NOT_ENOUGH_CASH`, `NOT_ENOUGH_QUANTITY`, `REASON_REQUIRED`, `STALE_TURN_PRICE`, `MARKET_HOLIDAY`, `SEASON_CLOSED`, `NOT_JOINED`, `DUPLICATE_REQUEST`

#### 61. 내 거래 내역

| 항목 | 내용 |
| --- | --- |
| Endpoint | `/api/v1/trading/orders` |
| Method | `GET` |
| 권한 | 회원 Lv.8 |
| 관련 | INV-15 |
| 설명 | 매매 이유와 함께. |

**Request**

- Query: seasonId=7&page=0&size=20

**Response**

```json
{ "totalCount": 4, "items": [ { "orderId": 901, "turnNo": 5, "stockName": "한빛반도체", "orderType": "BUY", "quantity": 6, "price": 84500, "reason": "뉴스를 보고" } ] }
```

#### 62. 시즌 랭킹

| 항목 | 내용 |
| --- | --- |
| Endpoint | `/api/v1/trading/seasons/{seasonId}/rankings` |
| Method | `GET` |
| 권한 | 회원 Lv.8 |
| 관련 | INV-16~18, SP-62 |
| 설명 | 관리자가 공개한 뒤에만. type=ASSET(순자산) |

**Request**

- Query: type=ASSET&page=0&size=10

**Response**

```json
{
  "seasonName": "시즌 6", "type": "ASSET",
  "items": [ { "rank": 1, "nickname": "초록개미", "title": "내 집", "netAsset": 8920400, "returnRate": 42.8, "hasBadge": true } ],
  "me": { "rank": 23, "netAsset": 4610000, "returnRate": 6.5 }
}
```

**Error**: `RANKING_NOT_PUBLISHED`

### 모의 청약

#### 63. 청약 캘린더

| 항목 | 내용 |
| --- | --- |
| Endpoint | `/api/v1/housing/complexes` |
| Method | `GET` |
| 권한 | 회원 Lv.12 |
| 관련 | SUB-01~02, NFR-12 |
| 설명 | 월별 공고일·신청 기간·발표일. 그 달에 일정이 하나라도 걸친 단지(공고일 ≤ 말일, 발표일 ≥ 1일)를 신청 시작일 순으로 준다. `status`는 오늘 기준(API 64 참고), `applied`는 내가 신청한 단지면 `true`. |

**Request**

- Query: month=2026-11

**Response**

```json
{ "items": [ { "complexId": 1, "name": "그린숲 행복주택", "noticeDate": "2026-10-28", "applyStartDate": "2026-11-03", "applyEndDate": "2026-11-10", "announceDate": "2026-11-17", "status": "OPEN", "applied": false } ] }
```

**Error**: `LEVEL_LOCKED`

#### 64. 공고 상세

| 항목 | 내용 |
| --- | --- |
| Endpoint | `/api/v1/housing/complexes/{complexId}` |
| Method | `GET` |
| 권한 | 회원 Lv.12 |
| 관련 | SUB-03, SUB-08, SUB-10, SP-74 |
| 설명 | 단지 정보, 일정, 내 신청 여부. 경쟁률은 접수 마감 뒤에만. |

**Request**

- Path: complexId=1

**Response**

```json
{
  "complexId": 1, "name": "그린숲 행복주택", "location": "가상시 덕양구", "areaType": "59㎡", "salePrice": 320000000,
  "supplyUnits": 120, "supplyType": "일반공급", "applyStartDate": "2026-11-03", "applyEndDate": "2026-11-10", "announceDate": "2026-11-17",
  "status": "OPEN", "competitionRate": null, "myApplication": null
}
```

- `status`: 오늘(Asia/Seoul) 기준으로 계산한다. 신청 시작일 전 `UPCOMING`, 신청 기간(시작일·마감일 당일 포함) `OPEN`, 마감 뒤 `CLOSED`, 관리자가 발표하면 `ANNOUNCED`.
- `competitionRate`: 접수 마감 뒤에만 `신청자 수 ÷ 공급 세대`를 소수 둘째 자리까지 준다(예: 250명 ÷ 120세대 → `2.08`). 마감 전에는 `null` (SP-74, SUB-10).
- `myApplication`: 이 단지에 신청했으면 아래 값, 신청하지 않았으면 `null` (SUB-08).

```json
"myApplication": { "applicationId": 40, "receiptNo": "MB-261105-0042", "totalScore": 24, "appliedAt": "2026-11-05T10:00:00" }
```

**Error**: `LEVEL_LOCKED`, `NOT_FOUND` (없거나 삭제된 단지)

#### 65. 내 청약통장

| 항목 | 내용 |
| --- | --- |
| Endpoint | `/api/v1/housing/account` |
| Method | `GET` |
| 권한 | 회원 |
| 관련 | SUB-04, SP-70 |
| 설명 | 잔액, 누적 인정금액, 납입회차, 오늘 납입 여부(`paidToday`, 오늘 = Asia/Seoul), 납입 기준(1회 20,000~500,000, 하루 인정 250,000. SP-70 확정 #76), 내 캐시. **레벨 조건 없음**(가입과 동시에 통장 개설, #28). |

**Request**

- 없음

**Response**

```json
{ "accountId": 1, "balance": 1840000, "recognizedAmount": 1590000, "paidCount": 14, "lastPaidDate": "2026-11-04", "paidToday": false,
  "depositPolicy": { "minAmount": 20000, "maxAmount": 500000, "dailyRecognizedLimit": 250000 }, "cash": 1250000 }
```

**Error**: `NOT_FOUND`(통장 없음 : 가입 때 만들어지므로 보통 생기지 않음)

#### 66. 청약통장 납입

| 항목 | 내용 |
| --- | --- |
| Endpoint | `/api/v1/housing/account/deposits` |
| Method | `POST` |
| 권한 | 회원 |
| 관련 | SUB-05, SP-70, DP-35, DP-40 |
| 설명 | 하루 1회, 1회 20,000~500,000캐시. 납입액은 모두 잔액에 더하고, 인정금액은 min(납입액, 250,000)만 더한다. 캐시 차감 + 통장 잔액·인정금액·회차 증가 + 납입 내역 저장을 한 트랜잭션으로. |

**Request**

```json
Header: Idempotency-Key: 31aa...
{ "amount": 300000 }
```

**Response**

```json
201 Created
{ "depositId": 15, "amount": 300000, "recognizedAmount": 250000, "balance": 2140000, "totalRecognizedAmount": 1840000, "paidCount": 15, "cash": 950000 }
```

**Error**: `ALREADY_PAID_TODAY`(409), `DEPOSIT_BELOW_MIN`(400), `DEPOSIT_ABOVE_MAX`(400), `NOT_ENOUGH_CASH`(캐시 차감 RewardService 가 던짐), `NOT_FOUND`(통장 없음)

- 처리 순서: 금액 검사 → 통장 잠금 조회(동시 요청 방지) → 하루 1회 확인 → 납입 내역 저장 → 캐시 차감 → 통장 잔액·인정금액·회차·마지막 납입일 반영. 하나라도 실패하면 모두 취소된다.
- `Idempotency-Key` 헤더 처리는 #65(IdempotencyFilter)에서 붙인다.

#### 67. 납입 내역

| 항목 | 내용 |
| --- | --- |
| Endpoint | `/api/v1/housing/account/deposits` |
| Method | `GET` |
| 권한 | 회원 |
| 관련 | SUB-05 |
| 설명 | 납입 기록. 기본 20개씩 납입일 최신순(`sort=paidDate,desc`). 목록 응답 형식(1-3)을 따른다. |

**Request**

- Query: page=0&size=20

**Response**

```json
{ "items": [ { "depositId": 15, "paidDate": "2026-11-05", "amount": 300000, "recognizedAmount": 250000, "paidCountAfter": 15 } ], "page": 0, "size": 20, "totalElements": 15, "totalPages": 1 }
```

#### 68. 가점 계산기

| 항목 | 내용 |
| --- | --- |
| Endpoint | `/api/v1/housing/score-preview` |
| Method | `POST` |
| 권한 | 회원 Lv.12 |
| 관련 | SUB-06, SP-71 |
| 설명 | 무주택 기간과 부양가족 수를 넣으면 내 청약통장 납입회차를 자동 반영해 계산. 저장하지 않음. 점수표는 실제 청약 가점제(SP-71 확정, #29) |

**Request**

```json
{ "homelessYears": 3, "dependents": 1 }
```

- `homelessYears`: 무주택 기간(년, 1년 미만은 0), `dependents`: 부양가족 수. 둘 다 0 이상.

**Response**

```json
{ "homelessScore": 8, "dependentScore": 10, "accountScore": 6, "totalScore": 24, "maxScore": 84, "baseDate": "2026-11-01" }
```

- 무주택(32): 1년 미만 2점, 1년마다 +2점, 15년 이상 32점 / 부양가족(35): 0명 5점, 1명마다 +5점, 6명 이상 35점 / 통장(17): 납입 1회 = 1개월로 보고 6회 미만 1점, 6~11회 2점, 12~23회 3점, 이후 12회마다 +1점, 180회 이상 17점. 예시는 납입 48회.
- `baseDate`: 계산한 날(오늘, Asia/Seoul). 화면에 기준일로 표시한다.

**Error**: `LEVEL_LOCKED`, `INVALID_SCORE_INPUT`(400, 무주택 기간 0~50년·부양가족 0~20명 밖), `NOT_FOUND`(청약통장 없음)

#### 69. 청약 신청

| 항목 | 내용 |
| --- | --- |
| Endpoint | `/api/v1/housing/complexes/{complexId}/applications` |
| Method | `POST` |
| 권한 | 회원 Lv.12 |
| 관련 | SUB-07~08, SP-72~73 |
| 설명 | 신청 기간(신청 시작일·마감일 당일 포함) 안에서만, 같은 단지 1회. 신청 후 취소·수정 불가. 신청 시점의 입력값·납입회차·항목별 점수를 저장(이후 납입해도 신청 점수는 그대로). 접수번호는 `MB-신청일(yyMMdd)-그날 순번 4자리`. |

**Request**

```json
{ "homelessYears": 3, "dependents": 1 }
```

**Response**

```json
201 Created
{ "applicationId": 40, "receiptNo": "MB-261105-0042", "totalScore": 24 }
```

**Error**: `LEVEL_LOCKED`, `NOT_IN_APPLY_PERIOD`(409), `ALREADY_APPLIED`(409, 같은 단지에 동시에 두 번 신청해 회원+단지 유니크 키에 걸린 경우 포함), `RECEIPT_NO_CONFLICT`(409, 다른 회원과 같은 순간에 신청해 접수번호가 겹침. 저장되지 않았으니 다시 신청), `INVALID_SCORE_INPUT`(400), `NOT_FOUND`(없거나 삭제된 단지, 청약통장 없음)

#### 70. 내 신청 내역

| 항목 | 내용 |
| --- | --- |
| Endpoint | `/api/v1/housing/applications/me` |
| Method | `GET` |
| 권한 | 회원 Lv.12 |
| 관련 | SUB-09, SUB-11 |
| 설명 | 신청 단지, 접수번호, 가점, 발표일, 발표 후 결과. 기본 20개씩 신청 최신순(`sort=appliedAt,desc`). 목록 응답 형식(1-3). `result`는 관리자가 발표하기 전에는 저장돼 있어도 `null`(SP-75). |

**Request**

- Query: page=0&size=20

**Response**

```json
{ "items": [ { "applicationId": 40, "complexName": "그린숲 행복주택", "receiptNo": "MB-261105-0042", "totalScore": 24, "announceDate": "2026-11-17", "result": null } ], "page": 0, "size": 20, "totalElements": 1, "totalPages": 1 }
```

**Error**: `LEVEL_LOCKED`

#### 71. 당첨 결과

| 항목 | 내용 |
| --- | --- |
| Endpoint | `/api/v1/housing/applications/{applicationId}/result` |
| Method | `GET` |
| 권한 | 회원 (본인) |
| 관련 | SUB-11 |
| 설명 | 관리자가 발표한 뒤에만(단지 `status = ANNOUNCED`이고 산정 결과가 있을 때). 본인 신청만 볼 수 있다. 레벨 조건 없음. |

**Request**

- Path: applicationId=40

**Response**

```json
{ "result": "LOSE", "totalScore": 24, "message": "이번에는 낙첨이에요. 내 가점은 24점이었어요" }
```

- 당첨이면 `"result": "WIN"`, `"message": "축하해요! 당첨이에요. 내 가점은 N점이었어요"`.

**Error**: `NOT_ANNOUNCED_YET`(403, 발표 전 또는 산정 전), `NOT_FOUND`(없는 신청이거나 남의 신청)

### 강사

#### 72. 내 강의 목록

| 항목 | 내용 |
| --- | --- |
| Endpoint | `/api/v1/instructor/courses` |
| Method | `GET` |
| 권한 | 강사 |
| 관련 | TCH-06 |
| 설명 | 내 강의(삭제 제외), 최근 만든 순. 검수 상태 포함. status 필터는 선택. learners(학습 시작 회원 수), waitingInquiries(답변 대기 문의 수, 삭제·숨김 제외), rejectReason(반려 상태일 때 가장 최근 반려 사유, 그 외 null). |

**Request**

- Query: status=DRAFT|REVIEW|APPROVED|REJECTED (선택)

**Response**

```json
{ "items": [ { "courseId": 7, "title": "청약통장 기초와 가점 계산", "status": "APPROVED", "isPublic": true, "thumbnailUrl": "/uploads/....png", "learners": 214, "waitingInquiries": 2, "rejectReason": null } ] }
```

#### 73. 강의 생성

| 항목 | 내용 |
| --- | --- |
| Endpoint | `/api/v1/instructor/courses` |
| Method | `POST` |
| 권한 | 강사 |
| 관련 | TCH-01 |
| 설명 | 사용 중인 소주제(2단계), 제목(100자), 소개(2,000자), 정보 기준일·출처(200자, 선택)로 강의를 만든다(DRAFT, 비공개). 제목·소개·출처 금칙어 검사. |

**Request**

```json
{ "categoryId": 21, "title": "청약통장 기초와 가점 계산", "description": "...", "infoBaseDate": "2026-10-01", "infoSource": "청약홈" }
```

**Response**

```json
201 Created
{ "courseId": 7, "status": "DRAFT" }
```

**Error**: `FORBIDDEN_WORD`, `CATEGORY_NOT_SELECTABLE`(없거나 비활성이거나 상위 카테고리)

#### 74. 강의 편집 정보

| 항목 | 내용 |
| --- | --- |
| Endpoint | `/api/v1/instructor/courses/{courseId}` |
| Method | `GET` |
| 권한 | 강사 (본인 강의) |
| 관련 | TCH-01~05 |
| 설명 | 본인 강의만. 강의 정보 + lessons(영상·요약글) + quizzes(테스트 퀴즈·퀴즈, 보기별 isCorrect·해설 포함) + rejectReason(가장 최근 반려 사유). |

**Request**

- Path: courseId=7

**Response**

- `{ courseId, categoryId, title, description, thumbnailUrl, status, isPublic, infoBaseDate, infoSource, submittedAt, approvedAt, rejectReason, lessons: [ { lessonId, sortOrder, title, videoUrl, durationSeconds, summary } ], quizzes: [ { quizQuestionId, quizType(TEST·MAIN), questionType, content, explanation, sortOrder, choices: [ { quizChoiceId, content, isCorrect, sortOrder } ] } ] }`

**Error**: `FORBIDDEN`(남의 강의), `NOT_FOUND`(삭제된 강의)

#### 75. 강의 수정

| 항목 | 내용 |
| --- | --- |
| Endpoint | `/api/v1/instructor/courses/{courseId}` |
| Method | `PUT` |
| 권한 | 강사 (본인 강의) |
| 관련 | TCH-06, SP-31 |
| 설명 | 본인 강의만. 검수 중(REVIEW)에는 수정 불가. 허가된 강의를 고치면 다시 검수(REVIEW, submitted_at 갱신) → 허가 전까지 사용자에게 보이지 않음. 요청 형식·검사는 API 73과 같다. |

**Request**

```json
{ "categoryId": 21, "title": "...", "description": "...", "infoBaseDate": "2026-10-01", "infoSource": "청약홈" }
```

**Response**

```json
{ "courseId": 7, "status": "REVIEW" }
```

**Error**: `COURSE_IN_REVIEW`, `FORBIDDEN_WORD`, `CATEGORY_NOT_SELECTABLE`, `FORBIDDEN`, `NOT_FOUND`

#### 76. 강의 삭제

| 항목 | 내용 |
| --- | --- |
| Endpoint | `/api/v1/instructor/courses/{courseId}` |
| Method | `DELETE` |
| 권한 | 강사 (본인 강의) |
| 관련 | TCH-06, SP-32 |
| 설명 | 본인 강의만. 학습 기록이 있으면 삭제 불가(비공개는 관리자에게 요청). 없으면 Soft Delete(deleted_at). |

**Request**

- Path: courseId=7

**Response**

- 204 No Content

**Error**: `COURSE_HAS_LEARNERS`, `FORBIDDEN`, `NOT_FOUND`

#### 77. 썸네일 업로드

| 항목 | 내용 |
| --- | --- |
| Endpoint | `/api/v1/instructor/courses/{courseId}/thumbnail` |
| Method | `POST` |
| 권한 | 강사 |
| 관련 | TCH-01 |
| 설명 | 본인 강의만, 검수 중에는 불가. multipart/form-data(필드 file). 이미지 2MB·jpg·png(DP-32). 허가된 강의의 썸네일을 바꾸면 정보 수정(API 75)과 같이 다시 검수(REVIEW, 제출 시각 갱신)로 바뀌어 허가 전까지 사용자에게 보이지 않는다(SP-31). |

**Request**

- Form: file

**Response**

```json
{ "thumbnailUrl": "/uploads/3f2a....png" }
```

**Error**: `FILE_TOO_LARGE`, `FILE_TYPE_NOT_ALLOWED`, `COURSE_IN_REVIEW`, `FORBIDDEN`, `NOT_FOUND`

#### 78. 영상·요약글 저장

| 항목 | 내용 |
| --- | --- |
| Endpoint | `/api/v1/instructor/courses/{courseId}/lessons` |
| Method | `PUT` |
| 권한 | 강사 |
| 관련 | TCH-02~03, SP-30, DP-31, DP-33 |
| 설명 | 영상 1~3개를 순서대로 일괄 저장. 요약글 2,000자. 영상 파일은 먼저 한 개씩 올려서(아래 "영상 파일 올리기") 받은 `videoUrl`을 이 요청에 담아 저장한다. **이미 저장된 영상을 고칠 때만 `lessonId`를 넣고, 새 영상은 `lessonId`를 생략한다**(서버가 새 영상으로 만들고 응답 `lessonIds`에 돌려준다). 응답의 `lessonIds`는 요청 `lessons`와 **같은 순서로 하나씩 대응**하며, 요청한 모든 영상의 ID를 돌려준다(기존 영상은 보낸 `lessonId` 그대로, 새 영상은 새로 만든 ID). **재시도**: 새 영상은 `lessonId`가 없어 저장 뒤 응답이 유실된 채 다시 보내면 중복 생성될 수 있으므로, 이 요청도 `Idempotency-Key` 헤더를 보낸다(DP-37). 같은 키로 다시 오면 영상을 또 만들지 않고 **최초 응답의 `lessonIds`를 같은 순서로** 돌려주며, 같은 키에 내용이 다르면 거절한다(409, `IDEMPOTENCY_KEY_CONFLICT`). 기존 영상의 `lessonId` 처리와 응답 순서 규칙은 그대로다. 영상은 1개 500MB 이하, mp4 (DP-33, 확정). |

**Request**

```json
Header: Idempotency-Key: 4d8e...
{ "lessons": [
  { "lessonId": 20, "sortOrder": 1, "title": "청약통장이 뭐예요?", "videoUrl": "https://...", "durationSeconds": 480, "summary": "..." },
  { "sortOrder": 2, "title": "가점은 어떻게 계산할까", "videoUrl": "https://...", "durationSeconds": 840, "summary": "..." }
] }
```

첫 번째는 이미 저장된 영상(`lessonId` 있음), 두 번째는 새 영상(`lessonId` 없음)이다.

**Response**

```json
{ "lessonIds": [20, 21] }
```

요청 `lessons`와 같은 순서다: 첫 번째 영상은 기존 ID 20, 두 번째 새 영상은 새로 만든 ID 21.

**Error**: `TOO_MANY_LESSONS`, `SUMMARY_TOO_LONG`

**영상 파일 올리기** (#78의 부속 요청, API 번호는 늘리지 않는다)

| 항목 | 내용 |
| --- | --- |
| Endpoint | `/api/v1/instructor/courses/{courseId}/lessons/video` |
| Method | `POST` |
| 권한 | 강사 |
| 관련 | TCH-02, DP-33 |
| 설명 | multipart/form-data. 영상 한 개씩 올린다. 한 요청에 파일 하나만 담으므로 요청 한도(510MB)로 충분하다. |

- Form: file=(mp4, 500MB 이하)

```json
{ "videoUrl": "lesson/7-1.mp4" }
```

응답의 `videoUrl`은 **만료되지 않는 파일 키**(스토리지 안의 경로)이고, 서명 주소가 아니다. 서버는 업로드 때 이 키를 받아 두었다가, 위 "영상·요약글 저장" 요청에 담긴 같은 값을 `lesson.video_url`(DB)에 그대로 저장한다. 영상을 보여 주는 응답(API 62 등)의 `videoUrl`은 저장된 키로 **조회할 때마다 새로 발급한 짧게 만료되는 서명 주소**다(local 환경은 서버가 `/files/` + 키로 로컬 파일을 직접 내려 준다). 그래서 서명 주소를 DB에 저장하거나 저장 요청에 담지 않는다.

**Error**: `FILE_TOO_LARGE`, `FILE_TYPE_NOT_ALLOWED`

#### 79. 테스트 퀴즈 저장

| 항목 | 내용 |
| --- | --- |
| Endpoint | `/api/v1/instructor/courses/{courseId}/test-quiz` |
| Method | `PUT` |
| 권한 | 강사 |
| 관련 | TCH-04 |
| 설명 | OX 5문제와 해설 일괄 저장. |

**Request**

```json
{ "questions": [ { "content": "청약통장은 1인 1개다.", "answer": "O", "explanation": "..." } ] }
```

**Response**

```json
{ "count": 5 }
```

**Error**: `QUIZ_COUNT_INVALID`

#### 80. 퀴즈 저장

| 항목 | 내용 |
| --- | --- |
| Endpoint | `/api/v1/instructor/courses/{courseId}/quiz` |
| Method | `PUT` |
| 권한 | 강사 |
| 관련 | TCH-05 |
| 설명 | 4지선다 5~15문제, 정답, 해설 일괄 저장. |

**Request**

```json
{ "questions": [ { "content": "청약 가점 항목이 아닌 것은?", "choices": ["무주택 기간", "부양가족 수", "통장 가입 기간", "연 소득"], "answerIndex": 3, "explanation": "..." } ] }
```

**Response**

```json
{ "count": 10 }
```

**Error**: `QUIZ_COUNT_INVALID`

#### 81. 검수 요청

| 항목 | 내용 |
| --- | --- |
| Endpoint | `/api/v1/instructor/courses/{courseId}/review-request` |
| Method | `POST` |
| 권한 | 강사 (본인 강의) |
| 관련 | TCH-07, DP-34 |
| 설명 | 작성 중(DRAFT)·반려(REJECTED) 강의만 요청 가능(반려된 강의를 고쳐 다시 요청). 영상 1개 이상, 테스트 퀴즈 5문제, 퀴즈 5~15문제가 있어야 한다. 요청하면 REVIEW로 바뀌고 submitted_at을 기록. |

**Request**

- Path: courseId=7

**Response**

```json
{ "courseId": 7, "status": "REVIEW" }
```

**Error**: `NO_LESSON`, `QUIZ_COUNT_INVALID`, `COURSE_NOT_REQUESTABLE`(검수 중·허가된 강의), `FORBIDDEN`(남의 강의), `NOT_FOUND`(삭제된 강의)

#### 82. 내 강의 문의

| 항목 | 내용 |
| --- | --- |
| Endpoint | `/api/v1/instructor/inquiries` |
| Method | `GET` |
| 권한 | 강사 |
| 관련 | TCH-08 |
| 설명 | 내 강의(삭제 제외)에 달린 문의. 답변 대기 글 먼저, 그다음 최신순. 삭제·숨긴 글 제외. courseId·status(WAITING·ANSWERED)는 선택. waitingCount는 필터와 상관없이 내 강의 전체의 답변 대기 수. |

**Request**

- Query: status=WAITING&courseId=&page=0&size=20

**Response**

```json
{ "waitingCount": 2, "items": [ { "inquiryId": 3, "courseId": 7, "courseTitle": "청약통장 기초와 가점 계산", "title": "...", "status": "WAITING", "createdAt": "2026-11-03T10:00:00" } ], "page": 0, "size": 20, "totalElements": 1, "totalPages": 1 }
```

**Error**: `FORBIDDEN`(내 강의가 아닌 courseId)

### 관리자

#### 83. 관리자 로그인

| 항목 | 내용 |
| --- | --- |
| Endpoint | `/api/v1/admin/auth/login` |
| Method | `POST` |
| 권한 | 비회원 |
| 관련 | ADM-01, DP-10 |
| 설명 | ADMIN 권한 계정만 성공. 일반 로그인(API 6)과 같은 **폼 로그인**이라 요청은 JSON 이 아니라 **폼 형식**(`application/x-www-form-urlencoded`)이고 CSRF 토큰도 함께 보낸다. 5회 연속 실패 시 잠금은 일반 로그인과 같은 규칙(1분, 이후 5회마다 5분)이고 잠금 횟수도 함께 센다. 일반 회원·강사 계정은 비밀번호가 맞아도 `FORBIDDEN`(403)이고 로그인 성공으로 기록되지 않는다. |

**Request**

```
POST /api/v1/admin/auth/login
Content-Type: application/x-www-form-urlencoded

email=admin@moneybeetle.kr&password=...
```

**Response**

```json
{ "memberId": 100, "role": "ADMIN" }
```

**Error**: `LOGIN_FAILED`, `LOGIN_LOCKED`, `FORBIDDEN`

#### 84. 대시보드

| 항목 | 내용 |
| --- | --- |
| Endpoint | `/api/v1/admin/dashboard` |
| Method | `GET` |
| 권한 | 관리자 |
| 관련 | ADM-08 |
| 설명 | 검수 대기 강의 수, 답변 대기 문의, 진행 중 시즌·턴, 참가자 수, 오늘 주문 수, 다음 턴 점검 결과. |

**Request**

- 없음

**Response**

```json
{ "reviewWaiting": 3, "inquiryWaiting": 2, "season": { "seasonId": 7, "currentTurn": 5, "participants": 1240, "todayOrders": 3820, "nextTurnReady": false, "missing": { "prices": 2, "news": 0 } }, "members": { "total": 5, "withdrawn": 1 } }
```

#### 85. 카테고리 목록

| 항목 | 내용 |
| --- | --- |
| Endpoint | `/api/v1/admin/categories` |
| Method | `GET` |
| 권한 | 관리자 |
| 관련 | ADM-02 |
| 설명 | 카테고리 → 소주제 트리(비활성 포함). 단계 → 순서 → ID 순. 항목마다 isActive, courseCount(삭제되지 않은 강의 수). 소주제의 children은 빈 목록. |



- 없음

**Response**
```json
[ { "categoryId": 2, "name": "부동산", "depth": 1, "sortOrder": 1, "isActive": true, "courseCount": 0,
    "children": [ { "categoryId": 22, "name": "전·월세", "depth": 2, "sortOrder": 2, "isActive": true, "courseCount": 3, "children": [] } ] } ]
```

- (카테고리 트리 + isActive, courseCount)

#### 86. 카테고리 등록

| 항목 | 내용 |
| --- | --- |
| Endpoint | `/api/v1/admin/categories` |
| Method | `POST` |
| 권한 | 관리자 |
| 관련 | ADM-02 |
| 설명 | parentId가 있으면 소주제(depth 2), 없으면 카테고리(depth 1). 2단계까지만(소주제 아래에는 등록 불가). 이름 30자, 순서 0 이상. |

**Request**

```json
{ "parentId": 2, "name": "전·월세", "sortOrder": 2 }
```

**Response**

```json
201 Created
{ "categoryId": 22 }
**Error**: `CATEGORY_DEPTH_EXCEEDED`, `NOT_FOUND`(없는 parentId)
```


#### 87. 카테고리 수정

| 항목 | 내용 |
| --- | --- |
| Endpoint | `/api/v1/admin/categories/{categoryId}` |
| Method | `PUT` |
| 권한 | 관리자 |
| 관련 | ADM-02 |
| 설명 | 이름·순서·사용 여부. |

**Request**

```json
{ "name": "전·월세", "sortOrder": 1, "isActive": true }
```

**Response**

- 204 No Content
**Error**: `NOT_FOUND`

#### 88. 카테고리 삭제

| 항목 | 내용 |
| --- | --- |
| Endpoint | `/api/v1/admin/categories/{categoryId}` |
| Method | `DELETE` |
| 권한 | 관리자 |
| 관련 | ADM-02 |
| 설명 | 강의(삭제된 강의 포함)나 소주제가 연결돼 있으면 삭제하지 않고 비활성(is_active=FALSE)으로 바꾼다. 연결이 없으면 실제 삭제. 두 경우 모두 204. |

**Request**

- Path: categoryId=22

**Response**

- 204 No Content

**Error**: `NOT_FOUND`, `CATEGORY_IN_USE`(삭제 확인 직후 강의가 동시에 연결된 경우. 다시 요청하면 비활성으로 처리)

#### 89. 강의 목록(검수)

| 항목 | 내용 |
| --- | --- |
| Endpoint | `/api/v1/admin/courses` |
| Method | `GET` |
| 권한 | 관리자 |
| 관련 | ADM-03~04 |
| 설명 | 상태별 강의 목록(삭제된 강의 제외). status=DRAFT·REVIEW·APPROVED·REJECTED, 없거나 모르는 값이면 전체. 검수 요청 최신순. 항목: courseId, title, instructorName, status, isPublic, submittedAt, learners(학습 시작 회원 수). 페이지 응답(PageResponse). |


- Query: status=REVIEW&page=0&size=20

**Response**

```json
{ "items": [ { "courseId": 9, "title": "...", "instructorName": "이준호", "status": "REVIEW", "submittedAt": "2026-11-04T09:00:00", "learners": 0 } ] }
```

#### 90. 검수 상세

| 항목 | 내용 |
| --- | --- |
| Endpoint | `/api/v1/admin/courses/{courseId}` |
| Method | `GET` |
| 권한 | 관리자 |
| 관련 | ADM-03 |
| 설명 | 강의 편집 정보 + lessons[](영상·요약글, 순서대로) + quizzes[](테스트·본 퀴즈 전체, 보기별 isCorrect·해설 포함) + reviews[](검수 이력 최신순: result, reason, adminId, createdAt). 삭제된 강의는 NOT_FOUND. |


- Path: courseId=9

**Response**

- (강의 편집 정보 + reviews[])

#### 91. 강의 허가

| 항목 | 내용 |
| --- | --- |
| Endpoint | `/api/v1/admin/courses/{courseId}/approve` |
| Method | `POST` |
| 권한 | 관리자 |
| 관련 | ADM-03, DP-43 |
| 설명 | 검수 중(REVIEW)인 강의만. 허가 + 공개, approved_at 기록. course_review 기록(admin_log는 후속). |


- Path: courseId=9

**Response**
#### **Error**: `COURSE_NOT_IN_REVIEW`, `NOT_FOUND`

```json
{ "status": "APPROVED", "isPublic": true }
```

#### 92. 강의 반려

| 항목 | 내용 |
| --- | --- |
| Endpoint | `/api/v1/admin/courses/{courseId}/reject` |
| Method | `POST` |
| 권한 | 관리자 |
| 관련 | ADM-03, SP-31 |
| 설명 | 검수 중(REVIEW)인 강의만. 사유 필수(500자). REJECTED + 비공개, course_review에 사유 기록, 강사에게 알림(type COURSE_REJECTED). 강사 화면의 rejectReason은 가장 최근 반려 사유. |
**Request**

```json
{ "reason": "퀴즈 해설이 비어 있어요" }
```

**Response**

```json
{ "status": "REJECTED" }
```

**Error**: `REASON_REQUIRED`, `COURSE_NOT_IN_REVIEW`, `NOT_FOUND`

#### 93. 공개·비공개

| 항목 | 내용 |
| --- | --- |
| Endpoint | `/api/v1/admin/courses/{courseId}/visibility` |
| Method | `PATCH` |
| 권한 | 관리자 |
| 관련 | ADM-04 |
| 설명 | 허가(APPROVED)된 강의만 비공개로 바꾸거나 다시 공개. 허가 안 된 강의는 공개할 수 없음. |


```json
{ "isPublic": false }
```

**Response**

- 204 No Content
**Error**: `COURSE_NOT_APPROVED`, `NOT_FOUND`

#### 94. 강의 삭제

| 항목 | 내용 |
| --- | --- |
| Endpoint | `/api/v1/admin/courses/{courseId}` |
| Method | `DELETE` |
| 권한 | 관리자 |
| 관련 | ADM-04, SP-32 |
| 설명 | 학습 기록이 있으면 삭제 불가(비공개만). |

**Request**

- Path: courseId=9

**Response**

- 204 No Content

**Error**: `COURSE_HAS_LEARNERS`

#### 95. 경제 용어 목록

| 항목 | 내용 |
| --- | --- |
| Endpoint | `/api/v1/admin/economic-terms` |
| Method | `GET` |
| 권한 | 관리자 |
| 관련 | ADM-05 |
| 설명 | 등록 순. |

**Request**

- Query: keyword=&page=0&size=50

**Response**

```json
{ "items": [ { "economicTermId": 3, "term": "복리", "description": "...", "isActive": true } ] }
```

#### 96. 경제 용어 등록

| 항목 | 내용 |
| --- | --- |
| Endpoint | `/api/v1/admin/economic-terms` |
| Method | `POST` |
| 권한 | 관리자 |
| 관련 | ADM-05 |
| 설명 | 용어 중복 불가. |

**Request**

```json
{ "term": "복리", "description": "이자에 이자가 붙는 방식" }
```

**Response**

```json
201 Created
{ "economicTermId": 3 }
```

**Error**: `TERM_DUPLICATED`

#### 97. 경제 용어 수정

| 항목 | 내용 |
| --- | --- |
| Endpoint | `/api/v1/admin/economic-terms/{termId}` |
| Method | `PUT` |
| 권한 | 관리자 |
| 관련 | ADM-05 |
| 설명 | 뜻·사용 여부 수정. |

**Request**

```json
{ "term": "복리", "description": "...", "isActive": true }
```

**Response**

- 204 No Content

#### 98. 경제 용어 삭제

| 항목 | 내용 |
| --- | --- |
| Endpoint | `/api/v1/admin/economic-terms/{termId}` |
| Method | `DELETE` |
| 권한 | 관리자 |
| 관련 | ADM-05 |
| 설명 | 노출 이력이 있으면 비활성. |

**Request**

- Path: termId=3

**Response**

- 204 No Content

#### 99. 날짜별 용어 일정

| 항목 | 내용 |
| --- | --- |
| Endpoint | `/api/v1/admin/term-schedules` |
| Method | `GET` |
| 권한 | 관리자 |
| 관련 | ADM-05 |
| 설명 | 월별 지정 현황. 그 달의 모든 날을 돌려주고, 아직 정해지지 않은 날은 자동 선정될 결과를 미리 보여 준다(`isAuto: true`, 저장되지 않음). |

**Request**

- Query: month=2026-11

**Response**

```json
{ "days": [ { "date": "2026-11-05", "isAuto": false, "terms": [ { "economicTermId": 3, "term": "복리" } ] } ] }
```

#### 100. 오늘의 용어 지정

| 항목 | 내용 |
| --- | --- |
| Endpoint | `/api/v1/admin/term-schedules/{date}` |
| Method | `PUT` |
| 권한 | 관리자 |
| 관련 | ADM-05, SP-46~47 |
| 설명 | 5개를 지정. 지난 날짜는 변경 불가, 사용 중인 용어가 5개가 되기 전이거나 고른 용어가 5개 미만이면 저장 불가. |

**Request**

```json
{ "economicTermIds": [3, 5, 8, 11, 14] }
```

**Response**

- 204 No Content

**Error**: `PAST_DATE_LOCKED`, `TERMS_NOT_ENOUGH`

#### 101. 사용자·강사 목록

| 항목 | 내용 |
| --- | --- |
| Endpoint | `/api/v1/admin/members` |
| Method | `GET` |
| 권한 | 관리자 |
| 관련 | ADM-06 |
| 설명 | 역할·상태·키워드 검색. |

**Request**

- Query: role=MEMBER|INSTRUCTOR&status=ACTIVE&keyword=민지&page=0&size=20

**Response**

```json
{ "items": [ { "memberId": 1, "nickname": "민지", "email": "minji@gmail.com", "level": 4, "role": "MEMBER", "status": "ACTIVE", "cash": 12400 } ] }
```

#### 102. 계정 정지·해제

| 항목 | 내용 |
| --- | --- |
| Endpoint | `/api/v1/admin/members/{memberId}/status` |
| Method | `PATCH` |
| 권한 | 관리자 |
| 관련 | ADM-06, SP-06, DP-43 |
| 설명 | 정지 시 사유 필수. |

**Request**

```json
{ "status": "SUSPENDED", "reason": "부적절한 문의 반복" }
```

**Response**

- 204 No Content

**Error**: `REASON_REQUIRED`

#### 103. 강사 권한 부여·회수

| 항목 | 내용 |
| --- | --- |
| Endpoint | `/api/v1/admin/members/{memberId}/role` |
| Method | `PATCH` |
| 권한 | 관리자 |
| 관련 | ADM-06, SP-07 |
| 설명 | INSTRUCTOR ↔︎ MEMBER. |

**Request**

```json
{ "role": "INSTRUCTOR" }
```

**Response**

- 204 No Content

#### 104. 강사 가입 신청 목록

| 항목 | 내용 |
| --- | --- |
| Endpoint | `/api/v1/admin/instructor-applications` |
| Method | `GET` |
| 권한 | 관리자 |
| 관련 | ADM-06 |
| 설명 | 승인 대기 강사. |

**Request**

- Query: status=PENDING

**Response**

```json
{ "items": [ { "memberId": 31, "displayName": "박서연", "field": "청약, ETF", "email": "teacher@moneybeetle.kr", "createdAt": "2026-11-04T10:00:00" } ] }
```

#### 105. 강사 승인

| 항목 | 내용 |
| --- | --- |
| Endpoint | `/api/v1/admin/instructor-applications/{memberId}/approve` |
| Method | `POST` |
| 권한 | 관리자 |
| 관련 | ADM-06 |
| 설명 | 승인하면 role=INSTRUCTOR. 거절은 /reject. |

**Request**

- Path: memberId=31

**Response**

- 204 No Content

#### 106. 문의 게시판 관리

| 항목 | 내용 |
| --- | --- |
| Endpoint | `/api/v1/admin/inquiries` |
| Method | `GET` |
| 권한 | 관리자 |
| 관련 | ADM-07 |
| 설명 | 전체 문의(숨김·삭제 포함). 필터는 모두 선택: courseId(그 강의 문의만), status(WAITING·ANSWERED), hidden(true·false), deleted(true=삭제된 글만, false=삭제 안 된 글만). 숨긴 글도 원문 제목 그대로. 최신순. |

**Request**

- Query: courseId=&status=&hidden=&deleted=&page=0&size=20

**Response**
```json
{ "items": [ { "inquiryId": 3, "courseId": 7, "category": null, "title": "...", "status": "WAITING", "writerId": 1, "writerNickname": "민지", "isHidden": true, "isDeleted": false, "createdAt": "2026-11-03T10:00:00" } ], "page": 0, "size": 20, "totalElements": 1 }
```

- (문의 목록 + isHidden, isDeleted)

#### 107. 문의 숨김·해제

| 항목 | 내용 |
| --- | --- |
| Endpoint | `/api/v1/admin/inquiries/{inquiryId}/hidden` |
| Method | `PATCH` |
| 권한 | 관리자 |
| 관련 | ADM-07, DP-22 |
| 설명 | 숨기면 hidden_by에 관리자 ID를 남기고, 사용자 목록(API 47)에서 빠지며 상세(API 49)는 “관리자에 의해 숨김 처리된 글이에요”로 보인다. 해제하면 hidden_by를 지운다. 삭제된 글은 NOT_FOUND. |

**Request**

```json
{ "hidden": true }
```

**Response**

- 204 No Content
**Error**: `NOT_FOUND`

#### 108. 보상 규칙 조회

| 항목 | 내용 |
| --- | --- |
| Endpoint | `/api/v1/admin/reward-rules` |
| Method | `GET` |
| 권한 | 관리자 |
| 관련 | ADM-29 |
| 설명 | 활동별 캐시·경험치. |

**Request**

- 없음

**Response**

```json
{ "rules": [ { "rewardRuleId": 1, "code": "COURSE_COMPLETE", "name": "학습 완료", "cashAmount": 3000, "expAmount": 100, "isActive": true } ] }
```

#### 109. 보상 규칙 수정

| 항목 | 내용 |
| --- | --- |
| Endpoint | `/api/v1/admin/reward-rules/{ruleId}` |
| Method | `PUT` |
| 권한 | 관리자 |
| 관련 | ADM-29, NFR-13 |
| 설명 | 금액 변경은 admin_log 기록. |

**Request**

```json
{ "cashAmount": 3000, "expAmount": 100, "isActive": true }
```

**Response**

- 204 No Content

#### 110. 캐시 수동 지급·회수

| 항목 | 내용 |
| --- | --- |
| Endpoint | `/api/v1/admin/members/{memberId}/cash-adjustments` |
| Method | `POST` |
| 권한 | 관리자 |
| 관련 | ADM-30, SP-17, DP-43 |
| 설명 | 사유 필수(200자 이하). 금액은 1 이상 1,000,000,000(10억) 캐시 이하이고(SP-17), 회수로 0 미만이 되면 거절. |

**Request**

```json
{ "type": "GIVE", "amount": 2000, "reason": "출석 누락 보정" }
```

**Response**

```json
201 Created
{ "cashHistoryId": 130, "balanceAfter": 14400 }
```

**Error**: `INVALID_AMOUNT`, `REASON_REQUIRED`, `NOT_ENOUGH_CASH`

#### 111. 서비스 설정

| 항목 | 내용 |
| --- | --- |
| Endpoint | `/api/v1/admin/settings` |
| Method | `GET` |
| 권한 | 관리자 |
| 관련 | NFR-13, ADM-11 |
| 설명 | 해금 레벨, 퀴즈 통과 점수, 청약 납입액 등. |

**Request**

- 없음

**Response**

```json
{ "settings": [ { "key": "UNLOCK_LEVEL_TRADING", "value": "8" }, { "key": "QUIZ_PASS_SCORE", "value": "70" } ] }
```

#### 112. 서비스 설정 변경

| 항목 | 내용 |
| --- | --- |
| Endpoint | `/api/v1/admin/settings/{key}` |
| Method | `PUT` |
| 권한 | 관리자 |
| 관련 | NFR-13 |
| 설명 | 값 변경, admin_log 기록. |

**Request**

```json
{ "value": "12" }
```

**Response**

- 204 No Content

#### 113. 관리자 작업 기록

| 항목 | 내용 |
| --- | --- |
| Endpoint | `/api/v1/admin/admin-logs` |
| Method | `GET` |
| 권한 | 관리자 |
| 관련 | NFR-14, DP-43 |
| 설명 | 대상·관리자·기간으로 조회. |

**Request**

- Query (모두 선택): targetType=SEASON&targetId=7&adminId=1&from=2026-11-01&to=2026-11-05&page=0&size=50
    - `from`·`to` 는 날짜(yyyy-MM-dd)이고 둘 다 그날을 포함한다. `size` 기본 50, 최대 100. 최신순.

**Response**

```json
{ "items": [ { "adminLogId": 77, "adminNickname": "김운영", "action": "TURN_ADVANCE", "targetType": "SEASON", "targetId": 7, "detail": { "from": 4, "to": 5 }, "createdAt": "2026-11-05T00:00:03" } ] }
```

### 관리자 · 모의투자 운영

#### 114. 시즌 목록

| 항목 | 내용 |
| --- | --- |
| Endpoint | `/api/v1/admin/trading/seasons` |
| Method | `GET` |
| 권한 | 관리자 |
| 관련 | ADM-09~10 |
| 설명 | 모든 시즌. |

**Request**

- 없음

**Response**

```json
{ "items": [ { "seasonId": 7, "name": "시즌 7", "startDate": "2026-11-02", "status": "RUNNING", "currentTurn": 5, "participants": 1240 } ] }
```

#### 115. 시즌 생성

| 항목 | 내용 |
| --- | --- |
| Endpoint | `/api/v1/admin/trading/seasons` |
| Method | `POST` |
| 권한 | 관리자 |
| 관련 | ADM-09, ADM-12, SP-50 |
| 설명 | 이름과 시작일(월요일)로 14일·12턴 달력(일요일 제외)을 자동 생성. 기본 설정은 이월 100%·상한 없음·해금 Lv.8(SP-63). copyFromSeasonId를 주면 그 시즌의 이월 비율·상한·해금 레벨을 그대로 가져온다(분야·종목은 시즌과 상관없이 공통이라 복사할 것이 없다). 없는 시즌이면 `NOT_FOUND`. 만든 관리자(`created_by`)는 로그인한 관리자 번호이고, 로그인 정보가 없으면 401 `UNAUTHORIZED` (#40) |

**Request**

```json
{ "name": "시즌 8", "startDate": "2026-11-16", "copyFromSeasonId": 7 }
```

**Response**

```json
201 Created
{ "seasonId": 8, "turns": [ { "turnNo": 1, "tradeDate": "2026-11-16" } ] }
```

**Error**: `START_DATE_NOT_MONDAY`, `NOT_FOUND`

#### 116. 시즌 설정

| 항목 | 내용 |
| --- | --- |
| Endpoint | `/api/v1/admin/trading/seasons/{seasonId}/settings` |
| Method | `PATCH` |
| 권한 | 관리자 |
| 관련 | ADM-11 |
| 설명 | 이월 비율(0~100%, 소수 둘째 자리까지)·상한(없으면 null), 참가 해금 레벨(1~20). 정산 중·종료된 시즌은 바꿀 수 없다. |

**Request**

```json
{ "carryoverRate": 100.0, "carryoverCap": null, "unlockLevel": 8 }
```

**Response**

- 204 No Content

**Error**: `SEASON_CLOSED`, `NOT_FOUND`

#### 117. 시즌 상태 변경

| 항목 | 내용 |
| --- | --- |
| Endpoint | `/api/v1/admin/trading/seasons/{seasonId}/status` |
| Method | `PATCH` |
| 권한 | 관리자 |
| 관련 | ADM-10, SP-65 |
| 설명 | READY → RUNNING ⇄ HOLIDAY → SETTLING → ENDED 순서로만(SP-65). 휴장(HOLIDAY) 뒤에는 다시 RUNNING으로 돌아오고, 정산(SETTLING)은 RUNNING·HOLIDAY에서 시작한다. 그 밖의 변경과 거꾸로 가는 변경은 거절. |

**Request**

```json
{ "status": "RUNNING" }
```

**Response**

- 204 No Content

**Error**: `INVALID_STATUS_TRANSITION`

#### 118. 턴 달력·진행 기록

| 항목 | 내용 |
| --- | --- |
| Endpoint | `/api/v1/admin/trading/seasons/{seasonId}/turns` |
| Method | `GET` |
| 권한 | 관리자 |
| 관련 | ADM-13, ADM-15 |
| 설명 | 지난 턴/오늘/남은 턴/휴장일, 누가 언제 넘겼는지. |

**Request**

- 없음

**Response**

```json
{ "turns": [ { "turnNo": 4, "tradeDate": "2026-11-05", "state": "DONE", "progressedAt": "2026-11-05T00:00:03", "progressedBy": "김운영" } ] }
```

#### 119. 다음 턴 점검

| 항목 | 내용 |
| --- | --- |
| Endpoint | `/api/v1/admin/trading/seasons/{seasonId}/turns/next-check` |
| Method | `GET` |
| 권한 | 관리자 |
| 관련 | ADM-14 |
| 설명 | 다음 턴의 30개 종목 가격과 뉴스가 모두 있는지. |

**Request**

- 없음

**Response**

```json
{ "ready": false, "nextTurn": 6, "missingPrices": ["모아모빌리티", "별숲엔터"], "newsCount": 2, "isHoliday": false }
```

#### 120. 다음 턴 진행

| 항목 | 내용 |
| --- | --- |
| Endpoint | `/api/v1/admin/trading/seasons/{seasonId}/turns/next` |
| Method | `POST` |
| 권한 | 관리자 |
| 관련 | ADM-14~15, SP-59, DP-43 |
| 설명 | 되돌릴 수 없음. 점검 실패·휴장일이면 거절. admin_log 기록. |

**Request**

```json
{ "expectedCurrentTurn": 5 }
```

**Response**

```json
{ "currentTurn": 6 }
```

**Error**: `NEXT_TURN_NOT_READY`, `MARKET_HOLIDAY`, `TURN_CONFLICT`

#### 121. 분야 목록

| 항목 | 내용 |
| --- | --- |
| Endpoint | `/api/v1/admin/trading/sectors` |
| Method | `GET` |
| 권한 | 관리자 |
| 관련 | ADM-16 |
| 설명 | 15개 분야. |

**Request**

- 없음

**Response**

```json
{ "items": [ { "sectorId": 1, "name": "반도체", "sortOrder": 1, "isActive": true } ] }
```

- 정렬 순서(sortOrder) → 번호 순. 사용 안 함(isActive=false) 분야도 포함한다.

#### 122. 분야 등록

| 항목 | 내용 |
| --- | --- |
| Endpoint | `/api/v1/admin/trading/sectors` |
| Method | `POST` |
| 권한 | 관리자 |
| 관련 | ADM-16 |

**Request**

```json
{ "name": "핀테크", "sortOrder": 15 }
```

**Response**

```json
201 Created
{ "sectorId": 15 }
```

**Error**: `SECTOR_DUPLICATED`

#### 123. 분야 수정

| 항목 | 내용 |
| --- | --- |
| Endpoint | `/api/v1/admin/trading/sectors/{sectorId}` |
| Method | `PUT` |
| 권한 | 관리자 |
| 관련 | ADM-16 |

**Request**

```json
{ "name": "핀테크", "isActive": true, "sortOrder": 15 }
```

**Response**

- 204 No Content

- isActive·sortOrder는 줄 때만 바꾼다.

**Error**: `SECTOR_DUPLICATED`, `NOT_FOUND`

#### 124. 종목 목록

| 항목 | 내용 |
| --- | --- |
| Endpoint | `/api/v1/admin/trading/stocks` |
| Method | `GET` |
| 권한 | 관리자 |
| 관련 | ADM-17 |
| 설명 | 30개 종목. |

**Request**

- 없음

**Response**

```json
{ "items": [ { "stockId": 1, "sectorId": 1, "sectorName": "반도체", "name": "한빛반도체", "startPrice": 76800, "intro": "...", "business": "...", "revenue": "1조 2,000억", "operatingProfit": "1,450억", "isActive": true } ] }
```

- 분야 정렬 순서 → 종목 번호 순.

#### 125. 종목 등록

| 항목 | 내용 |
| --- | --- |
| Endpoint | `/api/v1/admin/trading/stocks` |
| Method | `POST` |
| 권한 | 관리자 |
| 관련 | ADM-17~18 |
| 설명 | 이름, 분야, 시작 가격, 기업정보. 회사 소개(`intro`)는 16,000자 이하(DB `TEXT` 65,535바이트에 들어가게). |

**Request**

```json
{ "sectorId": 1, "name": "한빛반도체", "startPrice": 76800, "intro": "...", "business": "...", "revenue": "1조 2,000억", "operatingProfit": "1,450억" }
```

**Response**

```json
201 Created
{ "stockId": 1 }
```

**Error**: `STOCK_DUPLICATED`, `STOCK_LIMIT_EXCEEDED`(그 분야에 사용 중 종목이 이미 2개), `NOT_FOUND`(없는 분야)

#### 126. 종목·기업정보 수정

| 항목 | 내용 |
| --- | --- |
| Endpoint | `/api/v1/admin/trading/stocks/{stockId}` |
| Method | `PUT` |
| 권한 | 관리자 |
| 관련 | ADM-17~18 |

**Request**

- (종목 등록과 같음)

**Response**

- 204 No Content

**Error**: `STOCK_DUPLICATED`, `STOCK_LIMIT_EXCEEDED`(사용 중 종목을 이미 2개인 분야로 옮길 때), `NOT_FOUND`

#### 127. 턴별 가격표

| 항목 | 내용 |
| --- | --- |
| Endpoint | `/api/v1/admin/trading/seasons/{seasonId}/prices` |
| Method | `GET` |
| 권한 | 관리자 |
| 관련 | ADM-19, ADM-22 |
| 설명 | 종목(행) × 1~12턴(열). 비어 있는 칸은 null. currentTurn 이하의 턴은 잠겨 있다(수정 불가). 종목 순서는 관리자 종목 목록(API 124)과 같다. |

**Request**

- 없음

**Response**

```json
{ "currentTurn": 5, "rows": [ { "stockId": 1, "stockName": "한빛반도체", "prices": [76800, 78900, 80100, 82200, 84500, null, null, null, null, null, null, null] } ] }
```

#### 128. 턴별 가격 저장

| 항목 | 내용 |
| --- | --- |
| Endpoint | `/api/v1/admin/trading/seasons/{seasonId}/prices` |
| Method | `PUT` |
| 권한 | 관리자 |
| 관련 | ADM-19~20, ADM-23~24, SP-60 |
| 설명 | 가격 또는 변동률(지난 턴 대비 %)로 입력. 변동률은 지난 턴 가격(없으면 시작 가격) × (1 + 변동률)을 50원 단위로 반올림해 가격으로 바꾼다. 현재 턴 이하(이미 지난 턴)는 수정 불가. 지난 턴보다 ±30%를 넘게 움직이면 warnings로 알려 주고, confirm=false면 저장하지 않고 `saved: 0`, confirm=true면 저장한다. 0원 이하는 DB가 받지 않으므로 경고가 아니라 거절. 저장할 때 변동률(change_rate)도 다시 계산한다. |

**Request**

```json
{ "confirm": false, "cells": [ { "stockId": 1, "turnNo": 6, "price": 86000 }, { "stockId": 2, "turnNo": 6, "changeRate": -3.0 } ] }
```

**Response**

```json
{ "saved": 2, "warnings": [ { "stockId": 2, "turnNo": 6, "message": "한 턴에 ±30%를 넘는 변동이에요" } ] }
```

**Error**: `PAST_TURN_LOCKED`, `PRICE_NOT_POSITIVE`, `PRICE_OUT_OF_RANGE`(변동률로 계산한 가격이 너무 큼), `NOT_FOUND`

#### 129. 가격표 CSV 내려받기

| 항목 | 내용 |
| --- | --- |
| Endpoint | `/api/v1/admin/trading/seasons/{seasonId}/prices/export` |
| Method | `GET` |
| 권한 | 관리자 |
| 관련 | ADM-21 |
| 설명 | text/csv(UTF-8, 엑셀용 BOM). 제목 줄 `stockId,stockName,turn1,…,turn12`, 빈 칸은 가격 없음. 종목 이름에 쉼표·큰따옴표·줄바꿈이 있으면 큰따옴표로 감싸고(안쪽 `"`는 `""`, RFC 4180), `=` `+` `-` `@`로 시작하면 엑셀 수식으로 읽히지 않게 앞에 `'`를 붙인다. |

**Request**

- 없음

**Response**

- text/csv 파일

#### 130. 가격표 CSV 올리기

| 항목 | 내용 |
| --- | --- |
| Endpoint | `/api/v1/admin/trading/seasons/{seasonId}/prices/import` |
| Method | `POST` |
| 권한 | 관리자 |
| 관련 | ADM-21 |
| 설명 | 내려받은 CSV와 같은 형식(큰따옴표로 감싼 칸은 RFC 4180 규칙으로 읽는다). 빈 칸은 그대로 두고 값이 있는 칸만 저장한다. 지난 턴 칸은 기존 값과 같으면 통과, 다르면 오류. 형식 오류(제목 줄, 칸 수, 없는 종목, 같은 종목 중복, 1 이상의 정수가 아닌 값)가 하나라도 있으면 전체 취소하고 오류 행을 돌려준다. ±30% 경고는 저장한 뒤 warnings로 알려 준다. |

**Request**

- Form: file=prices.csv

**Response**

```json
{ "saved": 360, "warnings": [] }
```

오류(400)

```json
{ "success": false, "data": { "errors": [ { "line": 3, "message": "없는 종목 번호예요: 99" } ] }, "error": { "code": "CSV_INVALID", "message": "CSV 형식이 맞지 않는 행이 있어요" } }
```

**Error**: `CSV_INVALID`

#### 131. 뉴스 목록

| 항목 | 내용 |
| --- | --- |
| Endpoint | `/api/v1/admin/trading/seasons/{seasonId}/news` |
| Method | `GET` |
| 권한 | 관리자 |
| 관련 | ADM-25~26 |
| 설명 | 턴별 뉴스(관리자 메모 포함). `turnNo`를 빼면 시즌 전체를 턴 순서로 (#43) |

**Request**

- Query: turnNo=6

**Response**

```json
{ "items": [ { "newsId": 31, "turnNo": 6, "title": "...", "content": "...", "adminMemo": "다음 턴 상승 유도",
  "sectors": [ { "id": 1, "name": "반도체" } ], "stocks": [ { "id": 1, "name": "한빛반도체" } ] } ] }
```

**Error**: `NOT_FOUND`(없는 시즌)

#### 132. 뉴스 등록

| 항목 | 내용 |
| --- | --- |
| Endpoint | `/api/v1/admin/trading/seasons/{seasonId}/news` |
| Method | `POST` |
| 권한 | 관리자 |
| 관련 | ADM-25~26 |
| 설명 | 제목(100자), 본문(16,000자), 관련 분야·종목(없으면 빈 목록), 관리자 메모(500자). 다음 턴 이후(`turnNo` > 현재 턴)에만 등록할 수 있다 (#43) |

**Request**

```json
{ "turnNo": 6, "title": "...", "content": "...", "sectorIds": [1], "stockIds": [1], "adminMemo": "다음 턴 상승 유도" }
```

**Response**

```json
201 Created
{ "newsId": 40 }
```

**Error**: `PAST_TURN_NEWS_LOCKED`(현재 턴 이하), `NOT_FOUND`(없는 시즌·분야·종목)

#### 133. 뉴스 수정

| 항목 | 내용 |
| --- | --- |
| Endpoint | `/api/v1/admin/trading/news/{newsId}` |
| Method | `PUT` |
| 권한 | 관리자 |
| 관련 | ADM-27, SP-60 |
| 설명 | 지난 턴(현재 턴 이하) 뉴스는 오타 수정만: 제목·본문·관리자 메모만 바꿀 수 있고, `turnNo`나 관련 분야·종목을 바꾸면 `PAST_TURN_NEWS_LOCKED`. 다음 턴 이후 뉴스는 턴·관련 분야·종목까지 바꿀 수 있지만 현재 턴 이하로 옮길 수는 없다. 지난 턴 뉴스 수정의 admin_log 기록은 로그인 관리자 번호(#6)가 생긴 뒤 연결 (#43) |

**Request**

- (뉴스 등록과 같음)

**Response**

- 204 No Content

**Error**: `PAST_TURN_NEWS_LOCKED`, `NOT_FOUND`

#### 134. 뉴스 삭제

| 항목 | 내용 |
| --- | --- |
| Endpoint | `/api/v1/admin/trading/news/{newsId}` |
| Method | `DELETE` |
| 권한 | 관리자 |
| 관련 | ADM-25 |
| 설명 | 다음 턴 이후 뉴스만. 관련 분야·종목도 함께 지운다 (#43) |

**Request**

- Path: newsId=40

**Response**

- 204 No Content

**Error**: `PAST_TURN_NEWS_LOCKED`, `NOT_FOUND`

#### 135. 참가자 목록

| 항목 | 내용 |
| --- | --- |
| Endpoint | `/api/v1/admin/trading/seasons/{seasonId}/participants` |
| Method | `GET` |
| 권한 | 관리자 |
| 관련 | ADM-28 |
| 설명 | 닉네임, 레벨, 투자 가능 금액, 현재 순자산, 수익률. |

**Request**

- Query: sort=netAsset&page=0&size=50

**Response**

```json
{ "items": [ { "participantId": 120, "nickname": "민지", "level": 8, "investAmount": 4820000, "netAsset": 5134000, "returnRate": 6.5 } ] }
```

#### 136. 참가자 상세

| 항목 | 내용 |
| --- | --- |
| Endpoint | `/api/v1/admin/trading/participants/{participantId}` |
| Method | `GET` |
| 권한 | 관리자 |
| 관련 | ADM-28, DP-04 |
| 설명 | 보유 종목, 거래 내역, 매매 이유. |

**Request**

- 없음

**Response**

- (holdings[], orders[])

#### 137. 랭킹 미리보기

| 항목 | 내용 |
| --- | --- |
| Endpoint | `/api/v1/admin/trading/seasons/{seasonId}/rankings/preview` |
| Method | `GET` |
| 권한 | 관리자 |
| 관련 | ADM-31 |
| 설명 | 시즌 중에도 관리자만. |

**Request**

- Query: type=ASSET

**Response**

- (랭킹 응답과 같음)

#### 138. 정산 실행

| 항목 | 내용 |
| --- | --- |
| Endpoint | `/api/v1/admin/trading/seasons/{seasonId}/settle` |
| Method | `POST` |
| 권한 | 관리자 |
| 관련 | ADM-32, SP-61, SP-63, DP-40, DP-43 |
| 설명 | 최종 순자산·수익률·순위·이월금을 한 트랜잭션으로 확정. |

**Request**

- 없음

**Response**

```json
{ "participants": 1240, "settledAt": "2026-11-15T00:10:00" }
```

**Error**: `INVALID_STATUS_TRANSITION`

#### 139. 랭킹 공개

| 항목 | 내용 |
| --- | --- |
| Endpoint | `/api/v1/admin/trading/seasons/{seasonId}/rankings/publish` |
| Method | `POST` |
| 권한 | 관리자 |
| 관련 | ADM-33, DP-43 |
| 설명 | 정산 후에만. |

**Request**

- 없음

**Response**

```json
{ "publishedAt": "2026-11-15T09:00:00" }
```

**Error**: `NOT_SETTLED`

#### 140. 랭킹 보상 지급

| 항목 | 내용 |
| --- | --- |
| Endpoint | `/api/v1/admin/trading/seasons/{seasonId}/rewards` |
| Method | `POST` |
| 권한 | 관리자 |
| 관련 | ADM-34, SP-64 |
| 설명 | 순자산·수익률 상위 3위에게 뱃지·칭호 장식, 수상 내역 기록. |

**Request**

- 없음

**Response**

```json
{ "awarded": 6 }
```

#### 141. 시즌 통계

| 항목 | 내용 |
| --- | --- |
| Endpoint | `/api/v1/admin/trading/seasons/{seasonId}/statistics` |
| Method | `GET` |
| 권한 | 관리자 |
| 관련 | ADM-35 |
| 설명 | 평균 수익률, 많이 산 종목, 매매 이유 비율, 급락 턴 매도 비율. |

**Request**

- 없음

**Response**

```json
{ "avgReturnRate": 9.2, "topBought": ["한빛반도체"], "reasonRatio": { "뉴스를 보고": 41.2 }, "panicSellRate": 18.5 }
```

### 관리자 · 모의 청약 운영

#### 142. 단지 목록

| 항목 | 내용 |
| --- | --- |
| Endpoint | `/api/v1/admin/housing/complexes` |
| Method | `GET` |
| 권한 | 관리자 |
| 관련 | ADM-36 |
| 설명 | 삭제되지 않은 단지와 일정, 오늘 기준 상태, 신청자 수. 신청 시작일 최신순. |

**Request**

- 없음

**Response**

```json
{ "items": [ { "complexId": 1, "name": "그린숲 행복주택", "location": "가상시 덕양구", "areaType": "59㎡", "salePrice": 320000000, "supplyUnits": 120, "supplyType": "일반공급", "noticeDate": "2026-10-28", "applyStartDate": "2026-11-03", "applyEndDate": "2026-11-10", "announceDate": "2026-11-17", "status": "OPEN", "applicants": 3 } ] }
```

- `status`: `UPCOMING`(접수 전) · `OPEN`(접수 중) · `CLOSED`(마감, 발표 전) · `ANNOUNCED`(발표)
- `applicants`가 1 이상이면 일정·공급 세대 수 수정(144)과 삭제(145)가 막힌다

#### 143. 공고 등록

| 항목 | 내용 |
| --- | --- |
| Endpoint | `/api/v1/admin/housing/complexes` |
| Method | `POST` |
| 권한 | 관리자 |
| 관련 | ADM-36 |
| 설명 | 가상 단지와 공고·신청·발표 일정. |

**Request**

```json
{ "name": "그린숲 행복주택", "location": "가상시 덕양구", "areaType": "59㎡", "salePrice": 320000000, "supplyUnits": 120, "supplyType": "일반공급", "noticeDate": "2026-10-28", "applyStartDate": "2026-11-03", "applyEndDate": "2026-11-10", "announceDate": "2026-11-17" }
```

**Response**

```json
201 Created
{ "complexId": 1 }
```

- 글자 칸은 비울 수 없고 `salePrice`·`supplyUnits`는 1 이상, 날짜 4개는 필수
- 일정 순서: `noticeDate` ≤ `applyStartDate` ≤ `applyEndDate` ≤ `announceDate`
- 등록한 관리자 번호(`created_by`)는 로그인 세션에서 정한다

**Error**: `INVALID_COMPLEX_INPUT`(400), `INVALID_SCHEDULE`(400)

#### 144. 공고 수정

| 항목 | 내용 |
| --- | --- |
| Endpoint | `/api/v1/admin/housing/complexes/{complexId}` |
| Method | `PUT` |
| 권한 | 관리자 |
| 관련 | ADM-36, SP-76 |
| 설명 | 신청자가 있으면 일정·공급 세대 수 수정 불가. 이름·위치·면적·분양가·공급 유형은 신청자가 있어도 수정 가능. |

**Request**

- (공고 등록과 같음, 입력 규칙도 같음)

**Response**

- 204 No Content

**Error**: `INVALID_COMPLEX_INPUT`(400), `INVALID_SCHEDULE`(400), `NOT_FOUND`(404, 없거나 삭제된 단지), `COMPLEX_HAS_APPLICANTS`(409), `ALREADY_COMPUTED`(409, 산정한 공고는 신청자가 없어도 일정·세대 수 변경 불가 — 신청이 다시 열리지 않게, #48)

#### 145. 공고 삭제

| 항목 | 내용 |
| --- | --- |
| Endpoint | `/api/v1/admin/housing/complexes/{complexId}` |
| Method | `DELETE` |
| 권한 | 관리자 |
| 관련 | ADM-36 |
| 설명 | 신청자가 없을 때만(Soft Delete). |

**Request**

- 없음

**Response**

- 204 No Content

**Error**: `NOT_FOUND`(404), `COMPLEX_HAS_APPLICANTS`(409)

#### 146. 신청자 목록

| 항목 | 내용 |
| --- | --- |
| Endpoint | `/api/v1/admin/housing/complexes/{complexId}/applications` |
| Method | `GET` |
| 권한 | 관리자 |
| 관련 | ADM-37 |
| 설명 | 단지 신청자와 신청 시점 가점·납입회차, 산정 결과. 산정 전에는 가점 높은 순 → 납입회차 많은 순 → 먼저 신청한 순, 산정 뒤에는 산정 순위 순. |

**Request**

- 없음 (정렬은 위 순서로 고정)

**Response**

```json
{ "items": [ { "applicationId": 40, "nickname": "민지", "receiptNo": "MB-261105-0001", "totalScore": 24, "paidCount": 14, "appliedAt": "2026-11-05T09:00:00", "result": null, "resultRank": null } ] }
```

- `paidCount`: 신청 시점 납입회차(`paid_count_snapshot`)
- `result`: `WIN` · `LOSE`, 산정 전 `null` / `resultRank`: 산정 순위(1부터, 낙첨자 포함), 산정 전 `null`

**Error**: `NOT_FOUND`(404, 없거나 삭제된 단지)

#### 147. 당첨자 산정

| 항목 | 내용 |
| --- | --- |
| Endpoint | `/api/v1/admin/housing/complexes/{complexId}/compute` |
| Method | `POST` |
| 권한 | 관리자 |
| 관련 | ADM-38, SP-75 |
| 설명 | 신청 마감 다음 날부터 한 번만. 가점 → 납입회차 → 추첨 순으로 순위를 매겨 공급 세대 수만큼 `WIN`, 나머지 `LOSE`. 산정만으로는 회원에게 결과가 보이지 않는다(발표 148 뒤에 공개). |

**Request**

- 없음

**Response**

```json
{ "applicants": 4, "winners": 2, "computedAt": "2026-11-11T09:00:00" }
```

- 신청자가 공급 세대 수보다 적으면 모두 당첨(`winners` = `applicants`). 신청자가 0명이어도 산정 완료로 기록한다(`computed_at`)

**Error**: `APPLY_NOT_CLOSED`(409, 신청 마감일까지), `ALREADY_COMPUTED`(409), `NOT_FOUND`(404)

#### 148. 결과 발표

| 항목 | 내용 |
| --- | --- |
| Endpoint | `/api/v1/admin/housing/complexes/{complexId}/announce` |
| Method | `POST` |
| 권한 | 관리자 |
| 관련 | ADM-39 |
| 설명 | 산정한 뒤 발표일부터 한 번만. 단지 상태가 `ANNOUNCED` 가 되어 회원에게 결과가 보이고(API 71), 신청자 모두에게 알림(`HOUSING_RESULT`)을 보낸다. |

**Request**

- 없음

**Response**

```json
{ "announcedAt": "2026-11-17T10:00:00", "notified": 4 }
```

- `notified`: 알림을 보낸 신청자 수

**Error**: `NOT_COMPUTED`(409), `BEFORE_ANNOUNCE_DATE`(409), `ALREADY_ANNOUNCED`(409), `NOT_FOUND`(404)

---

## 4. 에러 코드

사용자 문구는 정책 정의서 3-2와 같습니다.

| 구분 | code | HTTP | message |
| --- | --- | --- | --- |
| 공통 | `UNAUTHORIZED` | 401 | 로그인이 필요해요 |
| 공통 | `SESSION_EXPIRED` | 401 | 로그인 시간이 지났어요. 다시 로그인해 주세요 |
| 공통 | `FORBIDDEN` | 403 | 이 페이지를 볼 수 있는 권한이 없어요 |
| 공통 | `LEVEL_LOCKED` | 403 | Lv.8부터 열려요. 지금은 Lv.5예요 |
| 공통 | `NOT_FOUND` | 404 | 찾을 수 없는 페이지예요 |
| 공통 | `DUPLICATE_REQUEST` | 409 | 이미 처리된 요청이에요 (같은 `Idempotency-Key`의 처음 요청이 아직 처리 중일 때. 처리가 끝났으면 이 에러 대신 처음 결과를 그대로 반환) |
| 공통 | `IDEMPOTENCY_KEY_CONFLICT` | 409 | 같은 요청 번호로 내용이 다른 요청을 보냈어요. 새로 시도해 주세요 |
| 공통 | `IDEMPOTENCY_KEY_TOO_LONG` | 400 | 요청 번호(Idempotency-Key)가 너무 길어요. 100자 이하로 보내 주세요 |
| 공통 | `FORBIDDEN_WORD` | 400 | 사용할 수 없는 표현이 들어 있어요 |
| 공통 | `INVALID_INPUT` | 400 | 입력한 값을 다시 확인해 주세요 (입력칸별 안내는 `error.fields`) |
| 공통 | `INTERNAL_ERROR` | 500 | 처리하지 못했어요. 내 정보는 바뀌지 않았어요 |
| 회원 | `LOGIN_FAILED` | 401 | 이메일 또는 비밀번호를 확인해 주세요 |
| 회원 | `LOGIN_LOCKED` | 423 | 로그인 시도가 너무 많아요. 잠시 뒤에 다시 시도해 주세요 |
| 회원 | `ACCOUNT_SUSPENDED` | 403 | 이용이 제한된 계정이에요 |
| 회원 | `ACCOUNT_WITHDRAWN` | 403 | 탈퇴한 계정이에요 |
| 회원 | `EMAIL_DUPLICATED` | 409 | 이미 가입된 이메일이에요 |
| 회원 | `NICKNAME_DUPLICATED` | 409 | 이미 사용 중인 닉네임이에요 |
| 회원 | `PASSWORD_MISMATCH` | 400 | 비밀번호가 서로 달라요 |
| 회원 | `REQUIRED_TERMS_NOT_AGREED` | 400 | 필수 약관에 동의해 주세요 |
| 회원 | `AGE_UNDER_14` | 400 | 만 14세 이상부터 가입할 수 있어요 |
| 회원 | `IDENTITY_NOT_VERIFIED` | 400 | 본인인증을 먼저 해 주세요 |
| 회원 | `IDENTITY_CODE_INVALID` | 400 | 인증번호가 맞지 않아요. 다시 확인해 주세요 |
| 회원 | `IDENTITY_CODE_EXPIRED` | 400 | 인증번호가 만료되었거나 너무 많이 틀렸어요. 인증번호를 다시 받아 주세요 |
| 회원 | `IDENTITY_CODE_TOO_SOON` | 429 | 인증번호는 1분에 한 번만 받을 수 있어요. 잠시 뒤에 다시 시도해 주세요 |
| 회원 | `IDENTITY_REQUEST_LIMITED` | 429 | 요청이 너무 많아요. 잠시 뒤에 다시 시도해 주세요 |
| 회원 | `IDENTITY_MAIL_UNAVAILABLE` | 503 | 지금은 인증 메일을 보낼 수 없어요. 잠시 뒤에 다시 시도해 주세요 |
| 회원 | `OAUTH_FAILED` | 502 | 간편 로그인에 실패했어요. 다시 시도하거나 이메일로 로그인해 주세요 |
| 회원 | `OAUTH_PROVIDER_UNAVAILABLE` | 503 | 지금은 이 간편 로그인을 쓸 수 없어요. 이메일로 로그인해 주세요 |
| 회원 | `OAUTH_SESSION_EXPIRED` | 400 | 간편 로그인 정보가 만료됐어요. 처음부터 다시 시도해 주세요 |
| 학습 | `COURSE_NOT_PUBLIC` | 404 | 공개되지 않은 강의예요 |
| 학습 | `QUIZ_LOCKED` | 403 | 영상을 모두 본 뒤에 퀴즈를 풀 수 있어요 |
| 학습 | `ALREADY_SCRAPPED` | 409 | 이미 스크랩한 강의예요 |
| 학습 | `INQUIRY_TOO_LONG` | 400 | 내용은 1,000자까지 쓸 수 있어요 |
| 학습 | `INQUIRY_DAILY_LIMIT` | 429 | 오늘은 문의를 더 남길 수 없어요 |
| 모의투자 | `NO_RUNNING_SEASON` | 404 | 다음 시즌을 준비 중이에요 |
| 모의투자 | `ALREADY_JOINED` | 409 | 이미 이번 시즌에 참가했어요 |
| 모의투자 | `JOIN_CLOSED` | 409 | 이번 시즌 참가는 마감됐어요 |
| 모의투자 | `NOT_ENOUGH_CASH` | 400 | 현금이 부족해요 |
| 모의투자 | `NOT_ENOUGH_QUANTITY` | 400 | 보유한 수량보다 많이 팔 수 없어요 |
| 모의투자 | `REASON_REQUIRED` | 400 | 매매 이유를 골라 주세요 |
| 모의투자 | `STALE_TURN_PRICE` | 409 | 가격이 바뀌었어요. 현재 가격을 확인해 주세요 |
| 모의투자 | `MARKET_HOLIDAY` | 409 | 오늘은 휴장일이에요. 다음 거래일에 다시 거래할 수 있어요 |
| 모의투자 | `SEASON_CLOSED` | 409 | 시즌이 끝났어요. 결과를 기다려 주세요 |
| 모의투자 | `RANKING_NOT_PUBLISHED` | 403 | 랭킹은 시즌이 끝나면 공개돼요 |
| 강사·관리자 | `SECTOR_DUPLICATED` | 409 | 이미 있는 분야 이름이에요 |
| 강사·관리자 | `STOCK_DUPLICATED` | 409 | 이미 있는 종목 이름이에요 |
| 강사·관리자 | `STOCK_LIMIT_EXCEEDED` | 409 | 한 분야에는 종목을 2개까지 둘 수 있어요 |
| 강사·관리자 | `START_DATE_NOT_MONDAY` | 400 | 시즌 시작일은 월요일이어야 해요 |
| 강사·관리자 | `INVALID_STATUS_TRANSITION` | 409 | 지금 상태에서는 그 상태로 바꿀 수 없어요 |
| 모의 청약 | `NOT_IN_APPLY_PERIOD` | 409 | 신청 기간은 ○월 ○일 ~ ○월 ○일이에요 |
| 모의 청약 | `ALREADY_APPLIED` | 409 | 신청 완료 |
| 모의 청약 | `RECEIPT_NO_CONFLICT` | 409 | 신청이 몰려 접수하지 못했어요. 다시 신청해 주세요 |
| 모의 청약 | `ALREADY_PAID_TODAY` | 409 | 오늘은 이미 납입했어요 |
| 모의 청약 | `DEPOSIT_BELOW_MIN` | 400 | 최소 20,000 캐시부터 납입할 수 있어요 |
| 모의 청약 | `DEPOSIT_ABOVE_MAX` | 400 | 한 번에 최대 500,000 캐시까지 납입할 수 있어요 |
| 모의 청약 | `NOT_ANNOUNCED_YET` | 403 | 발표는 ○월 ○일이에요 |
| 모의 청약 | `INVALID_SCORE_INPUT` | 400 | 무주택 기간은 0~50년, 부양가족 수는 0~20명으로 입력해 주세요 |
| 모의 청약 | `INVALID_COMPLEX_INPUT` | 400 | 단지 정보를 모두 입력해 주세요. 분양가와 공급 세대 수는 1 이상이에요 |
| 모의 청약 | `INVALID_SCHEDULE` | 400 | 일정 순서가 맞지 않아요. 공고일 ≤ 신청 시작일 ≤ 신청 마감일 ≤ 발표일로 정해 주세요 |
| 모의 청약 | `COMPLEX_HAS_APPLICANTS` | 409 | 신청자가 있는 공고는 일정·공급 세대 수를 바꾸거나 삭제할 수 없어요 |
| 모의 청약 | `APPLY_NOT_CLOSED` | 409 | 신청이 마감된 뒤에 산정할 수 있어요 |
| 모의 청약 | `ALREADY_COMPUTED` | 409 | 이미 산정한 공고예요 |
| 모의 청약 | `NOT_COMPUTED` | 409 | 당첨자를 먼저 산정해 주세요 |
| 모의 청약 | `BEFORE_ANNOUNCE_DATE` | 409 | 발표일부터 발표할 수 있어요 |
| 모의 청약 | `ALREADY_ANNOUNCED` | 409 | 이미 발표한 공고예요 |
| 강사·관리자 | `TOO_MANY_LESSONS` | 400 | 영상은 3개까지 등록할 수 있어요 |
| 강사·관리자 | `QUIZ_COUNT_INVALID` | 400 | 테스트 퀴즈는 5문제, 퀴즈는 5~15문제여야 해요 |
| 강사·관리자 | `COURSE_HAS_LEARNERS` | 409 | 학습 기록이 있어 삭제할 수 없어요. 비공개로 바꿀까요? |
| 강사·관리자 | `NEXT_TURN_NOT_READY` | 409 | 다음 턴에 가격이 비어 있는 종목이 N개 있어요 |
| 강사·관리자 | `PAST_TURN_LOCKED` | 409 | 지난 턴의 가격은 수정할 수 없어요 |
| 강사·관리자 | `PAST_TURN_NEWS_LOCKED` | 409 | 지난 턴 뉴스는 오타 수정만 할 수 있어요 |
| 강사·관리자 | `CSV_INVALID` | 400 | CSV 형식이 맞지 않는 행이 있어요 (오류 행은 data.errors) |
| 강사·관리자 | `PRICE_NOT_POSITIVE` | 400 | 가격은 1원 이상이어야 해요 |
| 강사·관리자 | `PRICE_OUT_OF_RANGE` | 400 | 변동률이 너무 커서 가격을 계산할 수 없어요 |
| 강사·관리자 | `BEFORE_ANNOUNCE_DATE` | 409 | 발표일 이후에 진행할 수 있어요 |
| 강사·관리자 | `REASON_REQUIRED` | 400 | 사유를 입력해 주세요 |

---

## 5. 변경 이력

| 날짜 | 버전 | 내용 | 작성자 |
| --- | --- | --- | --- |
| 2026-11-05 | v0.1 | 요구사항 명세서·정책 정의서·DB 설계 기준 초안 작성 |  |
