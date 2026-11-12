package com.wanted.moneybeetle.domain.quiz.model.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import com.wanted.moneybeetle.domain.quiz.exception.QuizErrorCode;
import com.wanted.moneybeetle.domain.quiz.model.dto.QuizFileType;
import com.wanted.moneybeetle.domain.quiz.model.dto.QuizSaveDTO;
import com.wanted.moneybeetle.global.exception.BusinessException;
import com.wanted.moneybeetle.global.exception.CommonErrorCode;
import com.wanted.moneybeetle.global.exception.ErrorCode;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.assertj.core.api.AbstractThrowableAssert;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockMultipartFile;

/* comment.
*   담당 : 서수영 (#368)
*   퀴즈 문제 파일 읽기 (API 150) : 예시 파일 3개(csv·xlsx·txt, src/test/resources/quiz-files)와 줄마다 틀린 경우
*   - 결과는 저장 API(79·80)의 요청(QuizSaveDTO) 모양과 같아야 한다 (TEST : answer "O"/"X", MAIN : choices 4개 + answerIndex 0~3)
*   - 오류는 QUIZ_FILE_INVALID 이고 문구에 줄 번호와 이유가 있다. 용량·형식은 FILE_TOO_LARGE·FILE_TYPE_NOT_ALLOWED
*  */
class QuizFileServiceTest {

    private static final String TEST_HEADER = "문제,정답,해설\n";
    private static final String MAIN_HEADER = "문제,보기1,보기2,보기3,보기4,정답,해설\n";

    private final QuizFileService service = new QuizFileService();

    private static MockMultipartFile sample(String name) throws IOException {
        try (InputStream in = QuizFileServiceTest.class.getResourceAsStream("/quiz-files/" + name)) {
            return new MockMultipartFile("file", name, null, in.readAllBytes());
        }
    }

    private static MockMultipartFile file(String name, String text) {
        return new MockMultipartFile("file", name, null, text.getBytes(StandardCharsets.UTF_8));
    }

    /** OX 문제 n줄 (머리글 포함) */
    private static String testRows(int count) {
        StringBuilder sb = new StringBuilder(TEST_HEADER);
        for (int i = 1; i <= count; i++) {
            sb.append("문제 ").append(i).append(",O,해설 ").append(i).append('\n');
        }
        return sb.toString();
    }

    /** 4지선다 문제 n줄 (머리글 포함) */
    private static String mainRows(int count) {
        StringBuilder sb = new StringBuilder(MAIN_HEADER);
        for (int i = 1; i <= count; i++) {
            sb.append("문제 ").append(i).append(",가,나,다,라,1,해설 ").append(i).append('\n');
        }
        return sb.toString();
    }

