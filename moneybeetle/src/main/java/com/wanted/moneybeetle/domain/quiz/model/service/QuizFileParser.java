package com.wanted.moneybeetle.domain.quiz.model.service;

import com.wanted.moneybeetle.domain.quiz.exception.QuizErrorCode;
import com.wanted.moneybeetle.global.exception.BusinessException;
import com.wanted.moneybeetle.global.exception.CommonErrorCode;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.zip.ZipInputStream;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.util.XMLHelper;
import org.apache.poi.xssf.eventusermodel.ReadOnlySharedStringsTable;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler.SheetContentsHandler;
import org.apache.poi.xssf.usermodel.XSSFComment;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

/* comment.
*   담당 : 서수영 (#368, quiz 도메인 담당 신아림이 나눠 준 작업)
*   퀴즈 문제 파일을 "줄 번호 + 칸 목록"의 표로 읽는다. 문항이 맞는지는 QuizFileService 가 본다
*   - CSV : UTF-8(BOM 허용), 쉼표로 나눈다. 엑셀이 저장하는 모양처럼 큰따옴표로 감싼 칸 안의 쉼표·줄바꿈·"" 도 읽는다
*   - TXT : CSV 와 같은 규칙인데, 첫 줄에 탭이 있으면 탭으로, 없으면 쉼표로 나눈다
*   - XLSX : 첫 번째 시트만 읽는다. 칸 값은 엑셀 화면에 보이는 글자 그대로(숫자 1 → "1"), 수식 칸은 저장된 결과 값을 쓴다(수식을 실행하지 않음)
*   - 줄 번호는 CSV·TXT 는 그 줄이 시작하는 줄, XLSX 는 엑셀의 행 번호다. 오류 문구 "N번째 줄"에 쓴다
*   - 칸 값의 앞뒤 공백은 지운다. 모든 칸이 빈 줄(끝의 빈 줄, 서식만 있는 행)은 담지 않는다. 줄 번호는 빈 줄을 포함한 실제 줄 번호다
*   - 스프링 빈이 아니라 값만 바꾸는 도우미라 static 메서드로 둔다 (QuestionSheet 처럼 바로 테스트할 수 있다)
*
*   읽는 양을 처음부터 묶어 둔다 (#378 CodeRabbit 지적, CWE-400). 1MB 는 압축된 크기라 엑셀은 풀면 훨씬 커질 수 있다
*   - 칸 : 한 줄에서 maxColumns 칸까지만 담고, 그 뒤에 글자가 있는 칸이 있으면 "넘친 칸" 하나만 덧붙인다
*     (그래야 QuizFileService 가 "칸이 더 있어요"로 알려 줄 수 있다). 마지막 열(XFD)에만 값이 있는 행도 칸 16,384개를 만들지 않는다
*   - 줄 : 글자가 있는 줄을 maxLines 개 읽으면 그만 읽는다 (머리글 + 최대 문항 수 + 1 이면 "문제가 넘친다"를 알 수 있다)
*   - XLSX : 압축을 푼 전체 크기가 10MB 를 넘으면 열지 않는다(FILE_TOO_LARGE). 시트는 한꺼번에 메모리에 올리지 않고
*     POI 의 이벤트 방식(XSSFReader + XSSFSheetXMLHandler)으로 한 행씩 읽다가 필요한 만큼 읽으면 멈춘다
*  */
final class QuizFileParser {

    private static final char BOM = '﻿';
    static final long XLSX_MAX_UNZIPPED_SIZE = 10L * 1024 * 1024;   // 문제 15개짜리 엑셀은 풀어도 수백 KB 다

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
    static List<Line> readCsv(byte[] bytes, int maxColumns, int maxLines) {
        return readDelimited(decodeUtf8(bytes), ',', maxColumns, maxLines);
    }

    /** TXT : 첫 줄에 탭이 있으면 탭, 없으면 쉼표로 나눈 표 */
    static List<Line> readTxt(byte[] bytes, int maxColumns, int maxLines) {
        String text = decodeUtf8(bytes);
        int firstLineEnd = text.indexOf('\n');
        String firstLine = firstLineEnd < 0 ? text : text.substring(0, firstLineEnd);
        return readDelimited(text, firstLine.indexOf('\t') >= 0 ? '\t' : ',', maxColumns, maxLines);
    }

