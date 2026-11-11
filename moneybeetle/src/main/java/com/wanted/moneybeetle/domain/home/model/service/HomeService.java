package com.wanted.moneybeetle.domain.home.model.service;

import com.wanted.moneybeetle.domain.course.exception.CourseErrorCode;
import com.wanted.moneybeetle.domain.course.model.dto.CourseDetailDTO;
import com.wanted.moneybeetle.domain.course.model.dto.CourseListDTO;
import com.wanted.moneybeetle.domain.course.model.dto.LessonPlayDTO;
import com.wanted.moneybeetle.domain.course.model.dto.WatchHistoryDTO;
import com.wanted.moneybeetle.domain.course.model.service.CourseService;
import com.wanted.moneybeetle.domain.growth.model.service.GrowthService;
import com.wanted.moneybeetle.domain.home.model.dto.HomeDTO;
import com.wanted.moneybeetle.domain.housing.model.dto.AccountDTO;
import com.wanted.moneybeetle.domain.housing.model.dto.ComplexDTO;
import com.wanted.moneybeetle.domain.housing.model.service.HousingService;
import com.wanted.moneybeetle.domain.home.model.dto.SearchResultDTO;
import com.wanted.moneybeetle.domain.term.model.service.TermService;
import com.wanted.moneybeetle.domain.trading.model.dto.StockSearchDTO;
import com.wanted.moneybeetle.domain.trading.model.dto.TradingHomeDTO;
import com.wanted.moneybeetle.domain.trading.model.service.ParticipationService;
import com.wanted.moneybeetle.domain.trading.model.service.StockSearchService;
import com.wanted.moneybeetle.global.security.UnlockFeature;
import com.wanted.moneybeetle.global.security.UnlockLevelProvider;
import com.wanted.moneybeetle.global.exception.BusinessException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/* comment.
*   담당 : DBA ② (홈 API, #13) · PM (통합 검색 API, #15)
*   course / term / trading 서비스의 조회 메서드만 모아서 보여 준다 (다른 도메인 테이블 직접 조회 금지)
*   - 통합 검색(#15, API 28) : 강의(보이는 강의만), 경제 용어, 종목(모의투자 해금 레벨 이상일 때만. 운영 설정, 기본 Lv.8)
*     키워드는 앞뒤 공백을 빼고, 비어 있으면 빈 결과
*     TODO(DBA ② TermService) 경제 용어 검색 메서드가 생기면 terms 를 채운다 (지금은 빈 목록)
*   - 홈 화면 (API 19): 오늘의 경제 용어(term), 이어서 볼 강의·학습 기록(course), 트레이닝 해금(growth 의 레벨)을 한 번에 모은다.
*     시안의 값(#261): 이어서 볼 영상의 길이·몇 강째인지·소주제(course), 모의투자 시즌 요약(trading), 접수 중 공고 수·내 납입 횟수(housing)도 담는다.
*     요약은 해금된 기능만 만들고, 만들 수 없으면(시즌·통장 없음, 예상 밖 오류) 홈 전체를 실패시키지 않고 그 요약만 null 로 둔다.
*   - 이 클래스는 트랜잭션을 열지 않는다. 각 서비스 호출이 자기 트랜잭션을 연다 (오늘의 용어 자동 선정이 트랜잭션 밖에서 실행돼야 해서).
*   - 이어서 볼 강의는 course 도메인의 공개된 조회 메서드(이어 볼 강의 목록, 그 강의의 마지막 시청 기록 한 건(#188), 영상 재생 정보·강의 상세)를 조합한다.
*     본 영상 수·학습 완료 강의 수는 개수 전용 조회(#188)로 센다 (#267).
*  */
@Slf4j
@Service
@RequiredArgsConstructor
public class HomeService {

    /** 청약 단지 상태 중 접수 중인 값. housing 의 ComplexDTO.status 가 이 문자열로 내려온다 (다른 도메인의 Entity 를 쓰지 않으려고 여기에 따로 둔다) */
    private static final String HOUSING_STATUS_OPEN = "OPEN";

