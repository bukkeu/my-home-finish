package com.wanted.moneybeetle.domain.inquiry.model.dao;

import com.wanted.moneybeetle.domain.inquiry.model.entity.InquiryReply;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/* comment.
 *   담당 : DBA ① (문의)
 *   테이블 inquiry_reply
 *  */
public interface InquiryReplyRepository extends JpaRepository<InquiryReply, Long> {

    /* 문의 상세의 답글: 지워지지 않은 것만, 오래된 순. 같은 시각이면 ID로 순서 고정 */
    List<InquiryReply> findByInquiryIdAndDeletedAtIsNullOrderByCreatedAtAscInquiryReplyIdAsc(Long inquiryId);
}