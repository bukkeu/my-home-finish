package com.wanted.moneybeetle;

/* comment.
*   담당 : SCM (공통 기반·보안)
*   스프링 부트 시작 클래스 (@SpringBootApplication, main 메서드)
*   루트 패키지(com.wanted.moneybeetle)에 둔다 (#114) → @SpringBootApplication 이 이 패키지와 그 아래 전부를 스캔한다
*   (Component·Entity·Repository 를 따로 스캔 지정할 필요가 없다. 예전에는 global.config 에 있어서 ContextConfig·JpaConfig 로 범위를 넓혔다)
*  */

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class MoneyBeetleApplication {

    public static void main(String[] args) {
        SpringApplication.run(MoneyBeetleApplication.class, args);
    }
}
