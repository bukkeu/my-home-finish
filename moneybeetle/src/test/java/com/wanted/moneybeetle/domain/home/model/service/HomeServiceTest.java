package com.wanted.moneybeetle.domain.home.model.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wanted.moneybeetle.domain.course.model.dto.CompletedCourseDTO;
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
    private HomeService service;

    @BeforeEach
    void setUp() {
        termService = mock(TermService.class);
        courseService = mock(CourseService.class);
        growthService = mock(GrowthService.class);
        service = new HomeService(termService, courseService, growthService);

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
        when(courseService.getLessonPlay(MEMBER, 7L, 21L)).thenReturn(play);

        var home = service.getHome(MEMBER);

        assertThat(home.continueCourse().courseId()).isEqualTo(7L);
        assertThat(home.continueCourse().title()).isEqualTo("청약통장 기초와 가점 계산");
        assertThat(home.continueCourse().lessonId()).isEqualTo(21L);
        assertThat(home.continueCourse().lessonTitle()).isEqualTo("가점은 어떻게 계산할까");
        assertThat(home.continueCourse().positionSeconds()).isEqualTo(312);
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
        verify(courseService, never()).getLessonPlay(any(), any(), any());
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
}
