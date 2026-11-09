package com.wanted.moneybeetle.global.common;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/* comment.
 *   담당 : SCM (공통 기반·보안)
 *   created_at 만 있는 기록성 테이블용 공통 부모 (DB 설계서 1-1)
 *   사용 : public class SubscriptionDeposit extends CreatedTimeEntity
 *  */
@Getter
@MappedSuperclass                                   // 테이블은 아니고, 자식 엔티티에 컬럼만 물려준다
@EntityListeners(AuditingEntityListener.class)      // 저장할 때 시간을 자동으로 채워 주는 감시자 연결
public abstract class CreatedTimeEntity {

    @CreatedDate                                    // 처음 저장(INSERT)할 때 현재 시간 기록
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}