    /** XLSX : 첫 번째 시트. 풀어서 10MB 가 넘으면 FILE_TOO_LARGE, 엑셀 파일이 아니거나 깨졌으면 QUIZ_FILE_INVALID */
    static List<Line> readXlsx(byte[] bytes, int maxColumns, int maxLines) {
        checkUnzippedSize(bytes);
        RowCollector collector = new RowCollector(maxColumns, maxLines);
        try (OPCPackage pkg = OPCPackage.open(new ByteArrayInputStream(bytes))) {
            XSSFReader reader = new XSSFReader(pkg);
            Iterator<InputStream> sheets = reader.getSheetsData();
            if (!sheets.hasNext()) {
                return collector.lines;
            }
            XMLReader parser = XMLHelper.newXMLReader();
            parser.setContentHandler(new XSSFSheetXMLHandler(reader.getStylesTable(), new ReadOnlySharedStringsTable(pkg),
                    collector, formatter(isDate1904(reader)), false));    // false : 수식 대신 저장된 결과 값
            try (InputStream sheet = sheets.next()) {
                parser.parse(new InputSource(sheet));
            }
            return collector.lines;
        } catch (Exception e) {
            if (collector.full) {
                return collector.lines;                         // 필요한 만큼 읽어서 멈춘 것 (StopReading)
            }
            // 확장자만 xlsx 인 다른 파일, 깨진 파일, 암호가 걸린 파일 등
            throw new BusinessException(QuizErrorCode.QUIZ_FILE_INVALID, "엑셀 파일을 열 수 없어요. xlsx 로 저장한 파일인지 확인해 주세요");
        }
    }

    /* 엑셀의 날짜 기준 (#378 CodeRabbit). 예전 Mac 엑셀 파일은 1904년을 기준으로 날짜를 숫자로 저장한다(workbook.xml 의 workbookPr date1904).
     * 이벤트 방식 처리기(XSSFSheetXMLHandler)는 날짜 기준 없이 서식을 바꾸는 메서드를 불러 늘 1900 기준으로 보므로,
     * 날짜 칸이 1,462일 앞당겨져 나온다. 그래서 기준을 읽어 서식 변환기에 넘긴다 */
    private static DataFormatter formatter(boolean date1904) {
        return new DataFormatter() {
            @Override
            public String formatRawCellContents(double value, int formatIndex, String formatString) {
                return super.formatRawCellContents(value, formatIndex, formatString, date1904);
            }
        };
    }

