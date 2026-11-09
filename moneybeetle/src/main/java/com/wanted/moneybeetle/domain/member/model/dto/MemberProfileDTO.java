package com.wanted.moneybeetle.domain.member.model.dto;

import com.wanted.moneybeetle.domain.member.model.entity.Member;
import java.time.LocalDate;

/* comment.
*   담당 : DBA ② (회원·인증)
*   회원 한 명의 프로필 원본 값. 같은 member 도메인의 MemberProfileService 가 레벨 칭호·학습 수 같은
*   다른 도메인 값을 붙여 MyInfoDTO·PublicProfileDTO 로 만든다 (Entity 를 Controller·다른 Service 로 내보내지 않는다).
*  */
public record MemberProfileDTO(
        Long memberId,
        String email,
        String nickname,
        String profileImageUrl,
        LocalDate birthDate,
        String gender,
        String role,
        int level,
        int exp,
        long cash,
        int attendanceStreak) {

    public static MemberProfileDTO from(Member member) {
        return new MemberProfileDTO(
                member.getMemberId(), member.getEmail(), member.getNickname(), member.getProfileImageUrl(),
                member.getBirthDate(), member.getGender(), member.getRole(),
                member.getLevel(), member.getExp(), member.getCash(), member.getAttendanceStreak());
    }
}
