package com.wanted.moneybeetle.domain.course.model.entity;

/* comment.
 *   담당 : DBA ① 신아림 (#295, 강의 상태)
 *   강의 상태 (course.status). 이름이 DB 에 저장되는 문자열이다 - 이름을 바꾸면 저장된 값과 어긋나므로 바꾸지 않는다.
 *   DRAFT(작성 중) -> REVIEW(검수 중) -> APPROVED(허가) / REJECTED(반려). 허가된 강의를 고치면 다시 REVIEW
 *   목록 API(관리자 89, 강사 72)의 status 필터는 filterOf 로 거른다: 아는 값이면 그 상태, 비었거나 모르는 값이면 null(거르지 않음)
 *  */
public enum CourseStatus {

    DRAFT,
    REVIEW,
    APPROVED,
    REJECTED;

    /* 목록 필터 값. 대소문자·공백은 그대로 비교한다 (API 명세의 값만 받는다) */
    public static CourseStatus filterOf(String raw) {
        if (raw == null) {
            return null;
        }
        for (CourseStatus status : values()) {
            if (status.name().equals(raw)) {
                return status;
            }
        }
        return null;
    }
}
