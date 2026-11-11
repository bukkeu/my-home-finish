package com.wanted.moneybeetle.domain.member.model.service;

import com.wanted.moneybeetle.domain.member.exception.MemberErrorCode;
import com.wanted.moneybeetle.domain.member.model.dao.InstructorProfileRepository;
import com.wanted.moneybeetle.domain.member.model.dao.MemberRepository;
import com.wanted.moneybeetle.domain.member.model.dao.TermsRepository;
import com.wanted.moneybeetle.domain.member.model.dto.AvailabilityDTO;
import com.wanted.moneybeetle.domain.member.model.dto.InstructorSignupDTO;
import com.wanted.moneybeetle.domain.member.model.dto.InstructorSignupResultDTO;
import com.wanted.moneybeetle.domain.member.model.dto.LoginMemberDTO;
import com.wanted.moneybeetle.domain.member.model.dto.MemberAttendanceDTO;
import com.wanted.moneybeetle.domain.member.model.dto.MemberBalanceDTO;
import com.wanted.moneybeetle.domain.member.model.dto.MemberCountDTO;
import com.wanted.moneybeetle.domain.member.model.dto.MemberProfileDTO;
import com.wanted.moneybeetle.domain.member.model.dto.MemberUpdateDTO;
import com.wanted.moneybeetle.domain.member.model.dto.MemberUpdateResultDTO;
import com.wanted.moneybeetle.domain.member.model.dto.ProfileImageDTO;
import com.wanted.moneybeetle.domain.member.model.dto.SignupDTO;
import com.wanted.moneybeetle.domain.member.model.dto.SignupResultDTO;
import com.wanted.moneybeetle.domain.member.model.dto.SocialSignupDTO;
import com.wanted.moneybeetle.domain.member.model.dto.TermsDTO;
import com.wanted.moneybeetle.domain.member.model.entity.InstructorProfile;
import com.wanted.moneybeetle.domain.member.model.entity.Member;
import com.wanted.moneybeetle.domain.member.model.policy.SignupRules;
import com.wanted.moneybeetle.global.exception.BusinessException;
import org.springframework.data.domain.PageRequest;
import java.util.Collection;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.wanted.moneybeetle.domain.member.model.dao.MemberTermsAgreementRepository;
import com.wanted.moneybeetle.domain.member.model.dao.SocialAccountRepository;
import com.wanted.moneybeetle.domain.member.model.entity.MemberTermsAgreement;
import com.wanted.moneybeetle.domain.member.model.entity.SocialAccount;
import com.wanted.moneybeetle.domain.member.model.entity.Terms;
import com.wanted.moneybeetle.domain.operation.model.service.ForbiddenWordService;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Set;
import java.util.stream.Collectors;

