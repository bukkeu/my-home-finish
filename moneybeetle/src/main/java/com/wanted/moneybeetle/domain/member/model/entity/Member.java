package com.wanted.moneybeetle.domain.member.model.entity;

import com.wanted.moneybeetle.domain.member.model.policy.SignupRules;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/* comment.
*   담당 : DBA ② (회원·인증)
*   테이블 member - 회원(학습자·강사·관리자 role 구분)
*   - 이메일은 소문자로 저장한다 (SP-01 대소문자 구분 없음)
*   - 레벨·경험치·캐시 값 변경은 growth.RewardService 가 MemberService 메서드를 통해서만 한다 (직접 수정 금지).
*     변경 메서드는 addReward(캐시·경험치 받기, 레벨업 캐시도 이 경로), levelUp(레벨만 올리기, #11), spendCash(캐시 쓰기)다.
*     새 레벨을 얼마로 할지(레벨 정책표 계산)는 growth 가 정하고, 여기서는 규칙만 확인한다.
*   - 가입에 필요한 부분(#5): 생성(signup)과 상태·권한 상수.
*  */
@Entity
@Table(name = "member")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member {

    /** 권한 (role). Security 는 이 이름 그대로 권한(authority)으로 쓴다 (ROLE_ 접두어 없음) */
    public static final String ROLE_MEMBER = "MEMBER";
    public static final String ROLE_INSTRUCTOR = "INSTRUCTOR";
    public static final String ROLE_ADMIN = "ADMIN";

    /** 탈퇴한 회원의 개인정보를 지우기까지 두는 기간(일) (DP-06) */
    public static final int PERSONAL_INFO_RETENTION_DAYS = 30;
    /** 개인정보를 지운 뒤 닉네임은 "탈퇴한 사용자#회원ID" 가 된다 (닉네임 컬럼은 NULL 불가·고유라서 ID 를 붙인다) */
    public static final String PURGED_NICKNAME_PREFIX = "탈퇴한 사용자#";

    /** 최대 레벨 (SP-20) */
    public static final int MAX_LEVEL = 20;

    /** 탈퇴한 회원의 학습·문의 기록에 보여 주는 이름 (DP-06) */
    public static final String WITHDRAWN_NICKNAME = "탈퇴한 사용자";
    /**
     * 로그인 연속 실패 잠금 (DP-10): 5회마다 잠그고, 첫 잠금은 1분, 그 뒤 잠금은 5분.
     * system_setting 에 LOGIN_MAX_FAIL·LOGIN_LOCK_MINUTES 시드 키가 있지만 SystemSettingService 가 아직 비어 있고(#39),
     * 단일 "분" 값으로는 1분/5분 두 단계를 표현할 수 없어서 이 상수를 쓴다. 설정 연동은 #39 에서 키와 함께 정한다.
     */
    public static final int LOGIN_FAIL_LIMIT = 5;
    public static final long FIRST_LOCK_MINUTES = 1;
    public static final long REPEAT_LOCK_MINUTES = 5;

    public static final String GENDER_MALE = "MALE";
    public static final String GENDER_FEMALE = "FEMALE";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long memberId;

    /** 로그인 아이디. 소문자로 저장. 탈퇴 30일 후 NULL */
    @Column(length = 100)
    private String email;

    /** BCrypt 해시. 간편 로그인만 쓰면 NULL */
    @Column(length = 255)
    private String passwordHash;

    @Column(nullable = false, length = 30)
    private String nickname;

    @Column(length = 500)
    private String profileImageUrl;

    /** 만 14세 이상 확인용. 탈퇴 30일 후 NULL */
    private LocalDate birthDate;

    @Column(length = 10)
    private String gender;

    @Column(name = "is_identity_verified")
    private boolean identityVerified;

    @Column(nullable = false, length = 20)
    private String role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MemberStatus status;

    @Column(length = 200)
    private String suspendedReason;

    /** 레벨 1~20. 내려가지 않음 */
    private int level;

    /** 누적 경험치 */
    private int exp;

    /** 보유 캐시. 0 미만 불가 */
    private long cash;

    private int attendanceStreak;

    private LocalDate lastAttendedDate;

    private int failedLoginCount;

    private LocalDateTime lockedUntil;

    private LocalDateTime lastLoginAt;

    private LocalDateTime withdrawnAt;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    private Member(String email, String passwordHash, String nickname, LocalDate birthDate, String gender) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.nickname = nickname;
        this.birthDate = birthDate;
        this.gender = gender;
        this.role = ROLE_MEMBER;
        this.status = MemberStatus.ACTIVE;
        this.level = 1;
        this.exp = 0;
        this.cash = 0;
    }

    /**
     * 일반 회원(학습자)으로 가입한다. 레벨 1·경험치 0·캐시 0으로 시작한다.
     *
     * @param passwordHash 이미 BCrypt 로 만든 해시 (평문을 넘기지 않는다, DP-02)
     * @param today        가입 연령 계산 기준일 (KST 오늘). 테스트하기 쉽게 밖에서 받는다
     */
    public static Member signup(String email, String passwordHash, String nickname,
                                LocalDate birthDate, String gender, LocalDate today) {
        require(email != null && !email.isBlank(), "이메일이 필요합니다.");
        require(passwordHash != null && !passwordHash.isBlank(), "비밀번호 해시가 필요합니다.");
        require(nickname != null && !nickname.isBlank(), "닉네임이 필요합니다.");
        require(birthDate != null && SignupRules.isAtLeastMinAge(birthDate, today), "만 14세 이상만 가입할 수 있습니다.");
        require(gender == null || GENDER_MALE.equals(gender) || GENDER_FEMALE.equals(gender),
                "성별은 MALE 또는 FEMALE 이어야 합니다.");
        return new Member(SignupRules.normalizeEmail(email), passwordHash, nickname, birthDate, gender);
    }

    /**
     * 간편 로그인(구글)으로 가입한다 (이슈 #9). 비밀번호는 없고(NULL) 제공자가 확인해 준 이메일을 쓰므로 본인인증을 마친 것으로 둔다.
     * 레벨 1·경험치 0·캐시 0으로 시작하는 것은 일반 가입과 같다.
     *
     * @param today 가입 연령 계산 기준일 (KST 오늘)
     */
    public static Member signupSocial(String email, String nickname, LocalDate birthDate, String gender, LocalDate today) {
        require(email != null && !email.isBlank(), "이메일이 필요합니다.");
        require(nickname != null && !nickname.isBlank(), "닉네임이 필요합니다.");
        require(birthDate != null && SignupRules.isAtLeastMinAge(birthDate, today), "만 14세 이상만 가입할 수 있습니다.");
        require(gender == null || GENDER_MALE.equals(gender) || GENDER_FEMALE.equals(gender),
                "성별은 MALE 또는 FEMALE 이어야 합니다.");
        Member member = new Member(SignupRules.normalizeEmail(email), null, nickname, birthDate, gender);
        member.completeIdentityVerification();
        return member;
    }

    /**
     * 강사 가입 신청으로 회원을 만든다 (API 12, #30). 강사명을 닉네임으로 쓴다.
     * 강사 신청 화면에는 생년월일·성별이 없어 비워 둔다 (member.birth_date 는 NULL 허용).
     * 권한은 MEMBER 로 시작한다: 관리자가 승인하면(InstructorService.approve) INSTRUCTOR 로 바뀐다 (SP-07).
     * 본인인증은 가입 흐름(MemberSignupService)이 확인하고, 마쳤으면 completeIdentityVerification 으로 남긴다.
     *
     * @param passwordHash 이미 BCrypt 로 만든 해시 (평문을 넘기지 않는다, DP-02)
     */
    public static Member signupInstructor(String email, String passwordHash, String nickname) {
        require(email != null && !email.isBlank(), "이메일이 필요합니다.");
        require(passwordHash != null && !passwordHash.isBlank(), "비밀번호 해시가 필요합니다.");
        require(nickname != null && !nickname.isBlank(), "닉네임이 필요합니다.");
        return new Member(SignupRules.normalizeEmail(email), passwordHash, nickname, null, null);
    }

    /** 가입 중 본인인증(이메일 코드)을 마쳤다고 기록한다 (결과값만 저장한다, DB설계 member.is_identity_verified) */
    public void completeIdentityVerification() {
        this.identityVerified = true;
    }

    /**
     * 활동 보상으로 캐시·경험치를 받는다 (growth.RewardService 가 MemberService 를 통해 부른다).
     * 레벨은 여기서 바꾸지 않는다 - 경험치로 레벨이 오르면 growth 가 {@link #levelUp} 을 이어서 부른다.
     */
    public void addReward(long cashAmount, int expAmount) {
        require(cashAmount >= 0 && expAmount >= 0, "보상 값은 0 이상이어야 합니다.");
        this.cash += cashAmount;
        this.exp += expAmount;
    }

    /**
     * 레벨을 올린다 (growth.RewardService 가 MemberService 를 통해 부른다). 레벨만 바꾼다.
     * 레벨업 캐시는 {@link #addReward} 경로로 따로 받는다 (캐시 변경 경로를 하나로 둔다).
     * 레벨은 내려가지 않고(SP-20) 최대 Lv.20 이다. 새 레벨이 지금보다 높아야 한다.
     */
    public void levelUp(int newLevel) {
        require(newLevel > this.level, "새 레벨은 현재 레벨보다 높아야 합니다.");
        require(newLevel <= MAX_LEVEL, "레벨은 " + MAX_LEVEL + " 이하여야 합니다.");
        this.level = newLevel;
    }

    /**
     * 오늘 출석을 기록한다 (growth.AttendanceService 가 MemberService 를 통해 부른다). 하루에 한 번만 기록된다 (SP-10).
     * 어제도 출석했으면 연속 일수가 1 늘고, 하루라도 빠졌거나 처음이면 1 부터 다시 센다 (SP-11).
     *
     * @param today 서울(KST) 오늘 날짜. 테스트하기 쉽게 밖에서 받는다
     * @return 오늘 처음 기록했으면 true, 이미 오늘 출석했으면 false (값은 바뀌지 않는다)
     */
    public boolean checkAttendance(LocalDate today) {
        require(today != null, "오늘 날짜가 필요합니다.");
        if (lastAttendedDate != null && !today.isAfter(lastAttendedDate)) {
            return false;   // 오늘(또는 기기 시간이 어긋난 미래 날짜) 이미 기록됨
        }
        boolean consecutive = lastAttendedDate != null && lastAttendedDate.plusDays(1).equals(today);
        this.attendanceStreak = consecutive ? attendanceStreak + 1 : 1;
        this.lastAttendedDate = today;
        return true;
    }

    /**
     * 오늘 기준으로 이어지고 있는 연속 출석 일수. 어제나 오늘 출석했으면 저장된 값이고, 그보다 오래됐으면 이미 끊겨서 0 이다.
     * (저장된 값은 다음 출석 때 1 로 다시 시작하므로, 화면에는 이 값을 보여 준다)
     */
    public int currentStreak(LocalDate today) {
        if (lastAttendedDate == null || lastAttendedDate.plusDays(1).isBefore(today)) {
            return 0;
        }
        return attendanceStreak;
    }

    /** 오늘 이미 출석했는가 */
    public boolean hasAttended(LocalDate today) {
        return lastAttendedDate != null && lastAttendedDate.equals(today);
    }

    /** 캐시를 쓴다. 잔액은 0 미만이 될 수 없다 (SP-15) */
    public void spendCash(long amount) {
        require(amount > 0, "사용 금액은 0보다 커야 합니다.");
        require(hasEnoughCash(amount), "캐시가 부족합니다.");
        this.cash -= amount;
    }

    public boolean hasEnoughCash(long amount) {
        return this.cash >= amount;
    }

    /**
     * 다른 사람에게 보여 줄 닉네임. 탈퇴한 회원은 실제 닉네임 대신 "탈퇴한 사용자"를 보여 준다 (DP-06).
     * 정지된 회원은 그대로 보인다.
     */
    public String displayNickname() {
        return status == MemberStatus.WITHDRAWN ? WITHDRAWN_NICKNAME : nickname;
    }


    /**
     * 로그인 실패를 기록하고 잠글 때가 되면 잠근다 (DP-10).
     * 연속 5회 실패하면 1분, 그 뒤 잠금이 풀리고 실패를 5회 더 하면(누적 10회) 5분, 이후에도 5회마다 5분 잠근다.
     * 실패 횟수는 잠금이 풀려도 지우지 않고(그래야 "또 5회"를 셀 수 있다) 로그인에 성공할 때만 0 으로 돌아간다.
     * 이미 잠겨 있는 동안의 시도는 횟수를 올리지 않는다.
     */
    public void recordLoginFailure(LocalDateTime now) {
        if (isLoginLocked(now)) {
            return;
        }

        failedLoginCount++;

        if (failedLoginCount % LOGIN_FAIL_LIMIT == 0) {
            long minutes = failedLoginCount == LOGIN_FAIL_LIMIT ? FIRST_LOCK_MINUTES : REPEAT_LOCK_MINUTES;
            lockedUntil = now.plusMinutes(minutes);
        }
    }

    /** 현재 로그인 잠금 상태인지 확인한다. */
    public boolean isLoginLocked(LocalDateTime now) {
        return lockedUntil != null && now.isBefore(lockedUntil);
    }

    /** 로그인 성공 시 실패 횟수와 잠금을 초기화하고 마지막 로그인 시간을 기록한다. */
    public void recordLoginSuccess(LocalDateTime now) {
        failedLoginCount = 0;
        lockedUntil = null;
        lastLoginAt = now;
    }
    /** 비밀번호를 바꾼다 (#8). 평문이 아니라 이미 BCrypt 로 만든 해시를 받는다 (DP-02) */
    public void changePasswordHash(String passwordHash) {
        require(passwordHash != null && !passwordHash.isBlank(), "비밀번호 해시가 필요합니다.");
        this.passwordHash = passwordHash;
    }

    /**
     * 탈퇴한다 (#8, USR-14, DP-06). 계정을 비활성화(WITHDRAWN)하고 탈퇴 시각을 남긴다.
     * 이메일·생년월일·성별·프로필 이미지는 30일 뒤 파기 배치가 지운다 (여기서는 지우지 않는다).
     */
    public void withdraw(LocalDateTime now) {
        require(status != MemberStatus.WITHDRAWN, "이미 탈퇴한 회원입니다.");
        this.status = MemberStatus.WITHDRAWN;
        this.withdrawnAt = now;
    }

    /**
     * 탈퇴한 지 PERSONAL_INFO_RETENTION_DAYS 일이 지난 회원의 개인정보를 지운다 (DP-06, DH-05).
     * 이메일·비밀번호 해시·생년월일·성별·프로필 이미지는 NULL 로 만들고, 닉네임은 "탈퇴한 사용자#회원ID" 로 바꾼다.
     * 행은 학습·거래 기록 보존을 위해 남긴다. 탈퇴한 회원만 지울 수 있다.
     */
    public void purgePersonalInfo() {
        require(status == MemberStatus.WITHDRAWN, "탈퇴한 회원만 개인정보를 지울 수 있습니다.");
        this.email = null;
        this.passwordHash = null;
        this.birthDate = null;
        this.gender = null;
        this.profileImageUrl = null;
        this.nickname = PURGED_NICKNAME_PREFIX + memberId;
    }

    /**
     * 설정(닉네임·생년월일·성별)을 바꾼다 (#7). 이메일은 바꿀 수 없어 받지 않는다.
     * 중복·금칙어·만 14세처럼 DB·오늘 날짜가 필요한 규칙은 MemberService 가 먼저 확인한다. 여기서는 값의 모양만 확인한다.
     */
    public void changeProfile(String nickname, LocalDate birthDate, String gender) {
        require(nickname != null && !nickname.isBlank(), "닉네임이 필요합니다.");
        require(birthDate != null, "생년월일이 필요합니다.");
        require(gender == null || GENDER_MALE.equals(gender) || GENDER_FEMALE.equals(gender),
                "성별은 MALE 또는 FEMALE 이어야 합니다.");
        this.nickname = nickname;
        this.birthDate = birthDate;
        this.gender = gender;
    }

    /** 프로필 이미지 주소를 바꾼다 (#7) */
    public void changeProfileImageUrl(String profileImageUrl) {
        require(profileImageUrl != null && !profileImageUrl.isBlank(), "프로필 이미지 주소가 필요합니다.");
        this.profileImageUrl = profileImageUrl;
    }

    /**
     * 관리자가 계정을 정지한다 (#37, API 102, SP-06). 정지된 계정은 로그인할 수 없고,
     * 이미 로그인한 세션도 다음 요청에서 끊긴다(ActiveMemberFilter 가 isActive 를 본다). 사유는 서비스가 확인한다.
     */
    public void suspend(String reason) {
        require(status != MemberStatus.WITHDRAWN, "탈퇴한 회원은 정지할 수 없습니다.");
        require(reason != null && !reason.isBlank(), "정지 사유가 필요합니다.");
        this.status = MemberStatus.SUSPENDED;
        this.suspendedReason = reason;
    }

    /** 관리자가 정지를 푼다 (#37, API 102). 정지 사유도 지운다 */
    public void activate() {
        require(status != MemberStatus.WITHDRAWN, "탈퇴한 회원은 바꿀 수 없습니다.");
        this.status = MemberStatus.ACTIVE;
        this.suspendedReason = null;
    }

    /**
     * 관리자가 계정 상태를 바꾼다 (#288, API 102). 어떤 상태로 바꿀 수 있는지는 엔티티가 판단한다.
     * - 지금과 같은 상태이면 아무것도 바꾸지 않고 false 를 돌려준다
     * - SUSPENDED 이면 suspend(reason) (탈퇴 회원이면 예외, 사유가 없으면 예외), ACTIVE 이면 activate() (탈퇴 회원이면 예외)
     * - 그 밖의 상태(WITHDRAWN)는 이 메서드로 만들 수 없다. 탈퇴는 withdraw(now) 로만 한다 (IllegalArgumentException)
     *
     * @return 실제로 바뀌었으면 true
     */
    public boolean changeStatus(MemberStatus target, String reason) {
        require(target != null, "바꿀 상태가 필요합니다.");
        if (status == target) {
            return false;
        }
        switch (target) {
            case SUSPENDED -> suspend(reason);
            case ACTIVE -> activate();
            default -> throw new IllegalArgumentException("이 상태로는 바꿀 수 없습니다: " + target);
        }
        return true;
    }

    /**
     * 계정 상태 이름("ACTIVE" 등). 화면·JSON·다른 도메인 코드가 문자열로 읽던 방식을 그대로 지키려고 name() 을 돌려준다.
     * enum 자체가 필요하면 {@link #getMemberStatus()} 를 쓴다.
     */
    public String getStatus() {
        return status == null ? null : status.name();
    }

    /** 계정 상태 (enum) */
    public MemberStatus getMemberStatus() {
        return status;
    }

    /**
     * 관리자가 강사 권한을 주거나 회수한다 (#37, API 103·105, SP-07). MEMBER ↔ INSTRUCTOR 만 바꾼다.
     * 실제 INSTRUCTOR 권한은 강사 정보(instructor_profile)가 승인(APPROVED)일 때만 생긴다 (MemberService.grantedRole).
     */
    public void changeRole(String newRole) {
        require(!ROLE_ADMIN.equals(role), "관리자 권한은 바꿀 수 없습니다.");
        require(ROLE_MEMBER.equals(newRole) || ROLE_INSTRUCTOR.equals(newRole), "MEMBER 또는 INSTRUCTOR 만 줄 수 있습니다.");
        this.role = newRole;
    }

    /** 탈퇴한 회원인가 */
    public boolean isWithdrawn() {
        return status == MemberStatus.WITHDRAWN;
    }

    /** 로그인할 수 있는 계정인가 (정지·탈퇴 계정은 로그인 불가, SP-06) */
    public boolean isActive() {
        return status == MemberStatus.ACTIVE;
    }

    /** 탈퇴·삭제 대상이 아닌 일반 회원인가 */
    public boolean isMember() {
        return ROLE_MEMBER.equals(role);
    }

    private static void require(boolean ok, String message) {
        if (!ok) {
            throw new IllegalArgumentException(message);
        }
    }
}
