package com.wanted.moneybeetle.domain.inquiry.model.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wanted.moneybeetle.domain.course.model.service.CourseService;
import com.wanted.moneybeetle.domain.inquiry.model.dao.InquiryRepository;
import com.wanted.moneybeetle.domain.inquiry.model.dto.InstructorInquiryListDTO;
import com.wanted.moneybeetle.domain.inquiry.model.entity.Inquiry;
import com.wanted.moneybeetle.global.exception.BusinessException;
import com.wanted.moneybeetle.global.exception.CommonErrorCode;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

/* comment.
*   담당 : DBA ① 신아림 (#59, 강사 센터 Q&A 관리가 쓰는 강사 문의함 API 82)
*   로그인한 강사의 강의에 달린 문의만 조회하는지 확인한다 (TCH-08, 강사 센터 "본인 강의와 문의만 표시")
*   - 다른 강사의 강의 ID로 거르면 FORBIDDEN 이고 문의를 조회하지 않는다
*   - 거르지 않으면 내 강의 ID들로만 조회한다
*   저장소와 강의 서비스는 가짜로 둔다. DB 없이 돈다.
*  */
class InstructorInquiryServiceTest {

    private static final Long ME = 2L;
    private static final Pageable PAGE = PageRequest.of(0, 20);

    private InquiryRepository inquiryRepository;
    private CourseService courseService;
    private InstructorInquiryService service;

    @BeforeEach
    void setUp() {
        inquiryRepository = mock(InquiryRepository.class);
        courseService = mock(CourseService.class);
        service = new InstructorInquiryService(inquiryRepository, courseService);
        when(courseService.findCourseTitlesByInstructor(ME)).thenReturn(Map.of(15L, "청약통장 기초와 가점 계산", 21L, "전세 계약"));
    }

    @Test
    @DisplayName("다른 강사의 강의 ID로 거르면 FORBIDDEN 이고 문의를 조회하지 않는다")
    void forbidsOtherInstructorsCourse() {
        assertThatThrownBy(() -> service.findMyCourseInquiries(ME, 99L, null, PAGE))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.FORBIDDEN));

        verify(inquiryRepository, never()).searchForInstructor(anyCollection(), any(), anyString(), any());
    }

    @Test
    @DisplayName("거르지 않으면 로그인한 강사의 강의 ID들로만 조회하고 강의 이름을 붙인다")
    @SuppressWarnings("unchecked")
    void searchesOnlyMyCourses() {
        Inquiry mine = Inquiry.create(8L, 15L, null, "부양가족에 부모님도 포함되나요?", "같이 안 살면요?");
        ReflectionTestUtils.setField(mine, "inquiryId", 7L);
        Page<Inquiry> page = new PageImpl<>(List.of(mine), PAGE, 1);
        when(inquiryRepository.searchForInstructor(anyCollection(), isNull(), eq(Inquiry.STATUS_WAITING), eq(PAGE))).thenReturn(page);

        InstructorInquiryListDTO result = service.findMyCourseInquiries(ME, null, null, PAGE);

        ArgumentCaptor<Collection<Long>> ids = ArgumentCaptor.forClass(Collection.class);
        verify(inquiryRepository).searchForInstructor(ids.capture(), isNull(), eq(Inquiry.STATUS_WAITING), eq(PAGE));
        assertThat(ids.getValue()).containsExactlyInAnyOrder(15L, 21L);
        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).courseTitle()).isEqualTo("청약통장 기초와 가점 계산");
    }

    @Test
    @DisplayName("내 강의 하나로 거르면 그 강의 ID로만 조회한다")
    @SuppressWarnings("unchecked")
    void searchesOneOfMyCourses() {
        when(inquiryRepository.searchForInstructor(anyCollection(), any(), anyString(), any())).thenReturn(Page.empty(PAGE));

        service.findMyCourseInquiries(ME, 15L, "WAITING", PAGE);

        ArgumentCaptor<Collection<Long>> ids = ArgumentCaptor.forClass(Collection.class);
        verify(inquiryRepository).searchForInstructor(ids.capture(), eq("WAITING"), eq(Inquiry.STATUS_WAITING), eq(PAGE));
        assertThat(ids.getValue()).containsExactly(15L);
    }

    @Test
    @DisplayName("강의가 없는 강사는 빈 목록이고 문의를 조회하지 않는다")
    void emptyWhenNoCourses() {
        when(courseService.findCourseTitlesByInstructor(3L)).thenReturn(Map.of());

        InstructorInquiryListDTO result = service.findMyCourseInquiries(3L, null, null, PAGE);

        assertThat(result.items()).isEmpty();
        assertThat(result.waitingCount()).isZero();
        verify(inquiryRepository, never()).searchForInstructor(anyCollection(), any(), anyString(), any());
    }
}
