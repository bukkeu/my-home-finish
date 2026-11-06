# MoneyBeetle (머니비틀)

2030 사회초년생을 위한 금융 학습 LMS. 영상 강의와 퀴즈로 배우고, 가상 캐시로 모의투자·모의 청약을 연습한다.

> 지금은 폴더 구조만 잡아 둔 상태입니다. 각 파일의 주석에 들어갈 내용과 담당자가 적혀 있습니다.

## 패키지 구조

```
com.wanted.moneybeetle
├─ global                공통 (SCM)
│  ├─ config             Application, ContextConfig, JpaConfig, WebConfig
│  ├─ common             ApiResponse, PageResponse, BaseTimeEntity
│  ├─ exception          ErrorCode, CommonErrorCode, BusinessException, GlobalExceptionHandler
│  ├─ security           레벨 해금 확인, 중복 요청 방지
│  └─ file               파일 업로드
└─ domain
   ├─ auth               로그인·인증              SCM
   ├─ member             회원·강사·약관            SCM
   ├─ inquiry            문의                      SCM
   ├─ operation          설정·관리자 기록·금칙어    SCM
   ├─ course             강의·영상·스크랩           DBA ①
   ├─ quiz               퀴즈·오답노트              DBA ①
   ├─ growth             출석·캐시·경험치·레벨       PM
   ├─ home               홈·통합 검색               PM
   ├─ term               오늘의 경제 용어           PM
   ├─ notification       알림                      PM
   ├─ housing            모의 청약                  PM
   └─ trading            모의투자                   DBA ②
```

도메인 안은 모두 같은 모양입니다: `controller` / `model/dao` / `model/dto` / `model/entity` / `model/service` / `exception`

## 충돌 방지 규칙

1. 자기 도메인 패키지 밖의 파일은 고치지 않는다. 필요하면 담당자에게 요청한다.
2. 다른 도메인의 테이블(Repository)을 직접 쓰지 않는다. 그 도메인의 Service 메서드를 부른다.
   - 캐시·경험치 지급/차감 : `growth.RewardService` / 알림 : `notification.NotificationService` / 관리자 기록 : `operation.AdminLogService`
3. 에러 코드는 도메인별 enum(`XxxErrorCode`)에 추가한다. `CommonErrorCode`는 SCM만 고친다.
4. SQL은 `resources/sql`의 번호 파일로 나누고 DBA가 관리한다. 테이블 변경은 DBA에게 요청한다.
5. 마이페이지·관리자 대시보드는 틀(SCM)과 탭 내용(`templates/fragments/...`, 각 담당)을 나눈다.

## 문서

- DB 설계 : `MoneyBeetle_DB설계.md`, `moneybeetle.sql`
- API 명세서 : `MoneyBeetle_API명세서.md`
