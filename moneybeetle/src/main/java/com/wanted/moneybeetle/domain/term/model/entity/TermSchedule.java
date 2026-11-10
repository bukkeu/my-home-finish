package com.wanted.moneybeetle.domain.term.model.entity;

import com.wanted.moneybeetle.global.common.CreatedTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/* comment.
*   담당 : DBA ② (경제 용어)
*   테이블 term_schedule - 날짜별 오늘의 경제 용어 (하루 5개, SP-45)
*   - 같은 날짜·순서, 같은 날짜·같은 용어는 DB UNIQUE 로 막는다.
*   - 자동 선정(is_auto)은 관리자가 지정하지 않은 날을 처음 볼 때 한 번 정해서 저장한다 (SP-46).
*   - 다른 도메인이 아니라 같은 도메인의 용어는 ID 로 참조한다.
*  */
@Entity
@Table(name = "term_schedule", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"show_date", "sort_order"}),
        @UniqueConstraint(columnNames = {"show_date", "economic_term_id"})})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TermSchedule extends CreatedTimeEntity {

    public static final int DAILY_COUNT = 5;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long termScheduleId;

    /** 노출일 (KST) */
    @Column(nullable = false)
    private LocalDate showDate;

    @Column(nullable = false)
    private Long economicTermId;

    /** 순서 1~5 */
    @Column(nullable = false)
    private int sortOrder;

    @Column(name = "is_auto", nullable = false)
    private boolean auto;


    private TermSchedule(LocalDate showDate, Long economicTermId, int sortOrder, boolean auto) {
        this.showDate = showDate;
        this.economicTermId = economicTermId;
        this.sortOrder = sortOrder;
        this.auto = auto;
    }

    public static TermSchedule create(LocalDate showDate, Long economicTermId, int sortOrder, boolean auto) {
        if (showDate == null || economicTermId == null) {
            throw new IllegalArgumentException("노출일과 용어가 필요합니다.");
        }
        if (sortOrder < 1 || sortOrder > DAILY_COUNT) {
            throw new IllegalArgumentException("순서는 1~" + DAILY_COUNT + " 이어야 합니다.");
        }
        return new TermSchedule(showDate, economicTermId, sortOrder, auto);
    }
}
