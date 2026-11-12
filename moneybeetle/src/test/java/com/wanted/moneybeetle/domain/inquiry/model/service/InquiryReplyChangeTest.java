package com.wanted.moneybeetle.domain.inquiry.model.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wanted.moneybeetle.domain.course.model.service.CourseService;
import com.wanted.moneybeetle.domain.inquiry.exception.InquiryErrorCode;
import com.wanted.moneybeetle.domain.inquiry.model.dao.InquiryReplyRepository;
import com.wanted.moneybeetle.domain.inquiry.model.dao.InquiryRepository;
import com.wanted.moneybeetle.domain.inquiry.model.dto.InquiryReplyCreateDTO;
import com.wanted.moneybeetle.domain.inquiry.model.dto.InquiryReplyUpdateDTO;
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
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/* comment.
*   담당 : PM 이재성 (#370, 신아림 담당 inquiry 도메인에서 나눠 받은 작업)
*   API 52-1·52-2 답변 수정·삭제
*   - 답글을 쓴 본인만 고치고 지울 수 있다 (다른 강사·관리자·문의 작성자는 FORBIDDEN)
*   - 관리자가 숨긴 답글은 고칠 수 없다 (INQUIRY_REPLY_HIDDEN, 금칙어가 있어도 이 오류가 먼저)
*   - 마지막 답변을 지우면 문의가 다시 답변 대기(WAITING)가 되고, 다른 답변이 남아 있으면 답변 완료 그대로다
*   - 답글 작성·삭제는 문의 행을 잠그고 읽는다 (동시에 들어와도 상태를 순서대로 정한다, #370 리뷰)
*   저장소만 가짜로 두고 확인한다
*  */
class InquiryReplyChangeTest {

    private static final Long WRITER = 8L;        // 문의 작성자 (학생)
    private static final Long INSTRUCTOR = 2L;    // 답변을 쓴 강사
    private static final Long OTHER = 5L;         // 다른 강사
    private static final Long INQUIRY_ID = 3L;
    private static final Long REPLY_ID = 11L;

    private InquiryRepository inquiryRepository;
    private InquiryReplyRepository replyRepository;
    private ForbiddenWordService forbiddenWordService;
    private InquiryService inquiryService;
    private Inquiry inquiry;
    private InquiryReply reply;

    @BeforeEach
    void setUp() {
        inquiryRepository = mock(InquiryRepository.class);
        replyRepository = mock(InquiryReplyRepository.class);
        forbiddenWordService = mock(ForbiddenWordService.class);
        inquiryService = new InquiryService(inquiryRepository, replyRepository, mock(CourseService.class),
                forbiddenWordService, mock(NotificationService.class),
                Clock.fixed(Instant.parse("2026-11-12T01:00:00Z"), ZoneId.of("Asia/Seoul")),
                mock(MemberService.class), mock(AdminLogService.class), mock(SystemSettingService.class));

        inquiry = Inquiry.create(WRITER, 15L, null, "부양가족에 부모님도 포함되나요?", "같이 안 살면요?");
        ReflectionTestUtils.setField(inquiry, "inquiryId", INQUIRY_ID);
        inquiry.markAnswered();
        reply = InquiryReply.create(INQUIRY_ID, INSTRUCTOR, "포함돼요.");
        ReflectionTestUtils.setField(reply, "inquiryReplyId", REPLY_ID);
        when(inquiryRepository.findByInquiryIdAndDeletedAtIsNull(INQUIRY_ID)).thenReturn(Optional.of(inquiry));
        when(inquiryRepository.findActiveByIdForUpdate(INQUIRY_ID)).thenReturn(Optional.of(inquiry));
        when(replyRepository.findByInquiryReplyIdAndInquiryIdAndDeletedAtIsNull(REPLY_ID, INQUIRY_ID)).thenReturn(Optional.of(reply));
    }

    @Test
    @DisplayName("답변을 쓴 강사는 답변을 고칠 수 있다")
    void writerUpdatesReply() {
        inquiryService.updateReply(INSTRUCTOR, INQUIRY_ID, REPLY_ID, new InquiryReplyUpdateDTO("함께 살면 포함돼요."));

        assertThat(reply.getContent()).isEqualTo("함께 살면 포함돼요.");
    }

