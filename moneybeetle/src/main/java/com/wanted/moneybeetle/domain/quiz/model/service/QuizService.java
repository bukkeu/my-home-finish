package com.wanted.moneybeetle.domain.quiz.model.service;

import com.wanted.moneybeetle.domain.course.model.service.CourseService;
import com.wanted.moneybeetle.domain.growth.model.dto.LevelDTO;
import com.wanted.moneybeetle.domain.growth.model.dto.RewardResultDTO;
import com.wanted.moneybeetle.domain.growth.model.entity.RewardRule;
import com.wanted.moneybeetle.domain.growth.model.service.GrowthService;
import com.wanted.moneybeetle.domain.growth.model.service.RewardService;
import com.wanted.moneybeetle.domain.member.model.service.MemberService;
import com.wanted.moneybeetle.domain.operation.model.service.SystemSettingService;
import com.wanted.moneybeetle.domain.quiz.exception.QuizErrorCode;
import com.wanted.moneybeetle.domain.quiz.model.dao.QuizAnswerRepository;
import com.wanted.moneybeetle.domain.quiz.model.dao.QuizAttemptRepository;
import com.wanted.moneybeetle.domain.quiz.model.dao.QuizChoiceRepository;
import com.wanted.moneybeetle.domain.quiz.model.dao.QuizQuestionRepository;
import com.wanted.moneybeetle.domain.quiz.model.dao.WrongAnswerNoteRepository;
import com.wanted.moneybeetle.domain.quiz.model.dto.QuizPaperDTO;
import com.wanted.moneybeetle.domain.quiz.model.dto.QuizQuestionDTO;
import com.wanted.moneybeetle.domain.quiz.model.dto.QuizResultDTO;
import com.wanted.moneybeetle.domain.quiz.model.dto.QuizReviewDTO;
import com.wanted.moneybeetle.domain.quiz.model.dto.QuizSubmitDTO;
import com.wanted.moneybeetle.domain.quiz.model.dto.TestQuizResultDTO;
import com.wanted.moneybeetle.domain.quiz.model.entity.QuizAnswer;
import com.wanted.moneybeetle.domain.quiz.model.entity.QuizAttempt;
import com.wanted.moneybeetle.domain.quiz.model.entity.QuizChoice;
import com.wanted.moneybeetle.domain.quiz.model.entity.QuizQuestion;
import com.wanted.moneybeetle.domain.quiz.model.entity.WrongAnswerNote;
import com.wanted.moneybeetle.domain.quiz.model.policy.QuestionSheet;
import com.wanted.moneybeetle.global.exception.BusinessException;
import com.wanted.moneybeetle.global.exception.CommonErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/* comment.
 *   담당 : DBA ① (강의·퀴즈)
 *   테스트 퀴즈 채점(기록 없음), 퀴즈 서버 채점(70점 통과), 오답 저장
 *   - 채점 규칙(정답 찾기·보기 소속·맞았는지)은 QuestionSheet(model/policy)에 있다 (#294)
 *   - 오답노트 조회·다시 풀기·삭제는 WrongNoteService (#294). 여기서는 제출할 때 틀린 문제를 오답노트에 넣기만 한다
 *   최초 통과 시 학습 완료 처리 + growth.RewardService 에 보상 요청 (한 트랜잭션, NFR-06)
 *   - 퀴즈 제출(API 42)의 중복 요청은 global IdempotencyFilter(Idempotency-Key)가 막는다 (DP-37)
 *  */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class QuizService {

    private static final int TEST_QUIZ_COUNT = 5;       // 테스트 퀴즈(OX) 문항 수 (TCH-04)
    private static final int MAIN_QUIZ_MIN = 5;         // 본 퀴즈(4지선다) 최소 (TCH-05)
    private static final int MAIN_QUIZ_MAX = 15;        // 본 퀴즈 최대
    private static final String PASS_SCORE_KEY = "QUIZ_PASS_SCORE";   // system_setting 키 (SP-34)
    private static final int DEFAULT_PASS_SCORE = 70;   // 설정이 없거나 숫자가 아닐 때 (SP-34 초안 값)

    private final CourseService courseService;
    private final QuizQuestionRepository quizQuestionRepository;
    private final QuizChoiceRepository quizChoiceRepository;
    private final WrongAnswerNoteRepository wrongAnswerNoteRepository;
    private final QuizAttemptRepository quizAttemptRepository;
    private final QuizAnswerRepository quizAnswerRepository;
    private final RewardService rewardService;                  // growth 도메인 (DBA②) - 보상 지급
    private final GrowthService growthService;                  // growth 도메인 (DBA②) - 레벨 이름
    private final SystemSettingService systemSettingService;    // operation 도메인 (DBA②) - 통과 점수
    private final MemberService memberService;                  // member 도메인 (DBA②) - 회원 행 잠금
    private final Clock clock;                                  // Asia/Seoul 공통 시계

    /* ===== 테스트 퀴즈 (#19) ===== */

    /* API 39. 테스트 퀴즈 문제: OX 5문제, 정답은 보내지 않는다 (QST-01, SP-35) */
    public QuizQuestionDTO getTestQuiz(Long courseId) {
        courseService.checkCourseVisible(courseId);

        List<QuizQuestion> questions = findQuestions(courseId, QuizQuestion.QUIZ_TYPE_TEST);
        return QuizQuestionDTO.from(questions, findChoicesByQuestion(questions));
    }

    /* API 40. 테스트 퀴즈 채점: 정답·해설만 돌려주고 아무것도 저장하지 않는다 (QST-01, SP-33) */
    public TestQuizResultDTO gradeTestQuiz(Long courseId, QuizSubmitDTO request) {
        courseService.checkCourseVisible(courseId);

        List<QuestionSheet> sheets = findSheets(findQuestions(courseId, QuizQuestion.QUIZ_TYPE_TEST));
        Map<Long, Long> selected = request.toSelectedMap();

        // 이 강의의 테스트 문항만 채점한다. 다른 강의의 문항 ID를 보내도 무시된다
        List<TestQuizResultDTO.ResultItem> results = QuestionSheet.gradeAll(sheets, selected);

        int correctCount = (int) results.stream().filter(TestQuizResultDTO.ResultItem::isCorrect).count();
        // 문항이 없으면 열지 않는다. 모든 문항에서 "그 문항의 보기"를 골랐을 때만 영상이 열린다
        boolean allAnswered = !sheets.isEmpty()
                && sheets.stream().allMatch(sheet -> sheet.owns(selected.get(sheet.questionId())));

        return TestQuizResultDTO.of(correctCount, results, allAnswered);
    }

    /* ===== 퀴즈 제출·채점 (#20) ===== */

    /* API 41. 퀴즈 문제: 4지선다, 영상을 모두 본 뒤 열린다. 정답·해설은 보내지 않는다 (QST-02, SP-35) */
    public QuizPaperDTO getQuiz(Long memberId, Long courseId) {
        List<QuizQuestion> questions = getOpenQuiz(memberId, courseId);
        return new QuizPaperDTO(passScore(),
                QuizQuestionDTO.from(questions, findChoicesByQuestion(questions)).getQuestions());
    }

    /* API 42. 퀴즈 제출·채점 (QST-02~05, SP-34~36, NFR-06)
     * 서버에서 채점 → 응시·답안 저장 → 틀린 문제는 오답노트에 → 통과했고 처음 완료면 학습 완료 + 보상.
     * 모두 한 트랜잭션이라 보상 지급이 실패하면 채점 기록도 함께 취소된다.
     * 맨 처음 회원 행을 잠가 같은 회원의 제출·시청 위치 저장을 한 줄로 세운다 (보상 지급도 같은 잠금을 쓰므로
     * 잠금 순서가 항상 회원 → 학습 기록이라 교착 상태가 없고, 동시 제출이 오래된 완료 여부로 보상을 두 번 판단하지 않는다) */
    @Transactional
    public QuizResultDTO submitQuiz(Long memberId, Long courseId, QuizSubmitDTO request) {
        memberService.lockBalance(memberId);
        List<QuestionSheet> sheets = findSheets(getOpenQuiz(memberId, courseId));
        Map<Long, Long> selected = request.toSelectedMap();

        // 이 강의의 퀴즈 문항만 채점한다. 안 푼 문제·이 문항의 보기가 아닌 답은 오답
        List<TestQuizResultDTO.ResultItem> results = QuestionSheet.gradeAll(sheets, selected);
        int correctCount = (int) results.stream().filter(TestQuizResultDTO.ResultItem::isCorrect).count();
        QuizAttempt attempt = quizAttemptRepository.save(QuizAttempt.graded(
                memberId, courseId, sheets.size(), correctCount, passScore(), LocalDateTime.now(clock)));

        saveAnswers(attempt, sheets, selected, results);
        saveWrongNotes(memberId, sheets, selected, results);

        QuizResultDTO.Rewards rewards = attempt.isPassed() && courseService.completeLearning(memberId, courseId)
                ? grantFirstPassRewards(memberId, courseId, attempt)
                : QuizResultDTO.Rewards.none();
        return QuizResultDTO.of(attempt, results, rewards);
    }

    /* API 43. 퀴즈 결과 다시 보기: 본인 응시만 (QST-03, DP-37). 보상은 이미 지급돼서 rewards 는 null */
    public QuizResultDTO getQuizResult(Long memberId, Long attemptId) {
        QuizAttempt attempt = quizAttemptRepository.findById(attemptId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        if (!attempt.isOwnedBy(memberId)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }

        List<QuizAnswer> answers = quizAnswerRepository.findByQuizAttemptIdOrderByQuizAnswerIdAsc(attemptId);
        Map<Long, QuizQuestion> questions = quizQuestionRepository.findAllById(
                        answers.stream().map(QuizAnswer::getQuizQuestionId).toList())
                .stream()
                .collect(Collectors.toMap(QuizQuestion::getQuizQuestionId, Function.identity()));
        Map<Long, QuestionSheet> sheets = findSheets(List.copyOf(questions.values())).stream()
                .collect(Collectors.toMap(QuestionSheet::questionId, Function.identity()));

        // 다시 보기는 저장된 채점 결과(isCorrect)를 그대로 쓰고, 정답 보기만 지금 보기에서 찾는다
        List<TestQuizResultDTO.ResultItem> results = answers.stream()
                .filter(answer -> questions.containsKey(answer.getQuizQuestionId()))
                .sorted(Comparator.comparingInt(answer -> questions.get(answer.getQuizQuestionId()).getSortOrder()))
                .map(answer -> TestQuizResultDTO.ResultItem.of(questions.get(answer.getQuizQuestionId()),
                        answer.isCorrect(), sheets.get(answer.getQuizQuestionId()).correctChoiceId()))
                .toList();
        return QuizResultDTO.of(attempt, results, null);
    }

    /* ===== 관리자 검수 (#35, course 도메인 AdminCourseService에서 호출) ===== */

    /* API 90. 검수 상세의 퀴즈: 테스트·본 퀴즈 모든 문항 + 정답 포함 보기. 보기는 한 번에 조회 */
    public List<QuizReviewDTO> findQuizzesForReview(Long courseId) {
        return findSheets(quizQuestionRepository.findByCourseIdOrderByQuizTypeAscSortOrderAscQuizQuestionIdAsc(courseId))
                .stream()
                .map(sheet -> QuizReviewDTO.of(sheet.question(), sheet.choices()))
                .toList();
    }

    /* ===== 강사 검수 요청 (#32, course 도메인 InstructorCourseService에서 호출) ===== */

    /* API 81 검수 요청 조건 (DP-34): 테스트 퀴즈 5문제, 본 퀴즈 5~15문제. 아니면 QUIZ_COUNT_INVALID */
    public void checkReadyForReview(Long courseId) {
        long testCount = quizQuestionRepository.countByCourseIdAndQuizType(courseId, QuizQuestion.QUIZ_TYPE_TEST);
        long mainCount = quizQuestionRepository.countByCourseIdAndQuizType(courseId, QuizQuestion.QUIZ_TYPE_MAIN);
        if (testCount != TEST_QUIZ_COUNT || mainCount < MAIN_QUIZ_MIN || mainCount > MAIN_QUIZ_MAX) {
            throw new BusinessException(QuizErrorCode.QUIZ_COUNT_INVALID);
        }
    }

    /* ===== 내부 도우미 ===== */

    /* 퀴즈를 풀 수 있는지: 보이는 강의, 영상을 모두 끝까지 봤는지(QUIZ_LOCKED), 퀴즈 문항이 있는지(NOT_FOUND) */
    private List<QuizQuestion> getOpenQuiz(Long memberId, Long courseId) {
        courseService.checkCourseVisible(courseId);
        if (!courseService.hasWatchedAllLessons(memberId, courseId)) {
            throw new BusinessException(QuizErrorCode.QUIZ_LOCKED);
        }
        List<QuizQuestion> questions = findQuestions(courseId, QuizQuestion.QUIZ_TYPE_MAIN);
        if (questions.isEmpty()) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND);
        }
        return questions;
    }

    /* 통과 점수: 관리자가 바꿀 수 있는 설정 값 (system_setting QUIZ_PASS_SCORE, NFR-13).
     * 없거나 1~100 밖이면 70 (0 이하면 누구나 통과, 100 초과면 아무도 통과 못 하는 설정 실수 방지) */
    private int passScore() {
        int score = systemSettingService.findIntValue(PASS_SCORE_KEY).orElse(DEFAULT_PASS_SCORE);
        return (score >= 1 && score <= 100) ? score : DEFAULT_PASS_SCORE;
    }

    /* 응시별 문항 답안 저장 (안 풀었거나 이 문항의 보기가 아니면 고른 보기는 null로 남긴다) */
    private void saveAnswers(QuizAttempt attempt, List<QuestionSheet> sheets, Map<Long, Long> selected,
                             List<TestQuizResultDTO.ResultItem> results) {
        List<QuizAnswer> answers = new ArrayList<>();
        for (int i = 0; i < sheets.size(); i++) {
            QuestionSheet sheet = sheets.get(i);
            answers.add(QuizAnswer.create(attempt.getQuizAttemptId(), sheet.questionId(),
                    sheet.validChoice(selected.get(sheet.questionId())), results.get(i).isCorrect()));
        }
        quizAnswerRepository.saveAll(answers);
    }

    /* 틀린 문제를 오답노트에: 같은 문제는 한 번만 저장하고, 또 틀리면 마지막 오답만 바꾼다 (SP-36) */
    private void saveWrongNotes(Long memberId, List<QuestionSheet> sheets, Map<Long, Long> selected,
                                List<TestQuizResultDTO.ResultItem> results) {
        List<QuestionSheet> wrong = new ArrayList<>();
        for (int i = 0; i < sheets.size(); i++) {
            if (!results.get(i).isCorrect()) {
                wrong.add(sheets.get(i));
            }
        }
        if (wrong.isEmpty()) {
            return;
        }
        Map<Long, WrongAnswerNote> existing = wrongAnswerNoteRepository.findByMemberIdAndQuizQuestionIdIn(
                        memberId, wrong.stream().map(QuestionSheet::questionId).toList())
                .stream()
                .collect(Collectors.toMap(WrongAnswerNote::getQuizQuestionId, Function.identity()));
        List<WrongAnswerNote> created = new ArrayList<>();
        for (QuestionSheet sheet : wrong) {
            // 이 문항의 보기일 때만 남긴다 (다른 문항의 보기 ID가 FK로 저장되지 않게)
            Long choiceId = sheet.validChoice(selected.get(sheet.questionId()));
            WrongAnswerNote note = existing.get(sheet.questionId());
            if (note != null) {
                note.changeLastChoice(choiceId);
            } else {
                created.add(WrongAnswerNote.create(memberId, sheet.questionId(), choiceId));
            }
        }
        wrongAnswerNoteRepository.saveAll(created);
    }

    /* 최초 통과 보상 (SP-34, GRW-03~06): 학습 완료 + 만점이면 만점 보상. 이중 지급은 RewardService가 막는다 (원인 = 강의 ID) */
    private QuizResultDTO.Rewards grantFirstPassRewards(Long memberId, Long courseId, QuizAttempt attempt) {
        List<RewardResultDTO> granted = new ArrayList<>();
        granted.add(rewardService.grant(memberId, RewardRule.COURSE_COMPLETE, courseId));
        if (attempt.isPerfect()) {
            granted.add(rewardService.grant(memberId, RewardRule.QUIZ_PERFECT, courseId));
        }
        int levelTo = granted.stream().mapToInt(RewardResultDTO::getLevelTo).max().orElse(0);
        boolean levelUp = granted.stream().anyMatch(RewardResultDTO::isLevelUp);
        return QuizResultDTO.Rewards.firstPass(granted, levelUp ? levelTitle(levelTo) : null);
    }

    /* 레벨 이름 (레벨업 모달용). 정책표에 없으면 null */
    private String levelTitle(int level) {
        return growthService.findLevels().stream()
                .filter(policy -> policy.getLevel() == level)
                .map(LevelDTO::getTitle)
                .findFirst()
                .orElse(null);
    }

    private List<QuizQuestion> findQuestions(Long courseId, String quizType) {
        return quizQuestionRepository.findByCourseIdAndQuizTypeOrderBySortOrderAsc(courseId, quizType);
    }

    /* 문항별 보기를 한 번에 조회해서 문항 ID로 묶는다 (문제지 API 41: 정답 없이 보기만 내보낸다) */
    private Map<Long, List<QuizChoice>> findChoicesByQuestion(List<QuizQuestion> questions) {
        return findSheets(questions).stream()
                .collect(Collectors.toMap(QuestionSheet::questionId, QuestionSheet::choices));
    }

    /* 문항들의 보기를 한 번에 조회해서 문항마다 채점용 시트로 묶는다. 문항이 없으면 조회하지 않는다 */
    private List<QuestionSheet> findSheets(List<QuizQuestion> questions) {
        if (questions.isEmpty()) {
            return List.of();
        }
        List<Long> questionIds = questions.stream().map(QuizQuestion::getQuizQuestionId).toList();
        return QuestionSheet.of(questions, quizChoiceRepository.findByQuizQuestionIdInOrderBySortOrderAsc(questionIds));
    }
}
