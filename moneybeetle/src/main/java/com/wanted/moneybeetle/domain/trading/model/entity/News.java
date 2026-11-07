package com.wanted.moneybeetle.domain.trading.model.entity;

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
import org.hibernate.annotations.UpdateTimestamp;

/* comment.
*   담당 : DBA ② (모의투자)
*   테이블 news
*  */
@Entity
@Table(name = "news")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class News {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long newsId;

    private Long tradingSeasonId;

    /** 공개 턴 */
    private int turnNo;

    private String title;

    private String content;

    /** 설계 의도 메모 (관리자 전용) */
    private String adminMemo;

    // TODO(#2): BaseTimeEntity 가 생기면 상속으로 바꾼다
    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    private News(Long tradingSeasonId, int turnNo, String title, String content,
            String adminMemo) {
        this.tradingSeasonId = tradingSeasonId;
        this.turnNo = turnNo;
        this.title = title;
        this.content = content;
        this.adminMemo = adminMemo;
    }

    public static News create(Long tradingSeasonId, int turnNo, String title, String content,
            String adminMemo) {
        return new News(tradingSeasonId, turnNo, title, content, adminMemo);
    }

    public void changeContent(String title, String content, String adminMemo) {
        this.title = title;
        this.content = content;
        this.adminMemo = adminMemo;
    }
}
