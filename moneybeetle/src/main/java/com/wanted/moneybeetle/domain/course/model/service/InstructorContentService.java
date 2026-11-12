package com.wanted.moneybeetle.domain.course.model.service;

import com.wanted.moneybeetle.domain.course.exception.CourseErrorCode;
import com.wanted.moneybeetle.domain.course.model.dao.CourseRepository;
import com.wanted.moneybeetle.domain.course.model.dao.LessonRepository;
import com.wanted.moneybeetle.domain.course.model.dto.InstructorQuizItemDTO;
import com.wanted.moneybeetle.domain.course.model.dto.InstructorQuizListDTO;
import com.wanted.moneybeetle.domain.course.model.dto.InstructorSummaryItemDTO;
import com.wanted.moneybeetle.domain.course.model.dto.InstructorSummaryListDTO;
import com.wanted.moneybeetle.domain.course.model.dto.SummaryUpdateDTO;
import com.wanted.moneybeetle.domain.course.model.dto.SummaryUpdatedDTO;
import com.wanted.moneybeetle.domain.course.model.entity.Course;
import com.wanted.moneybeetle.domain.course.model.entity.CourseStatus;
import com.wanted.moneybeetle.domain.course.model.entity.Lesson;
import com.wanted.moneybeetle.domain.operation.model.service.ForbiddenWordService;
import com.wanted.moneybeetle.domain.quiz.model.dto.CourseQuizStatDTO;
import com.wanted.moneybeetle.domain.quiz.model.service.InstructorQuizService;
import com.wanted.moneybeetle.global.exception.BusinessException;
import com.wanted.moneybeetle.global.exception.CommonErrorCode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/* comment.
*   담당 : DBA ① (강의·퀴즈) - 강사 센터 화면 작업을 DBA ② 김예린이 나눠 받아 만들었다 (#366)
*   강사 센터 "퀴즈 관리·요약글 관리" 메뉴 (API 151~152). 새 테이블 없이 있는 데이터에 연결한다
*   - 퀴즈 목록 : 내 강의마다 한 줄 (지금 쓰는 테스트 퀴즈·퀴즈 문항 수). 문항이 하나도 없는 강의는 뺀다. 최근 수정 순
*   - 요약글 목록 : 요약글이 있는 내 영상마다 한 줄. 최근 수정 순
*   - 요약글 수정 : 영상 하나의 요약글만. 규칙은 API 78(영상·요약글 저장)과 같다 - 본인 강의만, 검수 중이면 불가(COURSE_IN_REVIEW),
*       허가된 강의를 고치면 다시 검수(REVIEW), 2,000자·금칙어
*   - 목록은 강의·영상·문항을 한 번에 모아 조회한다 (강의마다 따로 조회하지 않는다)
*  */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InstructorContentService {

    static final int SUMMARY_MAX_LENGTH = 2000;     // 요약글 2,000자 (DP-31, InstructorCourseService 와 같은 값)

    private final CourseRepository courseRepository;
    private final LessonRepository lessonRepository;
    private final InstructorQuizService instructorQuizService;   // quiz 도메인은 Service 로만
    private final ForbiddenWordService forbiddenWordService;     // operation 도메인
    private final Clock clock;                                   // Asia/Seoul 공통 시계

    /* API 150 퀴즈 목록: 내 강의 중 문항이 있는 강의만, 가장 늦게 바뀐 문항 순 */
    public InstructorQuizListDTO findQuizzes(Long instructorId) {
        List<Course> courses = courseRepository.findByInstructorIdAndDeletedAtIsNull(instructorId);
        Map<Long, Course> byId = courses.stream().collect(Collectors.toMap(Course::getCourseId, Function.identity()));
        List<CourseQuizStatDTO> stats = instructorQuizService.findQuizStats(List.copyOf(byId.keySet()));
        List<InstructorQuizItemDTO> items = stats.stream()
                .filter(stat -> stat.testCount() + stat.mainCount() > 0)
                .sorted(Comparator.comparing(CourseQuizStatDTO::updatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(CourseQuizStatDTO::courseId, Comparator.reverseOrder()))
                .map(stat -> new InstructorQuizItemDTO(stat.courseId(), byId.get(stat.courseId()).getTitle(),
                        stat.testCount(), stat.mainCount(), stat.updatedAt()))
                .toList();
        return new InstructorQuizListDTO(items);
    }

    /* API 151 요약글 목록: 요약글이 있는 내 영상만, 최근 수정 순 */
    public InstructorSummaryListDTO findSummaries(Long instructorId) {
        Map<Long, Course> byId = courseRepository.findByInstructorIdAndDeletedAtIsNull(instructorId).stream()
                .collect(Collectors.toMap(Course::getCourseId, Function.identity()));
        if (byId.isEmpty()) {
            return new InstructorSummaryListDTO(List.of());
        }
        List<InstructorSummaryItemDTO> items = lessonRepository.findWithSummaryByCourseIds(byId.keySet()).stream()
                .map(lesson -> {
                    Course course = byId.get(lesson.getCourseId());
                    return new InstructorSummaryItemDTO(lesson.getLessonId(), lesson.getTitle(), course.getCourseId(),
                            course.getTitle(), lesson.getSummary(), course.getThumbnailUrl(), isPublished(course),
                            lesson.getUpdatedAt());
                })
                .toList();
        return new InstructorSummaryListDTO(items);
    }

    /* API 152 요약글 수정: 없는 영상 404(LESSON_NOT_FOUND), 삭제된 강의 404, 남의 강의 403, 검수 중 409(COURSE_IN_REVIEW),
     * 2,000자 초과 400(SUMMARY_TOO_LONG), 금칙어 400. 빈 글은 요약글을 지운다. 기존과 같은 글이면 바꾸지 않고 재검수도 하지 않는다. 강의 행을 잠가 검사와 저장 사이에 상태가 바뀌지 않게 한다 */
    @Transactional
    public SummaryUpdatedDTO updateSummary(Long instructorId, Long lessonId, SummaryUpdateDTO request) {
        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new BusinessException(CourseErrorCode.LESSON_NOT_FOUND));
        Course course = courseRepository.findByIdForUpdate(lesson.getCourseId())
                .filter(found -> !found.isDeleted())
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        if (!course.isOwnedBy(instructorId)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }

        String summary = request.summary() == null || request.summary().isBlank() ? null : request.summary().strip();
        if (summary != null && summary.length() > SUMMARY_MAX_LENGTH) {
            throw new BusinessException(CourseErrorCode.SUMMARY_TOO_LONG);
        }
        forbiddenWordService.check(summary);

        if (Objects.equals(summary, lesson.getSummary())) {
            // 내용이 그대로면 바꾼 것이 아니라서 허가된 강의를 다시 검수로 돌리지 않는다. 검수 중이면 그래도 거절한다
            course.checkEditable();
            return new SummaryUpdatedDTO(lesson.getLessonId(), course.getCourseId(), lesson.getSummary(), course.getStatus());
        }
        course.markContentChanged(LocalDateTime.now(clock));   // 검수 중이면 409, 허가된 강의면 다시 검수
        lesson.change(lesson.getTitle(), lesson.getVideoUrl(), lesson.getDurationSeconds(), summary);
        return new SummaryUpdatedDTO(lesson.getLessonId(), course.getCourseId(), lesson.getSummary(), course.getStatus());
    }

    /* "게시" 배지: 허가되고 공개 중인 강의 */
    private boolean isPublished(Course course) {
        return course.getCourseStatus() == CourseStatus.APPROVED && course.isPublic();
    }
}
