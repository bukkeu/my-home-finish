package com.wanted.moneybeetle.domain.inquiry.model.service;

import com.wanted.moneybeetle.domain.course.model.service.CourseService;
import com.wanted.moneybeetle.domain.inquiry.exception.InquiryErrorCode;
import com.wanted.moneybeetle.domain.inquiry.model.dao.InquiryReplyRepository;
import com.wanted.moneybeetle.domain.inquiry.model.dao.InquiryRepository;
import com.wanted.moneybeetle.domain.inquiry.model.dto.AdminInquiryListItemDTO;
import com.wanted.moneybeetle.domain.inquiry.model.dto.InquiryCreateDTO;
import com.wanted.moneybeetle.domain.inquiry.model.dto.InquiryDetailDTO;
import com.wanted.moneybeetle.domain.inquiry.model.dto.InquiryListItemDTO;
import com.wanted.moneybeetle.domain.inquiry.model.dto.InquiryReplyCreateDTO;
import com.wanted.moneybeetle.domain.inquiry.model.dto.InquiryUpdateDTO;
import com.wanted.moneybeetle.domain.inquiry.model.dto.MyInquiryItemDTO;
import com.wanted.moneybeetle.domain.inquiry.model.entity.Inquiry;
import com.wanted.moneybeetle.domain.inquiry.model.entity.InquiryReply;
import com.wanted.moneybeetle.domain.member.model.service.MemberService;
import com.wanted.moneybeetle.domain.notification.model.entity.Notification;
import com.wanted.moneybeetle.domain.notification.model.service.NotificationService;
import com.wanted.moneybeetle.domain.operation.model.service.ForbiddenWordService;
import com.wanted.moneybeetle.global.common.PageResponse;
import com.wanted.moneybeetle.global.exception.BusinessException;
import com.wanted.moneybeetle.global.exception.CommonErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wanted.moneybeetle.domain.operation.model.service.AdminLogService;
import java.util.LinkedHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/* comment.
 *   담당 : DBA ① (문의)
 *   API 47~52 문의 게시판. 볼 수 있는 사람: 작성자, 그 강의의 강사, 관리자 (SP-40)
 *   API 106~107 관리자 문의 게시판 관리(조회·숨김·해제, #38)
 *  */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InquiryService {

    private static final int DAILY_LIMIT = 10;                          // DP-30. TODO: system_setting 서비스가 생기면 그 값으로
    private static final String NOTIFICATION_TYPE = Notification.TYPE_INQUIRY_REPLY;
    private static final String NOTIFICATION_TITLE = "문의에 답변이 등록됐어요";
    private static final String HIDDEN_TEXT = "관리자에 의해 숨김 처리된 글이에요";  // DP-22
    private static final String ROLE_MEMBER = "MEMBER";
    private static final String ROLE_INSTRUCTOR = "INSTRUCTOR";
    private static final String ROLE_ADMIN = "ADMIN";
    private static final Long NO_COURSE = -1L;                          // 빈 IN () 방지
    static final String ACTION_HIDE = "INQUIRY_HIDE";
    static final String ACTION_UNHIDE = "INQUIRY_UNHIDE";
    static final String LOG_TARGET_TYPE = "INQUIRY";
    private static final ObjectMapper JSON = new ObjectMapper();

    private final InquiryRepository inquiryRepository;
    private final InquiryReplyRepository inquiryReplyRepository;
    private final CourseService courseService;
    private final ForbiddenWordService forbiddenWordService;
    private final NotificationService notificationService;
    private final Clock clock;                                          // Asia/Seoul 공통 시계 (global TimeConfig)
    private final MemberService memberService;                          // 하루 한도용 회원 행 잠금, 작성자 닉네임 조회
    private final AdminLogService adminLogService;                      // 숨김·해제 작업 기록 (화면설계 감사 로그)

    /* API 47 문의 목록 (관리자가 아니면 숨긴 글은 목록에서 빠진다, #38) */
    public PageResponse<InquiryListItemDTO> findInquiries(Long memberId, boolean admin,
                                                          Long courseId, String status, Pageable pageable) {
        String statusFilter = isKnownStatus(status) ? status : null;
        List<Long> myCourseIds = courseService.findCourseIdsByInstructor(memberId);
        if (myCourseIds.isEmpty()) {
            myCourseIds = List.of(NO_COURSE);
        }

        Page<Inquiry> page = inquiryRepository.search(courseId, statusFilter, admin, memberId, myCourseIds, pageable);
        Map<Long, String> nicknames = findNicknames(page.getContent().stream().map(Inquiry::getMemberId).toList());

        return PageResponse.from(page.map(inquiry -> InquiryListItemDTO.from(
                inquiry,
                maskIfHidden(inquiry.isHidden(), admin, inquiry.getTitle()),
                nicknames.get(inquiry.getMemberId()))));
    }

    /* API 149 내가 쓴 문의 모음 (마이페이지, #71). 숨긴 글은 제목을 안내 문구로 가린다.
     * 강의 제목은 수강생에게 보이는 강의만 넣는다 (비공개·삭제된 강의로 가는 버튼을 만들지 않게) */
    public PageResponse<MyInquiryItemDTO> findMyInquiries(Long memberId, Pageable pageable) {
        Page<Inquiry> page = inquiryRepository.findMine(memberId, pageable);
        Map<Long, String> courseTitles = courseService.findVisibleCourseTitles(page.getContent().stream()
                .map(Inquiry::getCourseId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()));
        return PageResponse.from(page.map(inquiry -> MyInquiryItemDTO.from(
                inquiry,
                maskIfHidden(inquiry.isHidden(), false, inquiry.getTitle()),
                inquiry.getCourseId() == null ? null : courseTitles.get(inquiry.getCourseId()))));
    }

    /* API 48 문의 작성 */
    @Transactional
    public Long createInquiry(Long memberId, InquiryCreateDTO request) {
        if (request.courseId() != null) {
            courseService.checkCourseVisible(request.courseId());
        }
        forbiddenWordService.check(request.title());
        forbiddenWordService.check(request.content());

        // 같은 회원의 작성 요청을 한 줄로 세운다: 회원 행을 잠근 뒤 세고 저장 (트랜잭션 끝까지 잠금 유지)
        memberService.lockBalance(memberId);
        LocalDateTime startOfToday = LocalDate.now(clock).atStartOfDay();
        if (inquiryRepository.countByMemberIdAndCreatedAtGreaterThanEqual(memberId, startOfToday) >= DAILY_LIMIT) {
            throw new BusinessException(InquiryErrorCode.INQUIRY_DAILY_LIMIT);
        }

        String category = request.courseId() == null ? request.category() : null;
        Inquiry inquiry = Inquiry.create(memberId, request.courseId(), category, request.title(), request.content());
        return inquiryRepository.save(inquiry).getInquiryId();
    }

    /* API 49 문의 상세 */
    public InquiryDetailDTO getInquiry(Long memberId, boolean admin, Long inquiryId) {
        Inquiry inquiry = getInquiryOrThrow(inquiryId);
        Long instructorId = findInstructorId(inquiry);
        boolean instructor = memberId.equals(instructorId);
        if (!admin && !instructor && !inquiry.isWrittenBy(memberId)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }

        List<InquiryReply> replies =
                inquiryReplyRepository.findByInquiryIdAndDeletedAtIsNullOrderByCreatedAtAscInquiryReplyIdAsc(inquiryId);

        Set<Long> writerIds = new HashSet<>();
        writerIds.add(inquiry.getMemberId());
        replies.forEach(reply -> writerIds.add(reply.getWriterId()));
        Map<Long, String> nicknames = findNicknames(writerIds);

        List<InquiryDetailDTO.Reply> replyDTOs = replies.stream()
                .map(reply -> new InquiryDetailDTO.Reply(
                        reply.getInquiryReplyId(),
                        new InquiryDetailDTO.Writer(reply.getWriterId(), nicknames.get(reply.getWriterId()),
                                roleOf(reply.getWriterId(), inquiry, instructorId)),
                        maskIfHidden(reply.isHidden(), admin, reply.getContent()),
                        reply.getCreatedAt()))
                .toList();

        InquiryDetailDTO.Writer writer = new InquiryDetailDTO.Writer(
                inquiry.getMemberId(), nicknames.get(inquiry.getMemberId()), ROLE_MEMBER);

        return InquiryDetailDTO.of(inquiry,
                maskIfHidden(inquiry.isHidden(), admin, inquiry.getTitle()),
                maskIfHidden(inquiry.isHidden(), admin, inquiry.getContent()),
                writer, replyDTOs);
    }

    /* API 50 문의 수정: 작성자만, 답변 전에만 */
    @Transactional
    public void updateInquiry(Long memberId, Long inquiryId, InquiryUpdateDTO request) {
        Inquiry inquiry = getInquiryOrThrow(inquiryId);
        if (!inquiry.isWrittenBy(memberId)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
        forbiddenWordService.check(request.title());
        forbiddenWordService.check(request.content());
        inquiry.change(request.title(), request.content());   // 답변 완료면 Inquiry.change()가 409 INQUIRY_ALREADY_ANSWERED
    }

    /* API 51 문의 삭제: 작성자·관리자, Soft Delete */
    @Transactional
    public void deleteInquiry(Long memberId, boolean admin, Long inquiryId) {
        Inquiry inquiry = getInquiryOrThrow(inquiryId);
        if (!admin && !inquiry.isWrittenBy(memberId)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
        inquiry.delete(LocalDateTime.now(clock));
    }

    /* API 52 답글 작성: 강사·관리자 답글이면 ANSWERED + 작성자에게 알림 */
    @Transactional
    public Long createReply(Long memberId, boolean admin, Long inquiryId, InquiryReplyCreateDTO request) {
        Inquiry inquiry = getInquiryOrThrow(inquiryId);
        boolean instructor = memberId.equals(findInstructorId(inquiry));
        boolean writer = inquiry.isWrittenBy(memberId);
        if (!admin && !instructor && !writer) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
        forbiddenWordService.check(request.content());

        InquiryReply reply = inquiryReplyRepository.save(InquiryReply.create(inquiryId, memberId, request.content()));

        if ((admin || instructor) && !writer) {
            inquiry.markAnswered();
            notificationService.send(inquiry.getMemberId(), NOTIFICATION_TYPE, NOTIFICATION_TITLE,
                    "/user/inquiry/" + inquiryId);
        }
        return reply.getInquiryReplyId();
    }

    /* ===== 관리자 (#38) ===== */

    /* API 106 관리자 문의 게시판: 숨김·삭제 포함, 필터는 모두 선택 (null이면 거르지 않음) */
    public PageResponse<AdminInquiryListItemDTO> findInquiriesForAdmin(Long courseId, String status,
                                                                       Boolean hidden, Boolean deleted,
                                                                       Pageable pageable) {
        String statusFilter = isKnownStatus(status) ? status : null;
        Page<Inquiry> page = inquiryRepository.searchForAdmin(courseId, statusFilter, hidden, deleted, pageable);
        Map<Long, String> nicknames = findNicknames(page.getContent().stream().map(Inquiry::getMemberId).toList());

        return PageResponse.from(page.map(inquiry ->
                AdminInquiryListItemDTO.of(inquiry, nicknames.get(inquiry.getMemberId()))));
    }

    /* API 107 숨김·해제 (DP-22). 숨기면 사용자 목록에서 빠지고, 상세는 안내 문구로 보인다 */
    @Transactional
    public void changeHidden(Long adminId, Long inquiryId, boolean hidden) {
        Inquiry inquiry = getInquiryOrThrow(inquiryId);
        boolean before = inquiry.isHidden();
        Long beforeHiddenBy = inquiry.getHiddenBy();
        if (before == hidden) {
            return;                                                     // 바뀐 게 없으면 기록도 남기지 않는다
        }
        inquiry.changeHidden(hidden, adminId);
        // 같은 트랜잭션에서 관리자 작업 기록을 남긴다. 해제하면 hidden_by 가 지워지므로 누가 숨겼는지 기록에 남긴다
        adminLogService.record(adminId, hidden ? ACTION_HIDE : ACTION_UNHIDE, LOG_TARGET_TYPE, inquiryId,
                hiddenLogJson(before, hidden, beforeHiddenBy));
    }


    /* ===== 내부 도우미 ===== */

    private static String hiddenLogJson(boolean before, boolean after, Long beforeHiddenBy) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("before", before);
        detail.put("after", after);
        detail.put("beforeHiddenBy", beforeHiddenBy);
        try {
            return JSON.writeValueAsString(detail);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("작업 기록을 만들지 못했습니다.", e);
        }
    }

    private Inquiry getInquiryOrThrow(Long inquiryId) {
        return inquiryRepository.findByInquiryIdAndDeletedAtIsNull(inquiryId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
    }

    private Long findInstructorId(Inquiry inquiry) {
        return inquiry.getCourseId() == null ? null : courseService.findInstructorId(inquiry.getCourseId());
    }

    /* 답글 작성자 역할: 강의 강사 → INSTRUCTOR, 문의 작성자 → MEMBER, 그 외(답글 권한상 관리자뿐) → ADMIN */
    private String roleOf(Long writerId, Inquiry inquiry, Long instructorId) {
        if (Objects.equals(writerId, instructorId)) {
            return ROLE_INSTRUCTOR;
        }
        if (inquiry.isWrittenBy(writerId)) {
            return ROLE_MEMBER;
        }
        return ROLE_ADMIN;
    }

    private String maskIfHidden(boolean hidden, boolean admin, String text) {
        return hidden && !admin ? HIDDEN_TEXT : text;
    }

    private boolean isKnownStatus(String status) {
        return Inquiry.STATUS_WAITING.equals(status) || Inquiry.STATUS_ANSWERED.equals(status);
    }

    /* 작성자 닉네임 (member 도메인 Service로만 조회, 탈퇴 회원은 "탈퇴한 사용자") */
    private Map<Long, String> findNicknames(Collection<Long> memberIds) {
        return memberService.findNicknames(memberIds);
    }
}