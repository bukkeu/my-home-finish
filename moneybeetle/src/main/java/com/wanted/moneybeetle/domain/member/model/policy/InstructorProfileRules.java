package com.wanted.moneybeetle.domain.member.model.policy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/* comment.
*   담당 : DBA ② (회원·인증)
*   강사 본인 프로필 규칙 (이슈 #365, API 12-1·12-2) - 값만 보고 판단되는 순수한 검사들이다. 서버·DB 없이 테스트한다.
*   - 이름(displayName)은 앞뒤 공백을 뺀 2~20자, 한 줄 소개는 60자, 강사 소개는 1,000자
*   - 전문 분야는 1개 이상, 고를 수 있는 값은 FIELDS 7개뿐이다. DB 에는 instructor_profile.field(VARCHAR(100)) 에 "청약,부동산" 처럼 쉼표로 저장한다
*   - 금칙어는 DB 조회가 필요해서 Service 가 검사한다 (FORBIDDEN_WORD)
*  */
public final class InstructorProfileRules {

    public static final int DISPLAY_NAME_MIN_LENGTH = 2;
    public static final int DISPLAY_NAME_MAX_LENGTH = 20;
    public static final int HEADLINE_MAX_LENGTH = 60;
    public static final int BIO_MAX_LENGTH = 1000;

    /** 고를 수 있는 전문 분야 (목업 마이페이지의 선택지 순서) */
    public static final List<String> FIELDS = List.of("청약", "ETF", "주식", "부동산", "예적금", "재테크 기초", "대출");

    private static final String FIELD_SEPARATOR = ",";

    private InstructorProfileRules() {
    }

    /** 앞뒤 공백을 뺀 이름이 2~20자인가 */
    public static boolean isValidDisplayName(String displayName) {
        if (displayName == null) {
            return false;
        }
        int length = displayName.strip().length();
        return length >= DISPLAY_NAME_MIN_LENGTH && length <= DISPLAY_NAME_MAX_LENGTH;
    }

    /** 고를 수 있는 전문 분야 값인가 (앞뒤 공백은 무시) */
    public static boolean isAllowedField(String field) {
        return field != null && FIELDS.contains(field.strip());
    }

    /** 전문 분야 목록을 DB 에 저장할 문자열로 바꾼다. 앞뒤 공백을 빼고 중복은 첫 번째만 남기며 순서는 입력 그대로다 */
    public static String joinFields(List<String> fields) {
        Set<String> unique = new LinkedHashSet<>();
        for (String field : fields) {
            unique.add(field.strip());
        }
        return String.join(FIELD_SEPARATOR, unique);
    }

    /** 저장된 문자열을 분야 목록으로 되돌린다. 비어 있으면 빈 목록. 목록에 없는 옛 값(가입 때 직접 쓴 분야)도 그대로 돌려준다 */
    public static List<String> splitFields(String stored) {
        if (stored == null || stored.isBlank()) {
            return List.of();
        }
        List<String> fields = new ArrayList<>();
        for (String part : Arrays.asList(stored.split(FIELD_SEPARATOR))) {
            String field = part.strip();
            if (!field.isEmpty()) {
                fields.add(field);
            }
        }
        return List.copyOf(fields);
    }
}
