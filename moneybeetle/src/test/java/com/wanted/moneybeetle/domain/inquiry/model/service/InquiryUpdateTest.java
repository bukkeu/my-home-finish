package com.wanted.moneybeetle.domain.inquiry.model.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.wanted.moneybeetle.domain.course.model.service.CourseService;
import com.wanted.moneybeetle.domain.inquiry.exception.InquiryErrorCode;
import com.wanted.moneybeetle.domain.inquiry.model.dao.InquiryReplyRepository;
import com.wanted.moneybeetle.domain.inquiry.model.dao.InquiryRepository;
import com.wanted.moneybeetle.domain.inquiry.model.dto.InquiryUpdateDTO;
import com.wanted.moneybeetle.domain.inquiry.model.entity.Inquiry;
import com.wanted.moneybeetle.domain.member.model.service.MemberService;
import com.wanted.moneybeetle.domain.notification.model.service.NotificationService;
import com.wanted.moneybeetle.domain.operation.model.service.AdminLogService;
import com.wanted.moneybeetle.domain.operation.model.service.ForbiddenWordService;
import com.wanted.moneybeetle.domain.operation.model.service.SystemSettingService;
import com.wanted.moneybeetle.global.exception.BusinessException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/* comment.
*   담당 : DBA ① 신아림 (#245, 숨긴 문의 수정 막기)
*   API 50 문의 수정: 작성자만, 답변 전에만, 관리자가 숨긴 글은 안 됨(INQUIRY_HIDDEN). 저장소만 가짜로 두고 확인한다
*  */
class InquiryUpdateTest {

    private static final Long WRITER = 8L;
    private static final Long INQUIRY_ID = 3L;

    private InquiryRepository inquiryRepository;
    private InquiryService inquiryService;
    private Inquiry inquiry;

    @BeforeEach
    void setUp() {
        inquiryRepository = mock(InquiryRepository.class);
        inquiryService = new InquiryService(inquiryRepository, mock(InquiryReplyRepository.class), mock(CourseService.class),
                mock(ForbiddenWordService.class), mock(NotificationService.class),
                Clock.fixed(Instant.parse("2026-11-11T01:00:00Z"), ZoneId.of("Asia/Seoul")),
                mock(MemberService.class), mock(AdminLogService.class), mock(SystemSettingService.class));

        inquiry = Inquiry.create(WRITER, null, "계정·캐시", "원래 제목", "원래 내용");
        ReflectionTestUtils.setField(inquiry, "inquiryId", INQUIRY_ID);
        when(inquiryRepository.findByInquiryIdAndDeletedAtIsNull(INQUIRY_ID)).thenReturn(Optional.of(inquiry));
    }

    @Test
    @DisplayName("답변 전이고 숨기지 않은 내 문의는 고칠 수 있다")
    void updatesWaitingInquiry() {
        inquiryService.updateInquiry(WRITER, INQUIRY_ID, new InquiryUpdateDTO("새 제목", "새 내용"));

        assertThat(inquiry.getTitle()).isEqualTo("새 제목");
        assertThat(inquiry.getContent()).isEqualTo("새 내용");
    }

    @Test
    @DisplayName("관리자가 숨긴 문의는 작성자도 고칠 수 없고(INQUIRY_HIDDEN) 원래 글이 그대로 남는다")
    void rejectsHiddenInquiry() {
        inquiry.changeHidden(true, 1L);

        assertThatThrownBy(() -> inquiryService.updateInquiry(WRITER, INQUIRY_ID,
                new InquiryUpdateDTO("관리자에 의해 숨김 처리된 글이에요", "관리자에 의해 숨김 처리된 글이에요")))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(InquiryErrorCode.INQUIRY_HIDDEN));
        assertThat(inquiry.getTitle()).isEqualTo("원래 제목");
        assertThat(inquiry.getContent()).isEqualTo("원래 내용");
    }

    @Test
    @DisplayName("숨김을 해제하면 다시 고칠 수 있다")
    void updatesAfterUnhide() {
        inquiry.changeHidden(true, 1L);
        inquiry.changeHidden(false, 1L);

        inquiryService.updateInquiry(WRITER, INQUIRY_ID, new InquiryUpdateDTO("새 제목", "새 내용"));

        assertThat(inquiry.getTitle()).isEqualTo("새 제목");
    }

    @Test
    @DisplayName("답변이 달린 문의는 고칠 수 없다(INQUIRY_ALREADY_ANSWERED)")
    void rejectsAnsweredInquiry() {
        inquiry.markAnswered();

        assertThatThrownBy(() -> inquiryService.updateInquiry(WRITER, INQUIRY_ID, new InquiryUpdateDTO("새 제목", "새 내용")))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(InquiryErrorCode.INQUIRY_ALREADY_ANSWERED));
    }
}
