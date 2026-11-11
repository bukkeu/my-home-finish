package com.wanted.moneybeetle.domain.home.model.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wanted.moneybeetle.domain.course.model.dto.CompletedCourseDTO;
import com.wanted.moneybeetle.domain.course.model.dto.CourseDetailDTO;
import com.wanted.moneybeetle.domain.housing.model.dto.AccountDTO;
import com.wanted.moneybeetle.domain.housing.model.dto.ComplexDTO;
import com.wanted.moneybeetle.domain.housing.model.service.HousingService;
import com.wanted.moneybeetle.domain.trading.exception.TradingErrorCode;
import com.wanted.moneybeetle.domain.trading.model.dto.TradingHomeDTO;
import com.wanted.moneybeetle.domain.trading.model.service.ParticipationService;
import com.wanted.moneybeetle.global.exception.BusinessException;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import com.wanted.moneybeetle.domain.course.model.dto.CourseListDTO;
import com.wanted.moneybeetle.domain.course.model.dto.LessonPlayDTO;
import com.wanted.moneybeetle.domain.course.model.dto.WatchHistoryDTO;
import com.wanted.moneybeetle.domain.course.model.service.CourseService;
import com.wanted.moneybeetle.domain.growth.model.service.GrowthService;
import com.wanted.moneybeetle.domain.term.model.dto.EconomicTermDTO;
import com.wanted.moneybeetle.domain.term.model.dto.TodayTermsDTO;
import com.wanted.moneybeetle.domain.term.model.service.TermService;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

class HomeServiceTest {

    private static final Long MEMBER = 8L;

    private TermService termService;
    private CourseService courseService;
    private GrowthService growthService;
    private ParticipationService participationService;
    private HousingService housingService;
    private HomeService service;

    @BeforeEach
    void setUp() {
        termService = mock(TermService.class);
        courseService = mock(CourseService.class);
        growthService = mock(GrowthService.class);
        participationService = mock(ParticipationService.class);
        housingService = mock(HousingService.class);
        // 2026-11-10 (서울) 고정 시계: 시즌 종료일까지 남은 일수·"이번 달" 판단이 실행 날짜에 흔들리지 않게
        Clock clock = Clock.fixed(Instant.parse("2026-11-10T03:00:00Z"), ZoneId.of("Asia/Seoul"));
        service = new HomeService(termService, courseService, growthService, null, participationService, housingService, clock);
        // 이어서 볼 강의의 상세 기본값: 영상 목록이 비어 있다 (몇 강째인지는 null). 필요한 테스트가 덮어쓴다
        CourseDetailDTO emptyDetail = mock(CourseDetailDTO.class);
        when(emptyDetail.getLessons()).thenReturn(List.of());
        when(courseService.getCourseDetail(eq(MEMBER), any())).thenReturn(emptyDetail);

        when(termService.getToday()).thenReturn(new TodayTermsDTO(LocalDate.of(2026, 11, 9),
                List.of(new EconomicTermDTO(3L, "복리", "이자에 이자가 붙는 방식"))));
        when(growthService.getLevel(MEMBER)).thenReturn(5);
        when(courseService.findContinueWatching(MEMBER)).thenReturn(List.of());
        givenWatchTotals(0, 0);
    }

    @SuppressWarnings("unchecked")
    private void givenWatchTotals(long watched, long completed) {
        Page<WatchHistoryDTO> watchPage = new PageImpl<>(List.of(), PageRequest.of(0, 1), watched);
        Page<CompletedCourseDTO> completedPage = new PageImpl<>(List.of(), PageRequest.of(0, 1), completed);
        when(courseService.findWatchHistories(eq(MEMBER), any(Pageable.class))).thenReturn(watchPage);
        when(courseService.findCompletedCourses(eq(MEMBER), any(Pageable.class))).thenReturn(completedPage);
    }

    private CourseListDTO course(long id, String title) {
        CourseListDTO course = mock(CourseListDTO.class);
        when(course.getCourseId()).thenReturn(id);
        when(course.getTitle()).thenReturn(title);
        return course;
    }

    private CourseDetailDTO.LessonItem lessonItem(long lessonId) {
        CourseDetailDTO.LessonItem item = mock(CourseDetailDTO.LessonItem.class);
        when(item.getLessonId()).thenReturn(lessonId);
        return item;
    }

