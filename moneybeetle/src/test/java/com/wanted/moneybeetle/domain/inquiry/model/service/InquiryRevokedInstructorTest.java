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
import com.wanted.moneybeetle.domain.inquiry.model.dao.InquiryReplyRepository;
import com.wanted.moneybeetle.domain.inquiry.model.dao.InquiryRepository;
import com.wanted.moneybeetle.domain.inquiry.model.dto.InquiryReplyCreateDTO;
import com.wanted.moneybeetle.domain.inquiry.model.entity.Inquiry;
import com.wanted.moneybeetle.domain.inquiry.model.entity.InquiryReply;
import com.wanted.moneybeetle.domain.member.model.service.MemberService;
import com.wanted.moneybeetle.domain.notification.model.entity.NotificationType;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

/* comment.
*   #410 강사 권한 회수 후 문의 API 재접근 차단
*   관리자가 강사 권한을 회수(API 103)한 사람이 일반 회원으로 다시 로그인하면 (admin=false, 회원 도메인이 "지금 강사 권한 없음"이라고 답한다)
*   자기가 맡았던 강의의 문의를 보거나(API 49) 답변을 달거나(API 52) 목록에서 볼 수(API 47) 없어야 한다.
*   문의 작성자와 관리자의 권한은 그대로다. 저장소와 다른 서비스는 가짜로 둔다
*  */
class InquiryRevokedInstructorTest {

    private static final Long WRITER = 8L;
    private static final Long ADMIN_ID = 1L;
    private static final Long REVOKED_INSTRUCTOR = 2L;     // 강의 15의 담당자였지만 권한을 회수당했다
    private static final Long COURSE_ID = 15L;
    private static final Long INQUIRY_ID = 7L;
    private static final List<Long> NO_COURSE = List.of(-1L);
    private static final Pageable PAGE = PageRequest.of(0, 20);

    private InquiryRepository inquiryRepository;
    private InquiryReplyRepository inquiryReplyRepository;
    private NotificationService notificationService;
    private InquiryService inquiryService;
    private Inquiry inquiry;

    @BeforeEach
    void setUp() {
        inquiryRepository = mock(InquiryRepository.class);
        inquiryReplyRepository = mock(InquiryReplyRepository.class);
        notificationService = mock(NotificationService.class);
        CourseService courseService = mock(CourseService.class);
        MemberService memberService = mock(MemberService.class);
        inquiryService = new InquiryService(inquiryRepository, inquiryReplyRepository, courseService,
                mock(ForbiddenWordService.class), notificationService,
                Clock.fixed(Instant.parse("2026-11-12T01:00:00Z"), ZoneId.of("Asia/Seoul")),
                memberService, mock(AdminLogService.class), mock(SystemSettingService.class));

        inquiry = Inquiry.create(WRITER, COURSE_ID, null, "부양가족에 부모님도 포함되나요?", "같이 안 살면요?");
        ReflectionTestUtils.setField(inquiry, "inquiryId", INQUIRY_ID);
        when(inquiryRepository.findByInquiryIdAndDeletedAtIsNull(INQUIRY_ID)).thenReturn(Optional.of(inquiry));
        when(inquiryRepository.findActiveByIdForUpdate(INQUIRY_ID)).thenReturn(Optional.of(inquiry));
        when(courseService.findInstructorId(COURSE_ID)).thenReturn(REVOKED_INSTRUCTOR);   // 강의 담당자 ID는 회수 뒤에도 남는다
        when(courseService.findCourseIdsByInstructor(REVOKED_INSTRUCTOR)).thenReturn(List.of(COURSE_ID));
        when(memberService.isInstructorRevoked(REVOKED_INSTRUCTOR)).thenReturn(true);
        when(memberService.findNicknames(anyCollection())).thenReturn(Map.of(WRITER, "민지", REVOKED_INSTRUCTOR, "박서연"));
        when(inquiryReplyRepository.findByInquiryIdAndDeletedAtIsNullOrderByCreatedAtAscInquiryReplyIdAsc(INQUIRY_ID))
                .thenReturn(List.of());
        when(inquiryReplyRepository.save(any(InquiryReply.class))).thenAnswer(invocation -> {
            InquiryReply saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "inquiryReplyId", 1L);
            return saved;
        });
        when(inquiryRepository.search(any(), any(), eq(false), any(), any(), any())).thenReturn(new PageImpl<>(List.of()));
    }

    @Test
    @DisplayName("권한을 회수당한 뒤 일반 회원으로 다시 로그인하면 맡았던 강의의 문의 상세를 볼 수 없다(FORBIDDEN)")
    void revokedInstructorCannotRead() {
        assertThatThrownBy(() -> inquiryService.getInquiry(REVOKED_INSTRUCTOR, false, INQUIRY_ID))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.FORBIDDEN));
    }

    @Test
    @DisplayName("권한을 회수당한 뒤에는 답변을 달 수 없고, 답글·답변 완료·알림이 남지 않는다(FORBIDDEN)")
    void revokedInstructorCannotReply() {
        assertThatThrownBy(() -> inquiryService.createReply(REVOKED_INSTRUCTOR, false, INQUIRY_ID,
                new InquiryReplyCreateDTO("함께 살아야 포함돼요")))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.FORBIDDEN));

        verify(inquiryReplyRepository, never()).save(any(InquiryReply.class));
        verify(notificationService, never()).send(any(), any(NotificationType.class), anyString(), anyString());
        assertThat(inquiry.isAnswered()).isFalse();
    }

    @Test
    @DisplayName("권한을 회수당한 뒤에는 문의 목록에서도 맡았던 강의의 문의를 강사로서 보지 않는다")
    void revokedInstructorListHasNoCourseInquiries() {
        inquiryService.findInquiries(REVOKED_INSTRUCTOR, false, null, null, PAGE);

        verify(inquiryRepository).search(isNull(), isNull(), eq(false), eq(REVOKED_INSTRUCTOR), eq(NO_COURSE), eq(PAGE));
    }

    @Test
    @DisplayName("강의 강사 권한이 회수돼도 문의 작성자는 상세를 보고 댓글을 달 수 있다 (댓글은 답변 완료로 바꾸지 않는다)")
    void writerStillAllowed() {
        assertThat(inquiryService.getInquiry(WRITER, false, INQUIRY_ID).inquiryId()).isEqualTo(INQUIRY_ID);

        inquiryService.createReply(WRITER, false, INQUIRY_ID, new InquiryReplyCreateDTO("추가 질문이에요"));

        assertThat(inquiry.isAnswered()).isFalse();
    }

    @Test
    @DisplayName("강의 강사 권한이 회수돼도 관리자는 상세를 보고 답변할 수 있고, 답변하면 답변 완료·작성자 알림")
    void adminStillAllowed() {
        assertThat(inquiryService.getInquiry(ADMIN_ID, true, INQUIRY_ID).inquiryId()).isEqualTo(INQUIRY_ID);

        inquiryService.createReply(ADMIN_ID, true, INQUIRY_ID, new InquiryReplyCreateDTO("관리자가 대신 답해요"));

        assertThat(inquiry.isAnswered()).isTrue();
        verify(notificationService).send(eq(WRITER), any(NotificationType.class), anyString(), eq("/user/inquiry/" + INQUIRY_ID));
    }
}