    private AbstractThrowableAssert<?, ? extends Throwable> assertError(MockMultipartFile file, QuizFileType type,
                                                                       ErrorCode code, String message) {
        return assertThatThrownBy(() -> service.parse(file, type))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(code))
                .hasMessageContaining(message);
    }

    @Nested
    @DisplayName("예시 파일 3개")
    class Samples {

        @Test
        @DisplayName("CSV(UTF-8 BOM) 테스트 퀴즈 : 따옴표 안의 쉼표·\"\" 를 읽고, 소문자 o 는 O 로 바꾼다")
        void csvTestQuiz() throws IOException {
            QuizSaveDTO result = service.parse(sample("test-quiz.csv"), QuizFileType.TEST);

            List<QuizSaveDTO.Question> questions = result.questions();
            assertThat(questions).hasSize(5);
            assertThat(questions.get(0).content()).isEqualTo("청약통장은 1인 1개만 만들 수 있다.");   // BOM 이 붙지 않는다
            assertThat(questions.get(1).content()).isEqualTo("예금자 보호는 1인당, 금융회사마다 5천만 원까지다.");
            assertThat(questions.get(2).answer()).isEqualTo("O");
            assertThat(questions.get(3).explanation()).isEqualTo("주식은 원금이 보장되지 않아요. \"투자\"에는 손실 위험이 있어요.");
            assertThat(questions).extracting(QuizSaveDTO.Question::answer).containsExactly("O", "X", "O", "X", "O");
            assertThat(questions).allSatisfy(q -> {
                assertThat(q.choices()).isNull();
                assertThat(q.answerIndex()).isNull();
            });
        }

        @Test
        @DisplayName("엑셀(xlsx) 퀴즈 : 숫자 칸 정답 4 → answerIndex 3, 서식만 있는 빈 행은 건너뛴다")
        void xlsxMainQuiz() throws IOException {
            QuizSaveDTO result = service.parse(sample("main-quiz.xlsx"), QuizFileType.MAIN);

            List<QuizSaveDTO.Question> questions = result.questions();
            assertThat(questions).hasSize(5);
            assertThat(questions.get(0).content()).isEqualTo("청약 가점 항목이 아닌 것은?");
            assertThat(questions.get(0).choices()).containsExactly("무주택 기간", "부양가족 수", "청약통장 가입 기간", "연 소득");
            assertThat(questions).extracting(QuizSaveDTO.Question::answerIndex).containsExactly(3, 1, 2, 1, 2);
            assertThat(questions.get(0).answer()).isNull();
        }

        @Test
        @DisplayName("세 파일의 결과는 저장 API(79·80) 요청의 입력 검사(@Valid QuizSaveDTO)를 그대로 통과한다")
        void resultPassesSaveValidation() throws IOException {
            try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
                Validator validator = factory.getValidator();
                assertThat(validator.validate(service.parse(sample("test-quiz.csv"), QuizFileType.TEST))).isEmpty();
                assertThat(validator.validate(service.parse(sample("main-quiz.xlsx"), QuizFileType.MAIN))).isEmpty();
                assertThat(validator.validate(service.parse(sample("main-quiz.txt"), QuizFileType.MAIN))).isEmpty();
            }
        }

        @Test
        @DisplayName("텍스트(txt, 탭으로 나눔) 퀴즈 : 해설 안의 쉼표는 칸을 나누지 않는다")
        void txtMainQuiz() throws IOException {
            QuizSaveDTO result = service.parse(sample("main-quiz.txt"), QuizFileType.MAIN);

            assertThat(result.questions()).hasSize(6);
            assertThat(result.questions().get(0).explanation())
                    .isEqualTo("물가가 계속 오르는 것을 인플레이션이라고 해요, 반대는 디플레이션이에요.");
            assertThat(result.questions()).extracting(QuizSaveDTO.Question::answerIndex).containsOnly(1);
        }
    }

    @Nested
    @DisplayName("여러 저장 모양")
    class Formats {

        @Test
        @DisplayName("쉼표로 나눈 txt, 줄바꿈 \\r\\n, 칸 앞뒤 공백, 대문자 확장자, 끝의 빈 줄·빈 칸을 읽는다")
        void commaTxtWithCrlf() {
            String text = "문제 , 정답 , 해설\r\n" + " 문제 1 , x , 해설 1 ,,\r\n" + testRows(4).substring(TEST_HEADER.length()).replace("\n", "\r\n") + "\r\n\r\n";

            QuizSaveDTO result = service.parse(file("QUIZ.TXT", text), QuizFileType.TEST);

            assertThat(result.questions()).hasSize(5);
            assertThat(result.questions().get(0).content()).isEqualTo("문제 1");
            assertThat(result.questions().get(0).answer()).isEqualTo("X");
            assertThat(result.questions().get(0).explanation()).isEqualTo("해설 1");
        }

        @Test
        @DisplayName("따옴표로 감싼 칸 안의 줄바꿈은 글자로 읽고, 다음 오류의 줄 번호는 실제 줄을 따른다")
        void quotedNewline() {
            String text = TEST_HEADER
                    + "\"두 줄\n문제\",O,해설\n"        // 2~3번째 줄
                    + "문제 2,O,해설\n"                 // 4번째 줄
                    + "문제 3,Y,해설\n"                 // 5번째 줄 : 정답 오류
                    + "문제 4,O,해설\n" + "문제 5,O,해설\n";

            assertError(file("q.csv", text), QuizFileType.TEST, QuizErrorCode.QUIZ_FILE_INVALID, "5번째 줄: 정답은 O 또는 X");
        }

        @ParameterizedTest(name = "date1904 = {0}")
        @ValueSource(booleans = {false, true})
        @DisplayName("엑셀 보기 칸이 날짜면 1900·1904 날짜 기준 모두 엑셀에 보이는 날짜 그대로 읽는다 (#378 CodeRabbit)")
        void xlsxDateCellKeepsDate(boolean date1904) throws IOException {
            try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                if (date1904) {
                    var ct = workbook.getCTWorkbook();
                    (ct.isSetWorkbookPr() ? ct.getWorkbookPr() : ct.addNewWorkbookPr()).setDate1904(true);
                }
                CellStyle dateStyle = workbook.createCellStyle();
                dateStyle.setDataFormat(workbook.createDataFormat().getFormat("yyyy-mm-dd"));
                Sheet sheet = workbook.createSheet();
                String[] header = {"문제", "보기1", "보기2", "보기3", "보기4", "정답", "해설"};
                Row first = sheet.createRow(0);
                for (int c = 0; c < header.length; c++) {
                    first.createCell(c).setCellValue(header[c]);
                }
                for (int r = 1; r <= 5; r++) {
                    Row row = sheet.createRow(r);
                    row.createCell(0).setCellValue("청약 1순위가 발표되는 날은?");
                    Cell date = row.createCell(1);
                    date.setCellValue(LocalDate.of(2026, 11, 12));   // 날짜 기준에 맞는 숫자로 저장된다
                    date.setCellStyle(dateStyle);
                    for (int c = 2; c <= 4; c++) {
                        row.createCell(c).setCellValue("다른 날");
                    }
                    row.createCell(5).setCellValue(1);
                    row.createCell(6).setCellValue("해설");
                }
                workbook.write(out);

                QuizSaveDTO result = service.parse(new MockMultipartFile("file", "q.xlsx", null, out.toByteArray()), QuizFileType.MAIN);

                assertThat(result.questions()).allSatisfy(q -> assertThat(q.choices().get(0)).isEqualTo("2026-11-12"));
            }
        }

        @Test
        @DisplayName("따옴표로 감싼 칸 안의 줄바꿈은 문제 글에 그대로 남는다")
        void quotedNewlineKept() {
            String text = TEST_HEADER + "\"두 줄\n문제\",O,해설\n" + testRows(4).substring(TEST_HEADER.length());

            assertThat(service.parse(file("q.csv", text), QuizFileType.TEST).questions().get(0).content())
                    .isEqualTo("두 줄\n문제");
        }
    }

    @Nested
    @DisplayName("문항 수 (TEST 5개, MAIN 5~15개)")
    class Count {

        @Test
        @DisplayName("모자라면 마지막으로 읽은 줄 번호와 함께 알려 준다")
        void 테스트퀴즈_4문제는_오류() {
            assertError(file("q.csv", testRows(4)), QuizFileType.TEST, QuizErrorCode.QUIZ_FILE_INVALID,
                    "5번째 줄까지 4문제뿐이에요. 테스트 퀴즈는 5문제가 필요해요");
        }

        @Test
        @DisplayName("넘치면 처음 넘친 문제의 줄 번호와 함께 알려 준다")
        void 테스트퀴즈_6문제는_오류() {
            assertError(file("q.csv", testRows(6)), QuizFileType.TEST, QuizErrorCode.QUIZ_FILE_INVALID,
                    "7번째 줄: 테스트 퀴즈는 5문제가 필요한데, 이 줄이 6번째 문제예요. 이 줄부터는 지워 주세요");
        }

        @Test
        void 퀴즈_5문제와_15문제는_통과() {
            assertThat(service.parse(file("q.csv", mainRows(5)), QuizFileType.MAIN).questions()).hasSize(5);
            assertThat(service.parse(file("q.csv", mainRows(15)), QuizFileType.MAIN).questions()).hasSize(15);
        }

        @Test
        void 퀴즈_4문제와_16문제는_오류() {
            assertError(file("q.csv", mainRows(4)), QuizFileType.MAIN, QuizErrorCode.QUIZ_FILE_INVALID,
                    "5번째 줄까지 4문제뿐이에요. 퀴즈는 5~15문제가 필요해요");
            assertError(file("q.csv", mainRows(16)), QuizFileType.MAIN, QuizErrorCode.QUIZ_FILE_INVALID,
                    "17번째 줄: 퀴즈는 5~15문제가 필요한데, 이 줄이 16번째 문제예요");
        }

        @Test
        @DisplayName("빈 줄이 섞여 있어도 줄 번호는 실제 줄을 따른다")
        void countLineWithBlankLines() {
            String text = TEST_HEADER + "\n" + testRows(6).substring(TEST_HEADER.length()).replace("문제 6", "\n\n문제 6");
            // 머리글 1, 빈 줄 2, 문제 1~5 는 3~7, 빈 줄 8~9, 문제 6 은 10번째 줄
            assertError(file("q.csv", text), QuizFileType.TEST, QuizErrorCode.QUIZ_FILE_INVALID, "10번째 줄: 테스트 퀴즈는");
        }

        @Test
        @DisplayName("머리글만 있으면 0문제")
        void headerOnly() {
            assertError(file("q.csv", TEST_HEADER), QuizFileType.TEST, QuizErrorCode.QUIZ_FILE_INVALID, "1번째 줄까지 0문제뿐이에요");
        }

        @Test
        @DisplayName("퀴즈 파일을 테스트 퀴즈로 올리면 머리글을 보고 종류가 다르다고 알려 준다 (csv·xlsx 모두)")
        void mainFileAsTest() throws IOException {
            String message = "테스트 퀴즈 파일의 머리글은 문제, 정답, 해설 순서여야 해요. 퀴즈 종류에 맞는 파일인지 확인해 주세요";
            assertError(file("q.csv", mainRows(5)), QuizFileType.TEST, QuizErrorCode.QUIZ_FILE_INVALID, message);
            assertError(sample("main-quiz.xlsx"), QuizFileType.TEST, QuizErrorCode.QUIZ_FILE_INVALID, message);
        }

        @Test
        @DisplayName("테스트 퀴즈 파일을 퀴즈로 올려도 머리글을 보고 종류가 다르다고 알려 준다")
        void testFileAsMain() throws IOException {
            String message = "퀴즈 파일의 머리글은 문제, 보기1, 보기2, 보기3, 보기4, 정답, 해설 순서여야 해요";
            assertError(file("q.csv", testRows(5)), QuizFileType.MAIN, QuizErrorCode.QUIZ_FILE_INVALID, message);
            assertError(sample("test-quiz.csv"), QuizFileType.MAIN, QuizErrorCode.QUIZ_FILE_INVALID, message);
        }

        @Test
        @DisplayName("머리글 칸 이름은 따지지 않는다 (보기 1 처럼 띄어 써도 칸 수가 맞으면 통과)")
        void headerNamesAreLoose() {
            String text = mainRows(5).replace(MAIN_HEADER, "문제,보기 1,보기 2,보기 3,보기 4,정답(1~4),해설\n");
            assertThat(service.parse(file("q.csv", text), QuizFileType.MAIN).questions()).hasSize(5);
        }
    }

    @Nested
    @DisplayName("정답 값")
    class Answer {

        @Test
        void OX가_아니면_줄번호와_함께_오류() {
            String text = testRows(5).replace("문제 2,O,", "문제 2,예,");
            assertError(file("q.csv", text), QuizFileType.TEST, QuizErrorCode.QUIZ_FILE_INVALID, "3번째 줄: 정답은 O 또는 X 로 써 주세요");
        }

        @Test
        void 보기번호가_1부터_4가_아니면_오류() {
            for (String wrong : List.of("0", "5", "가", "1.5", "①")) {
                String text = mainRows(5).replace("문제 4,가,나,다,라,1,", "문제 4,가,나,다,라," + wrong + ",");
                assertError(file("q.csv", text), QuizFileType.MAIN, QuizErrorCode.QUIZ_FILE_INVALID,
                        "5번째 줄: 정답은 보기 번호 1~4 중 하나로 써 주세요");
            }
        }

        @Test
        void 보기번호_1부터_4는_answerIndex_0부터_3() {
            String text = MAIN_HEADER + "a,가,나,다,라,1,해\n" + "b,가,나,다,라,2,해\n" + "c,가,나,다,라,3,해\n"
                    + "d,가,나,다,라,4,해\n" + "e,가,나,다,라,4,해\n";
            assertThat(service.parse(file("q.csv", text), QuizFileType.MAIN).questions())
                    .extracting(QuizSaveDTO.Question::answerIndex).containsExactly(0, 1, 2, 3, 3);
        }
    }

    @Nested
    @DisplayName("빈 칸·칸 수·글자 수·머리글")
    class Cells {

        @Test
        void 해설이_비면_어느_칸인지_알려준다() {
            String text = testRows(5).replace("문제 3,O,해설 3", "문제 3,O,");
            assertError(file("q.csv", text), QuizFileType.TEST, QuizErrorCode.QUIZ_FILE_INVALID, "4번째 줄: '해설' 칸이 비어 있어요");
        }

        @Test
        void 문제가_비면_오류() {
            String text = testRows(5).replace("문제 1,O,", " ,O,");
            assertError(file("q.csv", text), QuizFileType.TEST, QuizErrorCode.QUIZ_FILE_INVALID, "2번째 줄: '문제' 칸이 비어 있어요");
        }

        @Test
        void 보기가_비면_오류() {
            String text = mainRows(5).replace("문제 2,가,나,다,라", "문제 2,가,나,,라");
            assertError(file("q.csv", text), QuizFileType.MAIN, QuizErrorCode.QUIZ_FILE_INVALID, "3번째 줄: '보기3' 칸이 비어 있어요");
        }

        @Test
        void 칸이_모자라면_빈_칸으로_알려준다() {
            String text = testRows(5).replace("문제 5,O,해설 5", "문제 5,O");
            assertError(file("q.csv", text), QuizFileType.TEST, QuizErrorCode.QUIZ_FILE_INVALID, "6번째 줄: '해설' 칸이 비어 있어요");
        }

        @Test
        void 따옴표_없이_쓴_쉼표로_칸이_넘치면_오류() {
            String text = testRows(5).replace("해설 2", "해설, 두 번째");
            assertError(file("q.csv", text), QuizFileType.TEST, QuizErrorCode.QUIZ_FILE_INVALID,
                    "3번째 줄: 칸이 3개(문제, 정답, 해설)여야 하는데 더 있어요");
        }

        @Test
        void 문제_500자는_통과_501자는_오류() {
            String ok = testRows(5).replace("문제 1,", "가".repeat(500) + ",");
            assertThat(service.parse(file("q.csv", ok), QuizFileType.TEST).questions().get(0).content()).hasSize(500);

            String tooLong = testRows(5).replace("문제 1,", "가".repeat(501) + ",");
            assertError(file("q.csv", tooLong), QuizFileType.TEST, QuizErrorCode.QUIZ_FILE_INVALID, "2번째 줄: 문제는 500자까지");
        }

        @Test
        void 보기_201자는_오류() {
            String text = mainRows(5).replace("문제 1,가,", "문제 1," + "가".repeat(201) + ",");
            assertError(file("q.csv", text), QuizFileType.MAIN, QuizErrorCode.QUIZ_FILE_INVALID, "2번째 줄: '보기1' 칸은 200자까지");
        }

        @Test
        void 해설이_65535바이트를_넘으면_오류() {
            String text = testRows(5).replace("해설 1", "가".repeat(21_846));   // 한글 3바이트 × 21,846 = 65,538바이트
            MockMultipartFile file = file("q.csv", text);
            assertError(file, QuizFileType.TEST, QuizErrorCode.QUIZ_FILE_INVALID, "2번째 줄: 해설이 너무 길어요");
        }

        @Test
        void 머리글이_없으면_첫_줄이_머리글이어야_한다고_알려준다() {
            String text = testRows(5).substring(TEST_HEADER.length());
            assertError(file("q.csv", text), QuizFileType.TEST, QuizErrorCode.QUIZ_FILE_INVALID,
                    "1번째 줄은 머리글(문제, 정답, 해설)이어야 해요");
        }

        @Test
        void 닫히지_않은_따옴표는_오류() {
            String text = testRows(4) + "\"문제 5,O,해설\n";
            assertError(file("q.csv", text), QuizFileType.TEST, QuizErrorCode.QUIZ_FILE_INVALID, "6번째 줄: 큰따옴표(\")가 닫히지 않았어요");
        }
    }

    @Nested
    @DisplayName("읽는 양 제한 (#378 CodeRabbit, CWE-400)")
    class Limits {

        /** 머리글 + rows 줄짜리 4지선다 엑셀. extraColumn 이 0 이상이면 2번째 줄의 그 열에도 값을 넣는다 */
        private MockMultipartFile xlsx(int rows, int extraColumn) throws IOException {
            try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                Sheet sheet = workbook.createSheet();
                String[] header = {"문제", "보기1", "보기2", "보기3", "보기4", "정답", "해설"};
                Row first = sheet.createRow(0);
                for (int c = 0; c < header.length; c++) {
                    first.createCell(c).setCellValue(header[c]);
                }
                for (int r = 1; r <= rows; r++) {
                    Row row = sheet.createRow(r);
                    String[] cells = {"문제 " + r, "가", "나", "다", "라", "1", "해설"};
                    for (int c = 0; c < cells.length; c++) {
                        row.createCell(c).setCellValue(cells[c]);
                    }
                }
                if (extraColumn >= 0) {
                    sheet.getRow(1).createCell(extraColumn).setCellValue("끝 열의 값");
                }
                workbook.write(out);
                return new MockMultipartFile("file", "q.xlsx", null, out.toByteArray());
            }
        }

        @Test
        @DisplayName("마지막 열(XFD)에만 값이 더 있는 행 : 칸 16,384개를 만들지 않고 '칸이 더 있어요'로 알려 준다 (큰따옴표 안내 없음)")
        void sparseXlsxRow() throws IOException {
            MockMultipartFile file = xlsx(5, 16_383);
            assertTimeoutPreemptively(Duration.ofSeconds(5), () ->
                    assertError(file, QuizFileType.MAIN, QuizErrorCode.QUIZ_FILE_INVALID, "2번째 줄: 칸이 7개(")
                            .hasMessageEndingWith("여야 하는데 더 있어요"));
        }

        @Test
        @DisplayName("문제가 수천 줄인 엑셀 : 16번째 문제까지만 읽고 그 줄 번호로 알려 준다")
        void manyXlsxRows() throws IOException {
            MockMultipartFile file = xlsx(5_000, -1);
            assertTimeoutPreemptively(Duration.ofSeconds(5), () ->
                    assertError(file, QuizFileType.MAIN, QuizErrorCode.QUIZ_FILE_INVALID, "17번째 줄: 퀴즈는 5~15문제가 필요한데"));
            // 읽기 도우미가 실제로 멈추는지 : 머리글 + 16문제 = 17줄만 돌려준다
            assertThat(QuizFileParser.readXlsx(file.getBytes(), 7, 17)).hasSize(17);
        }

        @Test
        @DisplayName("빈 줄이 수십만 개인 CSV : 빈 줄은 담지 않고, 문제 줄 번호는 실제 줄을 따른다")
        void blankHeavyCsv() {
            String text = TEST_HEADER + "\n".repeat(300_000) + testRows(5).substring(TEST_HEADER.length());
            byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
            assertThat((long) bytes.length).isLessThan(QuizFileService.MAX_FILE_SIZE);

            List<QuizFileParser.Line> lines = QuizFileParser.readCsv(bytes, 3, 7);

            assertThat(lines).hasSize(6);                                   // 머리글 + 문제 5줄만
            assertThat(lines.get(1).lineNumber()).isEqualTo(300_002);      // 머리글 1 + 빈 줄 300,000 다음 줄
            assertThat(service.parse(file("q.csv", text), QuizFileType.TEST).questions()).hasSize(5);
        }

        @Test
        @DisplayName("빈 행이 수천 개인 엑셀 : 빈 행은 담지 않고, 문제 행 번호는 실제 행을 따른다")
        void blankHeavyXlsx() throws IOException {
            try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                Sheet sheet = workbook.createSheet();
                Row header = sheet.createRow(0);
                String[] columns = {"문제", "정답", "해설"};
                for (int c = 0; c < columns.length; c++) {
                    header.createCell(c).setCellValue(columns[c]);
                }
                for (int r = 1; r <= 5_000; r++) {
                    sheet.createRow(r).createCell(0).setCellValue("");     // 값이 빈 칸만 있는 행
                }
                for (int r = 5_001; r <= 5_005; r++) {
                    Row row = sheet.createRow(r);
                    row.createCell(0).setCellValue("문제 " + r);
                    row.createCell(1).setCellValue("O");
                    row.createCell(2).setCellValue("해설");
                }
                workbook.write(out);
                byte[] bytes = out.toByteArray();

                List<QuizFileParser.Line> lines = QuizFileParser.readXlsx(bytes, 3, 7);

                assertThat(lines).hasSize(6);
                assertThat(lines.get(1).lineNumber()).isEqualTo(5_002);        // 엑셀 행 번호 (0부터 센 5,001 + 1)
                assertThat(service.parse(new MockMultipartFile("file", "q.xlsx", null, bytes), QuizFileType.TEST).questions())
                        .hasSize(5);
            }
        }

        @Test
        @DisplayName("1MB 안에 4만 줄을 넣은 CSV 도 16번째 문제까지만 읽는다")
        void manyCsvRows() {
            StringBuilder text = new StringBuilder(MAIN_HEADER);
            for (int i = 0; i < 40_000; i++) {
                text.append("q,1,2,3,4,1,e\n");
            }
            MockMultipartFile file = file("q.csv", text.toString());
            assertThat(file.getSize()).isLessThan(QuizFileService.MAX_FILE_SIZE);
            assertTimeoutPreemptively(Duration.ofSeconds(5), () ->
                    assertError(file, QuizFileType.MAIN, QuizErrorCode.QUIZ_FILE_INVALID, "17번째 줄: 퀴즈는"));
        }

        @Test
        @DisplayName("한 줄에 쉼표가 수십만 개인 CSV 도 칸 7개 + 넘친 칸 하나만 담는다")
        void manyCsvColumns() {
            String wide = "문제 1,가,나,다,라,1,해설" + ",x".repeat(300_000) + "\n";
            String text = MAIN_HEADER + wide + mainRows(4).substring(MAIN_HEADER.length());
            assertError(file("q.csv", text), QuizFileType.MAIN, QuizErrorCode.QUIZ_FILE_INVALID,
                    "2번째 줄: 칸이 7개(문제, 보기1, 보기2, 보기3, 보기4, 정답, 해설)여야 하는데 더 있어요. 글 안의 쉼표는");
        }

        @Test
        @DisplayName("압축을 풀면 10MB 가 넘는 xlsx 는 열지 않고 FILE_TOO_LARGE")
        void zipBomb() throws IOException {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(out)) {
                zip.putNextEntry(new ZipEntry("xl/worksheets/sheet1.xml"));
                byte[] zeros = new byte[1024 * 1024];
                for (int i = 0; i < 11; i++) {
                    zip.write(zeros);                           // 풀면 11MB, 압축하면 수십 KB
                }
                zip.closeEntry();
            }
            MockMultipartFile file = new MockMultipartFile("file", "q.xlsx", null, out.toByteArray());
            assertThat(file.getSize()).isLessThan(QuizFileService.MAX_FILE_SIZE);
            assertError(file, QuizFileType.MAIN, CommonErrorCode.FILE_TOO_LARGE, "엑셀 파일 내용이 너무 커요");
        }
    }

    @Nested
    @DisplayName("파일 검사")
    class FileCheck {

        @Test
        void 파일이_없거나_비어_있으면_오류() {
            assertError(null, QuizFileType.TEST, QuizErrorCode.QUIZ_FILE_INVALID, "문제 파일을 골라 주세요");
            assertError(file("q.csv", ""), QuizFileType.TEST, QuizErrorCode.QUIZ_FILE_INVALID, "문제 파일을 골라 주세요");
        }

        @Test
        void 빈_줄만_있으면_내용이_없다고_알려준다() {
            assertError(file("q.csv", "\n\n ,, \n"), QuizFileType.TEST, QuizErrorCode.QUIZ_FILE_INVALID, "파일에 내용이 없어요");
        }

        @Test
        void 용량이_1MB를_넘으면_FILE_TOO_LARGE() {
            MockMultipartFile big = new MockMultipartFile("file", "q.csv", null, new byte[(int) QuizFileService.MAX_FILE_SIZE + 1]);
            assertError(big, QuizFileType.TEST, CommonErrorCode.FILE_TOO_LARGE, "1MB");
        }

        @Test
        void csv_xlsx_txt가_아니면_FILE_TYPE_NOT_ALLOWED() {
            for (String name : List.of("q.pdf", "q.docx", "q.xls", "q", "q.csv.exe")) {
                assertError(file(name, testRows(5)), QuizFileType.TEST, CommonErrorCode.FILE_TYPE_NOT_ALLOWED, "CSV·엑셀(xlsx)·텍스트(txt)");
            }
        }

        @Test
        @DisplayName("엑셀 기본 CSV(CP949)처럼 UTF-8 이 아니면 UTF-8 로 저장하라고 알려 준다")
        void notUtf8() {
            byte[] cp949 = testRows(5).getBytes(Charset.forName("MS949"));
            assertError(new MockMultipartFile("file", "q.csv", null, cp949), QuizFileType.TEST,
                    QuizErrorCode.QUIZ_FILE_INVALID, "UTF-8 로 저장한 파일만");
        }

        @Test
        @DisplayName("확장자만 xlsx 인 파일은 엑셀을 열 수 없다고 알려 준다")
        void fakeXlsx() {
            assertError(file("q.xlsx", testRows(5)), QuizFileType.TEST, QuizErrorCode.QUIZ_FILE_INVALID, "엑셀 파일을 열 수 없어요");
        }
    }
}