    private WatchHistoryDTO watch(long courseId, long lessonId, String lessonTitle) {
        WatchHistoryDTO history = mock(WatchHistoryDTO.class);
        when(history.getCourseId()).thenReturn(courseId);
        when(history.getLessonId()).thenReturn(lessonId);
        when(history.getLessonTitle()).thenReturn(lessonTitle);
        return history;
    }

    @Test
    @DisplayName("오늘의 용어는 TermService 가 준 5개를 그대로 담는다 (같은 날 모든 사용자에게 같은 값)")
    void todayTermsFromTermService() {
        var home = service.getHome(MEMBER);

        assertThat(home.todayTerms()).extracting("economicTermId").containsExactly(3L);
        assertThat(home.todayTerms().get(0).term()).isEqualTo("복리");
    }

    @Test
    @DisplayName("이어서 볼 강의가 없으면 continueCourse 는 null 이다")
    void noContinueCourse() {
        var home = service.getHome(MEMBER);

        assertThat(home.continueCourse()).isNull();
    }

    @Test
    @DisplayName("이어서 볼 강의가 있으면 그 강의에서 가장 최근에 본 영상과 이어보기 위치를 담는다")
    void continueCourseWithLastWatchedLesson() {
        // 목 객체는 when(...) 바깥에서 먼저 만든다 (when 안에서 다른 목에 스텁을 걸면 Mockito 가 꼬인다)
        CourseListDTO continuing = course(7, "청약통장 기초와 가점 계산");
        CourseListDTO other = course(9, "다른 강의");
        when(courseService.findContinueWatching(MEMBER)).thenReturn(List.of(continuing, other));
        WatchHistoryDTO otherCourseWatch = watch(9, 40, "다른 강의 영상");   // 최근에 본 영상이지만 이어서 볼 강의(7)가 아니다
        WatchHistoryDTO lastWatch = watch(7, 21, "가점은 어떻게 계산할까");
        WatchHistoryDTO olderWatch = watch(7, 20, "청약통장이란");
        Page<WatchHistoryDTO> histories = new PageImpl<>(List.of(otherCourseWatch, lastWatch, olderWatch));
        when(courseService.findWatchHistories(eq(MEMBER), eq(PageRequest.of(0, HomeService.WATCH_SCAN_SIZE))))
                .thenReturn(histories);
        LessonPlayDTO play = mock(LessonPlayDTO.class);
        when(play.getPositionSeconds()).thenReturn(312);
        when(play.getDurationSeconds()).thenReturn(870);
        when(courseService.getLessonPlay(MEMBER, 7L, 21L)).thenReturn(play);
        // 이 강의의 영상은 3개이고, 마지막으로 본 영상(21)이 두 번째다
        CourseDetailDTO detail = mock(CourseDetailDTO.class);
        when(detail.getCategoryName()).thenReturn("청약");
        CourseDetailDTO.LessonItem first = lessonItem(20L);
        CourseDetailDTO.LessonItem second = lessonItem(21L);
        CourseDetailDTO.LessonItem third = lessonItem(22L);
        when(detail.getLessons()).thenReturn(List.of(first, second, third));
        when(courseService.getCourseDetail(MEMBER, 7L)).thenReturn(detail);

        var home = service.getHome(MEMBER);

        assertThat(home.continueCourse().durationSeconds()).isEqualTo(870);
        assertThat(home.continueCourse().lessonNumber()).isEqualTo(2);
        assertThat(home.continueCourse().lessonCount()).isEqualTo(3);
        assertThat(home.continueCourse().categoryName()).isEqualTo("청약");
        assertThat(home.continueCourse().courseId()).isEqualTo(7L);
        assertThat(home.continueCourse().title()).isEqualTo("청약통장 기초와 가점 계산");
        assertThat(home.continueCourse().lessonId()).isEqualTo(21L);
        assertThat(home.continueCourse().lessonTitle()).isEqualTo("가점은 어떻게 계산할까");
        assertThat(home.continueCourse().positionSeconds()).isEqualTo(312);
    }

