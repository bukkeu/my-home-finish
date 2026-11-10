package com.wanted.moneybeetle.domain.term.model.entity;

import com.wanted.moneybeetle.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/* comment.
*   담당 : DBA ② (경제 용어)
*   테이블 economic_term - 관리자가 등록하는 경제 용어 (ADM-05)
*   - 용어(term)는 중복될 수 없다 (DB UNIQUE, 대소문자 구분 없음).
*   - 노출된 적이 있는 용어는 지우지 않고 사용 여부(is_active)를 끈다 (API 98).
*  */
@Entity
@Table(name = "economic_term")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EconomicTerm extends BaseTimeEntity {

    public static final int TERM_MAX_LENGTH = 50;
    public static final int DESCRIPTION_MAX_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long economicTermId;

    @Column(nullable = false, length = TERM_MAX_LENGTH, unique = true)
    private String term;

    @Column(nullable = false, length = DESCRIPTION_MAX_LENGTH)
    private String description;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    private EconomicTerm(String term, String description) {
        this.term = term;
        this.description = description;
        this.active = true;
    }

    /** 새 용어(사용 중). 값이 규칙에 맞지 않으면 IllegalArgumentException (입력 검사는 DTO 가 먼저 한다) */
    public static EconomicTerm create(String term, String description) {
        return new EconomicTerm(requireText(term, TERM_MAX_LENGTH, "term"),
                requireText(description, DESCRIPTION_MAX_LENGTH, "description"));
    }

    /** 용어·뜻을 바꾼다 */
    public void change(String term, String description) {
        this.term = requireText(term, TERM_MAX_LENGTH, "term");
        this.description = requireText(description, DESCRIPTION_MAX_LENGTH, "description");
    }

    public void activate() {
        this.active = true;
    }

    public void deactivate() {
        this.active = false;
    }

    private static String requireText(String value, int maxLength, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " 은 비어 있을 수 없습니다.");
        }
        String stripped = value.strip();
        if (stripped.length() > maxLength) {
            throw new IllegalArgumentException(name + " 은 " + maxLength + "자까지 쓸 수 있습니다.");
        }
        return stripped;
    }
}
