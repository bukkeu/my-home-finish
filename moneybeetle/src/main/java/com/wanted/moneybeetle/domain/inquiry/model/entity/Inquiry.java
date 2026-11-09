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
 *   테이블 inquiry. course_id가 있으면 강의 문의(강사에게), 없으면 사이트 문의(운영자에게)
 *  */
@Entity
@Table(name = "inquiry")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Inquiry extends BaseTimeEntity {

    public static final String STATUS_WAITING = "WAITING";
    public static final String STATUS_ANSWERED = "ANSWERED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long inquiryId;

    private Long memberId;
    private Long courseId;          // null이면 사이트 문의
    private String category;        // 사이트 문의 분류
    private String title;
    private String content;
    private String status;
    private boolean isHidden;
    private Long hiddenBy;
    private LocalDateTime deletedAt;

    private Inquiry(Long memberId, Long courseId, String category, String title, String content) {
        this.memberId = memberId;
        this.courseId = courseId;
        this.category = category;
        this.title = title;
        this.content = content;
        this.status = STATUS_WAITING;
        this.isHidden = false;
    }

    public static Inquiry create(Long memberId, Long courseId, String category, String title, String content) {
        return new Inquiry(memberId, courseId, category, title, content);
    }

    public boolean isWrittenBy(Long memberId) {
        return this.memberId.equals(memberId);
    }

    public boolean isAnswered() {
        return STATUS_ANSWERED.equals(status);
    }

    /* 답변이 달리기 전에만 고칠 수 있다 (API 50) */
    public void change(String title, String content) {
        if (isAnswered()) {
            throw new BusinessException(InquiryErrorCode.INQUIRY_ALREADY_ANSWERED);
        }
        this.title = title;
        this.content = content;
    }

    /* 강사·관리자가 답글을 달면 답변 완료 (API 52) */
    public void markAnswered() {
        this.status = STATUS_ANSWERED;
    }

    /* 실제로 지우지 않고 삭제 일시만 기록한다 (DH-01). 시각은 서비스가 공통 Clock으로 넘긴다 */
    public void delete(LocalDateTime deletedAt) {
        this.deletedAt = deletedAt;
    }

    /* 관리자 숨김·해제 (API 107, DP-22). 해제하면 숨긴 관리자 기록도 지운다 */
    public void changeHidden(boolean hidden, Long adminId) {
        this.isHidden = hidden;
        this.hiddenBy = hidden ? adminId : null;
    }
}