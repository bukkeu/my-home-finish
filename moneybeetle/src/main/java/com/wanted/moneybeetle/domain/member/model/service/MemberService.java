package com.wanted.moneybeetle.domain.member.model.service;

import com.wanted.moneybeetle.domain.member.exception.MemberErrorCode;
import com.wanted.moneybeetle.domain.member.model.dao.MemberRepository;
import com.wanted.moneybeetle.domain.member.model.dao.TermsRepository;
import com.wanted.moneybeetle.domain.member.model.dto.AvailabilityDTO;
import com.wanted.moneybeetle.domain.member.model.dto.MemberAttendanceDTO;
import com.wanted.moneybeetle.domain.member.model.dto.MemberBalanceDTO;
import com.wanted.moneybeetle.domain.member.model.dto.SignupDTO;
import com.wanted.moneybeetle.domain.member.model.dto.SignupResultDTO;
import com.wanted.moneybeetle.domain.member.model.dto.TermsDTO;
import com.wanted.moneybeetle.domain.member.model.entity.Member;
import com.wanted.moneybeetle.domain.member.model.policy.SignupRules;
import com.wanted.moneybeetle.global.exception.BusinessException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.wanted.moneybeetle.domain.member.model.dao.MemberTermsAgreementRepository;
import com.wanted.moneybeetle.domain.member.model.entity.MemberTermsAgreement;
import com.wanted.moneybeetle.domain.member.model.entity.Terms;
import com.wanted.moneybeetle.domain.operation.model.service.ForbiddenWordService;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Set;

/* comment.
*   담당 : DBA ② (회원·인증)
*   회원가입, 약관 조회, 이메일·닉네임 중복 확인 (이슈 #5). 이어서 내 정보·설정(#7), 비밀번호 변경·탈퇴(#8)를 맡는다.
*   다른 도메인에 제공하는 메서드 : getBalance / lockBalance / addReward / deductCash (growth.RewardService 전용, #10 2차).
*   회원의 캐시·경험치는 이 메서드를 통해서만 바뀐다 (다른 도메인은 Member Entity·Repository 를 쓰지 않는다).
*
*   진행 상태 (이슈 #5)
*   - 구현됨: findAgreements(약관 목록), isEmailAvailable, isNicknameAvailable (중복 확인), signup (회원가입)
*   - signup 은 입력 규칙을 확인하고 비밀번호를 BCrypt 로 저장한다. 동시 가입으로 DB 고유 제약에 걸리면 409 로 바꾼다.
*   - 남은 일: 가입 직후 청약통장 개설(서수영 HousingService.openAccount)은 아직 연결하지 않았다.
*  */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberService {

    /** DB 고유 키 이름 (schema.sql). 동시 가입의 중복을 알아보는 데 쓴다 */
    private static final String UK_MEMBER_EMAIL = "uk_member_email";
    private static final String UK_MEMBER_NICKNAME = "uk_member_nickname";

    private final MemberRepository memberRepository;
    private final TermsRepository termsRepository;
    private final MemberTermsAgreementRepository memberTermsAgreementRepository;
    private final PasswordEncoder passwordEncoder;
    private final ForbiddenWordService forbiddenWordService;
    private final Clock clock;

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

    private Member getMemberForUpdate(Long memberId) {
        return memberRepository.findByIdForUpdate(memberId)
                .orElseThrow(() -> new BusinessException(MemberErrorCode.MEMBER_NOT_FOUND));
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
     * 청약통장 개설(서수영 HousingService.openAccount)은 아직 연결하지 않았다.
     *
     * @return 만들어진 회원 (memberId, nickname)
     */
    @Transactional
    public SignupResultDTO signup(SignupDTO request) {
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
        Member savedMember = saveMember(member);
        saveAgreements(savedMember.getMemberId(), agreedTerms);

        return SignupResultDTO.from(savedMember);
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
        if (!SignupRules.isValidNickname(nickname)) {
            throw new BusinessException(MemberErrorCode.NICKNAME_INVALID);
        }
        forbiddenWordService.check(nickname);
        if (memberRepository.existsByNickname(nickname)) {
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
