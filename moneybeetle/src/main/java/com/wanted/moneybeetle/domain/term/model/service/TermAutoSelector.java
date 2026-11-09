package com.wanted.moneybeetle.domain.term.model.service;

import com.wanted.moneybeetle.domain.term.model.dao.EconomicTermRepository;
import com.wanted.moneybeetle.domain.term.model.dao.TermScheduleRepository;
import com.wanted.moneybeetle.domain.term.model.entity.EconomicTerm;
import com.wanted.moneybeetle.domain.term.model.entity.TermSchedule;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/* comment.
*   담당 : DBA ② (경제 용어)
*   관리자가 지정하지 않은 날의 오늘의 용어 자동 선정 (SP-46)
*   - 사용 중인 용어를 등록 순서로 놓고, 날짜마다 5개씩 이어서 고른다 (끝에 닿으면 처음으로 돌아간다).
*     같은 날에는 누가 언제 보든 같은 5개여야 해서(SP-45) 처음 볼 때 한 번 정해 저장하고(is_auto = true) 그 뒤에는 저장된 값을 쓴다.
*   - 용어가 5개보다 적으면 있는 만큼만 고른다 (없으면 아무것도 저장하지 않는다).
*   - 저장은 새 트랜잭션에서 한다. 동시에 두 요청이 같은 날을 정하려 하면 DB 의 UNIQUE 제약으로 한쪽이 실패하고,
*     실패한 쪽은 TermService 가 저장된 값을 다시 읽는다 (바깥 트랜잭션을 망가뜨리지 않으려고 따로 둔 클래스다).
*  */
@Component
@RequiredArgsConstructor
public class TermAutoSelector {

    private final EconomicTermRepository economicTermRepository;
    private final TermScheduleRepository termScheduleRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<TermSchedule> select(LocalDate date) {
        List<Long> picked = pickTermIds(date, economicTermRepository.findByActiveTrueOrderByEconomicTermIdAsc());
        List<TermSchedule> rows = new ArrayList<>();
        for (int i = 0; i < picked.size(); i++) {
            rows.add(TermSchedule.create(date, picked.get(i), i + 1, true));
        }
        return rows.isEmpty() ? rows : termScheduleRepository.saveAll(rows);
    }

    /**
     * 그 날짜에 자동 선정될 용어 번호를 순서대로 계산한다 (저장하지 않는다). 관리자 월별 화면이 지정하지 않은 날의 결과를
     * 미리 보여 줄 때도 이 계산을 쓴다. 용어가 없으면 빈 목록이다.
     *
     * @param activeTerms 사용 중인 용어를 번호(등록) 순서로 놓은 목록
     */
    static List<Long> pickTermIds(LocalDate date, List<EconomicTerm> activeTerms) {
        if (activeTerms.isEmpty()) {
            return List.of();
        }
        int count = Math.min(TermSchedule.DAILY_COUNT, activeTerms.size());
        int start = (int) Math.floorMod(date.toEpochDay() * TermSchedule.DAILY_COUNT, (long) activeTerms.size());
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            ids.add(activeTerms.get((start + i) % activeTerms.size()).getEconomicTermId());
        }
        return ids;
    }
}
