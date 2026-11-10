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
*   - 관리자 승인·회수(approve·revoke)는 #37 에서 더했다. 신청 폼 값은 강사 가입(API 12)에서 이어서 더한다
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

    /** 관리자가 강사를 승인한다 (#37, API 103·105). 승인한 관리자와 시각을 남긴다 */
    public void approve(Long adminId, LocalDateTime now) {
        if (adminId == null || now == null) {
            throw new IllegalArgumentException("승인한 관리자와 시각이 필요합니다.");
        }
        this.approvalStatus = APPROVED;
        this.approvedBy = adminId;
        this.approvedAt = now;
    }

    /** 관리자가 강사 권한을 회수한다 (#37, API 103). 승인 기록(approvedBy·approvedAt)은 이력으로 남긴다 */
    public void revoke() {
        this.approvalStatus = REVOKED;
    }

    /** 승인을 기다리는 신청인가 */
    public boolean isPending() {
        return PENDING.equals(approvalStatus);
    }

    /** 관리자가 승인한 강사인가 */
    public boolean isApproved() {
        return APPROVED.equals(approvalStatus);
    }
}
