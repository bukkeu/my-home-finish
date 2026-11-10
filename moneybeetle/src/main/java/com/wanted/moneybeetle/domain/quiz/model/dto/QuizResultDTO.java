package com.wanted.moneybeetle.domain.quiz.model.dto;

import com.wanted.moneybeetle.domain.growth.model.dto.RewardResultDTO;
import com.wanted.moneybeetle.domain.quiz.model.entity.QuizAttempt;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/* comment.
*   담당 : DBA ① (강의·퀴즈)
*   채점 결과 (점수, 통과 여부, 문항별 정답·해설, 보상) - API 42 제출 응답, API 43 결과 다시 보기
*   - rewards 는 제출 응답(API 42)에만 담는다. 다시 보기(API 43)에서는 null (보상은 이미 지급됨)
*  */
public record QuizResultDTO(
        Long attemptId,
        int score,
        int correctCount,
        int questionCount,
        boolean isPassed,
        List<TestQuizResultDTO.ResultItem> results,
        Rewards rewards
) {
    public static QuizResultDTO of(QuizAttempt attempt, List<TestQuizResultDTO.ResultItem> results, Rewards rewards) {
        return new QuizResultDTO(attempt.getQuizAttemptId(), attempt.getScore(), attempt.getCorrectCount(),
                attempt.getQuestionCount(), attempt.isPassed(), results, rewards);
    }

    /* 보상 요약. firstPass 가 false 면(이번이 첫 통과가 아님·미통과) 지급량은 0 */
    public record Rewards(boolean firstPass, int exp, long cash, LevelUp levelUp) {

        public static Rewards none() {
            return new Rewards(false, 0, 0, null);
        }

        /* 이번 제출로 받은 보상들(학습 완료, 만점)을 합친다. levelTitle 은 오른 레벨의 이름 (없으면 null) */
        public static Rewards firstPass(List<RewardResultDTO> granted, String levelTitle) {
            int exp = 0;
            long cash = 0;
            long levelUpCash = 0;
            int levelFrom = 0;
            int levelTo = 0;
            Set<String> unlocked = new LinkedHashSet<>();
            for (RewardResultDTO result : granted) {
                exp += result.getGrantedExp();
                cash += result.getGrantedCash();
                levelUpCash += result.getLevelUpCash();
                if (levelFrom == 0) {
                    levelFrom = result.getLevelFrom();
                }
                levelTo = Math.max(levelTo, result.getLevelTo());
                unlocked.addAll(result.getUnlocks());
            }
            LevelUp levelUp = levelTo > levelFrom
                    ? new LevelUp(levelFrom, levelTo, levelTitle, levelUpCash, new ArrayList<>(unlocked))
                    : null;
            return new Rewards(true, exp, cash, levelUp);
        }
    }

    /* 레벨업 정보 (오르지 않았으면 Rewards.levelUp 이 null) */
    public record LevelUp(int from, int to, String title, long levelupCash, List<String> unlocked) {
    }
}
