package com.wanted.moneybeetle.domain.trading.exception;

/* comment.
*   담당 : PM (모의투자)
*   가격표 CSV 올리기(API 130)에서 형식이 맞지 않는 행이 있을 때 : 오류 행 목록을 함께 담는다
*   - 하나라도 있으면 전체를 저장하지 않는다
*  */

import com.wanted.moneybeetle.global.exception.BusinessException;
import java.util.List;
import lombok.Getter;

@Getter
public class CsvInvalidException extends BusinessException {

    private final List<CsvError> errors;

    public CsvInvalidException(List<CsvError> errors) {
        super(TradingErrorCode.CSV_INVALID);
        this.errors = List.copyOf(errors);
    }

    /** line : 파일의 몇 번째 줄인지 (제목 줄 = 1) */
    public record CsvError(int line, String message) {
    }
}
