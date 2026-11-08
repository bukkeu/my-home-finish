package com.wanted.moneybeetle.domain.member.model.service;

import com.wanted.moneybeetle.domain.member.exception.MemberErrorCode;
import com.wanted.moneybeetle.domain.member.model.dao.MemberRepository;
import com.wanted.moneybeetle.domain.member.model.dao.TermsRepository;
import com.wanted.moneybeetle.domain.member.model.dto.AvailabilityDTO;
import com.wanted.moneybeetle.domain.member.model.dto.MemberBalanceDTO;
import com.wanted.moneybeetle.domain.member.model.dto.SignupDTO;
import com.wanted.moneybeetle.domain.member.model.dto.SignupResultDTO;
import com.wanted.moneybeetle.domain.member.model.dto.TermsDTO;
import com.wanted.moneybeetle.domain.member.model.entity.Member;
import com.wanted.moneybeetle.domain.member.model.policy.SignupRules;
import com.wanted.moneybeetle.global.exception.BusinessException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.wanted.moneybeetle.domain.member.model.dao.MemberTermsAgreementRepository;
import com.wanted.moneybeetle.domain.member.model.entity.MemberTermsAgreement;
import com.wanted.moneybeetle.domain.operation.model.service.ForbiddenWordService;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Set;
import org.springframework.security.crypto.password.PasswordEncoder;

/* comment.
*   담당 : DBA ② (회원·인증)
*   회원가입, 약관 조회, 이메일·닉네임 중복 확인 (이슈 #5). 이어서 내 정보·설정(#7), 비밀번호 변경·탈퇴(#8)를 맡는다.
*   다른 도메인에 제공하는 메서드 : getBalance / lockBalance / addReward / deductCash (growth.RewardService 전용, #10 2차).
*   회원의 캐시·경험치는 이 메서드를 통해서만 바뀐다 (다른 도메인은 Member Entity·Repository 를 쓰지 않는다).
*
*   진행 상태 (이슈 #5, 1차)
*   - 구현됨: findAgreements(약관 목록), isEmailAvailable, isNicknameAvailable (중복 확인)
*   - signup 은 시그니처만 공개한다. 비밀번호를 BCrypt 로 저장(PasswordEncoder Bean 은 #4)하고, 규칙을 어기면
*     BusinessException 을 던지고(#2), 가입 직후 청약통장을 만들어야(서수영 HousingService.openAccount) 해서
*     그 셋이 올라온 뒤 2차로 구현한다. 그전에 부르면 UnsupportedOperationException.
*  */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberService {

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
     * 회원가입 (API 4). 순서: 입력 규칙 → 이메일·닉네임 중복 → 금칙어 → 만 14세 → 필수 약관 → 비밀번호 해시 저장 → 약관 동의 기록 → 청약통장 개설.
     *
     * @return 만들어진 회원 (memberId, nickname)
     */
    @Transactional
    public SignupResultDTO signup(SignupDTO request) {
        if (request == null) {
            throw new IllegalArgumentException("회원가입 정보가 필요합니다.");
        }

        // 1. 이메일 형식 및 중복 검사
        String email = SignupRules.normalizeEmail(request.getEmail());

        if (!SignupRules.isValidEmail(request.getEmail())) {
            throw new BusinessException(MemberErrorCode.EMAIL_INVALID);
        }

        if (memberRepository.existsByEmail(email)) {
            throw new BusinessException(MemberErrorCode.EMAIL_DUPLICATED);
        }

        // 2. 닉네임 형식 및 중복 검사
        String nickname = request.getNickname();

        if (!SignupRules.isValidNickname(nickname)) {
            throw new BusinessException(MemberErrorCode.NICKNAME_INVALID);
        }

        forbiddenWordService.check(nickname);

        if (memberRepository.existsByNickname(nickname)) {
            throw new BusinessException(MemberErrorCode.NICKNAME_DUPLICATED);
        }

        // 3. 비밀번호 검증
        if (!SignupRules.isValidPassword(request.getPassword())) {
            throw new BusinessException(MemberErrorCode.PASSWORD_INVALID);
        }

        if (!request.getPassword().equals(request.getPasswordConfirm())) {
            throw new BusinessException(MemberErrorCode.PASSWORD_MISMATCH);
        }

        // 4. 생년월일 및 가입 연령 검증
        LocalDate today = LocalDate.now(clock);
        LocalDate birthDate = request.getBirthDate();

        if (!SignupRules.isPastDate(birthDate, today)) {
            throw new BusinessException(MemberErrorCode.BIRTH_DATE_INVALID);
        }

        if (!SignupRules.isAtLeastMinAge(birthDate, today)) {
            throw new BusinessException(MemberErrorCode.AGE_UNDER_14);
        }

        // 5. 약관 동의 정보 검증
        if (request.getAgreedTermsIds() == null
                || request.getAgreedTermsIds().isEmpty()
                || request.getAgreedTermsIds().stream().anyMatch(java.util.Objects::isNull)) {
            throw new BusinessException(MemberErrorCode.REQUIRED_TERMS_NOT_AGREED);
        }

        Set<Long> agreedIds = new java.util.LinkedHashSet<>(request.getAgreedTermsIds());

        List<com.wanted.moneybeetle.domain.member.model.entity.Terms> agreedTerms =
                termsRepository.findByTermsIdIn(agreedIds);

        if (agreedTerms.size() != agreedIds.size()) {
            throw new BusinessException(MemberErrorCode.TERMS_NOT_FOUND);
        }

        List<Long> requiredIds = termsRepository.findByRequiredTrue()
                .stream()
                .map(term -> term.getTermsId())
                .toList();

        if (!SignupRules.agreedAllRequired(requiredIds, agreedIds)) {
            throw new BusinessException(MemberErrorCode.REQUIRED_TERMS_NOT_AGREED);
        }

        // 6. 비밀번호 암호화 및 회원 저장
        String encodedPassword = passwordEncoder.encode(request.getPassword());

        Member member = Member.signup(
                email,
                encodedPassword,
                nickname,
                birthDate,
                request.getGender(),
                today
        );

        Member savedMember = memberRepository.save(member);

        // 7. 동의한 약관 기록 저장
        List<MemberTermsAgreement> agreements = agreedTerms.stream()
                .map(term -> MemberTermsAgreement.create(
                        savedMember.getMemberId(),
                        term.getTermsId(),
                        true
                ))
                .toList();

        memberTermsAgreementRepository.saveAll(agreements);

        return SignupResultDTO.from(savedMember);
    }
}