    private final TermService termService;
    private final CourseService courseService;
    private final GrowthService growthService;
    private final StockSearchService stockSearchService;
    private final ParticipationService participationService;
    private final HousingService housingService;
    private final Clock clock;
    /** 해금 레벨은 운영 설정(관리자 API 112)에서 읽는다 → 모의투자·모의 청약 API·화면과 같은 기준 (#238) */
    private final UnlockLevelProvider unlockLevelProvider;

    public HomeDTO getHome(Long memberId) {
        var todayTerms = termService.getToday().terms();
        int level = growthService.getLevel(memberId);

        // 목록을 만들지 않고 개수만 센다 (#188 의 개수 전용 조회, 시청 기록·학습 완료 목록과 같은 조건)
        long watchedLessons = courseService.countWatchedLessons(memberId);
        long completedCourses = courseService.countCompletedCourses(memberId);

        return new HomeDTO(
                todayTerms,
                findContinueCourse(memberId),
                new HomeDTO.Learning(watchedLessons, completedCourses),
                buildTraining(memberId, level));
    }

    /** 트레이닝 해금 상태와, 해금된 기능의 카드 요약 */
    private HomeDTO.Training buildTraining(Long memberId, int level) {
        // 해금 레벨은 운영 설정 값(#238). 한 번 읽어 해금 여부·필요 레벨·요약 여부에 같이 쓴다 (한 응답 안에서 어긋나지 않게)
        int tradingLevel = unlockLevelProvider.getLevel(UnlockFeature.TRADING);
        int housingLevel = unlockLevelProvider.getLevel(UnlockFeature.HOUSING);
        boolean tradingOpen = level >= tradingLevel;
        boolean housingOpen = level >= housingLevel;
        return new HomeDTO.Training(
                new HomeDTO.Unlock(tradingOpen, tradingLevel),
                new HomeDTO.Unlock(housingOpen, housingLevel),
                tradingOpen ? findTradingSeason(memberId) : null,
                housingOpen ? findHousingSummary(memberId) : null);
    }

    /** 지금 보이는 모의투자 시즌 요약. 시즌이 없거나 읽지 못하면 null (홈은 그대로 나간다) */
    private HomeDTO.TradingSeason findTradingSeason(Long memberId) {
        try {
            TradingHomeDTO.Season season = participationService.getHome(memberId).getSeason();
            int daysLeft = season.getEndDate() == null ? 0
                    : (int) Math.max(0, ChronoUnit.DAYS.between(LocalDate.now(clock), season.getEndDate()));
            return new HomeDTO.TradingSeason(season.getSeasonId(), season.getName(), season.getStatus(),
                    season.getCurrentTurn(), season.getTotalTurns(), daysLeft);
        } catch (BusinessException e) {
            log.debug("홈의 모의투자 요약을 만들지 못했어요(시즌 없음 등): 오류 코드={}", e.getErrorCode().getCode());
            return null;
        } catch (RuntimeException e) {
            log.error("홈의 모의투자 요약을 만드는 중 예상하지 못한 오류가 났어요: memberId={}", memberId, e);
            return null;
        }
    }

    /** 접수 중인 공고 수(이번 달에 걸친 단지 중 OPEN)와 내 납입 횟수. 통장이 없으면 납입 횟수만 null, 공고를 못 읽으면 전체 null */
    private HomeDTO.HousingSummary findHousingSummary(Long memberId) {
        try {
            int open = (int) housingService.findCalendar(YearMonth.now(clock), memberId).stream()
                    .filter(complex -> HOUSING_STATUS_OPEN.equals(complex.getStatus()))
                    .count();
            return new HomeDTO.HousingSummary(open, findPaidCount(memberId));
        } catch (BusinessException e) {
            log.debug("홈의 모의 청약 요약을 만들지 못했어요: 오류 코드={}", e.getErrorCode().getCode());
            return null;
        } catch (RuntimeException e) {
            log.error("홈의 모의 청약 요약을 만드는 중 예상하지 못한 오류가 났어요: memberId={}", memberId, e);
            return null;
        }
    }

