package com.wanted.moneybeetle.domain.growth.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

/* comment.
*   담당 : PM (성장·홈·모의 청약)
*   테이블 attendance (하루 1회) - 출석한 날마다 한 줄. 같은 (회원, 날짜)는 DB UNIQUE 가 한 번만 받는다 (SP-10)
*   - 다른 도메인(member)은 Entity가 아니라 ID로 참조한다
*   - INSERT 만 한다 (지난 출석 기록은 바꾸지 않는다)
*  */
@Entity
@Table(name = "attendance")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Attendance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long attendanceId;

    @Column(nullable = false)
    private Long memberId;

    /** 출석일 (서울 KST 기준 날짜) */
    @Column(nullable = false)
    private LocalDate attendDate;

    /** 이날 기준 연속 출석 일수 (1 이상) */
    @Column(nullable = false)
    private int streakCount;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    private Attendance(Long memberId, LocalDate attendDate, int streakCount) {
        this.memberId = memberId;
        this.attendDate = attendDate;
        this.streakCount = streakCount;
    }

    public static Attendance create(Long memberId, LocalDate attendDate, int streakCount) {
        if (memberId == null || attendDate == null) {
            throw new IllegalArgumentException("회원과 출석일이 필요합니다.");
        }
        if (streakCount < 1) {
            throw new IllegalArgumentException("연속 출석 일수는 1 이상이어야 합니다.");
        }
        return new Attendance(memberId, attendDate, streakCount);
    }
}