    @Test
    @DisplayName("다른 사람(다른 강사·문의 작성자)은 답변을 고치거나 지울 수 없다")
    void othersCannotChangeReply() {
        for (Long someone : new Long[] {OTHER, WRITER}) {
            assertThatThrownBy(() -> inquiryService.updateReply(someone, INQUIRY_ID, REPLY_ID, new InquiryReplyUpdateDTO("바꿈")))
                    .isInstanceOfSatisfying(BusinessException.class,
                            e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.FORBIDDEN));
            assertThatThrownBy(() -> inquiryService.deleteReply(someone, INQUIRY_ID, REPLY_ID))
                    .isInstanceOfSatisfying(BusinessException.class,
                            e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.FORBIDDEN));
        }
        assertThat(reply.getContent()).isEqualTo("포함돼요.");
        assertThat(reply.getDeletedAt()).isNull();
        assertThat(inquiry.isAnswered()).isTrue();
    }

    @Test
    @DisplayName("관리자가 숨긴 답변은 쓴 사람도 고칠 수 없다 (INQUIRY_REPLY_HIDDEN)")
    void hiddenReplyCannotBeUpdated() {
        ReflectionTestUtils.setField(reply, "isHidden", true);

        assertThatThrownBy(() -> inquiryService.updateReply(INSTRUCTOR, INQUIRY_ID, REPLY_ID, new InquiryReplyUpdateDTO("바꿈")))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(InquiryErrorCode.INQUIRY_REPLY_HIDDEN));
        assertThat(reply.getContent()).isEqualTo("포함돼요.");
    }

    @Test
    @DisplayName("숨긴 답변에 금칙어를 넣어 고쳐도 금칙어 오류가 아니라 INQUIRY_REPLY_HIDDEN 이다")
    void hiddenReplyCheckedBeforeForbiddenWord() {
        ReflectionTestUtils.setField(reply, "isHidden", true);
        doThrow(new BusinessException(CommonErrorCode.FORBIDDEN_WORD)).when(forbiddenWordService).check(anyString());

        assertThatThrownBy(() -> inquiryService.updateReply(INSTRUCTOR, INQUIRY_ID, REPLY_ID, new InquiryReplyUpdateDTO("금칙어")))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(InquiryErrorCode.INQUIRY_REPLY_HIDDEN));
        verify(forbiddenWordService, never()).check(any());
    }

    @Test
    @DisplayName("답글 작성·삭제는 문의를 잠그고 읽는다 (잠그지 않는 조회는 쓰지 않는다)")
    void writesLockInquiry() {
        when(replyRepository.save(any(InquiryReply.class))).thenAnswer(inv -> inv.getArgument(0));

        inquiryService.createReply(WRITER, false, INQUIRY_ID, new InquiryReplyCreateDTO("추가로 궁금해요"));
        inquiryService.deleteReply(INSTRUCTOR, INQUIRY_ID, REPLY_ID);

        verify(inquiryRepository, org.mockito.Mockito.times(2)).findActiveByIdForUpdate(INQUIRY_ID);
        verify(inquiryRepository, never()).findByInquiryIdAndDeletedAtIsNull(anyLong());
    }

    @Test
    @DisplayName("그 문의에 없는 답글이면 NOT_FOUND")
    void missingReply() {
        assertThatThrownBy(() -> inquiryService.deleteReply(INSTRUCTOR, INQUIRY_ID, 99L))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.NOT_FOUND));
    }

    @Test
    @DisplayName("마지막 답변을 지우면 삭제 일시가 남고 문의가 다시 답변 대기가 된다")
    void deletingLastAnswerMakesInquiryWaiting() {
        when(replyRepository.existsByInquiryIdAndWriterIdNotAndDeletedAtIsNull(INQUIRY_ID, WRITER)).thenReturn(false);

        inquiryService.deleteReply(INSTRUCTOR, INQUIRY_ID, REPLY_ID);

        assertThat(reply.getDeletedAt()).isEqualTo(LocalDateTime.of(2026, 11, 12, 10, 0));
        assertThat(inquiry.getStatus()).isEqualTo(Inquiry.STATUS_WAITING);
    }

    @Test
    @DisplayName("다른 답변이 남아 있으면 지워도 답변 완료 그대로다")
    void otherAnswerKeepsAnswered() {
        when(replyRepository.existsByInquiryIdAndWriterIdNotAndDeletedAtIsNull(INQUIRY_ID, WRITER)).thenReturn(true);

        inquiryService.deleteReply(INSTRUCTOR, INQUIRY_ID, REPLY_ID);

        assertThat(reply.getDeletedAt()).isNotNull();
        assertThat(inquiry.getStatus()).isEqualTo(Inquiry.STATUS_ANSWERED);
    }

    @Test
    @DisplayName("문의 작성자가 자기 추가 댓글을 지우면 상태 확인을 하지 않는다 (답변 대기 문의는 그대로)")
    void writerCommentOnWaitingInquiry() {
        Inquiry waiting = Inquiry.create(WRITER, 15L, null, "질문", "내용");
        ReflectionTestUtils.setField(waiting, "inquiryId", 4L);
        InquiryReply comment = InquiryReply.create(4L, WRITER, "추가로 궁금해요");
        when(inquiryRepository.findActiveByIdForUpdate(4L)).thenReturn(Optional.of(waiting));
        when(replyRepository.findByInquiryReplyIdAndInquiryIdAndDeletedAtIsNull(12L, 4L)).thenReturn(Optional.of(comment));

        inquiryService.deleteReply(WRITER, 4L, 12L);

        assertThat(comment.getDeletedAt()).isNotNull();
        assertThat(waiting.getStatus()).isEqualTo(Inquiry.STATUS_WAITING);
        verify(replyRepository, never()).existsByInquiryIdAndWriterIdNotAndDeletedAtIsNull(anyLong(), anyLong());
    }
}