    private Integer findPaidCount(Long memberId) {
        try {
            AccountDTO account = housingService.getAccount(memberId);
            return account.getPaidCount();
        } catch (BusinessException e) {
            return null;   // 청약통장이 아직 없다
        }
    }

    /** 가장 최근에 본 진행 중인 강의 하나와 그 강의에서 마지막으로 본 영상·위치. 이어서 볼 강의가 없거나 그 사이 비공개가 되었으면 null */
    private HomeDTO.ContinueCourse findContinueCourse(Long memberId) {
        List<CourseListDTO> continuing = courseService.findContinueWatching(memberId);
        if (continuing.isEmpty()) {
            return null;
        }
        CourseListDTO course = continuing.get(0);

        // 그 강의에서 가장 최근에 본 영상 기록 한 건 (#188). 기록이 아주 많아도 쪽을 넘기지 않고 바로 찾는다
        WatchHistoryDTO lastWatched = courseService.findLastWatched(memberId, course.getCourseId()).orElse(null);
        if (lastWatched == null) {
            return new HomeDTO.ContinueCourse(course.getCourseId(), course.getTitle(), null, null, null, null, null, null, null);
        }
        LessonPlayDTO play;
        CourseDetailDTO detail;
        try {
            play = courseService.getLessonPlay(memberId, course.getCourseId(), lastWatched.getLessonId());
            detail = courseService.getCourseDetail(memberId, course.getCourseId());
        } catch (BusinessException e) {
            // 이어서 볼 강의를 찾은 뒤 그 강의가 비공개로 바뀌는 경우: 홈 전체가 실패하지 않고 이어서 볼 강의만 비운다.
            // 그 밖의 오류는 숨기지 않고 그대로 던진다
            if (e.getErrorCode() == CourseErrorCode.COURSE_NOT_PUBLIC) {
                return null;
            }
            throw e;
        }
        return new HomeDTO.ContinueCourse(course.getCourseId(), course.getTitle(),
                lastWatched.getLessonId(), lastWatched.getLessonTitle(), play.getPositionSeconds(), play.getDurationSeconds(),
                lessonNumber(detail, lastWatched.getLessonId()), detail.getLessons().size(), detail.getCategoryName());
    }

    /** 그 강의의 영상 목록(재생 순서)에서 이 영상이 몇 번째인지(1부터). 목록에 없으면 null */
    private static Integer lessonNumber(CourseDetailDTO detail, Long lessonId) {
        List<CourseDetailDTO.LessonItem> lessons = detail.getLessons();
        for (int i = 0; i < lessons.size(); i++) {
            if (lessonId.equals(lessons.get(i).getLessonId())) {
                return i + 1;
            }
        }
        return null;
    }

    /** API 28 통합 검색. memberId 는 세션의 로그인 회원 번호이고, 없으면 종목은 찾지 않는다. 각 서비스 호출이 자기 읽기 전용 트랜잭션을 연다 */
    public SearchResultDTO search(String keyword, Long memberId) {
        String word = keyword == null ? "" : keyword.trim();
        if (word.isEmpty()) {
            return SearchResultDTO.empty();
        }
        return SearchResultDTO.of(courseService.search(word), List.of(), searchStocksIfUnlocked(word, memberId));
    }

    /** 종목은 모의투자가 해금된 회원에게만 보여 준다 (INV-01) */
    private List<StockSearchDTO> searchStocksIfUnlocked(String keyword, Long memberId) {
        // 모의투자 해금 레벨은 운영 설정 값 (LevelCheckInterceptor 와 같은 UnlockLevelProvider, #238)
        if (memberId == null || growthService.getLevel(memberId) < unlockLevelProvider.getLevel(UnlockFeature.TRADING)) {
            return List.of();
        }
        return stockSearchService.searchStocks(keyword);
    }
}
