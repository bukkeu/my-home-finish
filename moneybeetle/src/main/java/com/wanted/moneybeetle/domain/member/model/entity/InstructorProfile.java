package com.wanted.moneybeetle.domain.member.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/* comment.
*   담당 : DBA ② (회원·인증)
*   테이블 instructor_profile (member 1:1) - 강사 정보와 승인 상태
*   - 로그인(AuthService)이 승인 상태를 읽어서, 승인된(APPROVED) 강사에게만 INSTRUCTOR 권한을 준다 (SP-07)
*   - 승인·반려를 바꾸는 메서드(관리자 승인, 이슈 #37)와 신청 폼 값은 그 이슈에서 이어서 더한다
*  */
@Entity
@Table(name = "instructor_profile")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InstructorProfile {

    /** 승인 상태 (approval_status) */
    public static final String PENDING = "PENDING";
    public static final String APPROVED = "APPROVED";
    public static final String REVOKED = "REVOKED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long instructorProfileId;

    /** 다른 도메인이 아니라 같은 member 도메인이지만 Entity 끼리 잇지 않고 ID 로 참조한다 */
    @Column(nullable = false)
    private Long memberId;

    @Column(nullable = false, length = 30)
    private String displayName;

    @Column(length = 100)
    private String field;

    @Column(nullable = false, length = 20)
    private String approvalStatus;

    private Long approvedBy;

    private LocalDateTime approvedAt;

    /** 새 강사 정보. 승인 전이라 PENDING 으로 시작한다 */
    public static InstructorProfile create(Long memberId, String displayName, String field) {
        if (memberId == null) {
            throw new IllegalArgumentException("강사 회원이 필요합니다.");
        }
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("표시 이름이 필요합니다.");
        }
        InstructorProfile profile = new InstructorProfile();
        profile.memberId = memberId;
        profile.displayName = displayName;
        profile.field = field;
        profile.approvalStatus = PENDING;
        return profile;
    }

    /** 관리자가 승인한 강사인가 */
    public boolean isApproved() {
        return APPROVED.equals(approvalStatus);
    }
}
