package com.wanted.moneybeetle.domain.inquiry.model.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wanted.moneybeetle.domain.course.model.service.CourseService;
import com.wanted.moneybeetle.domain.inquiry.model.dao.InquiryReplyRepository;
import com.wanted.moneybeetle.domain.inquiry.model.dao.InquiryRepository;
import com.wanted.moneybeetle.domain.inquiry.model.dto.InquiryDetailDTO;
import com.wanted.moneybeetle.domain.inquiry.model.dto.InquiryReplyCreateDTO;
import com.wanted.moneybeetle.domain.inquiry.model.entity.Inquiry;
import com.wanted.moneybeetle.domain.inquiry.model.entity.InquiryReply;
import com.wanted.moneybeetle.domain.member.model.service.MemberService;
import com.wanted.moneybeetle.domain.notification.model.service.NotificationService;
import com.wanted.moneybeetle.domain.operation.model.service.AdminLogService;
import com.wanted.moneybeetle.domain.operation.model.service.ForbiddenWordService;
import com.wanted.moneybeetle.domain.operation.model.service.SystemSettingService;
import com.wanted.moneybeetle.global.exception.BusinessException;
import com.wanted.moneybeetle.global.exception.CommonErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/* comment.
*   담당 : DBA ① 신아림 (#59, 강사 센터 Q&A 관리의 문의 상세·답글)
*   문의 상세(API 49)·답글(API 52)은 작성자, 그 강의의 강사, 관리자만 (SP-40).
*   다른 강사는 내 강의가 아닌 문의를 열거나 답글을 달 수 없다(FORBIDDEN). 저장소와 다른 서비스는 가짜로 둔다.
*  */
class InquiryAccessTest {

    private static final Long WRITER = 8L;
    private static final Long COURSE_INSTRUCTOR = 2L;
    private static final Long OTHER_INSTRUCTOR = 3L;
    private static final Long COURSE_ID = 15L;
    private static final Long INQUIRY_ID = 7L;

    private InquiryReplyRepository inquiryReplyRepository;
    private InquiryService inquiryService;
    private Inquiry inquiry;

    @BeforeEach
    void setUp() {
        InquiryRepository inquiryRepository = mock(InquiryRepository.class);
        inquiryReplyRepository = mock(InquiryReplyRepository.class);
        CourseService courseService = mock(CourseService.class);
        MemberService memberService = mock(MemberService.class);
        inquiryService = new InquiryService(inquiryRepository, inquiryReplyRepository, courseService,
                mock(ForbiddenWordService.class), mock(NotificationService.class),
                Clock.fixed(Instant.parse("2026-11-11T01:00:00Z"), ZoneId.of("Asia/Seoul")),
                memberService, mock(AdminLogService.class), mock(SystemSettingService.class));

        inquiry = Inquiry.create(WRITER, COURSE_ID, null, "부양가족에 부모님도 포함되나요?", "같이 안 살면요?");
        ReflectionTestUtils.setField(inquiry, "inquiryId", INQUIRY_ID);
        when(inquiryRepository.findByInquiryIdAndDeletedAtIsNull(INQUIRY_ID)).thenReturn(Optional.of(inquiry));
        when(courseService.findInstructorId(COURSE_ID)).thenReturn(COURSE_INSTRUCTOR);
        when(memberService.findNicknames(anyCollection())).thenReturn(Map.of(WRITER, "민지", COURSE_INSTRUCTOR, "박서연"));
        when(inquiryReplyRepository.findByInquiryIdAndDeletedAtIsNullOrderByCreatedAtAscInquiryReplyIdAsc(INQUIRY_ID))
                .thenReturn(List.of());
        when(inquiryReplyRepository.save(any(InquiryReply.class))).thenAnswer(invocation -> {
            InquiryReply saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "inquiryReplyId", 1L);
            return saved;
        });
    }

    @Test
    @DisplayName("그 강의의 강사는 문의 상세를 볼 수 있다")
    void courseInstructorCanRead() {
        InquiryDetailDTO detail = inquiryService.getInquiry(COURSE_INSTRUCTOR, false, INQUIRY_ID);

        assertThat(detail.inquiryId()).isEqualTo(INQUIRY_ID);
        assertThat(detail.content()).isEqualTo("같이 안 살면요?");
    }

    @Test
    @DisplayName("다른 강사는 내 강의가 아닌 문의를 볼 수 없다(FORBIDDEN)")
    void otherInstructorCannotRead() {
        assertThatThrownBy(() -> inquiryService.getInquiry(OTHER_INSTRUCTOR, false, INQUIRY_ID))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.FORBIDDEN));
    }

    @Test
    @DisplayName("다른 강사는 답글을 달 수 없고 답글이 저장되지 않는다(FORBIDDEN)")
    void otherInstructorCannotReply() {
        assertThatThrownBy(() -> inquiryService.createReply(OTHER_INSTRUCTOR, false, INQUIRY_ID, new InquiryReplyCreateDTO("답변")))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.FORBIDDEN));
        verify(inquiryReplyRepository, never()).save(any(InquiryReply.class));
        assertThat(inquiry.isAnswered()).isFalse();
    }

    @Test
    @DisplayName("그 강의의 강사가 답글을 달면 답변 완료가 된다")
    void courseInstructorReplyAnswers() {
        inquiryService.createReply(COURSE_INSTRUCTOR, false, INQUIRY_ID, new InquiryReplyCreateDTO("함께 살아야 포함돼요"));

        assertThat(inquiry.isAnswered()).isTrue();
    }

    @Test
    @DisplayName("작성자와 관리자의 접근은 그대로 허용된다 (작성자 댓글은 답변 완료로 바꾸지 않는다)")
    void writerAndAdminStillAllowed() {
        assertThat(inquiryService.getInquiry(WRITER, false, INQUIRY_ID).inquiryId()).isEqualTo(INQUIRY_ID);
        assertThat(inquiryService.getInquiry(1L, true, INQUIRY_ID).inquiryId()).isEqualTo(INQUIRY_ID);

        inquiryService.createReply(WRITER, false, INQUIRY_ID, new InquiryReplyCreateDTO("추가 질문"));
        assertThat(inquiry.isAnswered()).isFalse();
    }
}
