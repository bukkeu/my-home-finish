package com.wanted.moneybeetle.domain.growth.model.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wanted.moneybeetle.domain.growth.exception.GrowthErrorCode;
import com.wanted.moneybeetle.domain.growth.model.dao.LevelPolicyRepository;
import com.wanted.moneybeetle.domain.growth.model.dto.LevelDTO;
import com.wanted.moneybeetle.domain.growth.model.entity.LevelPolicy;
import com.wanted.moneybeetle.domain.member.model.dto.MemberBalanceDTO;
import com.wanted.moneybeetle.domain.member.model.service.MemberService;
import com.wanted.moneybeetle.global.exception.BusinessException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/* comment.
*   담당 : PM (성장·홈·모의 청약)
*   GrowthService (이슈 #11) - Repository·MemberService 를 Mock 으로 두고 서버·DB 없이 돈다
*  */
class GrowthServiceTest {

    private LevelPolicyRepository repository;
    private MemberService memberService;
    private GrowthService service;

    @BeforeEach
    void setUp() {
        repository = mock(LevelPolicyRepository.class);
        memberService = mock(MemberService.class);
        service = new GrowthService(repository, memberService);
    }

    @Test
    @DisplayName("getLevel: 회원의 현재 레벨을 DB 값으로 돌려준다")
    void getLevel() {
        when(memberService.getBalance(8L)).thenReturn(MemberBalanceDTO.of(8L, 8, 1_250, 12_000));

        assertThat(service.getLevel(8L)).isEqualTo(8);
    }

    @Test
    @DisplayName("findLevels: 20줄을 레벨 순으로 주고 Lv.8·Lv.12 에만 해금 기능이 붙는다 (API 24)")
    void findLevels() {
        when(repository.findAllByOrderByLevelAsc()).thenReturn(LevelTableFixture.policies());

        List<LevelDTO> levels = service.findLevels();

        assertThat(levels).hasSize(20);
        assertThat(levels.get(7).getLevel()).isEqualTo(8);
        assertThat(levels.get(7).getRequiredExp()).isEqualTo(1_250);
        assertThat(levels.get(7).getTitle()).isEqualTo("투룸");
        assertThat(levels.get(7).getLevelupCash()).isEqualTo(80_000);
        assertThat(levels.get(7).getUnlocks()).containsExactly("TRADING");
        assertThat(levels.get(11).getUnlocks()).containsExactly("HOUSING");
        assertThat(levels.get(0).getUnlocks()).isEmpty();
    }

    @Test
    @DisplayName("API 24 JSON: 해금 기능이 없는 레벨은 unlocks 필드를 내보내지 않는다")
    void levelJsonOmitsEmptyUnlocks() throws Exception {
        when(repository.findAllByOrderByLevelAsc()).thenReturn(LevelTableFixture.policies());
        List<LevelDTO> levels = service.findLevels();
        ObjectMapper mapper = new ObjectMapper();

        assertThat(mapper.writeValueAsString(levels.get(0))).doesNotContain("unlocks");
        assertThat(mapper.writeValueAsString(levels.get(7))).contains("\"unlocks\":[\"TRADING\"]");
    }

    @Test
    @DisplayName("레벨 정책이 비었거나 1레벨부터 이어지지 않으면 LEVEL_POLICY_INVALID(500)")
    void invalidPolicyTable() {
        when(repository.findAllByOrderByLevelAsc()).thenReturn(List.of());
        assertThatThrownBy(() -> service.loadLevelTable())
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(GrowthErrorCode.LEVEL_POLICY_INVALID));

        when(repository.findAllByOrderByLevelAsc()).thenReturn(List.of(LevelPolicy.create(2, 150, "고시원", 20_000)));
        assertThatThrownBy(() -> service.loadLevelTable())
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(GrowthErrorCode.LEVEL_POLICY_INVALID));
    }
}
