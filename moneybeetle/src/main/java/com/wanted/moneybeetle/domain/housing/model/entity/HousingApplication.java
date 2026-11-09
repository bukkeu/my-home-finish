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

    // 청약 신청 (#29) : 신청 시점의 입력값·납입회차·항목별 점수를 그대로 저장한다 (나중에 통장이 바뀌어도 신청 점수는 그대로)
    //  결과(result·resultRank)는 발표 전이라 비워 두고, 당첨 산정(#48)에서 채운다. 신청 후 취소·수정 불가 (SP-73)
    public static HousingApplication create(Long memberId, Long housingComplexId, String receiptNo,
                                            int homelessYears, int dependents, int paidCountSnapshot,
                                            int homelessScore, int dependentScore, int accountScore,
                                            LocalDateTime appliedAt) {
        HousingApplication application = new HousingApplication();
        application.memberId = memberId;
        application.housingComplexId = housingComplexId;
        application.receiptNo = receiptNo;
        application.homelessYears = homelessYears;
        application.dependents = dependents;
        application.paidCountSnapshot = paidCountSnapshot;
        application.homelessScore = homelessScore;
        application.dependentScore = dependentScore;
        application.accountScore = accountScore;
        application.totalScore = homelessScore + dependentScore + accountScore;
        application.appliedAt = appliedAt;
        return application;
    }

    // 결과가 나왔나? (산정 전이면 null)
    public boolean hasResult() {
        return result != null;
    }

    public boolean isWin() {
        return RESULT_WIN.equals(result);
    }

    // 당첨자 산정 (#48) : 순위와 결과(WIN/LOSE)를 기록한다. 산정은 단지마다 한 번뿐이다 (SP-75)
    public void changeResult(int rank, boolean win) {
        this.resultRank = rank;
        this.result = win ? RESULT_WIN : RESULT_LOSE;
    }
}
