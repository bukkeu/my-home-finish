package com.wanted.moneybeetle.domain.operation.model.dao;

import com.wanted.moneybeetle.domain.operation.model.entity.ForbiddenWord;
import org.springframework.data.jpa.repository.JpaRepository;

/* comment.
*   담당 : DBA ② (관리자 운영)
*   테이블 forbidden_word
*  */
public interface ForbiddenWordRepository extends JpaRepository<ForbiddenWord, Long> {

}
