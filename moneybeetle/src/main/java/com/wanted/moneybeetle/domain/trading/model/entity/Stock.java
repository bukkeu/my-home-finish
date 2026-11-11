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
*   테이블 stock (30개, 기업정보)
*   created_at·updated_at 은 BaseTimeEntity(JPA 감사, 서울 시간)가 채운다 (#293)
*  */
@Entity
@Table(name = "stock")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Stock extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long stockId;

    private Long sectorId;

    private String name;

    private int startPrice;

    private String intro;

    private String business;

    private String revenue;

    private String operatingProfit;

    private boolean isActive;

    private Stock(Long sectorId, String name, int startPrice) {
        this.sectorId = sectorId;
        this.name = name;
        this.startPrice = startPrice;
        this.isActive = true;
    }

    public static Stock create(Long sectorId, String name, int startPrice) {
        return new Stock(sectorId, name, startPrice);
    }

    public void changeBasicInfo(Long sectorId, String name, int startPrice) {
        this.sectorId = sectorId;
        this.name = name;
        this.startPrice = startPrice;
    }

    public void changeCompanyInfo(String intro, String business, String revenue,
            String operatingProfit) {
        this.intro = intro;
        this.business = business;
        this.revenue = revenue;
        this.operatingProfit = operatingProfit;
    }

    public void activate() {
        this.isActive = true;
    }

    public void deactivate() {
        this.isActive = false;
    }
}
