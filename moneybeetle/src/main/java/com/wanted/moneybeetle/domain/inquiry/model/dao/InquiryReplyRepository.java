package com.wanted.moneybeetle.domain.inquiry.model.dao;

import com.wanted.moneybeetle.domain.inquiry.model.entity.InquiryReply;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/* comment.
 *   담당 : DBA ① (문의)
 *   테이블 inquiry_reply
 *  */
public interface InquiryReplyRepository extends JpaRepository<InquiryReply, Long> {

    /* 문의 상세의 답글: 지워지지 않은 것만, 오래된 순. 같은 시각이면 ID로 순서 고정 */
    List<InquiryReply> findByInquiryIdAndDeletedAtIsNullOrderByCreatedAtAscInquiryReplyIdAsc(Long inquiryId);

    /* 답변 수정·삭제 (API 52-1·52-2, #370): 그 문의에 달린, 지워지지 않은 답글 하나 */
    Optional<InquiryReply> findByInquiryReplyIdAndInquiryIdAndDeletedAtIsNull(Long inquiryReplyId, Long inquiryId);

    /* 남은 답변이 있는지 (API 52-2): 문의 작성자가 아닌 사람(강사·관리자)이 쓴, 지워지지 않은 답글 */
    boolean existsByInquiryIdAndWriterIdNotAndDeletedAtIsNull(Long inquiryId, Long inquiryWriterId);
}