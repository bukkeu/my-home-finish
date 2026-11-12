package com.wanted.moneybeetle.domain.quiz.model.dto;

import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/* comment.
*   담당 : 서수영 (#368, quiz 도메인 담당 신아림이 나눠 준 작업)
*   퀴즈 문제 파일의 종류 (API 150 요청 값 type)
*   - TEST : 테스트 퀴즈 OX 정확히 5문제 (TCH-04, DP-34). 칸 : 문제, 정답(O·X), 해설
*   - MAIN : 퀴즈 4지선다 5~15문제 (TCH-05, DP-34). 칸 : 문제, 보기1~4, 정답(1~4), 해설
*   - 문항 수는 저장 API(79·80)와 같은 기준이다. 파일을 읽을 때 먼저 알려 주려고 같은 값을 둔다
*  */
@Getter
@RequiredArgsConstructor
public enum QuizFileType {

    TEST("테스트 퀴즈", List.of("문제", "정답", "해설"), 5, 5),
    MAIN("퀴즈", List.of("문제", "보기1", "보기2", "보기3", "보기4", "정답", "해설"), 5, 15);

    private final String label;             // 오류 문구에 쓰는 이름
    private final List<String> columns;     // 머리글 칸 이름 (순서대로)
    private final int minCount;             // 최소 문항 수
    private final int maxCount;             // 최대 문항 수

    /** 문항 수 안내 : "5문제" 또는 "5~15문제" */
    public String countText() {
        return minCount == maxCount ? minCount + "문제" : minCount + "~" + maxCount + "문제";
    }
}
