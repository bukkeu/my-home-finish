package com.wanted.moneybeetle.domain.trading.model.service;

/* comment.
*   담당 : PM (모의투자)
*   관리자 시즌 관리 (#40, API 114~117)
*   - 시즌 생성 : 시작일 월요일 확인 -> 시즌 저장 -> 12턴 달력(일요일 제외) 저장. 이전 시즌의 이월·해금 설정 복사
*   - 설정 변경 : 이월 비율·상한 (정산 중·종료 시즌은 막음). 해금 레벨은 시즌 설정이 아니라 운영 설정이다 (#242)
*   - 상태 변경 : SP-65 순서만 허용 (규칙은 TradingSeason 엔티티)
*   다음 턴 점검·진행(#44)은 이 서비스에 이어서 추가한다
*  */

import com.wanted.moneybeetle.domain.trading.model.dao.SeasonParticipantRepository;
import com.wanted.moneybeetle.domain.trading.model.dao.SeasonTurnRepository;
import com.wanted.moneybeetle.domain.trading.model.dao.TradingSeasonRepository;
import com.wanted.moneybeetle.domain.trading.model.dto.SeasonCreatedDTO;
import com.wanted.moneybeetle.domain.trading.model.dto.SeasonSaveDTO;
import com.wanted.moneybeetle.domain.trading.model.dto.SeasonSettingDTO;
import com.wanted.moneybeetle.domain.trading.model.dto.SeasonStatusDTO;
import com.wanted.moneybeetle.domain.trading.model.dto.SeasonSummaryDTO;
import com.wanted.moneybeetle.domain.trading.model.entity.SeasonTurn;
import com.wanted.moneybeetle.domain.trading.model.entity.TradingSeason;
import com.wanted.moneybeetle.global.exception.BusinessException;
import com.wanted.moneybeetle.global.exception.CommonErrorCode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SeasonAdminService {

    private final TradingSeasonRepository seasonRepository;
    private final SeasonTurnRepository turnRepository;
    private final SeasonParticipantRepository participantRepository;

    /** API 114 시즌 목록 */
    public List<SeasonSummaryDTO> findSeasons() {
        List<TradingSeason> seasons = seasonRepository.findAllByOrderByStartDateDesc();
        if (seasons.isEmpty()) {
            return List.of();
        }
        // 시즌마다 세지 않고 한 번에 센다. 참가자가 없는 시즌은 결과에 없으므로 0으로 둔다
        Map<Long, Long> counts = participantRepository
                .countByTradingSeasonIds(seasons.stream().map(TradingSeason::getTradingSeasonId).toList()).stream()
                .collect(Collectors.toMap(SeasonParticipantRepository.SeasonParticipantCount::getSeasonId,
                        SeasonParticipantRepository.SeasonParticipantCount::getParticipantCount));
        return seasons.stream()
                .map(season -> SeasonSummaryDTO.from(season, counts.getOrDefault(season.getTradingSeasonId(), 0L)))
                .toList();
    }

    /** API 115 시즌 생성 : 14일·12턴 달력을 함께 만든다 */
    @Transactional
    public SeasonCreatedDTO createSeason(SeasonSaveDTO request, Long adminId) {
        // trading_season.created_by 는 NOT NULL + FK 라, 로그인한 관리자 번호가 없으면 저장 단계에서 500 이 난다
        if (adminId == null) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
        }
        request.checkStartDate();

        TradingSeason season = TradingSeason.create(request.getName(), request.getStartDate(), adminId);
        if (request.getCopyFromSeasonId() != null) {
            TradingSeason source = getSeason(request.getCopyFromSeasonId());
            season.changeSetting(source.getCarryoverRate(), source.getCarryoverCap());
        }
        TradingSeason saved = seasonRepository.save(season);

        List<SeasonTurn> turns = new ArrayList<>();
        List<LocalDate> tradeDates = saved.tradeDates();
        for (int i = 0; i < tradeDates.size(); i++) {
            turns.add(SeasonTurn.create(saved.getTradingSeasonId(), i + 1, tradeDates.get(i)));
        }
        List<SeasonTurn> savedTurns = turnRepository.saveAll(turns);
        return SeasonCreatedDTO.from(saved, savedTurns);
    }

    /** API 116 시즌 설정 */
    @Transactional
    public void changeSetting(Long seasonId, SeasonSettingDTO request) {
        // 상태 변경과 같은 시즌 행 잠금. 잠금 없이 읽고 저장하면 그 사이 커밋된 상태(예: 정산 중)가 옛 값으로 되돌아간다
        seasonRepository.findByIdForUpdate(seasonId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND))
                .changeSetting(request.getCarryoverRate(), request.getCarryoverCap());
    }

    /** API 117 시즌 상태 변경 */
    @Transactional
    public void changeStatus(Long seasonId, SeasonStatusDTO request) {
        seasonRepository.findByIdForUpdate(seasonId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND))
                .changeStatus(request.getStatus());
    }

    private TradingSeason getSeason(Long seasonId) {
        return seasonRepository.findById(seasonId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
    }
}
