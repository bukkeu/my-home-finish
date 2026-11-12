package com.wanted.moneybeetle.domain.quiz.model.service;

import com.wanted.moneybeetle.domain.quiz.exception.QuizErrorCode;
import com.wanted.moneybeetle.domain.quiz.model.dto.QuizFileType;
import com.wanted.moneybeetle.domain.quiz.model.dto.QuizSaveDTO;
import com.wanted.moneybeetle.domain.quiz.model.service.QuizFileParser.Line;
import com.wanted.moneybeetle.global.exception.BusinessException;
import com.wanted.moneybeetle.global.exception.CommonErrorCode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/* comment.
*   담당 : 서수영 (#368, quiz 도메인 담당 신아림이 나눠 준 작업)
*   퀴즈 문제 파일(CSV·엑셀·텍스트)을 읽어 문항 목록으로 돌려준다 (API 153, TCH-04·05)
*   - 저장하지 않는다. 화면이 결과를 보여 준 뒤 그대로 저장 API(79 테스트 퀴즈·80 퀴즈)로 보낸다 → 응답은 저장 요청과 같은 QuizSaveDTO
*   - 흐름 : 파일 검사(있는지·1MB·확장자) → 표 읽기(QuizFileParser) → 머리글 확인 → 문항 수 확인 → 줄마다 문항으로 바꾸기
*   - 틀리면 QUIZ_FILE_INVALID 이고, 문구에 몇 번째 줄이 왜 틀렸는지 담는다 (예: "3번째 줄: 정답은 O 또는 X 로 써 주세요").
*     한 번에 첫 번째 오류 하나만 알려 준다
*   - 칸 한도(문제 500자, 보기 200자, 해설 65,535바이트)와 문항 수는 저장 API 와 같은 기준이다. 저장 API 도 다시 검사하지만,
*     파일을 올린 자리에서 줄 번호와 함께 알려 주려고 여기서 먼저 본다
*   - 파일 용량·형식 오류는 다른 업로드와 같은 FILE_TOO_LARGE·FILE_TYPE_NOT_ALLOWED 다 (global.file.FileStorageService 와 같은 방식)
*  */
@Service
public class QuizFileService {

    static final long MAX_FILE_SIZE = 1024L * 1024;               // 1MB
    private static final List<String> EXTENSIONS = List.of(".csv", ".xlsx", ".txt");
    private static final int CONTENT_MAX_LENGTH = 500;           // quiz_question.content
    private static final int CHOICE_MAX_LENGTH = 200;            // quiz_choice.content
    private static final int EXPLANATION_MAX_BYTES = 65_535;     // quiz_question.explanation (MySQL TEXT, UTF-8 바이트)
    private static final int CHOICE_COUNT = 4;

    /** API 153 : 파일을 읽어 문항 목록을 돌려준다 (저장하지 않음) */
    public QuizSaveDTO parse(MultipartFile file, QuizFileType type) {
        String extension = checkFile(file);
        List<Line> lines = read(file, extension, type);   // 글자가 있는 줄만 (빈 줄은 QuizFileParser 가 담지 않는다)

        if (lines.isEmpty()) {
            throw invalid("파일에 내용이 없어요. 첫 줄은 머리글(" + String.join(", ", type.getColumns()) + ")이고 둘째 줄부터 문제를 써 주세요");
        }
        checkHeader(lines.get(0), type);

        // 문항 수도 줄 번호와 함께 알려 준다 (#378 CodeRabbit). 넘치면 처음 넘친 문제의 줄, 모자라면 마지막으로 읽은 줄
        List<Line> rows = lines.subList(1, lines.size());
        if (rows.size() > type.getMaxCount()) {
            throw invalid(rows.get(type.getMaxCount()).lineNumber() + "번째 줄: " + type.getLabel() + "는 " + type.countText()
                    + "가 필요한데, 이 줄이 " + (type.getMaxCount() + 1) + "번째 문제예요. 이 줄부터는 지워 주세요");
        }
        if (rows.size() < type.getMinCount()) {
            throw invalid(lines.get(lines.size() - 1).lineNumber() + "번째 줄까지 " + rows.size() + "문제뿐이에요. "
                    + type.getLabel() + "는 " + type.countText() + "가 필요해요");
        }

        boolean delimited = !".xlsx".equals(extension);   // 쉼표·탭으로 나눈 글 파일 (칸이 넘칠 때 큰따옴표 안내를 붙인다)
        List<QuizSaveDTO.Question> questions = new ArrayList<>();
        for (Line row : rows) {
            checkColumnCount(row, type, delimited);
            questions.add(type == QuizFileType.TEST ? toTestQuestion(row, type) : toMainQuestion(row, type));
        }
        return new QuizSaveDTO(questions);
    }

