package com.wanted.moneybeetle.domain.growth.model.dto;

import java.util.List;

/* comment.
*   담당 : DBA ② (성장·캐시·레벨)
*   레벨·칭호 표 응답 (API 24) : levels 배열을 돌려준다.
*   각 항목은 { level, requiredExp, title, levelupCash, unlocks } 이고, 열리는 기능이 없는 레벨은 unlocks 필드를 생략한다 (LevelDTO).
*  */
public record LevelListDTO(List<LevelDTO> levels) {
}
