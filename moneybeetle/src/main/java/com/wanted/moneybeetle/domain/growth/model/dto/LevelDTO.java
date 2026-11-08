package com.wanted.moneybeetle.domain.growth.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.wanted.moneybeetle.domain.growth.model.entity.LevelPolicy;
import java.util.List;
import lombok.Getter;

/* comment.
*   담당 : PM (성장·홈·모의 청약)
*   레벨 한 줄 (API 24 levels): 필요 누적 경험치, 칭호, 레벨업 캐시, 이 레벨에서 새로 열리는 기능
*   - unlocks 는 열리는 기능이 있는 레벨에만 내려간다 (없으면 필드를 생략)
*  */
@Getter
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class LevelDTO {

    private final int level;
    private final int requiredExp;
    private final String title;
    private final int levelupCash;
    private final List<String> unlocks;

    private LevelDTO(int level, int requiredExp, String title, int levelupCash, List<String> unlocks) {
        this.level = level;
        this.requiredExp = requiredExp;
        this.title = title;
        this.levelupCash = levelupCash;
        this.unlocks = unlocks;
    }

    public static LevelDTO of(LevelPolicy policy, List<String> unlocks) {
        return new LevelDTO(policy.getLevel(), policy.getRequiredExp(), policy.getTitle(),
                policy.getLevelupCash(), unlocks);
    }
}
