package com.wanted.moneybeetle.domain.inquiry.model.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/* comment.
*   담당 : DBA ① 신아림 (#297)
*   목록 status 필터: 아는 값만 그대로, 나머지는 거르지 않음(null)
*  */
class InquiryStatusTest {

    @Test
    @DisplayName("WAITING·ANSWERED 는 그대로 필터가 된다")
    void knownValues() {
        assertThat(InquiryStatus.filterOf("WAITING")).isEqualTo("WAITING");
        assertThat(InquiryStatus.filterOf("ANSWERED")).isEqualTo("ANSWERED");
    }

    @Test
    @DisplayName("비었거나 모르는 값(소문자 포함)은 거르지 않는다")
    void unknownValues() {
        assertThat(InquiryStatus.filterOf(null)).isNull();
        assertThat(InquiryStatus.filterOf("")).isNull();
        assertThat(InquiryStatus.filterOf("ALL")).isNull();
        assertThat(InquiryStatus.filterOf("waiting")).isNull();
    }

    @Test
    @DisplayName("엔티티의 상태 상수는 enum 이름과 같다 (DB 에 저장된 값과 맞음)")
    void entityConstantsMatch() {
        assertThat(Inquiry.STATUS_WAITING).isEqualTo(InquiryStatus.WAITING.name());
        assertThat(Inquiry.STATUS_ANSWERED).isEqualTo(InquiryStatus.ANSWERED.name());
    }
}
