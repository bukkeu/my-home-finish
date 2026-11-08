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
*   담당 : PM (모의투자)
*   테이블 sector (15개)
*  */
@Entity
@Table(name = "sector")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Sector {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long sectorId;

    private String name;

    private int sortOrder;

    private boolean isActive;

    // TODO(#2): BaseTimeEntity 가 생기면 상속으로 바꾼다
    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

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
