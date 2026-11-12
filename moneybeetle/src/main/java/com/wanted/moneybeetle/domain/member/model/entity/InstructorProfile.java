package com.wanted.moneybeetle.domain.member.model.entity;

import com.wanted.moneybeetle.global.common.BaseTimeEntity;
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
*   - 강사가 마이페이지에서 고치는 값(이름·한 줄 소개·강사 소개·전문 분야)은 changeProfile 로 한꺼번에 바꾼다 (#365, API 12-2)
*   - created_at·updated_at 은 BaseTimeEntity(JPA 감사, 서울 시간)가 채운다. 신청 시각은 applied_at 에 따로 둔다 (#37)
*  */
@Entity
@Table(name = "instructor_profile")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InstructorProfile extends BaseTimeEntity {

    /** 승인 상태 (approval_status) */
    public static final String PENDING = "PENDING";
    public static final String APPROVED = "APPROVED";
    public static final String REVOKED = "REVOKED";

    /** 컬럼 길이 (API 12-2 의 입력 제한은 InstructorProfileRules 가 더 좁게 정한다) */
    public static final int DISPLAY_NAME_MAX_LENGTH = 30;
    public static final int HEADLINE_MAX_LENGTH = 60;
    public static final int BIO_MAX_LENGTH = 1000;
    public static final int FIELD_MAX_LENGTH = 100;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long instructorProfileId;

    /** 다른 도메인이 아니라 같은 member 도메인이지만 Entity 끼리 잇지 않고 ID 로 참조한다 */
    @Column(nullable = false)
    private Long memberId;

    @Column(nullable = false, length = DISPLAY_NAME_MAX_LENGTH)
    private String displayName;

    /** 한 줄 소개 (강사 마이페이지, #365). 입력한 적 없으면 NULL */
    @Column(length = HEADLINE_MAX_LENGTH)
    private String headline;

    /** 강사 소개 (강사 마이페이지, #365). 입력한 적 없으면 NULL */
    @Column(length = BIO_MAX_LENGTH)
    private String bio;

    /** 전문 분야. 여러 개면 "청약,부동산" 처럼 쉼표로 이어 저장한다 (#365) */
    @Column(length = FIELD_MAX_LENGTH)
    private String field;

    @Column(nullable = false, length = 20)
    private String approvalStatus;

    private Long approvedBy;

    private LocalDateTime approvedAt;

    /** 강사 가입 신청 시각. 실제로 신청(API 12)한 강사만 있고, 관리자가 직접 권한을 준 강사는 NULL (#37) */
    private LocalDateTime appliedAt;

    /** 강사 가입 신청 (API 12). 승인 대기(PENDING)로 시작하고 신청 시각을 남긴다 → 관리자 신청 목록(API 104)에 나온다 */
    public static InstructorProfile apply(Long memberId, String displayName, String field, LocalDateTime appliedAt) {
        if (appliedAt == null) {
            throw new IllegalArgumentException("신청 시각이 필요합니다.");
        }
        InstructorProfile profile = create(memberId, displayName, field);
        profile.appliedAt = appliedAt;
        return profile;
    }

    /** 새 강사 정보. 승인 전이라 PENDING 으로 시작한다.
     *  신청 없이 만든다(관리자 직접 권한 부여 API 103) → 신청 시각이 없어 신청 목록에는 나오지 않는다 */
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

    /**
     * 강사가 마이페이지에서 자기 정보를 고친다 (#365, API 12-2). 이름과 전문 분야는 비울 수 없고,
     * 한 줄 소개·강사 소개는 비워 둘 수 있다(null). 승인 상태·승인 기록은 바꾸지 않는다.
     * 값이 규칙(길이)에 맞지 않으면 IllegalArgumentException (호출하는 코드의 실수이지 사용자 입력 오류가 아니다 - 입력 검사는 DTO 가 한다).
     */
    public void changeProfile(String displayName, String headline, String bio, String field) {
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("표시 이름이 필요합니다.");
        }
        if (field == null || field.isBlank()) {
            throw new IllegalArgumentException("전문 분야가 필요합니다.");
        }
        requireMaxLength(displayName, DISPLAY_NAME_MAX_LENGTH, "표시 이름");
        requireMaxLength(headline, HEADLINE_MAX_LENGTH, "한 줄 소개");
        requireMaxLength(bio, BIO_MAX_LENGTH, "강사 소개");
        requireMaxLength(field, FIELD_MAX_LENGTH, "전문 분야");
        this.displayName = displayName;
        this.headline = headline;
        this.bio = bio;
        this.field = field;
    }

    private static void requireMaxLength(String value, int max, String label) {
        if (value != null && value.length() > max) {
            throw new IllegalArgumentException(label + " 은 " + max + "자까지 쓸 수 있습니다.");
        }
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
