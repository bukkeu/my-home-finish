package com.wanted.moneybeetle.domain.inquiry.model.service;

import com.wanted.moneybeetle.domain.course.model.service.CourseService;
import com.wanted.moneybeetle.domain.inquiry.model.dao.InquiryRepository;
import com.wanted.moneybeetle.domain.inquiry.model.dto.InstructorInquiryItemDTO;
import com.wanted.moneybeetle.domain.inquiry.model.dto.InstructorInquiryListDTO;
import com.wanted.moneybeetle.domain.inquiry.model.entity.Inquiry;
import com.wanted.moneybeetle.global.common.PageResponse;
import com.wanted.moneybeetle.global.exception.BusinessException;
import com.wanted.moneybeetle.global.exception.CommonErrorCode;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/* comment.
 *   담당 : DBA ① (문의)
 *   강사 문의함 (API 82, TCH-08, #32): 내가 강사인 강의(삭제 제외)에 달린 문의, 답변 대기 글 먼저
 *  */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InstructorInquiryService {

    private final InquiryRepository inquiryRepository;
    private final CourseService courseService;          // course 도메인은 Service로만

    /* API 82. courseId로 거르면 내 강의인지 확인(아니면 403). status는 WAITING·ANSWERED만, 그 외 값은 무시 */
    public InstructorInquiryListDTO findMyCourseInquiries(Long instructorId, Long courseId, String status,
                                                          Pageable pageable) {
        Map<Long, String> courseTitles = courseService.findCourseTitlesByInstructor(instructorId);
        if (courseId != null && !courseTitles.containsKey(courseId)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
        if (courseTitles.isEmpty()) {
            return InstructorInquiryListDTO.of(0, PageResponse.from(Page.<InstructorInquiryItemDTO>empty(pageable)));
        }

        Set<Long> courseIds = courseId != null ? Set.of(courseId) : courseTitles.keySet();
        String statusFilter = isKnownStatus(status) ? status : null;
        Page<Inquiry> page = inquiryRepository.searchForInstructor(
                courseIds, statusFilter, Inquiry.STATUS_WAITING, pageable);
        long waitingCount = inquiryRepository.countByCourseIdInAndStatusAndDeletedAtIsNullAndIsHiddenFalse(
                courseTitles.keySet(), Inquiry.STATUS_WAITING);

        return InstructorInquiryListDTO.of(waitingCount, PageResponse.from(page.map(inquiry ->
                InstructorInquiryItemDTO.of(inquiry, courseTitles.get(inquiry.getCourseId())))));
    }

    private boolean isKnownStatus(String status) {
        return Inquiry.STATUS_WAITING.equals(status) || Inquiry.STATUS_ANSWERED.equals(status);
    }
}
