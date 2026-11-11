package com.wanted.moneybeetle.domain.inquiry.model.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.wanted.moneybeetle.domain.course.model.service.CourseService;
import com.wanted.moneybeetle.domain.inquiry.model.dao.InquiryReplyRepository;
import com.wanted.moneybeetle.domain.inquiry.model.dao.InquiryRepository;
import com.wanted.moneybeetle.domain.inquiry.model.dto.MyInquiryItemDTO;
import com.wanted.moneybeetle.domain.inquiry.model.entity.Inquiry;
import com.wanted.moneybeetle.domain.member.model.service.MemberService;
import com.wanted.moneybeetle.domain.notification.model.service.NotificationService;
import com.wanted.moneybeetle.domain.operation.model.service.AdminLogService;
import com.wanted.moneybeetle.domain.operation.model.service.ForbiddenWordService;
import com.wanted.moneybeetle.global.common.PageResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

/* comment.
*   담당 : DBA ① 신아림 (#71, 마이페이지 Q&A·문의 모음)
*   API 149 내가 쓴 문의 모음: 사이트 문의와 강의 문의를 함께 돌려주고,
*   강의 제목은 수강생에게 보이는 강의만 채우며, 숨긴 글은 제목을 안내 문구로 가린다. 저장소와 다른 서비스는 가짜로 둔다.
*  */
class MyInquiryListTest {

    private static final Long ME = 8L;
    private static final Long VISIBLE_COURSE = 15L;
    private static final Long PRIVATE_COURSE = 16L;
    private static final Pageable PAGE = PageRequest.of(0, 20);

    private InquiryRepository inquiryRepository;
    private CourseService courseService;
    private InquiryService inquiryService;

    @BeforeEach
    void setUp() {
        inquiryRepository = mock(InquiryRepository.class);
        courseService = mock(CourseService.class);
        inquiryService = new InquiryService(inquiryRepository, mock(InquiryReplyRepository.class), courseService,
                mock(ForbiddenWordService.class), mock(NotificationService.class),
                Clock.fixed(Instant.parse("2026-11-11T01:00:00Z"), ZoneId.of("Asia/Seoul")),
                mock(MemberService.class), mock(AdminLogService.class));
    }

    private Inquiry inquiry(Long id, Long courseId, String title, boolean hidden) {
        Inquiry inquiry = Inquiry.create(ME, courseId, courseId == null ? "ACCOUNT" : null, title, "내용");
        ReflectionTestUtils.setField(inquiry, "inquiryId", id);
        ReflectionTestUtils.setField(inquiry, "isHidden", hidden);
        return inquiry;
    }

    @Test
    @DisplayName("사이트 문의와 강의 문의를 함께 돌려주고, 보이는 강의만 강의 제목을 채운다")
    void siteAndCourseInquiries() {
        when(inquiryRepository.findMine(eq(ME), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(
                inquiry(3L, VISIBLE_COURSE, "부양가족에 부모님도 포함되나요?", false),
                inquiry(2L, PRIVATE_COURSE, "영상 소리가 작아요", false),
                inquiry(1L, null, "비밀번호를 바꾸고 싶어요", false)), PAGE, 3));
        when(courseService.findVisibleCourseTitles(anyCollection())).thenReturn(Map.of(VISIBLE_COURSE, "청약통장 기초"));

        PageResponse<MyInquiryItemDTO> result = inquiryService.findMyInquiries(ME, PAGE);

        assertThat(result.getItems()).extracting(MyInquiryItemDTO::inquiryId).containsExactly(3L, 2L, 1L);
        assertThat(result.getItems()).extracting(MyInquiryItemDTO::courseTitle)
                .containsExactly("청약통장 기초", null, null);
        assertThat(result.getItems().get(2).courseId()).isNull();
        assertThat(result.getTotalElements()).isEqualTo(3);
    }

    @Test
    @DisplayName("관리자가 숨긴 내 글은 목록에 남기되 제목을 안내 문구로 가린다")
    void hiddenTitleMasked() {
        when(inquiryRepository.findMine(eq(ME), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(
                inquiry(4L, null, "욕설이 섞인 제목", true)), PAGE, 1));
        when(courseService.findVisibleCourseTitles(anyCollection())).thenReturn(Map.of());

        MyInquiryItemDTO item = inquiryService.findMyInquiries(ME, PAGE).getItems().get(0);

        assertThat(item.isHidden()).isTrue();
        assertThat(item.title()).isEqualTo("관리자에 의해 숨김 처리된 글이에요");
    }
}
