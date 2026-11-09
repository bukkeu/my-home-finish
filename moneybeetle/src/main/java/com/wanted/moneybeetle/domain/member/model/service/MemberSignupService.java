package com.wanted.moneybeetle.domain.member.model.service;

import com.wanted.moneybeetle.domain.housing.model.service.HousingService;
import com.wanted.moneybeetle.domain.member.model.dto.SignupDTO;
import com.wanted.moneybeetle.domain.member.model.dto.SignupResultDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/* comment.
*   담당 : DBA ② (회원·인증)
*   회원가입 + 청약통장 자동 개설 (이슈 #5, SP-70 "가입과 동시에 개설")
*   - MemberService.signup 으로 회원을 만든 뒤, 같은 트랜잭션에서 housing.HousingService.openAccount 로 청약통장을 연다 (역할분담 §7 연동 약속).
*     통장 개설이 실패하면 회원 가입도 함께 되돌려져서, 통장 없는 회원이 생기지 않는다.
*   - MemberService 가 직접 HousingService 를 부르지 않는 이유: HousingService 가 RewardService 를 거쳐 MemberService 를 쓰므로,
*     MemberService 가 HousingService 를 가지면 서로 물려서 서버가 뜨지 않는다. 그래서 둘을 모두 아는 이 클래스가 이어 붙인다.
*   - 강사 가입(instructor-signup)은 학습자 통장이 필요 없어 이 경로를 쓰지 않는다.
*  */
@Service
@RequiredArgsConstructor
public class MemberSignupService {

    private final MemberService memberService;
    private final HousingService housingService;

    /** 회원가입 (API 4). 가입한 회원의 청약통장을 같은 트랜잭션에서 만든다 */
    @Transactional
    public SignupResultDTO signup(SignupDTO request) {
        SignupResultDTO result = memberService.signup(request);
        housingService.openAccount(result.getMemberId());
        return result;
    }
}
