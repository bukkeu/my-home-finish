package com.wanted.moneybeetle.domain.member.model.dto;

import java.time.LocalDate;

/* comment.
*   담당 : DBA ② (회원·인증)
*   GET /members/me 응답 (API 13) - 프로필, 로그인 방식, 레벨·칭호·경험치, 캐시, 출석, 학습, 해금 여부
*  */
public record MyInfoDTO(
        Long memberId,
        String email,
        String nickname,
        String profileImageUrl,
        LocalDate birthDate,
        String gender,
        String role,
        /** 로그인 방식: 이메일 가입 EMAIL, 구글 간편 로그인 GOOGLE (이슈 #255) */
        String loginProvider,
        int level,
        String title,
        int exp,
        int expToNextLevel,
        long cash,
        Attendance attendance,
        Learning learning,
        Unlocks unlocks) {

    /** streak 연속 출석 일수, daysToReward 연속 출석 캐시(SP-12)까지 남은 일수 (0 이면 매일 지급 구간) */
    public record Attendance(int streak, int daysToReward) {
    }

    public record Learning(long watchedLessons, long completedCourses) {
    }

    public record Unlocks(boolean trading, boolean housing, int housingLevel) {
    }
}
