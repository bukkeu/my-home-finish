package com.wanted.moneybeetle.domain.course.model.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.wanted.moneybeetle.domain.course.model.dao.CategoryRepository;
import com.wanted.moneybeetle.domain.course.model.dao.CourseRepository;
import com.wanted.moneybeetle.domain.course.model.dao.CourseReviewRepository;
import com.wanted.moneybeetle.domain.course.model.dao.LearningProgressRepository;
import com.wanted.moneybeetle.domain.course.model.dao.LessonRepository;
import com.wanted.moneybeetle.domain.course.model.dao.LessonWatchRepository;
import com.wanted.moneybeetle.domain.course.model.dao.ScrapRepository;
import com.wanted.moneybeetle.domain.course.model.dto.ProgressDTO;
import com.wanted.moneybeetle.domain.course.model.entity.Course;
import com.wanted.moneybeetle.domain.course.model.entity.LearningProgress;
import com.wanted.moneybeetle.domain.course.model.entity.Lesson;
import com.wanted.moneybeetle.domain.course.model.entity.LessonWatch;
import com.wanted.moneybeetle.domain.member.model.service.InstructorService;
import com.wanted.moneybeetle.domain.member.model.service.MemberService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/* comment.
*   담당 : DBA ① 신아림 (#71, 마이페이지 학습 기록의 이어보기)
*   이어보기 위치는 영상마다 남는다: 같은 강의에서 A 를 보다가 B 를 봐도 A 를 다시 열면 A 를 보던 위치부터 (API 32·33).
*   강의의 마지막 영상(처음 열 영상)은 진행 기록이 그대로 기억한다. 저장소와 다른 서비스는 가짜로 둔다.
*  */
class LessonPositionTest {

    private static final Long MEMBER = 8L;
    private static final Long COURSE = 15L;
    private static final Long LESSON_A = 21L;
    private static final Long LESSON_B = 22L;

    private CourseService courseService;
    private final Map<Long, LessonWatch> watches = new HashMap<>();
    private LearningProgress progress;

    @BeforeEach
    void setUp() {
        CourseRepository courseRepository = mock(CourseRepository.class);
        LessonRepository lessonRepository = mock(LessonRepository.class);
        LearningProgressRepository progressRepository = mock(LearningProgressRepository.class);
        LessonWatchRepository watchRepository = mock(LessonWatchRepository.class);
        courseService = new CourseService(mock(CategoryRepository.class), courseRepository, lessonRepository,
                progressRepository, watchRepository, mock(ScrapRepository.class), mock(CourseReviewRepository.class),
                Clock.fixed(Instant.parse("2026-11-11T01:00:00Z"), ZoneId.of("Asia/Seoul")),
                mock(MemberService.class), mock(InstructorService.class));

        Course course = mock(Course.class);
        when(course.isVisibleToLearner()).thenReturn(true);
        when(courseRepository.findById(COURSE)).thenReturn(Optional.of(course));
        when(lessonRepository.findByLessonIdAndCourseId(LESSON_A, COURSE)).thenReturn(Optional.of(lesson(LESSON_A)));
        when(lessonRepository.findByLessonIdAndCourseId(LESSON_B, COURSE)).thenReturn(Optional.of(lesson(LESSON_B)));

        when(progressRepository.findByMemberIdAndCourseId(MEMBER, COURSE)).thenAnswer(i -> Optional.ofNullable(progress));
        when(progressRepository.save(any(LearningProgress.class))).thenAnswer(i -> progress = i.getArgument(0));
        when(watchRepository.findByMemberIdAndLessonId(any(), anyLong()))
                .thenAnswer(i -> Optional.ofNullable(watches.get(i.<Long>getArgument(1))));
        when(watchRepository.save(any(LessonWatch.class))).thenAnswer(i -> {
            LessonWatch watch = i.getArgument(0);
            watches.put(watch.getLessonId(), watch);
            return watch;
        });
    }

    private Lesson lesson(Long lessonId) {
        Lesson lesson = Lesson.create(COURSE, 1, "영상", "/uploads/videos/sample-1.mp4", 600, null);
        ReflectionTestUtils.setField(lesson, "lessonId", lessonId);
        return lesson;
    }

    private void save(Long lessonId, int position) {
        ProgressDTO request = new ProgressDTO();
        ReflectionTestUtils.setField(request, "positionSeconds", position);
        ReflectionTestUtils.setField(request, "watchedSeconds", 0);
        courseService.saveProgress(MEMBER, COURSE, lessonId, request);
    }

    @Test
    @DisplayName("같은 강의의 다른 영상을 본 뒤에도 앞 영상은 보던 위치부터 이어 본다")
    void positionIsKeptPerLesson() {
        save(LESSON_A, 120);
        save(LESSON_B, 45);

        assertThat(courseService.getLessonPlay(MEMBER, COURSE, LESSON_A).getPositionSeconds()).isEqualTo(120);
        assertThat(courseService.getLessonPlay(MEMBER, COURSE, LESSON_B).getPositionSeconds()).isEqualTo(45);
        assertThat(progress.getLastLessonId()).isEqualTo(LESSON_B);   // 강의를 다시 열면 마지막 영상 B 부터
    }

    @Test
    @DisplayName("영상 길이를 넘는 위치는 길이로 맞추고, 본 적 없는 영상은 처음부터")
    void clampAndDefault() {
        save(LESSON_A, 9_999);

        assertThat(courseService.getLessonPlay(MEMBER, COURSE, LESSON_A).getPositionSeconds()).isEqualTo(600);
        assertThat(courseService.getLessonPlay(MEMBER, COURSE, LESSON_B).getPositionSeconds()).isZero();
    }
}
