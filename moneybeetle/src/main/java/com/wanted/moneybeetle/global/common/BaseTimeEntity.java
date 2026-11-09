package com.wanted.moneybeetle.global.common;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.springframework.data.annotation.LastModifiedDate;

import java.time.LocalDateTime;

/* comment.
 *   담당 : SCM (공통 기반·보안)
 *   created_at + updated_at 이 모두 있는 테이블용 공통 부모 (DB 설계서 1-1)
 *   created_at 은 CreatedTimeEntity 에서 물려받는다
 *   사용 : public class HousingComplex extends BaseTimeEntity
 *  */
@Getter
@MappedSuperclass
public abstract class BaseTimeEntity extends CreatedTimeEntity {

    @LastModifiedDate                               // 저장·수정(INSERT·UPDATE)할 때마다 현재 시간 기록
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}