    /* workbook.xml 의 workbookPr 에 date1904 가 켜져 있는가. workbookPr 는 시트 목록보다 앞에 있으므로 시트 목록을 만나면 그만 읽는다 */
    private static boolean isDate1904(XSSFReader reader) throws Exception {
        boolean[] date1904 = {false};
        XMLReader parser = XMLHelper.newXMLReader();
        parser.setContentHandler(new DefaultHandler() {
            @Override
            public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
                String name = localName.isEmpty() ? qName : localName;
                if ("workbookPr".equals(name)) {
                    String value = attributes.getValue("date1904");
                    date1904[0] = "1".equals(value) || "true".equalsIgnoreCase(value);
                    throw new FoundEnough();
                }
                if ("sheets".equals(name)) {
                    throw new FoundEnough();
                }
            }
        });
        try (InputStream workbook = reader.getWorkbookData()) {
            parser.parse(new InputSource(workbook));
        } catch (FoundEnough e) {
            // 필요한 곳까지 읽었다
        }
        return date1904[0];
    }

    /* 압축을 풀면서 크기만 센다. 10MB 를 넘는 순간 멈추므로 아무리 크게 부풀린 파일도 10MB 이상 풀지 않는다.
     * zip 이 아니면 센 크기가 0 이고, 엑셀인지는 다음 단계(POI)가 판단한다 */
    private static void checkUnzippedSize(byte[] bytes) {
        long total = 0;
        byte[] buffer = new byte[8192];
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            while (zip.getNextEntry() != null) {
                int read;
                while ((read = zip.read(buffer)) > 0) {
                    total += read;
                    if (total > XLSX_MAX_UNZIPPED_SIZE) {
                        throw new BusinessException(CommonErrorCode.FILE_TOO_LARGE,
                                "엑셀 파일 내용이 너무 커요. 첫 시트에 머리글과 문제만 남겨 다시 저장해 주세요");
                    }
                }
            }
        } catch (IOException e) {
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

    /* 구분 문자(쉼표·탭)로 나눈 표를 읽는다. 큰따옴표로 감싼 칸은 그 안의 구분 문자·줄바꿈을 글자로 보고, "" 는 " 하나다.
     * 글자가 있는 줄을 maxLines 개 읽으면 멈춘다 */
    private static List<Line> readDelimited(String text, char delimiter, int maxColumns, int maxLines) {
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
                addCell(cells, cell.toString().strip(), maxColumns);
                cell.setLength(0);
                quotedCell = false;
            } else if (c == '\r' || c == '\n') {
                if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                    i++;
                }
                addCell(cells, cell.toString().strip(), maxColumns);
                if (keep(lines, new Line(startLine, cells), maxLines)) {
                    return lines;                               // 필요한 만큼 읽었다
                }
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
            addCell(cells, cell.toString().strip(), maxColumns);
            keep(lines, new Line(startLine, cells), maxLines);
        }
        return lines;
    }

    /* 글자가 있는 줄만 담는다 (빈 줄이 아무리 많아도 목록이 커지지 않게, #378 CodeRabbit). 줄 번호는 실제 줄 그대로다.
     * maxLines 개를 채웠으면 true (그만 읽는다) */
    private static boolean keep(List<Line> lines, Line line, int maxLines) {
        if (!line.isBlank()) {
            lines.add(line);
        }
        return lines.size() >= maxLines;
    }

    /* maxColumns 칸까지는 그대로 담고, 그 뒤로는 글자가 있는 칸 하나("넘친 칸")만 담는다 */
    private static void addCell(List<String> cells, String value, int maxColumns) {
        if (cells.size() < maxColumns) {
            cells.add(value);
        } else if (cells.size() == maxColumns && !value.isEmpty()) {
            cells.add(value);
        }
    }

    /* XLSX 한 행씩 받는 곳 (XSSFSheetXMLHandler 가 부른다). 칸·줄 한도는 readDelimited 와 같다 */
    private static final class RowCollector implements SheetContentsHandler {

        private final int maxColumns;
        private final int maxLines;
        private final List<Line> lines = new ArrayList<>();
        private List<String> cells;
        private int nextColumn;          // 열 위치(r 속성)가 없는 칸을 위해 다음 열 번호를 센다
        private boolean full;            // 필요한 만큼 읽어서 멈췄는가

        RowCollector(int maxColumns, int maxLines) {
            this.maxColumns = maxColumns;
            this.maxLines = maxLines;
        }

        @Override
        public void startRow(int rowNum) {
            cells = new ArrayList<>();
            nextColumn = 0;
        }

        @Override
        public void cell(String cellReference, String formattedValue, XSSFComment comment) {
            int column = cellReference == null ? nextColumn : new CellReference(cellReference).getCol();
            nextColumn = column + 1;
            String value = formattedValue == null ? "" : formattedValue.strip();
            if (column < maxColumns) {
                while (cells.size() < column) {
                    cells.add("");                              // 빈 칸은 시트 XML 에 없으므로 채운다
                }
                cells.add(value);
            } else if (!value.isEmpty() && cells.size() <= maxColumns) {
                while (cells.size() < maxColumns) {
                    cells.add("");
                }
                cells.add(value);                               // 넘친 칸 하나만
            }
        }

        @Override
        public void endRow(int rowNum) {
            if (keep(lines, new Line(rowNum + 1, cells), maxLines)) {
                full = true;
                throw new StopReading();
            }
        }
    }

    /** workbook.xml 에서 날짜 기준을 찾았을 때 읽기를 멈추는 신호 */
    private static final class FoundEnough extends SAXException {
    }

    /** 필요한 만큼 읽었을 때 시트 읽기를 멈추는 신호 */
    private static final class StopReading extends RuntimeException {

        StopReading() {
            super(null, null, false, false);
        }
    }
}
