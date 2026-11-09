package com.wanted.moneybeetle.domain.operation.model.service;

import com.wanted.moneybeetle.domain.operation.model.dao.ForbiddenWordRepository;
import com.wanted.moneybeetle.domain.operation.model.entity.ForbiddenWord;
import com.wanted.moneybeetle.global.exception.BusinessException;
import com.wanted.moneybeetle.global.exception.CommonErrorCode;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/* comment.
*   담당 : DBA ② (관리자 운영)
*   금칙어 검사 (닉네임, 문의, 강의 제목, 요약글 DP-20~21)
*   - check(text)    : 금칙어가 들어 있으면 BusinessException(CommonErrorCode.FORBIDDEN_WORD, 400)을 던진다
*   - contains(text) : 예외 없이 true/false 만 알고 싶을 때
*   - 비교는 공백을 모두 지우고 대소문자를 구분하지 않는다 ("오픈 채팅", "OpenChat"도 같은 단어로 본다)
*   - 금칙어는 DB 에서 매번 읽는다. 목록이 작고 DB 에 직접 고치면 바로 반영된다 (DP-23)
*  */
@Service
@RequiredArgsConstructor
public class ForbiddenWordService {

    private final ForbiddenWordRepository forbiddenWordRepository;

    /** 금칙어가 들어 있으면 FORBIDDEN_WORD 로 막는다. null·빈 글은 통과한다 (필수 입력 검사는 DTO 몫) */
    @Transactional(readOnly = true)
    public void check(String text) {
        if (contains(text)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN_WORD);
        }
    }

    /** 금칙어가 들어 있는가 */
    @Transactional(readOnly = true)
    public boolean contains(String text) {
        String target = normalize(text);
        if (target.isEmpty()) {
            return false;
        }
        for (ForbiddenWord forbidden : forbiddenWordRepository.findAll()) {
            String word = normalize(forbidden.getWord());
            // 공백만 있는 금칙어가 모든 글을 막지 않도록 빈 문자열은 건너뛴다
            if (!word.isEmpty() && target.contains(word)) {
                return true;
            }
        }
        return false;
    }

    /** 공백(스페이스·탭·줄바꿈 등)을 모두 지우고 소문자로 맞춘다. null 은 빈 문자열 */
    static String normalize(String text) {
        if (text == null) {
            return "";
        }
        return text.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }
}
