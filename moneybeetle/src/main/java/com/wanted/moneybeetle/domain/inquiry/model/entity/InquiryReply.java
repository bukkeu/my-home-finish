package com.wanted.moneybeetle.domain.inquiry.model.entity;

import com.wanted.moneybeetle.domain.inquiry.exception.InquiryErrorCode;
import com.wanted.moneybeetle.global.common.BaseTimeEntity;
import com.wanted.moneybeetle.global.exception.BusinessException;
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

    public boolean isWrittenBy(Long memberId) {
        return this.writerId.equals(memberId);
    }

    /* 답변 수정 (API 52-1, #370). 관리자가 숨긴 답변은 고칠 수 없다 (숨긴 글은 안내 문구로 보여서, 고치면 원래 글을 덮어쓸 수 있다) */
    public void changeContent(String content) {
        checkEditable();
        this.content = content;
    }

    /* 고칠 수 있는 답글인지. 서비스가 금칙어 검사보다 먼저 불러 숨긴 답글이면 금칙어 오류 대신 INQUIRY_REPLY_HIDDEN 을 준다 (#370 리뷰) */
    public void checkEditable() {
        if (isHidden) {
            throw new BusinessException(InquiryErrorCode.INQUIRY_REPLY_HIDDEN);
        }
    }

    /* 답변 삭제 (API 52-2, #370). 실제로 지우지 않고 삭제 일시만 기록한다 (DH-01) */
    public void delete(LocalDateTime deletedAt) {
        this.deletedAt = deletedAt;
    }
}