package com.wanted.moneybeetle.domain.housing.model.entity;

/* comment.
*   담당 : SCM (성장·홈·모의 청약)
*   테이블 housing_complex (Soft Delete)
*  */

import com.wanted.moneybeetle.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@ToString
@Entity
@Table(name = "housing_complex")
public class HousingComplex extends BaseTimeEntity {

    public static final String STATUS_UPCOMING = "UPCOMING";    // 접수 전
    public static final String STATUS_OPEN = "OPEN";           // 접수 중
    public static final String STATUS_CLOSED = "CLOSED";       // 접수 마감, 발표 전
    public static final String STATUS_ANNOUNCED = "ANNOUNCED"; // 결과 발표

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // 번호는 MySQL의 AUTO_INCREMENT가 매기게 맡긴다
    @Column(name = "housing_complex_id")
    private Long id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "location", nullable = false)
    private String location;

    @Column(name = "supply_type", nullable = false)
    private String supplyType;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "supply_units", nullable = false)
    private int supplyUnits;

    @Column(name = "notice_date", nullable = false)
    private LocalDate noticeDate;

    @Column(name = "apply_start_date", nullable = false)
    private LocalDate applyStartDate;

    @Column(name = "announce_date", nullable = false)
    private LocalDate announceDate;

    @Column(name = "computed_at")
    private LocalDateTime computedAt;

    @Column(name = "announced_at")
    private LocalDateTime announcedAt;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "area_type", nullable = false)
    private String areaType;

    @Column(name = "sale_price", nullable = false)
    private long salePrice;

    @Column(name = "apply_end_date", nullable = false)
    private LocalDate applyEndDate;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;


    // ⑧ 상태를 묻는 메서드
    //    접수 마감 뒤인가? → 경쟁률을 보여 줘도 되는지 판단할 때 사용 (SP-74)
    public boolean isApplyClosed(LocalDate today) {
        return today.isAfter(applyEndDate);
    }

    // 오늘 기준 공고 상태 (API 63·64 의 status)
    public String currentStatus(LocalDate today) {
        // ① 관리자가 결과를 발표했으면 날짜와 상관없이 "발표"
        if (STATUS_ANNOUNCED.equals(status)) {
            return STATUS_ANNOUNCED;
        }
        // ② 신청 시작일 전이면 "접수 전"
        if (today.isBefore(applyStartDate)) {
            return STATUS_UPCOMING;
        }
        // ③ 신청 마감일 뒤면 "접수 마감" (위에서 만든 메서드 재사용)
        if (isApplyClosed(today)) {
            return STATUS_CLOSED;
        }
        // ④ 나머지는 신청 기간 안
        return STATUS_OPEN;
    }


}
