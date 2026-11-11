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
*   테이블 sector (15개)
*   created_at·updated_at 은 BaseTimeEntity(JPA 감사, 서울 시간)가 채운다 (#293)
*  */
@Entity
@Table(name = "sector")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Sector extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long sectorId;

    private String name;

    private int sortOrder;

    private boolean isActive;

    private Sector(String name, int sortOrder) {
        this.name = name;
        this.sortOrder = sortOrder;
        this.isActive = true;
    }

    public static Sector create(String name, int sortOrder) {
        return new Sector(name, sortOrder);
    }

    public void changeName(String name) {
        this.name = name;
    }

    public void changeSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public void activate() {
        this.isActive = true;
    }

    public void deactivate() {
        this.isActive = false;
    }
}
