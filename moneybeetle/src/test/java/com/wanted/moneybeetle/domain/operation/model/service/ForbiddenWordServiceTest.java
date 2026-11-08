package com.wanted.moneybeetle.domain.operation.model.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.wanted.moneybeetle.domain.operation.model.dao.ForbiddenWordRepository;
import com.wanted.moneybeetle.domain.operation.model.entity.ForbiddenWord;
import com.wanted.moneybeetle.global.exception.BusinessException;
import com.wanted.moneybeetle.global.exception.CommonErrorCode;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/* comment.
*   담당 : SCM (회원·공통)
*   ForbiddenWordService - Repository 를 Mock 으로 두고 서버·DB 없이 돈다
*  */
class ForbiddenWordServiceTest {

    private ForbiddenWordRepository repository;
    private ForbiddenWordService service;

    @BeforeEach
    void setUp() {
        repository = mock(ForbiddenWordRepository.class);
        when(repository.findAll()).thenReturn(List.of(
                ForbiddenWord.create("오픈채팅"), ForbiddenWord.create("텔레그램"), ForbiddenWord.create("OpenChat")));
        service = new ForbiddenWordService(repository);
    }

    @Test
    @DisplayName("금칙어가 글 중간에 들어 있으면 FORBIDDEN_WORD(400)로 막는다")
    void blocksContainedWord() {
        assertThatThrownBy(() -> service.check("지금 오픈채팅으로 오세요"))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.FORBIDDEN_WORD);
                    assertThat(e.getErrorCode().getStatus().value()).isEqualTo(400);
                });
    }

    @Test
    @DisplayName("공백을 끼워 넣거나 대소문자를 바꿔도 같은 단어로 본다")
    void ignoresWhitespaceAndCase() {
        assertThat(service.contains("오 픈  채\n팅")).isTrue();
        assertThat(service.contains("my OPENCHAT room")).isTrue();
        assertThat(service.contains("텔 레 그 램")).isTrue();
    }

    @Test
    @DisplayName("금칙어가 없으면 통과한다")
    void passesCleanText() {
        assertThatCode(() -> service.check("청약통장 가점 계산이 궁금해요")).doesNotThrowAnyException();
        assertThat(service.contains("청약통장 가점 계산이 궁금해요")).isFalse();
    }

    @Test
    @DisplayName("null·빈 글·공백만 있는 글은 통과하고 DB도 조회하지 않는다")
    void blankTextPasses() {
        assertThat(service.contains(null)).isFalse();
        assertThat(service.contains("")).isFalse();
        assertThat(service.contains("   \n")).isFalse();
        org.mockito.Mockito.verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("금칙어 목록이 비어 있거나 공백만 있는 금칙어가 있어도 모든 글이 막히지 않는다")
    void emptyOrBlankWordDoesNotBlockEverything() {
        when(repository.findAll()).thenReturn(List.of());
        assertThat(service.contains("아무 글")).isFalse();

        when(repository.findAll()).thenReturn(List.of(ForbiddenWord.create("  ")));
        assertThat(service.contains("아무 글")).isFalse();
    }

    @Test
    @DisplayName("normalize: null 은 빈 문자열, 공백 제거·소문자")
    void normalize() {
        assertThat(ForbiddenWordService.normalize(null)).isEmpty();
        assertThat(ForbiddenWordService.normalize(" A b\tC ")).isEqualTo("abc");
    }
}
