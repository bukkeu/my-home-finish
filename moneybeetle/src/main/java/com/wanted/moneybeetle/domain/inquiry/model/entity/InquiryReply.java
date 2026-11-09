package com.wanted.moneybeetle.domain.inquiry.model.entity;

import com.wanted.moneybeetle.global.common.BaseTimeEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/* comment.
 *   담당 : DBA ① (문의)
 *   테이블 inquiry_reply. 강사·관리자 답글과 작성자의 추가 댓글
 *  */
@Entity
@Table(name = "inquiry_reply")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InquiryReply extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long inquiryReplyId;

    private Long inquiryId;
    private Long writerId;
    private String content;
    private boolean isHidden;
    private LocalDateTime deletedAt;

    private InquiryReply(Long inquiryId, Long writerId, String content) {
        this.inquiryId = inquiryId;
        this.writerId = writerId;
        this.content = content;
        this.isHidden = false;
    }

    public static InquiryReply create(Long inquiryId, Long writerId, String content) {
        return new InquiryReply(inquiryId, writerId, content);
    }
}