    /* ===== 파일 검사 ===== */

    /* 파일이 있는지, 1MB 이하인지, 확장자가 csv·xlsx·txt 인지. 통과하면 소문자 확장자(".csv")를 돌려준다 */
    private String checkFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw invalid("문제 파일을 골라 주세요");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException(CommonErrorCode.FILE_TOO_LARGE, "문제 파일은 1MB까지 올릴 수 있어요");
        }
        String name = file.getOriginalFilename();
        String extension = name == null || !name.contains(".") ? "" : name.substring(name.lastIndexOf('.')).toLowerCase(Locale.ROOT);
        if (!EXTENSIONS.contains(extension)) {
            throw new BusinessException(CommonErrorCode.FILE_TYPE_NOT_ALLOWED, "CSV·엑셀(xlsx)·텍스트(txt) 파일만 읽을 수 있어요");
        }
        return extension;
    }

    /* 읽는 양을 정해 준다 : 한 줄에 이 종류의 칸 수까지, 글자가 있는 줄은 머리글 + 최대 문항 수 + 1 개까지
     * (+1 은 "문제가 넘친다"를 알려 주려고 한 줄 더 읽는 것이다. QuizFileParser 주석 참고) */
    private List<Line> read(MultipartFile file, String extension, QuizFileType type) {
        int maxColumns = type.getColumns().size();
        int maxLines = 1 + type.getMaxCount() + 1;
        try {
            byte[] bytes = file.getBytes();                   // 1MB 이하인 것을 확인했다
            return switch (extension) {
                case ".xlsx" -> QuizFileParser.readXlsx(bytes, maxColumns, maxLines);
                case ".csv" -> QuizFileParser.readCsv(bytes, maxColumns, maxLines);
                default -> QuizFileParser.readTxt(bytes, maxColumns, maxLines);
            };
        } catch (IOException e) {
            throw invalid("파일을 읽지 못했어요. 다시 올려 주세요");
        }
    }

    /* 첫 줄은 머리글이다.
     * - 첫 칸이 "문제"가 아니면 머리글을 빼먹은 것으로 보고 알려 준다 (첫 문제가 머리글로 버려지지 않게)
     * - 머리글 칸 수가 이 종류와 다르면 다른 종류의 파일(예: 퀴즈 파일을 테스트 퀴즈로)을 올린 것으로 보고 알려 준다.
     *   칸 이름은 따지지 않는다 ("보기 1"처럼 띄어 써도 된다) */
    private void checkHeader(Line header, QuizFileType type) {
        String columns = String.join(", ", type.getColumns());
        if (!type.getColumns().get(0).equals(header.cell(0))) {
            throw invalid(header.lineNumber() + "번째 줄은 머리글(" + columns + ")이어야 해요");
        }
        if (header.cells().stream().filter(cell -> !cell.isEmpty()).count() != type.getColumns().size()) {
            throw invalid(type.getLabel() + " 파일의 머리글은 " + columns + " 순서여야 해요. 퀴즈 종류에 맞는 파일인지 확인해 주세요");
        }
    }

    /* ===== 줄 → 문항 ===== */

    /* TEST : 문제, 정답(O·X), 해설 */
    private QuizSaveDTO.Question toTestQuestion(Line row, QuizFileType type) {
        String content = content(row, type);
        String answer = required(row, type, 1).toUpperCase(Locale.ROOT);
        if (!answer.equals("O") && !answer.equals("X")) {
            throw invalid(row.lineNumber() + "번째 줄: 정답은 O 또는 X 로 써 주세요");
        }
        String explanation = explanation(row, type, 2);
        return new QuizSaveDTO.Question(content, answer, null, null, explanation);
    }

    /* MAIN : 문제, 보기1~4, 정답(1~4), 해설. 저장 API 의 answerIndex 는 0~3 이라 1을 뺀다 */
    private QuizSaveDTO.Question toMainQuestion(Line row, QuizFileType type) {
        String content = content(row, type);
        List<String> choices = new ArrayList<>();
        for (int i = 1; i <= CHOICE_COUNT; i++) {
            String choice = required(row, type, i);
            if (choice.length() > CHOICE_MAX_LENGTH) {
                throw invalid(row.lineNumber() + "번째 줄: '" + type.getColumns().get(i) + "' 칸은 " + CHOICE_MAX_LENGTH + "자까지 쓸 수 있어요");
            }
            choices.add(choice);
        }
        String answer = required(row, type, 5);
        int answerNumber = switch (answer) {
            case "1", "2", "3", "4" -> Integer.parseInt(answer);
            default -> throw invalid(row.lineNumber() + "번째 줄: 정답은 보기 번호 1~4 중 하나로 써 주세요");
        };
        String explanation = explanation(row, type, 6);
        return new QuizSaveDTO.Question(content, null, choices, answerNumber - 1, explanation);
    }

    /* 칸이 모자란 것은 빈 칸으로 알려 주고, 남는 칸에 글자가 있으면 칸 수가 틀렸다고 알려 준다 (끝의 빈 칸은 괜찮다).
     * CSV·TXT 는 글 안의 쉼표 때문에 칸이 넘치는 일이 많아 큰따옴표로 감싸라고 덧붙인다 */
    private void checkColumnCount(Line row, QuizFileType type, boolean delimited) {
        int columnCount = type.getColumns().size();
        for (int i = columnCount; i < row.cells().size(); i++) {
            if (!row.cell(i).isEmpty()) {
                throw invalid(row.lineNumber() + "번째 줄: 칸이 " + columnCount + "개(" + String.join(", ", type.getColumns())
                        + ")여야 하는데 더 있어요" + (delimited ? ". 글 안의 쉼표는 칸 전체를 큰따옴표로 감싸 주세요" : ""));
            }
        }
    }

    private String content(Line row, QuizFileType type) {
        String content = required(row, type, 0);
        if (content.length() > CONTENT_MAX_LENGTH) {
            throw invalid(row.lineNumber() + "번째 줄: 문제는 " + CONTENT_MAX_LENGTH + "자까지 쓸 수 있어요");
        }
        return content;
    }

    /* 해설은 TEXT 칸이라 글자 수가 아니라 UTF-8 바이트로 한도를 본다 (저장 API 와 같은 기준) */
    private String explanation(Line row, QuizFileType type, int index) {
        String explanation = required(row, type, index);
        if (explanation.getBytes(StandardCharsets.UTF_8).length > EXPLANATION_MAX_BYTES) {
            throw invalid(row.lineNumber() + "번째 줄: 해설이 너무 길어요");
        }
        return explanation;
    }

    /* 빈 칸이면 어느 칸이 비었는지 알려 준다 */
    private String required(Line row, QuizFileType type, int index) {
        String value = row.cell(index);
        if (value.isEmpty()) {
            throw invalid(row.lineNumber() + "번째 줄: '" + type.getColumns().get(index) + "' 칸이 비어 있어요");
        }
        return value;
    }

    private static BusinessException invalid(String message) {
        return new BusinessException(QuizErrorCode.QUIZ_FILE_INVALID, message);
    }
}
