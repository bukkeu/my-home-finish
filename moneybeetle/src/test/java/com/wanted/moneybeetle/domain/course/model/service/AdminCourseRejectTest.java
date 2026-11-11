package com.wanted.moneybeetle.domain.course.model.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wanted.moneybeetle.domain.course.model.dao.CourseRepository;
import com.wanted.moneybeetle.domain.course.model.dao.CourseReviewRepository;
import com.wanted.moneybeetle.domain.course.model.dao.LearningProgressRepository;
import com.wanted.moneybeetle.domain.course.model.dao.LessonRepository;
import com.wanted.moneybeetle.domain.course.model.dto.CourseReviewResultDTO;
import com.wanted.moneybeetle.domain.course.model.entity.Course;
import com.wanted.moneybeetle.domain.course.model.entity.CourseStatus;
import com.wanted.moneybeetle.domain.member.model.service.MemberService;
import com.wanted.moneybeetle.domain.notification.model.entity.NotificationType;
import com.wanted.moneybeetle.domain.notification.model.service.NotificationService;
import com.wanted.moneybeetle.domain.operation.model.service.AdminLogService;
import com.wanted.moneybeetle.domain.quiz.model.service.QuizService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

/* comment.
*   담당 : DBA ① 신아림 (#295)
*   관리자 강의 반려(API 92): 상태 REJECTED, 강사에게 COURSE_REJECTED 알림 (NotificationType 으로 보냄)
*   관리자 목록(API 89)의 status 필터가 enum 으로 저장소에 넘어가는지
*  */
class AdminCourseRejectTest {

    private static final Long ADMIN = 1L;
    private static final Long INSTRUCTOR = 2L;
    private static final Long COURSE = 15L;

    private CourseRepository courseRepository;
    private NotificationService notificationService;
    private AdminCourseService adminCourseService;

    @BeforeEach
    void setUp() {
        courseRepository = mock(CourseRepository.class);
        notificationService = mock(NotificationService.class);
        adminCourseService = new AdminCourseService(courseRepository, mock(CourseReviewRepository.class),
                mock(LearningProgressRepository.class), mock(LessonRepository.class), mock(QuizService.class),
                mock(MemberService.class), notificationService, mock(AdminLogService.class), new ObjectMapper(),
                Clock.fixed(Instant.parse("2026-11-12T01:00:00Z"), ZoneId.of("Asia/Seoul")));
    }

    @Test
    @DisplayName("반려하면 REJECTED 가 되고 강사에게 COURSE_REJECTED 알림을 보낸다")
    void rejectSendsNotification() {
        Course course = Course.create(INSTRUCTOR, 21L, "청약통장 기초", "소개", null, null);
        ReflectionTestUtils.setField(course, "courseId", COURSE);
        course.requestReview(LocalDateTime.of(2026, 11, 11, 9, 0));
        when(courseRepository.findByIdForUpdate(COURSE)).thenReturn(Optional.of(course));

        CourseReviewResultDTO result = adminCourseService.reject(ADMIN, COURSE, "  영상이 잘려 있어요 ");

        assertThat(course.isRejected()).isTrue();
        assertThat(result.status()).isEqualTo("REJECTED");
        verify(notificationService).send(eq(INSTRUCTOR), eq(NotificationType.COURSE_REJECTED), anyString(),
                eq("/instructor/courses/" + COURSE));
    }

    @Test
    @DisplayName("목록 status 필터: 아는 값은 enum 으로, 모르는 값은 null(전체)로 넘긴다")
    void listFilter() {
        PageRequest page = PageRequest.of(0, 20);
        when(courseRepository.searchForAdmin(any(), eq(page)))
                .thenReturn(Page.empty(page));

        adminCourseService.findCourses("REVIEW", page);
        adminCourseService.findCourses("모름", page);

        verify(courseRepository).searchForAdmin(CourseStatus.REVIEW, page);
        verify(courseRepository).searchForAdmin(null, page);
    }
}
