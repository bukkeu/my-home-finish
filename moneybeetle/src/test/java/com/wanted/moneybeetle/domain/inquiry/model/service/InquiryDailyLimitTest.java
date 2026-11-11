package com.wanted.moneybeetle.domain.inquiry.model.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wanted.moneybeetle.domain.course.model.service.CourseService;
import com.wanted.moneybeetle.domain.inquiry.exception.InquiryErrorCode;
import com.wanted.moneybeetle.domain.inquiry.model.dao.InquiryReplyRepository;
import com.wanted.moneybeetle.domain.inquiry.model.dao.InquiryRepository;
import com.wanted.moneybeetle.domain.inquiry.model.dto.InquiryCreateDTO;
import com.wanted.moneybeetle.domain.inquiry.model.entity.Inquiry;
import com.wanted.moneybeetle.domain.member.model.service.MemberService;
import com.wanted.moneybeetle.domain.notification.model.service.NotificationService;
import com.wanted.moneybeetle.domain.operation.model.service.AdminLogService;
import com.wanted.moneybeetle.domain.operation.model.service.ForbiddenWordService;
import com.wanted.moneybeetle.domain.operation.model.service.SystemSettingService;
import com.wanted.moneybeetle.global.exception.BusinessException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.OptionalInt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/* comment.
*   담당 : DBA ① 신아림 (#297)
*   문의 하루 작성 한도 (DP-30) 는 system_setting INQUIRY_DAILY_LIMIT 값을 따른다. 없거나 1 미만이면 10
*  */
class InquiryDailyLimitTest {

    private static final Long MEMBER_ID = 8L;
    private static final String KEY = "INQUIRY_DAILY_LIMIT";
    private static final LocalDateTime START_OF_TODAY = LocalDateTime.of(2026, 11, 11, 0, 0); // 시계: 서울 11/11 10시

    private InquiryRepository inquiryRepository;
    private SystemSettingService systemSettingService;
    private InquiryService inquiryService;

    @BeforeEach
    void setUp() {
        inquiryRepository = mock(InquiryRepository.class);
        systemSettingService = mock(SystemSettingService.class);
        inquiryService = new InquiryService(inquiryRepository, mock(InquiryReplyRepository.class), mock(CourseService.class),
                mock(ForbiddenWordService.class), mock(NotificationService.class),
                Clock.fixed(Instant.parse("2026-11-11T01:00:00Z"), ZoneId.of("Asia/Seoul")),
                mock(MemberService.class), mock(AdminLogService.class), systemSettingService);
        when(inquiryRepository.save(any(Inquiry.class))).thenAnswer(invocation -> {
            Inquiry saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "inquiryId", 100L);
            return saved;
        });
    }

    private InquiryCreateDTO request() {
        return new InquiryCreateDTO(null, "사이트", "제목", "내용");
    }

    private void writtenToday(long count) {
        when(inquiryRepository.countByMemberIdAndCreatedAtGreaterThanEqual(MEMBER_ID, START_OF_TODAY)).thenReturn(count);
    }

    @Test
    @DisplayName("설정이 3이면 오늘 3개 쓴 회원의 4번째 글은 막힌다")
    void configuredLimitBlocksNext() {
        when(systemSettingService.findIntValue(KEY)).thenReturn(OptionalInt.of(3));
        writtenToday(3);

        assertThatThrownBy(() -> inquiryService.createInquiry(MEMBER_ID, request()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(InquiryErrorCode.INQUIRY_DAILY_LIMIT);
        verify(inquiryRepository, never()).save(any());
    }

    @Test
    @DisplayName("설정이 3이면 오늘 2개 쓴 회원은 한 개 더 쓸 수 있다")
    void configuredLimitAllowsUnder() {
        when(systemSettingService.findIntValue(KEY)).thenReturn(OptionalInt.of(3));
        writtenToday(2);

        assertThat(inquiryService.createInquiry(MEMBER_ID, request())).isEqualTo(100L);
    }

    @Test
    @DisplayName("설정이 없으면 10개까지: 9개면 쓸 수 있고 10개면 막힌다")
    void defaultLimitIsTen() {
        when(systemSettingService.findIntValue(KEY)).thenReturn(OptionalInt.empty());
        writtenToday(9);
        assertThat(inquiryService.createInquiry(MEMBER_ID, request())).isEqualTo(100L);

        writtenToday(10);
        assertThatThrownBy(() -> inquiryService.createInquiry(MEMBER_ID, request()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(InquiryErrorCode.INQUIRY_DAILY_LIMIT);
    }

    @Test
    @DisplayName("설정이 0·음수로 잘못 들어가면 10을 쓴다: 9개면 쓸 수 있고 10개면 막힌다")
    void nonPositiveSettingFallsBackToTen() {
        for (int invalidLimit : new int[] {0, -1}) {
            when(systemSettingService.findIntValue(KEY)).thenReturn(OptionalInt.of(invalidLimit));

            writtenToday(9);
            assertThat(inquiryService.createInquiry(MEMBER_ID, request())).isEqualTo(100L);

            writtenToday(10);
            assertThatThrownBy(() -> inquiryService.createInquiry(MEMBER_ID, request()))
                    .as("설정 %d", invalidLimit)
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(InquiryErrorCode.INQUIRY_DAILY_LIMIT);
        }
    }
}
