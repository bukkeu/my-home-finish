package com.wanted.moneybeetle.domain.housing.model.entity;

/* comment.
*   담당 : SCM (모의 청약)
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

    // 오늘 신청할 수 있나? (신청 시작일~마감일, 당일 포함 / 발표된 단지는 불가) - #29 SP-73
    public boolean isApplyOpen(LocalDate today) {
        return STATUS_OPEN.equals(currentStatus(today));
    }

    // 관리자가 결과를 발표했나? 발표 전에는 당첨 결과를 보여 주지 않는다 - #29 SP-75
    public boolean isAnnounced() {
        return STATUS_ANNOUNCED.equals(status);
    }

    // ── #47 관리자 공고 관리 ──────────────────────────────

    // 공고 등록 : 처음 상태는 접수 전(UPCOMING). 화면 상태는 날짜로 계산하므로 발표 전까지 status 는 바꾸지 않는다
    public static HousingComplex create(String name, String location, String areaType, long salePrice,
                                        int supplyUnits, String supplyType,
                                        LocalDate noticeDate, LocalDate applyStartDate,
                                        LocalDate applyEndDate, LocalDate announceDate, Long createdBy) {
        HousingComplex complex = new HousingComplex();
        complex.changeInfo(name, location, areaType, salePrice, supplyType);
        complex.changeSchedule(noticeDate, applyStartDate, applyEndDate, announceDate, supplyUnits);
        complex.status = STATUS_UPCOMING;
        complex.createdBy = createdBy;
        return complex;
    }

    // 단지 정보 수정 : 신청자가 있어도 바꿀 수 있다
    public void changeInfo(String name, String location, String areaType, long salePrice, String supplyType) {
        this.name = name;
        this.location = location;
        this.areaType = areaType;
        this.salePrice = salePrice;
        this.supplyType = supplyType;
    }

    // 일정·공급 세대 수 수정 : 신청자가 있으면 바꿀 수 없다 (SP-76, Service 가 먼저 확인)
    public void changeSchedule(LocalDate noticeDate, LocalDate applyStartDate, LocalDate applyEndDate,
                               LocalDate announceDate, int supplyUnits) {
        this.noticeDate = noticeDate;
        this.applyStartDate = applyStartDate;
        this.applyEndDate = applyEndDate;
        this.announceDate = announceDate;
        this.supplyUnits = supplyUnits;
    }

    // 일정·공급 세대 수가 지금 값과 같은가? (신청자가 있을 때 "정보만 바꾸는 수정" 인지 판단)
    public boolean hasSameSchedule(LocalDate noticeDate, LocalDate applyStartDate, LocalDate applyEndDate,
                                   LocalDate announceDate, int supplyUnits) {
        return this.noticeDate.equals(noticeDate) && this.applyStartDate.equals(applyStartDate)
                && this.applyEndDate.equals(applyEndDate) && this.announceDate.equals(announceDate)
                && this.supplyUnits == supplyUnits;
    }

    // 삭제 : 실제로 지우지 않고 삭제 시각만 남긴다 (Soft Delete, DB 설계 3장)
    public void delete(LocalDateTime now) {
        this.deletedAt = now;
    }
}