/* comment.
*   담당 : DBA ② (회원·인증)
*   회원가입, 약관 조회, 이메일·닉네임 중복 확인 (이슈 #5). 이어서 내 정보·설정(#7), 비밀번호 변경·탈퇴(#8)를 맡는다.
*   다른 도메인에 제공하는 메서드 : getBalance / lockBalance / addReward / deductCash (growth.RewardService 전용, #10 2차),
*   findLoginMemberForUpdate / recordLoginFailure / recordLoginSuccess (auth.AuthService 전용, #6),
*   recordAttendance / getAttendance (growth.AttendanceService 전용, #12).
*   findNicknames (회원 ID 여러 개 -> 닉네임, 문의 게시판 등, #145).
*   회원의 캐시·경험치는 이 메서드를 통해서만 바뀐다 (다른 도메인은 Member Entity·Repository 를 쓰지 않는다).
*
*   진행 상태 (이슈 #5)
*   - 구현됨: findAgreements(약관 목록), isEmailAvailable, isNicknameAvailable (중복 확인), signup (회원가입)
*   - signup 은 입력 규칙을 확인하고 비밀번호를 BCrypt 로 저장한다. 동시 가입으로 DB 고유 제약에 걸리면 409 로 바꾼다.
*   - 가입 직후 청약통장 개설은 MemberSignupService 가 이 메서드 다음에 같은 트랜잭션에서 한다 (HousingService 와 서로 물리지 않게).
*  */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberService {

    /** DB 고유 키 이름 (schema.sql). 동시 가입의 중복을 알아보는 데 쓴다 */
    private static final String LOGIN_PROVIDER_EMAIL = "EMAIL";
    private static final String UK_MEMBER_EMAIL = "uk_member_email";
    private static final String UK_MEMBER_NICKNAME = "uk_member_nickname";

    private final MemberRepository memberRepository;
    private final TermsRepository termsRepository;
    private final MemberTermsAgreementRepository memberTermsAgreementRepository;
    private final PasswordEncoder passwordEncoder;
    private final ForbiddenWordService forbiddenWordService;
    private final Clock clock;
    private final InstructorProfileRepository instructorProfileRepository;
    private final SocialAccountRepository socialAccountRepository;

    /** 회원의 현재 레벨·경험치·캐시 (growth.RewardService 용). 없는 회원이면 MEMBER_NOT_FOUND */
    public MemberBalanceDTO getBalance(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(MemberErrorCode.MEMBER_NOT_FOUND));
        return MemberBalanceDTO.from(member);
    }

    /**
     * 회원 행을 잠그고 현재 값을 돌려준다 (growth.RewardService 용). 잠금은 호출한 쪽의 트랜잭션이 끝날 때까지 유지되므로,
     * 이 메서드를 부른 뒤 같은 트랜잭션에서 이중 지급·잔액 확인을 해도 다른 요청이 끼어들지 못한다.
     */
    @Transactional
    public MemberBalanceDTO lockBalance(Long memberId) {
        return MemberBalanceDTO.from(getMemberForUpdate(memberId));
    }

    /** 활동 보상으로 받은 캐시·경험치를 회원에게 더하고 바뀐 값을 돌려준다 (growth.RewardService 용, 레벨은 levelUp 이 올린다) */
    @Transactional
    public MemberBalanceDTO addReward(Long memberId, long cashAmount, int expAmount) {
        Member member = getMemberForUpdate(memberId);
        member.addReward(cashAmount, expAmount);
        return MemberBalanceDTO.from(member);
    }

    /** 레벨만 올리고 바뀐 값을 돌려준다 (growth.RewardService 용). 레벨은 내려가지 않는다. 레벨업 캐시는 addReward 로 따로 준다 */
    @Transactional
    public MemberBalanceDTO levelUp(Long memberId, int newLevel) {
        Member member = getMemberForUpdate(memberId);
        member.levelUp(newLevel);
        return MemberBalanceDTO.from(member);
    }

    /** 캐시를 차감하고 바뀐 값을 돌려준다 (growth.RewardService 용). 잔액이 모자라면 Entity 가 거절한다 */
    @Transactional
    public MemberBalanceDTO deductCash(Long memberId, long amount) {
        Member member = getMemberForUpdate(memberId);
        member.spendCash(amount);
        return MemberBalanceDTO.from(member);
    }

    /**
     * 오늘 출석을 기록한다 (growth.AttendanceService 용). 회원 행을 잠가서 같은 사람이 동시에 두 번 불러도 하루 한 번만 기록된다.
     * 이미 오늘 출석했으면 값을 바꾸지 않고 newlyChecked = false 로 돌려준다.
     */
    @Transactional
    public MemberAttendanceDTO recordAttendance(Long memberId, LocalDate today) {
        Member member = getMemberForUpdate(memberId);
        boolean newlyChecked = member.checkAttendance(today);
        return MemberAttendanceDTO.of(member, newlyChecked, today);
    }

    /** 오늘 기준 출석 상태를 읽기만 한다 (growth.AttendanceService 용, API 21) */
    public MemberAttendanceDTO getAttendance(Long memberId, LocalDate today) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(MemberErrorCode.MEMBER_NOT_FOUND));
        return MemberAttendanceDTO.of(member, false, today);
    }

    /**
     * 로그인할 회원을 이메일로 찾고 그 행을 잠근다 (auth.AuthService 용). 이메일은 앞뒤 공백을 지우고 소문자로 바꿔 조회한다 (SP-01).
     * 잠금은 호출한 쪽의 트랜잭션이 끝날 때까지 유지된다. 로그인 한 번(잠금 확인 → 비밀번호 확인 → 성공·실패 기록)을 하나의 트랜잭션으로 묶어
     * 같은 계정의 동시 시도가 차례로 처리되게 한다 (auth.LoginAuthenticationProvider 가 그 트랜잭션을 연다).
     * 부여할 권한(grantedRole)도 여기서 정한다: 회원 정보상 강사여도 관리자가 승인(APPROVED)하기 전에는 MEMBER 다 (SP-07).
     * 이메일이 비었거나 없는 회원이면 빈 값이다.
     */
    @Transactional
    public Optional<LoginMemberDTO> findLoginMemberForUpdate(String email) {
        String normalized = SignupRules.normalizeEmail(email);
        if (normalized == null || normalized.isBlank()) {
            return Optional.empty();
        }
        return memberRepository.findByEmailForUpdate(normalized).map(member -> LoginMemberDTO.from(member, grantedRole(member)));
    }

    /**
     * 비밀번호가 틀렸을 때 로그인 실패를 기록한다 (auth.LoginAuthenticationProvider 가 AuthService 를 거쳐 부른다). 이번 실패로 잠겼으면 true.
     * 회원 행을 잠그고 읽어서 같은 계정의 동시 실패가 서로의 횟수를 덮어쓰지 않게 한다 (DP-10). 없는 이메일이면 false.
     */
    @Transactional
    public boolean recordLoginFailure(String email, LocalDateTime now) {
        String normalized = SignupRules.normalizeEmail(email);
        if (normalized == null || normalized.isBlank()) {
            return false;
        }
        return memberRepository.findByEmailForUpdate(normalized)
                .map(member -> {
                    member.recordLoginFailure(now);
                    return member.isLoginLocked(now);
                })
                .orElse(false);
    }

    /**
     * 로그인 성공을 기록한다: 실패 횟수·잠금을 지우고 마지막 로그인 시각을 남긴다 (auth.LoginAuthenticationProvider 가 AuthService 를 거쳐 부른다).
     * 회원 행을 잠그고 읽는다. 같은 계정의 동시 로그인 실패 기록(recordLoginFailure)과 서로 덮어쓰지 않고 차례로 처리된다.
     */
    @Transactional
    public void recordLoginSuccess(Long memberId, LocalDateTime now) {
        getMemberForUpdate(memberId).recordLoginSuccess(now);
    }

    /**
     * 관리자 대시보드용 회원 수 (operation.DashboardService 전용, #33). 학습자(MEMBER) 기준이고, total 에는 탈퇴한 회원도 들어 있다.
     * 강사·관리자는 세지 않는다.
     */
    public MemberCountDTO countMembers() {
        return new MemberCountDTO(memberRepository.countByRole(Member.ROLE_MEMBER),
                memberRepository.countByRoleAndStatus(Member.ROLE_MEMBER, Member.STATUS_WITHDRAWN));
    }

    /**
     * 탈퇴한 지 PERSONAL_INFO_RETENTION_DAYS 일이 지난 회원의 개인정보를 지운다 (예약 작업 MemberCleanupJob 전용, #66, DP-06).
     * 한 번에 batchSize 명까지만 처리하고 처리한 수를 돌려준다. 돌려준 수가 batchSize 면 아직 남았을 수 있어 다시 부른다.
     * 호출마다 하나의 트랜잭션이라, 중간에 실패해도 그 묶음만 되돌려지고 앞서 끝낸 묶음은 남는다.
     */
    @Transactional
    public int purgeWithdrawnMembers(LocalDateTime now, int batchSize) {
        LocalDateTime cutoff = now.minusDays(Member.PERSONAL_INFO_RETENTION_DAYS);
        List<Member> targets = memberRepository.findPurgeTargets(cutoff, PageRequest.of(0, batchSize));
        if (!targets.isEmpty()) {
            // 간편 로그인 연결(제공자 계정 ID)도 개인정보라 같은 트랜잭션에서 함께 지운다
            socialAccountRepository.deleteByMemberIdIn(targets.stream().map(Member::getMemberId).toList());
        }
        targets.forEach(Member::purgePersonalInfo);
        return targets.size();
    }

    /**
     * 정상(ACTIVE) 계정인가 (auth.ActiveMemberFilter 용). 로그인해 둔 세션도 탈퇴·정지된 뒤에는 막으려고 요청마다 확인한다 (SP-06).
     * 없는 회원이면 false 다.
     */
    public boolean isActiveMember(Long memberId) {
        return memberRepository.findById(memberId).map(Member::isActive).orElse(false);
    }

    /**
     * INSTRUCTOR 권한으로 로그인해 둔 회원의 강사 권한이 지금은 없는가 (auth.ActiveMemberFilter 가 INSTRUCTOR 세션에만 부른다, #37 CodeRabbit 지적).
     * 관리자가 강사 권한을 회수(API 103)하면 true → 이미 열린 세션도 다음 요청에서 끊긴다.
     * 권한 부여는 세션을 건드리지 않아 다음 로그인부터 적용된다 (SP-07). 없는 회원이면 true.
     */
    public boolean isInstructorRevoked(Long memberId) {
        return memberRepository.findById(memberId)
                .map(member -> !Member.ROLE_INSTRUCTOR.equals(grantedRole(member)))
                .orElse(true);
    }

    /** 회원 테이블의 role 이 INSTRUCTOR 여도 승인된 강사일 때만 INSTRUCTOR 권한을 준다 */
    private String grantedRole(Member member) {
        if (!Member.ROLE_INSTRUCTOR.equals(member.getRole())) {
            return member.getRole();
        }
        boolean approved = instructorProfileRepository.findByMemberId(member.getMemberId())
                .map(InstructorProfile::isApproved)
                .orElse(false);
        return approved ? Member.ROLE_INSTRUCTOR : Member.ROLE_MEMBER;
    }

    private Member getMemberForUpdate(Long memberId) {
        return memberRepository.findByIdForUpdate(memberId)
                .orElseThrow(() -> new BusinessException(MemberErrorCode.MEMBER_NOT_FOUND));
    }

    /** 회원의 프로필 원본 값 (member.MemberProfileService 용). 없는 회원이면 MEMBER_NOT_FOUND */
    public MemberProfileDTO getProfile(Long memberId) {
        return MemberProfileDTO.from(getMember(memberId));
    }

    /** 로그인 방식 (내 정보 API 13). social_account 에 연결이 있으면 그 provider(GOOGLE), 없으면 이메일 가입이라 EMAIL */
    public String getLoginProvider(Long memberId) {
        return socialAccountRepository.findFirstByMemberIdOrderBySocialAccountIdAsc(memberId)
                .map(SocialAccount::getProvider)
                .orElse(LOGIN_PROVIDER_EMAIL);
    }

    /** 다른 회원에게 보여 줄 프로필 원본 값. 탈퇴·정지 회원은 없는 회원처럼 MEMBER_NOT_FOUND (DP-03) */
    public MemberProfileDTO getActiveProfile(Long memberId) {
        Member member = getMember(memberId);
        if (!member.isActive()) {
            throw new BusinessException(MemberErrorCode.MEMBER_NOT_FOUND);
        }
        return MemberProfileDTO.from(member);
    }

    /**
     * 설정 수정 (API 14). 값이 없는 항목은 그대로 둔다. 닉네임이 바뀔 때만 형식·금칙어·중복을 확인하고(SP-02),
     * 생년월일은 가입 때와 같이 과거 날짜·만 14세 이상인지 확인한다. 이메일은 받지 않는다.
     * 같은 닉네임으로 동시에 바꿔 중복 확인을 둘 다 통과해도 저장 시점의 DB 고유 제약에서 409 로 바꾼다.
     */
    @Transactional
    public MemberUpdateResultDTO updateProfile(Long memberId, MemberUpdateDTO request) {
        Member member = getMemberForUpdate(memberId);

        String nickname = member.getNickname();
        if (request.getNickname() != null && !request.getNickname().equals(nickname)) {
            // 대소문자만 바꾼 경우는 DB 정렬 규칙(대소문자 구분 없음) 때문에 내 행이 중복으로 걸리므로 중복 검사만 건너뛴다
            validateNickname(request.getNickname(), !request.getNickname().equalsIgnoreCase(nickname));
            nickname = request.getNickname();
        }

        LocalDate birthDate = member.getBirthDate();
        if (request.getBirthDate() != null) {
            validateBirthDate(request.getBirthDate(), LocalDate.now(clock));
            birthDate = request.getBirthDate();
        }

        String gender = request.getGender() != null ? request.getGender() : member.getGender();

        member.changeProfile(nickname, birthDate, gender);
        flushProfile();
        return new MemberUpdateResultDTO(member.getMemberId(), member.getNickname());
    }

    /** 프로필 이미지 주소를 저장한다 (member.MemberProfileService 가 파일을 저장한 뒤 부른다) */
    @Transactional
    public ProfileImageDTO changeProfileImageUrl(Long memberId, String profileImageUrl) {
        Member member = getMemberForUpdate(memberId);
        member.changeProfileImageUrl(profileImageUrl);
        return new ProfileImageDTO(member.getProfileImageUrl());
    }

    private Member getMember(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(MemberErrorCode.MEMBER_NOT_FOUND));
    }

    /** 변경을 DB 에 바로 보내 고유 제약(닉네임) 위반을 이 자리에서 409 로 바꾼다 */
    private void flushProfile() {
        try {
            memberRepository.flush();
        } catch (DataIntegrityViolationException e) {
            if (UK_MEMBER_NICKNAME.equals(findConstraintName(e))) {
                throw new BusinessException(MemberErrorCode.NICKNAME_DUPLICATED);
            }
            throw e;
        }
    }

    /**
     * 회원 ID 여러 개의 닉네임을 한 번의 조회로 돌려준다 (다른 도메인용, 예: 문의 게시판 작성자 표시 #22).
     * 목록에서 회원마다 따로 조회해 N+1 이 나지 않게 ID 를 모아서 부른다.
     * - 없는 ID 는 결과에서 빠진다. null·중복 ID 는 걸러서 조회한다. 빈 목록이면 조회하지 않고 빈 맵이다.
     * - 탈퇴한 회원은 실제 닉네임 대신 "탈퇴한 사용자"로 돌려준다 (DP-06). 정지된 회원은 그대로다.
     */
    public Map<Long, String> findNicknames(Collection<Long> memberIds) {
        if (memberIds == null || memberIds.isEmpty()) {
            return Map.of();
        }
        Set<Long> ids = memberIds.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        return memberRepository.findAllById(ids).stream()
                .collect(Collectors.toUnmodifiableMap(Member::getMemberId, Member::displayNickname));
    }

    /** 가입 화면에 보여 줄 약관(필수·선택) 목록 (API 1) */
    public List<TermsDTO> findAgreements() {
        return termsRepository.findAllByOrderByTermsIdAsc().stream().map(TermsDTO::from).toList();
    }

    /**
     * 이메일을 쓸 수 있는가 — 형식·길이가 가입 요청과 같은 규칙에 맞고, 대소문자 구분 없이 중복이 아닐 때만 true (API 2, SP-01).
     * 원본이나 정규화한 값 중 하나라도 형식이 틀리면 조회하지 않고 바로 false 를 돌려준다 (사용 가능으로 보이지 않게).
     */
    public AvailabilityDTO isEmailAvailable(String email) {
        // 원본과 정규화한 값을 모두 검사한다. 정규화(앞뒤 공백 제거)는 틀린 입력을 올바른 값으로 바꿔 버릴 수 있는데,
        // 가입 요청(SignupDTO)은 원본 그대로 검증하므로 여기서도 원본이 틀리면 가입할 수 없는 이메일로 답한다.
        String normalized = SignupRules.normalizeEmail(email);
        if (!SignupRules.isValidEmail(email) || !SignupRules.isValidEmail(normalized)) {
            return AvailabilityDTO.of(false);
        }
        return AvailabilityDTO.of(!memberRepository.existsByEmail(normalized));
    }

    /**
     * 닉네임을 쓸 수 있는가 — 중복이면 false (API 3, SP-02).
     * 형식(2~12자 한글·영문·숫자)과 금칙어 검사는 2차에서 오류 코드(NICKNAME_INVALID, FORBIDDEN_WORD)와 함께 붙인다.
     */
    public AvailabilityDTO isNicknameAvailable(String nickname) {
        if (!SignupRules.isValidNickname(nickname)) {
            throw new BusinessException(MemberErrorCode.NICKNAME_INVALID);
        }

        forbiddenWordService.check(nickname);

        return AvailabilityDTO.of(!memberRepository.existsByNickname(nickname));
    }

    /**
     * 회원가입 (API 4). 순서: 이메일 → 닉네임(형식·금칙어·중복) → 비밀번호 → 생년월일·만 14세 → 약관 → 저장(회원 → 약관 동의 기록).
     * 청약통장 개설은 이 메서드 안이 아니라 MemberSignupService 가 이어서 한다 (컨트롤러는 MemberSignupService.signup 을 부른다).
     *
     * @return 만들어진 회원 (memberId, nickname)
     */
    @Transactional
    public SignupResultDTO signup(SignupDTO request) {
        return signup(request, false);
    }

    /**
     * 회원가입 (API 4). 가입 전에 본인인증(이메일 코드)을 마쳤으면 identityVerified 를 true 로 넘겨 그 결과를 함께 저장한다.
     * 인증을 마쳤는지 확인하는 일은 가입 흐름(MemberSignupService)이 하고, 여기서는 받은 결과만 저장한다.
     */
    @Transactional
    public SignupResultDTO signup(SignupDTO request, boolean identityVerified) {
        if (request == null) {
            throw new IllegalArgumentException("회원가입 정보가 필요합니다.");
        }

        String email = SignupRules.normalizeEmail(request.getEmail());
        validateEmail(request.getEmail(), email);

        String nickname = request.getNickname();
        validateNickname(nickname);

        validatePassword(request.getPassword(), request.getPasswordConfirm());

        LocalDate today = LocalDate.now(clock);
        validateBirthDate(request.getBirthDate(), today);

        List<Terms> agreedTerms = findAgreedTerms(request.getAgreedTermsIds());

        Member member = Member.signup(
                email,
                passwordEncoder.encode(request.getPassword()),
                nickname,
                request.getBirthDate(),
                request.getGender(),
                today);
        if (identityVerified) {
            member.completeIdentityVerification();
        }
        Member savedMember = saveMember(member);
        saveAgreements(savedMember.getMemberId(), agreedTerms);

        return SignupResultDTO.from(savedMember);
    }

    /**
     * 강사 가입 신청 (API 12, #30). 회원을 만들고 강사 정보를 승인 대기(PENDING)·신청 시각과 함께 저장한다 → 관리자 신청 목록(API 104).
     * 순서: 이메일 → 강사명(닉네임 규칙·금칙어·중복) → 비밀번호 → 약관 → 저장(회원 → 약관 동의 기록 → 강사 정보).
     * - 강사명을 닉네임으로 쓴다 (팀 결정). 이미 있는 닉네임이면 NICKNAME_DUPLICATED
     * - 강사는 학습자 청약통장을 만들지 않는다 (그래서 MemberSignupService.signup 경로를 쓰지 않는다)
     * - 본인인증을 마쳤는지 확인하는 일은 가입 흐름(MemberSignupService.signupInstructor)이 하고, 여기서는 결과만 저장한다
     */
    @Transactional
    public InstructorSignupResultDTO signupInstructor(InstructorSignupDTO request, boolean identityVerified) {
        if (request == null) {
            throw new IllegalArgumentException("강사 가입 정보가 필요합니다.");
        }

        String email = SignupRules.normalizeEmail(request.getEmail());
        validateEmail(request.getEmail(), email);

        String nickname = request.getDisplayName();
        validateNickname(nickname);
        if (request.getField() != null) {
            forbiddenWordService.check(request.getField());
        }

        validatePassword(request.getPassword(), request.getPasswordConfirm());

        List<Terms> agreedTerms = findAgreedTerms(request.getAgreedTermsIds());

        Member member = Member.signupInstructor(email, passwordEncoder.encode(request.getPassword()), nickname);
        if (identityVerified) {
            member.completeIdentityVerification();
        }
        Member savedMember = saveMember(member);
        saveAgreements(savedMember.getMemberId(), agreedTerms);

        InstructorProfile profile = instructorProfileRepository.save(InstructorProfile.apply(
                savedMember.getMemberId(), nickname, request.getField(), LocalDateTime.now(clock)));
        return InstructorSignupResultDTO.of(savedMember.getMemberId(), profile.getApprovalStatus());
    }

    /**
     * 간편 로그인(구글·카카오)으로 처음 온 사람의 가입 (API 8-2, DP-13, 이슈 #9). 이메일은 제공자가 확인해 준 값(세션)이고 비밀번호는 없다.
     * 순서: 이메일 → 닉네임(형식·금칙어·중복) → 생년월일·만 14세 → 약관 → 저장(회원 → 약관 동의 기록 → 간편 로그인 연결).
     * 같은 제공자 계정이 동시에 가입하면 DB 고유 제약(uk_social_account_1)이 한쪽을 거르고 그쪽은 OAUTH_FAILED 가 된다.
     *
     * @param provider       GOOGLE / KAKAO
     * @param providerUserId 제공자 쪽 사용자 ID
     */
    @Transactional
    public SignupResultDTO signupSocial(SocialSignupDTO request, String email, String provider, String providerUserId) {
        if (request == null) {
            throw new IllegalArgumentException("가입 정보가 필요합니다.");
        }
        String normalizedEmail = SignupRules.normalizeEmail(email);
        validateEmail(email, normalizedEmail);
        validateNickname(request.getNickname());
        LocalDate today = LocalDate.now(clock);
        validateBirthDate(request.getBirthDate(), today);
        List<Terms> agreedTerms = findAgreedTerms(request.getAgreedTermsIds());

        Member savedMember = saveMember(Member.signupSocial(
                normalizedEmail, request.getNickname(), request.getBirthDate(), request.getGender(), today));
        saveAgreements(savedMember.getMemberId(), agreedTerms);
        try {
            socialAccountRepository.saveAndFlush(
                    SocialAccount.create(savedMember.getMemberId(), provider, providerUserId));
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(MemberErrorCode.OAUTH_FAILED);
        }
        return SignupResultDTO.from(savedMember);
    }

    /** 제공자 계정(provider, providerUserId)에 연결된 회원. 연결이 없으면 비어 있다 (auth.SocialLoginService 용) */
    public Optional<LoginMemberDTO> findLoginMemberBySocial(String provider, String providerUserId) {
        return socialAccountRepository.findByProviderAndProviderUserId(provider, providerUserId)
                .flatMap(account -> memberRepository.findById(account.getMemberId()))
                .map(member -> LoginMemberDTO.from(member, grantedRole(member)));
    }

    /** 회원 번호로 로그인 회원 정보를 읽는다 (간편 로그인 가입 직후 로그인 처리용). 없으면 비어 있다 */
    public Optional<LoginMemberDTO> findLoginMemberById(Long memberId) {
        return memberRepository.findById(memberId).map(member -> LoginMemberDTO.from(member, grantedRole(member)));
    }

    /** 이메일 형식(원본 그대로)과 중복(정규화한 값, 대소문자 구분 없음)을 확인한다 */
    private void validateEmail(String rawEmail, String normalizedEmail) {
        if (!SignupRules.isValidEmail(rawEmail)) {
            throw new BusinessException(MemberErrorCode.EMAIL_INVALID);
        }
        if (memberRepository.existsByEmail(normalizedEmail)) {
            throw new BusinessException(MemberErrorCode.EMAIL_DUPLICATED);
        }
    }

    private void validateNickname(String nickname) {
        validateNickname(nickname, true);
    }

    private void validateNickname(String nickname, boolean checkDuplicate) {
        if (!SignupRules.isValidNickname(nickname)) {
            throw new BusinessException(MemberErrorCode.NICKNAME_INVALID);
        }
        forbiddenWordService.check(nickname);
        if (checkDuplicate && memberRepository.existsByNickname(nickname)) {
            throw new BusinessException(MemberErrorCode.NICKNAME_DUPLICATED);
        }
    }

    private void validatePassword(String password, String passwordConfirm) {
        if (!SignupRules.isValidPassword(password)) {
            throw new BusinessException(MemberErrorCode.PASSWORD_INVALID);
        }
        if (!password.equals(passwordConfirm)) {
            throw new BusinessException(MemberErrorCode.PASSWORD_MISMATCH);
        }
    }

    private void validateBirthDate(LocalDate birthDate, LocalDate today) {
        if (!SignupRules.isPastDate(birthDate, today)) {
            throw new BusinessException(MemberErrorCode.BIRTH_DATE_INVALID);
        }
        if (!SignupRules.isAtLeastMinAge(birthDate, today)) {
            throw new BusinessException(MemberErrorCode.AGE_UNDER_14);
        }
    }

    /** 동의한 약관이 모두 있는 약관이고 필수 약관을 모두 포함하는지 확인한 뒤, 동의한 약관을 돌려준다 */
    private List<Terms> findAgreedTerms(List<Long> agreedTermsIds) {
        if (agreedTermsIds == null || agreedTermsIds.isEmpty() || agreedTermsIds.stream().anyMatch(Objects::isNull)) {
            throw new BusinessException(MemberErrorCode.REQUIRED_TERMS_NOT_AGREED);
        }

        Set<Long> agreedIds = new LinkedHashSet<>(agreedTermsIds);
        List<Terms> agreedTerms = termsRepository.findByTermsIdIn(agreedIds);
        if (agreedTerms.size() != agreedIds.size()) {
            throw new BusinessException(MemberErrorCode.TERMS_NOT_FOUND);
        }

        List<Long> requiredIds = termsRepository.findByRequiredTrue().stream().map(Terms::getTermsId).toList();
        if (!SignupRules.agreedAllRequired(requiredIds, agreedIds)) {
            throw new BusinessException(MemberErrorCode.REQUIRED_TERMS_NOT_AGREED);
        }
        return agreedTerms;
    }

    /**
     * 회원을 저장한다. 같은 이메일·닉네임으로 동시에 가입하면 위의 중복 확인을 둘 다 통과할 수 있으므로,
     * 저장 시점(flush)에 DB 고유 제약 위반을 받아 uk_member_email / uk_member_nickname 만 각각의 중복 오류(409)로 바꾼다.
     * 다른 무결성 오류는 그대로 던진다.
     */
    private Member saveMember(Member member) {
        try {
            return memberRepository.saveAndFlush(member);
        } catch (DataIntegrityViolationException e) {
            String constraint = findConstraintName(e);
            if (UK_MEMBER_EMAIL.equals(constraint)) {
                throw new BusinessException(MemberErrorCode.EMAIL_DUPLICATED);
            }
            if (UK_MEMBER_NICKNAME.equals(constraint)) {
                throw new BusinessException(MemberErrorCode.NICKNAME_DUPLICATED);
            }
            throw e;
        }
    }

    private void saveAgreements(Long memberId, List<Terms> agreedTerms) {
        memberTermsAgreementRepository.saveAll(agreedTerms.stream()
                .map(term -> MemberTermsAgreement.create(memberId, term.getTermsId(), true))
                .toList());
    }

    /**
     * 예외 원인 사슬에서 Hibernate 가 알려 주는 위반 제약 이름을 찾는다. 오류 메시지 문자열은 입력한 이메일·닉네임이 섞일 수 있어 보지 않는다.
     * MySQL 은 "테이블.제약이름"으로 알려 줄 수 있어서 마지막 점 뒤만 소문자로 비교한다. 못 찾으면 null.
     */
    private static String findConstraintName(Throwable exception) {
        for (Throwable current = exception; current != null; current = current.getCause()) {
            if (current instanceof ConstraintViolationException violation && violation.getConstraintName() != null) {
                String name = violation.getConstraintName();
                return name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
            }
        }
        return null;
    }
}
