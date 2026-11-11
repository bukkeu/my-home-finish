package com.wanted.moneybeetle.domain.trading.model.entity;

import com.wanted.moneybeetle.global.common.BaseTimeEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/* comment.
*   담당 : PM (모의투자)
*   테이블 news
*   created_at·updated_at 은 BaseTimeEntity(JPA 감사, 서울 시간)가 채운다 (#293)
*  */
@Entity
@Table(name = "news")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class News extends BaseTimeEntity {

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

    /** 공개 턴을 옮긴다. 아직 공개 전인 뉴스만 옮길 수 있는지는 NewsAdminService 가 확인한다 (#43) */
    public void changeTurnNo(int turnNo) {
        this.turnNo = turnNo;
    }

    /** 지난 턴(현재 턴 이하)에 공개된 뉴스인지 (SP-60, ADM-27) */
    public boolean isPublishedBy(int currentTurn) {
        return this.turnNo <= currentTurn;
    }
}
