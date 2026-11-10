package com.wanted.moneybeetle.domain.course.exception;

import com.wanted.moneybeetle.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/* comment.
 *   담당 : DBA ① (강의·퀴즈)
 *   COURSE_NOT_PUBLIC, ALREADY_SCRAPPED, TOO_MANY_LESSONS, COURSE_HAS_LEARNERS, NO_LESSON, CATEGORY_IN_USE,
 *   COURSE_NOT_IN_REVIEW, COURSE_NOT_APPROVED, REASON_REQUIRED (#35), CATEGORY_DEPTH_EXCEEDED (#34), COURSE_NOT_REQUESTABLE (#32),
 *   CATEGORY_NOT_SELECTABLE, COURSE_IN_REVIEW (#30)
 *  */
@Getter
@RequiredArgsConstructor
public enum CourseErrorCode implements ErrorCode {

    COURSE_NOT_PUBLIC(HttpStatus.NOT_FOUND, "공개되지 않은 강의예요"),                          // SP-31 (없는 강의도 같은 코드)
    LESSON_NOT_FOUND(HttpStatus.NOT_FOUND, "영상을 찾을 수 없어요"),                           // API 32
    ALREADY_SCRAPPED(HttpStatus.CONFLICT, "이미 스크랩한 강의예요"),                           // SP-39
    TOO_MANY_LESSONS(HttpStatus.BAD_REQUEST, "영상은 3개까지 등록할 수 있어요"),               // TCH-02
    COURSE_HAS_LEARNERS(HttpStatus.CONFLICT, "학습 기록이 있어 삭제할 수 없어요. 비공개로 바꿀까요?"), // SP-32, API 94
    NO_LESSON(HttpStatus.BAD_REQUEST, "영상을 1개 이상 등록해 주세요"),                       // SP-30
    CATEGORY_IN_USE(HttpStatus.CONFLICT, "강의가 연결된 카테고리는 삭제할 수 없어요"),          // ADM-02
    COURSE_NOT_IN_REVIEW(HttpStatus.CONFLICT, "검수 중인 강의만 허가하거나 반려할 수 있어요"),   // API 91·92
    COURSE_NOT_APPROVED(HttpStatus.CONFLICT, "허가된 강의만 공개 여부를 바꿀 수 있어요"),        // API 93
    REASON_REQUIRED(HttpStatus.BAD_REQUEST, "반려 사유를 입력해 주세요"),                      // API 92
    CATEGORY_DEPTH_EXCEEDED(HttpStatus.BAD_REQUEST, "소주제 아래에는 더 만들 수 없어요"),       // API 86 (2단계까지)
    COURSE_NOT_REQUESTABLE(HttpStatus.CONFLICT, "작성 중이거나 반려된 강의만 검수를 요청할 수 있어요"), // API 81
    CATEGORY_NOT_SELECTABLE(HttpStatus.BAD_REQUEST, "사용 중인 소주제를 골라 주세요"),            // API 73·75 (TCH-01)
    COURSE_IN_REVIEW(HttpStatus.CONFLICT, "검수 중인 강의는 수정할 수 없어요");                  // API 75·77

    private final HttpStatus status;   // ErrorCode.getStatus() 규약 (#2) - @Getter 가 getStatus() 를 만든다
    private final String message;

    @Override
    public String getCode() {
        return name();                 // "COURSE_NOT_PUBLIC" 같은 이름을 그대로 코드로 사용
    }
}