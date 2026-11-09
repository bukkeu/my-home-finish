package com.wanted.moneybeetle.domain.trading.model.dao;

import com.wanted.moneybeetle.domain.trading.model.entity.NewsTag;
import org.springframework.data.jpa.repository.JpaRepository;

/* comment.
*   담당 : PM (모의투자)
*   테이블 news_tag
*  */
public interface NewsTagRepository extends JpaRepository<NewsTag, Long> {

}