    @Test
    @DisplayName("이어서 볼 강의의 기록이 첫 쪽 밖에 있어도 다음 쪽까지 넘겨 가며 찾는다")
    void findsLastWatchedOnLaterPage() {
        CourseListDTO continuing = course(7, "청약통장 기초와 가점 계산");
        when(courseService.findContinueWatching(MEMBER)).thenReturn(List.of(continuing));
        // 다른 강의 영상만 20개를 본 첫 쪽, 둘째 쪽에 이어서 볼 강의(7)의 기록이 있다
        List<WatchHistoryDTO> firstPageItems = new java.util.ArrayList<>();
        for (int i = 0; i < HomeService.WATCH_SCAN_SIZE; i++) {
            firstPageItems.add(watch(9, 100 + i, "다른 강의 영상 " + i));
        }
        WatchHistoryDTO lastWatch = watch(7, 21, "가점은 어떻게 계산할까");
        when(courseService.findWatchHistories(eq(MEMBER), eq(PageRequest.of(0, HomeService.WATCH_SCAN_SIZE))))
                .thenReturn(new PageImpl<>(firstPageItems, PageRequest.of(0, HomeService.WATCH_SCAN_SIZE), 25));
        when(courseService.findWatchHistories(eq(MEMBER), eq(PageRequest.of(1, HomeService.WATCH_SCAN_SIZE))))
                .thenReturn(new PageImpl<>(List.of(lastWatch), PageRequest.of(1, HomeService.WATCH_SCAN_SIZE), 25));
        LessonPlayDTO play = mock(LessonPlayDTO.class);
        when(play.getPositionSeconds()).thenReturn(312);
        when(courseService.getLessonPlay(MEMBER, 7L, 21L)).thenReturn(play);

        var home = service.getHome(MEMBER);

        assertThat(home.continueCourse().lessonId()).isEqualTo(21L);
        assertThat(home.continueCourse().positionSeconds()).isEqualTo(312);
    }

    @Test
    @DisplayName("마지막 쪽까지 봐도 그 강의 기록이 없으면 영상·위치는 null 이다 (기록을 한없이 넘기지 않는다)")
    void stopsAtLastPageWhenNotFound() {
        CourseListDTO continuing = course(7, "청약통장 기초와 가점 계산");
        when(courseService.findContinueWatching(MEMBER)).thenReturn(List.of(continuing));
        WatchHistoryDTO other = watch(9, 40, "다른 강의 영상");
        when(courseService.findWatchHistories(eq(MEMBER), eq(PageRequest.of(0, HomeService.WATCH_SCAN_SIZE))))
                .thenReturn(new PageImpl<>(List.of(other), PageRequest.of(0, HomeService.WATCH_SCAN_SIZE), 21));
        when(courseService.findWatchHistories(eq(MEMBER), eq(PageRequest.of(1, HomeService.WATCH_SCAN_SIZE))))
                .thenReturn(new PageImpl<>(List.of(other), PageRequest.of(1, HomeService.WATCH_SCAN_SIZE), 21));

        var home = service.getHome(MEMBER);

        assertThat(home.continueCourse().courseId()).isEqualTo(7L);
        assertThat(home.continueCourse().lessonId()).isNull();
        verify(courseService, never()).findWatchHistories(eq(MEMBER), eq(PageRequest.of(2, HomeService.WATCH_SCAN_SIZE)));
    }

    @Test
    @DisplayName("기록이 한없이 이어져도 최대 MAX_WATCH_PAGES 쪽까지만 읽고 멈춘다 (홈을 열 때마다 끝없이 읽지 않는다)")
    void stopsAtMaxPages() {
        CourseListDTO continuing = course(7, "청약통장 기초와 가점 계산");
        when(courseService.findContinueWatching(MEMBER)).thenReturn(List.of(continuing));
        WatchHistoryDTO other = watch(9, 40, "다른 강의 영상");
        // 어느 쪽을 읽어도 다른 강의 기록만 있고 다음 쪽이 계속 있다고 답한다
        when(courseService.findWatchHistories(eq(MEMBER), any(Pageable.class))).thenAnswer(invocation -> {
            Pageable pageable = invocation.getArgument(1);
            if (pageable.getPageSize() != HomeService.WATCH_SCAN_SIZE) {
                return new PageImpl<>(List.of(), pageable, 0);   // 학습 기록 개수를 세는 한 건짜리 조회
            }
            return new PageImpl<>(List.of(other), pageable, 1_000_000);
        });

        var home = service.getHome(MEMBER);

        assertThat(home.continueCourse().courseId()).isEqualTo(7L);
        assertThat(home.continueCourse().lessonId()).isNull();
        verify(courseService, times(HomeService.MAX_WATCH_PAGES))
                .findWatchHistories(eq(MEMBER), argThat(pageable -> pageable.getPageSize() == HomeService.WATCH_SCAN_SIZE));
        verify(courseService, never()).findWatchHistories(eq(MEMBER),
                eq(PageRequest.of(HomeService.MAX_WATCH_PAGES, HomeService.WATCH_SCAN_SIZE)));
    }

