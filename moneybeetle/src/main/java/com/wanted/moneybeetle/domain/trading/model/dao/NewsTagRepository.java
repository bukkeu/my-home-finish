package com.wanted.moneybeetle.domain.trading.model.dao;

import com.wanted.moneybeetle.domain.trading.model.entity.NewsTag;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/* comment.
*   담당 : PM (모의투자)
*   테이블 news_tag
*  */
public interface NewsTagRepository extends JpaRepository<NewsTag, Long> {

    /** 뉴스들의 관련 분야·종목을 한 번에 (#43 관리자 목록, #24 사용자 뉴스) */
    List<NewsTag> findByNewsIdIn(List<Long> newsIds);

    /** 뉴스의 관련 분야·종목 (#43 수정 때 비교) */
    List<NewsTag> findByNewsId(Long newsId);

    /** 뉴스의 관련 분야·종목을 지운다 (#43 수정·삭제) */
    void deleteByNewsId(Long newsId);
}
