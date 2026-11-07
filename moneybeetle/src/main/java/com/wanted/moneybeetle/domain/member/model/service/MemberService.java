package com.wanted.moneybeetle.domain.member.model.service;

import com.wanted.moneybeetle.domain.member.model.dao.MemberRepository;
import com.wanted.moneybeetle.domain.member.model.dao.TermsRepository;
import com.wanted.moneybeetle.domain.member.model.dto.AvailabilityDTO;
import com.wanted.moneybeetle.domain.member.model.dto.SignupDTO;
import com.wanted.moneybeetle.domain.member.model.dto.SignupResultDTO;
import com.wanted.moneybeetle.domain.member.model.dto.TermsDTO;
import com.wanted.moneybeetle.domain.member.model.policy.SignupRules;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/* comment.
*   담당 : DBA ② (회원·인증)
*   회원가입, 약관 조회, 이메일·닉네임 중복 확인 (이슈 #5). 이어서 내 정보·설정(#7), 비밀번호 변경·탈퇴(#8)를 맡는다.
*   다른 도메인에 제공하는 메서드 : 회원 조회, 레벨·경험치·캐시 변경 (growth.RewardService 전용, #10 2차)
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

    /** 가입 화면에 보여 줄 약관(필수·선택) 목록 (API 1) */
    public List<TermsDTO> findAgreements() {
        return termsRepository.findAllByOrderByTermsIdAsc().stream().map(TermsDTO::from).toList();
    }

    /** 이메일을 쓸 수 있는가 — 대소문자 구분 없이 중복이면 false (API 2, SP-01) */
    public AvailabilityDTO isEmailAvailable(String email) {
        String normalized = SignupRules.normalizeEmail(email);
        return AvailabilityDTO.of(normalized != null && !normalized.isEmpty() && !memberRepository.existsByEmail(normalized));
    }

    /**
     * 닉네임을 쓸 수 있는가 — 중복이면 false (API 3, SP-02).
     * 형식(2~12자 한글·영문·숫자)과 금칙어 검사는 2차에서 오류 코드(NICKNAME_INVALID, FORBIDDEN_WORD)와 함께 붙인다.
     */
    public AvailabilityDTO isNicknameAvailable(String nickname) {
        return AvailabilityDTO.of(SignupRules.isValidNickname(nickname) && !memberRepository.existsByNickname(nickname));
    }

    /**
     * 회원가입 (API 4). 순서: 입력 규칙 → 이메일·닉네임 중복 → 금칙어 → 만 14세 → 필수 약관 → 비밀번호 해시 저장 → 약관 동의 기록 → 청약통장 개설.
     *
     * @return 만들어진 회원 (memberId, nickname)
     */
    @Transactional
    public SignupResultDTO signup(SignupDTO request) {
        // TODO(#5 2차): PasswordEncoder(#4), BusinessException(#2), ForbiddenWordService(#64), HousingService.openAccount 가 올라온 뒤 구현
        throw new UnsupportedOperationException("MemberService.signup 은 #2·#4 가 올라온 뒤 구현합니다 (#5 2차)");
    }
}