    @Test
    @DisplayName("이어서 볼 강의를 찾은 뒤 비공개가 되어 영상 정보가 COURSE_NOT_PUBLIC 이면 홈은 실패하지 않고 이어서 볼 강의만 null 이다")
    void continueCourseBecamePrivate() {
        CourseListDTO continuing = course(7, "청약통장 기초와 가점 계산");
        when(courseService.findContinueWatching(MEMBER)).thenReturn(List.of(continuing));
        WatchHistoryDTO lastWatch = watch(7, 21, "가점은 어떻게 계산할까");
        when(courseService.findWatchHistories(eq(MEMBER), eq(PageRequest.of(0, HomeService.WATCH_SCAN_SIZE))))
                .thenReturn(new PageImpl<>(List.of(lastWatch)));
        when(courseService.getLessonPlay(MEMBER, 7L, 21L))
                .thenThrow(new com.wanted.moneybeetle.global.exception.BusinessException(
                        com.wanted.moneybeetle.domain.course.exception.CourseErrorCode.COURSE_NOT_PUBLIC));

        var home = service.getHome(MEMBER);

        assertThat(home.continueCourse()).isNull();
        assertThat(home.todayTerms()).isNotEmpty();   // 나머지 홈 정보는 그대로 나온다
        assertThat(home.learning()).isNotNull();
    }

