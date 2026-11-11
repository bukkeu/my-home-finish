package com.wanted.moneybeetle.domain.growth.model.dto;

import java.util.List;

/* comment.
*   담당 : DBA ② (성장·캐시·레벨)
*   레벨·칭호 표 응답 (API 24) { "levels": [ { level, requiredExp, title, levelupCash, unlocks } ] }
*  */
public record LevelListDTO(List<LevelDTO> levels) {
}
