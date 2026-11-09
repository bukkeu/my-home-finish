package com.wanted.moneybeetle.domain.housing.model.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/* comment.
*   담당 : SCM (모의 청약)
*   내가 이 단지에 신청했나?, 이 단지 신청자는 몇 명인가?(경쟁률 파악)
*   테이블 housing_application (신청 후 취소·수정 불가)
*  */
@NoArgsConstructor(access = AccessLevel.PROTECTED)  // JPA O, 그 외 X
@Getter
@Entity
@Table(name = "housing_application")
public class HousingApplication {

    public static final String RESULT_WIN = "WIN";
    public static final String RESULT_LOSE = "LOSE";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "housing_application_id")
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "housing_complex_id", nullable = false)
    private Long housingComplexId;

    @Column(name = "receipt_no", nullable = false)
    private String receiptNo;

    @Column(name = "homeless_years", nullable = false)
    private int homelessYears;

    @Column(name = "dependents", nullable = false)
    private int dependents;

    @Column(name = "paid_count_snapshot", nullable = false)
    private int paidCountSnapshot;

    @Column(name = "homeless_score", nullable = false)
    private int homelessScore;

    @Column(name = "dependent_score", nullable = false)
    private int dependentScore;

    @Column(name = "account_score", nullable = false)
    private int accountScore;

    @Column(name = "total_score", nullable = false)
    private int totalScore;

    @Column(name = "result", nullable = true)
    private String result;

    @Column(name = "result_rank", nullable = true)
    private Integer resultRank;

    @Column(name = "applied_at", nullable = false)
    private LocalDateTime appliedAt;

}
