package com.wanted.moneybeetle.domain.notification.model.entity;

/* comment.
*   담당 : DBA ② (알림)
*   알림 종류 (notification.type). 이름이 DB 에 저장되는 문자열이다 - 이름을 바꾸면 이미 저장된 알림과 어긋나므로 바꾸지 않는다.
*   - 화면·JSON 으로는 name() 그대로 내려간다 (NotificationDTO.type 은 문자열)
*   - 새 종류가 필요하면 여기에 한 줄 더한다. 다른 도메인은 Notification 엔티티가 아니라 이 enum 과 NotificationService.send 만 쓴다.
*  */
public enum NotificationType {

    /** 기능 해금 (레벨이 올라 모의투자·모의 청약이 열림) */
    UNLOCK,
    /** 문의 답변이 등록됨 */
    INQUIRY_REPLY,
    /** 청약 결과 발표 */
    HOUSING_RESULT,
    /** 시즌 랭킹 공개 */
    RANKING,
    /** 강의 반려 (강사에게 반려 사유를 알린다, #35) */
    COURSE_REJECTED
}
