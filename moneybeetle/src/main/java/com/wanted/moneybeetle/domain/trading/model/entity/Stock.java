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
*   테이블 stock (30개, 기업정보)
*  */
@Entity
@Table(name = "stock")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Stock {

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

    // TODO(#2): BaseTimeEntity 가 생기면 상속으로 바꾼다
    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

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
