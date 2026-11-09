package com.wanted.moneybeetle.domain.operation.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

/* comment.
*   담당 : DBA ② (관리자 운영)
*   테이블 forbidden_word (금칙어. 지금은 DB에 직접 넣어 쓴다, DP-23)
*   - 이 이슈(#64)는 검사만 한다. 등록·삭제 화면이 필요해지면 그때 생성·삭제 메서드를 더한다
*  */
@Entity
@Table(name = "forbidden_word")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ForbiddenWord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long forbiddenWordId;

    @Column(nullable = false, length = 50, unique = true)
    private String word;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    /** 테스트와 시드 용도의 정적 팩토리 */
    public static ForbiddenWord create(String word) {
        ForbiddenWord forbiddenWord = new ForbiddenWord();
        forbiddenWord.word = word;
        return forbiddenWord;
    }
}
