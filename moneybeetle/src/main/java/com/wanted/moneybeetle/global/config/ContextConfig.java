package com.wanted.moneybeetle.global.config;

/* comment.
*   담당 : SCM (공통 기반·보안)
*   @ComponentScan(basePackages = "com.wanted.moneybeetle")
*   Entity <-> DTO 변환은 정적 팩토리 메서드(DTO.from, Entity.create)로 처리한다.
*  */

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan(basePackages = "com.wanted.moneybeetle")
public class ContextConfig {

}
