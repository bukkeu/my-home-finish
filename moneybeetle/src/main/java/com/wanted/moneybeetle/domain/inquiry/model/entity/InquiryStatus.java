package com.wanted.moneybeetle.domain.inquiry.model.entity;

/* comment.
 *   담당 : DBA ① 신아림 (#297, 문의 상태)
 *   문의 상태 (inquiry.status). 이름이 DB 에 저장되는 문자열이다 - 이름을 바꾸면 저장된 값과 어긋나므로 바꾸지 않는다.
 *   - WAITING  : 답변 대기 (작성 직후)
 *   - ANSWERED : 답변 완료 (강사·관리자가 답글을 달면)
 *   목록 API 의 status 필터는 filterOf 로 거른다: 아는 값이면 그 이름, 비었거나 모르는 값이면 null(거르지 않음)
 *  */
public enum InquiryStatus {

    WAITING,
    ANSWERED;

    /* 목록 필터 값. 대소문자·공백은 그대로 비교한다 (API 명세의 값만 받는다) */
    public static String filterOf(String raw) {
        if (raw == null) {
            return null;
        }
        for (InquiryStatus status : values()) {
            if (status.name().equals(raw)) {
                return status.name();
            }
        }
        return null;
    }
}
