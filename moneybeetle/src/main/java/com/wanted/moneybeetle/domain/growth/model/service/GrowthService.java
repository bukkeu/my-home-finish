package com.wanted.moneybeetle.domain.growth.model.service;

import com.wanted.moneybeetle.domain.growth.exception.GrowthErrorCode;
import com.wanted.moneybeetle.domain.growth.model.dao.LevelPolicyRepository;
import com.wanted.moneybeetle.domain.growth.model.dto.LevelDTO;
import com.wanted.moneybeetle.domain.growth.model.entity.LevelPolicy;
import com.wanted.moneybeetle.domain.member.model.service.MemberService;
import com.wanted.moneybeetle.global.exception.BusinessException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/* comment.
*   담당 : DBA ② (성장·캐시·레벨)
*   레벨·칭호 창구 (역할분담 문서 §7 연동 약속)
*   - getLevel(memberId) : 회원의 현재 레벨 [호출: 서수영 레벨 해금 인터셉터]. 레벨은 DB 값으로 매번 확인한다 (SP-24, NFR-09)
*   - findLevels()       : 레벨별 필요 경험치·칭호·레벨업 캐시·해금 기능 (API 24)
*   - loadLevelTable()   : 레벨 정책표를 읽어 검증한다 (RewardService 가 레벨 재계산에 쓴다)
*   정책표는 20줄 안팎이라 캐시 없이 매번 읽는다. 호출이 많아지면 운영 설정 캐시(10분)에 맞춰 캐시한다.
*  */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GrowthService {

    private final LevelPolicyRepository levelPolicyRepository;
    private final MemberService memberService;

    /** 회원의 현재 레벨. 없는 회원이면 MEMBER_NOT_FOUND */
    public int getLevel(Long memberId) {
        return memberService.getBalance(memberId).getLevel();
    }

    /** 레벨 정책표 전체 (API 24) */
    public List<LevelDTO> findLevels() {
        return loadLevelTable().all().stream()
                .map(policy -> LevelDTO.of(policy, UnlockRules.unlockedAt(policy.getLevel())))
                .toList();
    }

    /** 레벨 정책표를 읽는다. 비어 있거나 1레벨부터 이어지지 않으면 LEVEL_POLICY_INVALID(500) */
    public LevelTable loadLevelTable() {
        List<LevelPolicy> policies = levelPolicyRepository.findAllByOrderByLevelAsc();
        try {
            return new LevelTable(policies);
        } catch (IllegalArgumentException e) {
            // BusinessException 은 원인(cause)을 담지 못한다(global 공통 클래스). 어느 줄이 잘못됐는지는 로그로 남긴다
            log.error("레벨 정책표 검증 실패: {}", e.getMessage(), e);
            throw new BusinessException(GrowthErrorCode.LEVEL_POLICY_INVALID);
        }
    }
}
