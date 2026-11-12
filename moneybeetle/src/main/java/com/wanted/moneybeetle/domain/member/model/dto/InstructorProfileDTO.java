package com.wanted.moneybeetle.domain.member.model.dto;

import com.wanted.moneybeetle.domain.member.model.entity.InstructorProfile;
import com.wanted.moneybeetle.domain.member.model.policy.InstructorProfileRules;
import java.util.List;

/* comment.
*   담당 : DBA ② (회원·인증)
*   강사 본인 프로필 (이슈 #365, API 12-1 조회·12-2 수정 응답) { displayName, headline, bio, fields }
*   headline·bio 를 입력한 적 없으면 null, fields 는 ["청약", "부동산"] 처럼 목록이다
*  */
public record InstructorProfileDTO(String displayName, String headline, String bio, List<String> fields) {

    public static InstructorProfileDTO from(InstructorProfile profile) {
        return new InstructorProfileDTO(profile.getDisplayName(), profile.getHeadline(), profile.getBio(),
                InstructorProfileRules.splitFields(profile.getField()));
    }
}
