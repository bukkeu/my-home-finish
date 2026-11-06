# Money Beetle 페이지·라우트 대응표

현재 프로젝트는 `.dc.html` 파일명으로 페이지를 직접 연결합니다. 아래 "향후 URL"은 **실제 앱 구현용 논리 경로**이며, 현재 동작하는 라우팅이 아닙니다.

| 역할 | 현재 파일 | 향후 URL | 들어오는 곳 |
|---|---|---|---|
| 공통 | Main.dc.html | / | 첫 화면 |
| 사용자 | UserLogin.dc.html | /login | Main "로그인"·"무료로 시작하기" |
| 사용자 | UserSignup.dc.html | /signup | UserLogin "회원가입" |
| 사용자 | UserHome-vr1.dc.html | /user/home (수강 이력 없음) | 회원가입 완료, 로그인(이력 없음) |
| 사용자 | UserHome-vr2.dc.html | /user/home (수강 이력 있음) | 로그인(이력 있음), 사이드바 "홈" |
| 사용자 | UserLectureCategory.dc.html | /user/lectures?cat=money\|estate | 사이드바 강의, 홈 카테고리 |
| 사용자 | UserLectureDetail.dc.html | /user/lecture-detail | 강의 카드, 이어서 듣기 |
| 사용자 | UserMyPage.dc.html | /user/my-page | 프로필, 사이드바 내 학습 |
| 사용자 | UserSearch.dc.html | /user/search?q= | 모든 사용자 화면 상단 검색창 |
| 사용자 | UserInquiry.dc.html | /user/inquiry | 사이드바 문의 게시판 |
| 사용자 | UserDemoTrading.dc.html | /user/demo-trading | 사이드바 트레이닝 (Lv.8~) |
| 사용자 | UserDemoHousingSubscription.dc.html | /user/demo-housing-subscription | 사이드바 트레이닝 (Lv.12~) |
| 강사 | InstructorLogin.dc.html | /instructor/login | 주소 직접 입력 (사용자 화면 링크 없음) |
| 강사 | InstructorSignup.dc.html | /instructor/signup | InstructorLogin "강사 회원가입" |
| 강사 | InstructorDashboard.dc.html | /instructor/dashboard | 강사 로그인 성공 |
| 강사 | InstructorCourseCreate.dc.html | /instructor/course-create | 강사 대시보드 "강의 생성" |
| 관리자 | AdminLogin.dc.html | /admin/login | 주소 직접 입력 |
| 관리자 | AdminDashboard.dc.html | /admin/dashboard (?view=…) | 관리자 로그인 성공 |
| 관리자 | AdminTradingManagement.dc.html | /admin/trading-management | 관리자 메뉴 트레이닝 운영 |
| 관리자 | AdminHousingManagement.dc.html | /admin/housing-management | 관리자 메뉴 트레이닝 운영 |

## 주요 이동 흐름
- 사용자: Main → UserLogin → (이력 없음) UserHome-vr1 / (이력 있음) UserHome-vr2 → UserLectureCategory → UserLectureDetail → UserInquiry
- 사용자 가입: UserLogin → UserSignup → UserHome-vr1
- 강사: InstructorLogin → InstructorDashboard (Q&A 관리는 대시보드 안 탭) → InstructorCourseCreate → InstructorDashboard
- 관리자: AdminLogin → AdminDashboard (?view=board 문의 게시판) → AdminTradingManagement / AdminHousingManagement
- 문의 데이터는 역할 간 공유되지 않음 (화면 이동만 연결, 백엔드 연동 필요)

## 공통 스크립트
- `mb-config.js` — 레벨·칭호·해금·출석·청약 납입 설정값 (팀 확정 시 이 파일만 수정)
- `mb-drawer.js` — 햄버거 Drawer 공통 동작
- `mb-search.js` — 통합 검색 색인·자동완성 (강의 데이터는 UserLectureCategory 와 동일하게 유지)
- `mb-notify.js` — 알림
- `housing/store.js` — 모의 청약 공통 저장소

## 이전 이름
Home→UserHome(-vr2), LectureCategory→UserLectureCategory, LectureDetail→UserLectureDetail, MyPage→UserMyPage, DemoTrading→UserDemoTrading, DemoHousingSubscription→UserDemoHousingSubscription, Instructor→InstructorDashboard, CourseCreate→InstructorCourseCreate, HomeAdmin→AdminDashboard, DemoTrading Admin→AdminTradingManagement, AdminHousing→AdminHousingManagement, LoginInstructor→InstructorLogin, Login→UserLogin, Signup→UserSignup. LoginSelect는 삭제됨.

## 참고
- `DemoTrading-code/`는 참고용 복사본이며 서비스 페이지가 아닙니다.
