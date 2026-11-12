package com.wanted.moneybeetle.domain.inquiry.model.service;

import com.wanted.moneybeetle.domain.course.model.service.CourseService;
import com.wanted.moneybeetle.domain.inquiry.model.dao.InquiryRepository;
import com.wanted.moneybeetle.domain.inquiry.model.dto.InstructorInquiryItemDTO;
import com.wanted.moneybeetle.domain.inquiry.model.dto.InstructorInquiryListDTO;
import com.wanted.moneybeetle.domain.inquiry.model.entity.Inquiry;
import com.wanted.moneybeetle.domain.inquiry.model.entity.InquiryStatus;
import com.wanted.moneybeetle.domain.member.model.service.MemberService;
import com.wanted.moneybeetle.global.common.PageResponse;
import com.wanted.moneybeetle.global.exception.BusinessException;
import com.wanted.moneybeetle.global.exception.CommonErrorCode;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/* comment.
 *   담당 : DBA ① (문의)
 *   강사 문의함 (API 82, TCH-08, #32): 내가 강사인 강의(삭제 제외)에 달린 문의, 답변 대기 글 먼저
 *   #370 : 줄마다 질문한 학생 닉네임(writerNickname)과 답변 완료 수(answeredCount)를 더한다.
 *          닉네임은 한 페이지의 작성자를 모아 member 도메인 Service 로 한 번에 읽는다 (줄마다 조회하지 않는다)
 *  */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InstructorInquiryService {

    private final InquiryRepository inquiryRepository;
    private final CourseService courseService;          // course 도메인은 Service로만
    private final MemberService memberService;          // 작성자 닉네임 (member 도메인은 Service로만, #370)

    /* API 82. courseId로 거르면 내 강의인지 확인(아니면 403). status는 WAITING·ANSWERED만, 그 외 값은 무시 */
    public InstructorInquiryListDTO findMyCourseInquiries(Long instructorId, Long courseId, String status,
                                                          Pageable pageable) {
        Map<Long, String> courseTitles = courseService.findCourseTitlesByInstructor(instructorId);
        if (courseId != null && !courseTitles.containsKey(courseId)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
        if (courseTitles.isEmpty()) {
            return InstructorInquiryListDTO.of(0, 0, PageResponse.from(Page.<InstructorInquiryItemDTO>empty(pageable)));
        }

        Set<Long> courseIds = courseId != null ? Set.of(courseId) : courseTitles.keySet();
        String statusFilter = InquiryStatus.filterOf(status);
        Page<Inquiry> page = inquiryRepository.searchForInstructor(
                courseIds, statusFilter, Inquiry.STATUS_WAITING, pageable);
        long waitingCount = inquiryRepository.countByCourseIdInAndStatusAndDeletedAtIsNullAndIsHiddenFalse(
                courseTitles.keySet(), Inquiry.STATUS_WAITING);
        long answeredCount = inquiryRepository.countByCourseIdInAndStatusAndDeletedAtIsNullAndIsHiddenFalse(
                courseTitles.keySet(), Inquiry.STATUS_ANSWERED);
        Map<Long, String> nicknames = memberService.findNicknames(
                page.getContent().stream().map(Inquiry::getMemberId).collect(Collectors.toSet()));

        return InstructorInquiryListDTO.of(waitingCount, answeredCount, PageResponse.from(page.map(inquiry ->
                InstructorInquiryItemDTO.of(inquiry, courseTitles.get(inquiry.getCourseId()),
                        nicknames.get(inquiry.getMemberId())))));
    }

    /* 강사 내 강의 목록(API 72, course 도메인에서 호출): 강의별 답변 대기 문의 수. 없는 강의는 결과에 없음 */
    public Map<Long, Long> countWaitingByCourse(Collection<Long> courseIds) {
        if (courseIds.isEmpty()) {
            return Map.of();
        }
        return inquiryRepository.countByCourse(courseIds, Inquiry.STATUS_WAITING).stream()
                .collect(Collectors.toMap(
                        InquiryRepository.CourseWaitingCount::getCourseId,
                        InquiryRepository.CourseWaitingCount::getWaiting));
    }
}
