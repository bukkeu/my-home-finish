package com.wanted.moneybeetle.domain.quiz.model.service;

import com.wanted.moneybeetle.domain.quiz.exception.QuizErrorCode;
import com.wanted.moneybeetle.global.exception.BusinessException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/* comment.
*   담당 : 서수영 (#368, quiz 도메인 담당 신아림이 나눠 준 작업)
*   퀴즈 문제 파일을 "줄 번호 + 칸 목록"의 표로 읽는다. 문항이 맞는지는 QuizFileService 가 본다
*   - CSV : UTF-8(BOM 허용), 쉼표로 나눈다. 엑셀이 저장하는 모양처럼 큰따옴표로 감싼 칸 안의 쉼표·줄바꿈·"" 도 읽는다
*   - TXT : CSV 와 같은 규칙인데, 첫 줄에 탭이 있으면 탭으로, 없으면 쉼표로 나눈다
*   - XLSX : 첫 번째 시트만 읽는다. 칸 값은 엑셀 화면에 보이는 글자 그대로(숫자 1 → "1"), 수식 칸은 저장된 결과 값을 쓴다(수식을 실행하지 않음)
*   - 줄 번호는 CSV·TXT 는 그 줄이 시작하는 줄, XLSX 는 엑셀의 행 번호다. 오류 문구 "N번째 줄"에 쓴다
*   - 칸 값의 앞뒤 공백은 지운다. 모든 칸이 빈 줄도 그대로 돌려주고, 건너뛸지는 QuizFileService 가 정한다
*   - 스프링 빈이 아니라 값만 바꾸는 도우미라 static 메서드로 둔다 (QuestionSheet 처럼 바로 테스트할 수 있다)
*  */
final class QuizFileParser {

    private static final char BOM = '﻿';

    private QuizFileParser() {
    }

    /** 표의 한 줄. lineNumber 는 오류 문구에 쓰는 줄 번호(1부터) */
    record Line(int lineNumber, List<String> cells) {

        Line {
            cells = List.copyOf(cells);
        }

        /** 모든 칸이 비었는가 (끝에 남은 빈 줄, 엑셀의 서식만 있는 행) */
        boolean isBlank() {
            return cells.stream().allMatch(String::isEmpty);
        }

        /** index 번째 칸 (없으면 빈 문자열) */
        String cell(int index) {
            return index < cells.size() ? cells.get(index) : "";
        }
    }

    /** CSV : 쉼표로 나눈 표 */
    static List<Line> readCsv(byte[] bytes) {
        return readDelimited(decodeUtf8(bytes), ',');
    }

    /** TXT : 첫 줄에 탭이 있으면 탭, 없으면 쉼표로 나눈 표 */
    static List<Line> readTxt(byte[] bytes) {
        String text = decodeUtf8(bytes);
        int firstLineEnd = text.indexOf('\n');
        String firstLine = firstLineEnd < 0 ? text : text.substring(0, firstLineEnd);
        return readDelimited(text, firstLine.indexOf('\t') >= 0 ? '\t' : ',');
    }

    /** XLSX : 첫 번째 시트. 엑셀 파일이 아니거나 깨졌으면 QUIZ_FILE_INVALID */
    static List<Line> readXlsx(InputStream in) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(in)) {
            if (workbook.getNumberOfSheets() == 0) {
                return List.of();
            }
            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter();
            formatter.setUseCachedValuesForFormulaCells(true);   // 수식은 실행하지 않고 엑셀이 저장해 둔 결과 값을 읽는다
            List<Line> lines = new ArrayList<>();
            for (Row row : sheet) {                                // 빈 행(엑셀에 없는 행)은 건너뛰고, 행 번호는 그대로 쓴다
                List<String> cells = new ArrayList<>();
                for (int i = 0; i < Math.max(row.getLastCellNum(), 0); i++) {
                    Cell cell = row.getCell(i);
                    cells.add(cell == null ? "" : formatter.formatCellValue(cell).strip());
                }
                lines.add(new Line(row.getRowNum() + 1, cells));
            }
            return lines;
        } catch (IOException | RuntimeException e) {
            // 확장자만 xlsx 인 다른 파일, 깨진 파일, 암호가 걸린 파일, 지나치게 압축된 파일(POI 가 막음) 등
            throw new BusinessException(QuizErrorCode.QUIZ_FILE_INVALID, "엑셀 파일을 열 수 없어요. xlsx 로 저장한 파일인지 확인해 주세요");
        }
    }

    /* UTF-8 로만 읽는다. 엑셀의 기본 "CSV(쉼표로 분리)"는 한글이 CP949 로 저장돼 글자가 깨지므로 그대로 읽지 않고 알려 준다 */
    private static String decodeUtf8(byte[] bytes) {
        try {
            String text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
            return !text.isEmpty() && text.charAt(0) == BOM ? text.substring(1) : text;
        } catch (CharacterCodingException e) {
            throw new BusinessException(QuizErrorCode.QUIZ_FILE_INVALID,
                    "UTF-8 로 저장한 파일만 읽을 수 있어요. 엑셀에서는 'CSV UTF-8(쉼표로 분리)'로 저장해 주세요");
        }
    }

    /* 구분 문자(쉼표·탭)로 나눈 표를 읽는다. 큰따옴표로 감싼 칸은 그 안의 구분 문자·줄바꿈을 글자로 보고, "" 는 " 하나다 */
    private static List<Line> readDelimited(String text, char delimiter) {
        List<Line> lines = new ArrayList<>();
        List<String> cells = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean inQuotes = false;       // 큰따옴표 안을 읽는 중
        boolean quotedCell = false;     // 이 칸이 큰따옴표로 시작했는가
        int lineNumber = 1;             // 지금 읽는 글자의 줄 번호
        int startLine = 1;              // 지금 만드는 줄이 시작한 줄 번호

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
                        cell.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                    continue;                                   // 칸 안의 줄바꿈 \r\n 은 \n 하나로
                } else {
                    if (c == '\n') {
                        lineNumber++;
                    }
                    cell.append(c);
                }
            } else if (c == '"' && cell.toString().isBlank() && !quotedCell) {
                cell.setLength(0);                              // 따옴표 앞의 공백은 버린다
                inQuotes = true;
                quotedCell = true;
            } else if (c == delimiter) {
                cells.add(cell.toString().strip());
                cell.setLength(0);
                quotedCell = false;
            } else if (c == '\r' || c == '\n') {
                if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                    i++;
                }
                cells.add(cell.toString().strip());
                lines.add(new Line(startLine, cells));
                cells = new ArrayList<>();
                cell.setLength(0);
                quotedCell = false;
                lineNumber++;
                startLine = lineNumber;
            } else {
                cell.append(c);
            }
        }
        if (inQuotes) {
            throw new BusinessException(QuizErrorCode.QUIZ_FILE_INVALID,
                    startLine + "번째 줄: 큰따옴표(\")가 닫히지 않았어요");
        }
        if (!cells.isEmpty() || !cell.isEmpty()) {             // 마지막 줄에 줄바꿈이 없을 때
            cells.add(cell.toString().strip());
            lines.add(new Line(startLine, cells));
        }
        return lines;
    }
}
