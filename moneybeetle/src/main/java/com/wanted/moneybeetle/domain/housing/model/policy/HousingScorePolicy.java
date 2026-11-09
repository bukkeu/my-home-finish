package com.wanted.moneybeetle.domain.housing.model.policy;

import com.wanted.moneybeetle.domain.housing.model.dto.ScoreDTO;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/* comment.
*   담당 : SCM (모의 청약)
*   청약 가점 계산 규칙 (SP-71 확정, #29) - 실제 청약 가점제 점수표(「주택공급에 관한 규칙」 별표1)
*   - 무주택 기간 (만점 32) : 1년 미만 2점, 1년마다 +2점, 15년 이상 32점
*   - 부양가족 수 (만점 35) : 0명 5점, 1명마다 +5점, 6명 이상 35점
*   - 청약통장   (만점 17) : 실제는 가입 기간. 서비스에는 가입 기간이 없어 "납입 1회 = 1개월" 로 본다
*                           6회 미만 1점, 6~11회 2점, 12~23회 3점, 이후 12회마다 +1점, 180회 이상 17점
*   입력값만으로 판단하는 계산이라 DB 를 보지 않는다 (강의 module07 의 policy 와 같은 위치)
*  */
@Component
public class HousingScorePolicy {

    public static final int MAX_HOMELESS = 32;
    public static final int MAX_DEPENDENT = 35;
    public static final int MAX_ACCOUNT = 17;
    public static final int MAX_TOTAL = MAX_HOMELESS + MAX_DEPENDENT + MAX_ACCOUNT;   // 84

    // 무주택 기간(년, 1년 미만은 0) → 2, 4, 6, ... 32
    public int homelessScore(int homelessYears) {
        return Math.min(MAX_HOMELESS, 2 * (homelessYears + 1));
    }

    // 부양가족 수 → 5, 10, 15, ... 35
    public int dependentScore(int dependents) {
        return Math.min(MAX_DEPENDENT, 5 + 5 * dependents);
    }

    // 납입회차(= 개월로 봄) → 1, 2, 3, 4, ... 17
    public int accountScore(int paidCount) {
        if (paidCount < 6) {
            return 1;
        }
        if (paidCount < 12) {
            return 2;
        }
        return Math.min(MAX_ACCOUNT, 3 + (paidCount - 12) / 12);   // 12~23회 3점, 24~35회 4점 ...
    }

    // 세 항목을 계산해 결과로 묶는다. baseDate = 계산한 날 (화면에 기준일로 표시)
    public ScoreDTO calculate(int homelessYears, int dependents, int paidCount, LocalDate baseDate) {
        int homeless = homelessScore(homelessYears);
        int dependent = dependentScore(dependents);
        int account = accountScore(paidCount);
        return new ScoreDTO(homeless, dependent, account, homeless + dependent + account, MAX_TOTAL, baseDate);
    }
}