    @Test
    @DisplayName("영상 정보를 읽다 COURSE_NOT_PUBLIC 이 아닌 업무 오류가 나면 숨기지 않고 그대로 던진다")
    void otherBusinessErrorsPropagate() {
        CourseListDTO continuing = course(7, "청약통장 기초와 가점 계산");
        when(courseService.findContinueWatching(MEMBER)).thenReturn(List.of(continuing));
        WatchHistoryDTO lastWatch = watch(7, 21, "가점은 어떻게 계산할까");
        when(courseService.findWatchHistories(eq(MEMBER), eq(PageRequest.of(0, HomeService.WATCH_SCAN_SIZE))))
                .thenReturn(new PageImpl<>(List.of(lastWatch)));
        var failure = new com.wanted.moneybeetle.global.exception.BusinessException(
                com.wanted.moneybeetle.global.exception.CommonErrorCode.NOT_FOUND);
        when(courseService.getLessonPlay(MEMBER, 7L, 21L)).thenThrow(failure);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.getHome(MEMBER)).isSameAs(failure);
    }

    @Test
    @DisplayName("그 강의에서 본 영상 기록이 없으면 강의만 담고 영상·위치는 null 이다")
    void continueCourseWithoutWatchedLesson() {
        CourseListDTO continuing = course(7, "청약통장 기초와 가점 계산");
        when(courseService.findContinueWatching(MEMBER)).thenReturn(List.of(continuing));
        WatchHistoryDTO otherCourseWatch = watch(9, 40, "다른 강의 영상");
        when(courseService.findWatchHistories(eq(MEMBER), eq(PageRequest.of(0, HomeService.WATCH_SCAN_SIZE))))
                .thenReturn(new PageImpl<>(List.of(otherCourseWatch)));

        var home = service.getHome(MEMBER);

        assertThat(home.continueCourse().courseId()).isEqualTo(7L);
        assertThat(home.continueCourse().lessonId()).isNull();
        assertThat(home.continueCourse().lessonTitle()).isNull();
        assertThat(home.continueCourse().positionSeconds()).isNull();
        assertThat(home.continueCourse().durationSeconds()).isNull();
        assertThat(home.continueCourse().lessonNumber()).isNull();
        assertThat(home.continueCourse().lessonCount()).isNull();
        assertThat(home.continueCourse().categoryName()).isNull();
        verify(courseService, never()).getLessonPlay(any(), any(), any());
        verify(courseService, never()).getCourseDetail(any(), any());
    }

    @Test
    @DisplayName("학습 기록은 시청한 영상 수와 완료한 강의 수의 전체 개수다")
    void learningTotals() {
        givenWatchTotals(14, 5);

        var home = service.getHome(MEMBER);

        assertThat(home.learning().watchedLessons()).isEqualTo(14);
        assertThat(home.learning().completedCourses()).isEqualTo(5);
    }

    @Test
    @DisplayName("트레이닝 해금은 내 레벨과 기준 레벨(모의투자 8, 모의 청약 12)을 비교한다")
    void trainingUnlocks() {
        when(growthService.getLevel(MEMBER)).thenReturn(7);
        var lv7 = service.getHome(MEMBER).training();
        assertThat(lv7.trading().unlocked()).isFalse();
        assertThat(lv7.trading().requiredLevel()).isEqualTo(8);
        assertThat(lv7.housing().unlocked()).isFalse();

        when(growthService.getLevel(MEMBER)).thenReturn(8);
        var lv8 = service.getHome(MEMBER).training();
        assertThat(lv8.trading().unlocked()).isTrue();
        assertThat(lv8.housing().unlocked()).isFalse();
        assertThat(lv8.housing().requiredLevel()).isEqualTo(12);

        when(growthService.getLevel(MEMBER)).thenReturn(12);
        assertThat(service.getHome(MEMBER).training().housing().unlocked()).isTrue();
    }

    // ---------------- 트레이닝 요약 (#261) ----------------

    private TradingHomeDTO givenSeason(String name, String status, int currentTurn, int totalTurns, java.time.LocalDate endDate) {
        TradingHomeDTO.Season season = mock(TradingHomeDTO.Season.class);
        when(season.getSeasonId()).thenReturn(6L);
        when(season.getName()).thenReturn(name);
        when(season.getStatus()).thenReturn(status);
        when(season.getCurrentTurn()).thenReturn(currentTurn);
        when(season.getTotalTurns()).thenReturn(totalTurns);
        when(season.getEndDate()).thenReturn(endDate);
        TradingHomeDTO tradingHome = mock(TradingHomeDTO.class);
        when(tradingHome.getSeason()).thenReturn(season);
        return tradingHome;
    }

    private ComplexDTO complex(String status) {
        ComplexDTO complex = mock(ComplexDTO.class);
        when(complex.getStatus()).thenReturn(status);
        return complex;
    }

    @Test
    @DisplayName("모의투자가 해금됐으면 시즌 이름·상태·턴과 종료일까지 남은 일수(서울 날짜 기준)를 담는다")
    void tradingSeasonSummary() {
        when(growthService.getLevel(MEMBER)).thenReturn(8);
        TradingHomeDTO tradingHome = givenSeason("시즌 6", "RUNNING", 3, 12, java.time.LocalDate.of(2026, 11, 22));
        when(participationService.getHome(MEMBER)).thenReturn(tradingHome);

        var season = service.getHome(MEMBER).training().tradingSeason();

        assertThat(season.name()).isEqualTo("시즌 6");
        assertThat(season.status()).isEqualTo("RUNNING");
        assertThat(season.currentTurn()).isEqualTo(3);
        assertThat(season.totalTurns()).isEqualTo(12);
        assertThat(season.daysLeft()).isEqualTo(12);   // 11/10 → 11/22
    }

    @Test
    @DisplayName("종료일이 지났거나 없으면 남은 일수는 0 이고, 음수가 되지 않는다")
    void daysLeftNeverNegative() {
        when(growthService.getLevel(MEMBER)).thenReturn(8);
        TradingHomeDTO ended = givenSeason("시즌 5", "ENDED", 12, 12, java.time.LocalDate.of(2026, 11, 1));
        TradingHomeDTO noEndDate = givenSeason("시즌 5", "ENDED", 12, 12, null);   // when(...) 안에서 다른 목에 스텁을 걸지 않도록 먼저 만든다
        when(participationService.getHome(MEMBER)).thenReturn(ended);
        assertThat(service.getHome(MEMBER).training().tradingSeason().daysLeft()).isZero();

        when(participationService.getHome(MEMBER)).thenReturn(noEndDate);
        assertThat(service.getHome(MEMBER).training().tradingSeason().daysLeft()).isZero();
    }

    @Test
    @DisplayName("모의투자가 잠겨 있으면 시즌 요약을 만들지 않고 모의투자 서비스를 부르지도 않는다 (잠금은 서버가 지킨다)")
    void tradingSeasonHiddenWhenLocked() {
        when(growthService.getLevel(MEMBER)).thenReturn(7);

        assertThat(service.getHome(MEMBER).training().tradingSeason()).isNull();
        verify(participationService, never()).getHome(any());
    }

    @Test
    @DisplayName("시즌이 없거나(NO_RUNNING_SEASON) 예상 밖 오류가 나도 홈은 실패하지 않고 시즌 요약만 null 이다")
    void tradingSeasonFailureKeepsHome() {
        when(growthService.getLevel(MEMBER)).thenReturn(8);
        when(participationService.getHome(MEMBER)).thenThrow(new BusinessException(TradingErrorCode.NO_RUNNING_SEASON));
        var noSeason = service.getHome(MEMBER);
        assertThat(noSeason.training().tradingSeason()).isNull();
        assertThat(noSeason.training().trading().unlocked()).isTrue();   // 해금 상태는 그대로 나온다

        org.mockito.Mockito.doThrow(new IllegalStateException("예상 밖")).when(participationService).getHome(MEMBER);
        assertThat(service.getHome(MEMBER).training().tradingSeason()).isNull();
        assertThat(service.getHome(MEMBER).todayTerms()).isNotEmpty();
    }

    @Test
    @DisplayName("모의 청약이 해금됐으면 이번 달의 접수 중(OPEN) 공고 수와 내 납입 횟수를 담는다")
    void housingSummary() {
        when(growthService.getLevel(MEMBER)).thenReturn(12);
        List<ComplexDTO> complexes = List.of(complex("OPEN"), complex("UPCOMING"), complex("OPEN"), complex("CLOSED"), complex("ANNOUNCED"));
        when(housingService.findCalendar(YearMonth.of(2026, 11), MEMBER)).thenReturn(complexes);
        AccountDTO account = mock(AccountDTO.class);
        when(account.getPaidCount()).thenReturn(18);
        when(housingService.getAccount(MEMBER)).thenReturn(account);

        var summary = service.getHome(MEMBER).training().housingSummary();

        assertThat(summary.openComplexes()).isEqualTo(2);
        assertThat(summary.paidCount()).isEqualTo(18);
    }

    @Test
    @DisplayName("청약통장이 없으면 납입 횟수만 null 이고 공고 수는 그대로 나온다")
    void housingWithoutAccount() {
        when(growthService.getLevel(MEMBER)).thenReturn(12);
        List<ComplexDTO> complexes = List.of(complex("OPEN"));
        when(housingService.findCalendar(YearMonth.of(2026, 11), MEMBER)).thenReturn(complexes);
        when(housingService.getAccount(MEMBER)).thenThrow(new BusinessException(com.wanted.moneybeetle.global.exception.CommonErrorCode.NOT_FOUND));

        var summary = service.getHome(MEMBER).training().housingSummary();

        assertThat(summary.openComplexes()).isEqualTo(1);
        assertThat(summary.paidCount()).isNull();
    }

    @Test
    @DisplayName("모의 청약이 잠겨 있으면 요약을 만들지 않고 청약 서비스를 부르지도 않으며, 공고를 못 읽으면 요약만 null 이다")
    void housingLockedOrFailing() {
        when(growthService.getLevel(MEMBER)).thenReturn(11);
        assertThat(service.getHome(MEMBER).training().housingSummary()).isNull();
        verify(housingService, never()).findCalendar(any(), any());

        when(growthService.getLevel(MEMBER)).thenReturn(12);
        when(housingService.findCalendar(any(), eq(MEMBER))).thenThrow(new IllegalStateException("예상 밖"));
        assertThat(service.getHome(MEMBER).training().housingSummary()).isNull();
        assertThat(service.getHome(MEMBER).learning()).isNotNull();
    }
}
