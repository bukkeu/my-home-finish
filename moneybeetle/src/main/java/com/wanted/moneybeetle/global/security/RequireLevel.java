package com.wanted.moneybeetle.global.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/* comment.
*   담당 : SCM (회원·공통)
*   컨트롤러 메서드(또는 클래스)에 붙여 해금 레벨이 필요하다고 표시하는 어노테이션
*   예) @RequireLevel(UnlockFeature.TRADING)  -> 모의투자 해금 레벨(Lv.8) 미만이면 403 LEVEL_LOCKED
*   API 명세서에서 "회원 Lv.8·Lv.12"로 표시된 API 에만 붙인다 (청약통장·당첨 결과는 레벨 조건 없음, #4)
*   실제 검사는 LevelCheckInterceptor 가 한다
*  */
@Target({ElementType.METHOD, ElementType.TYPE})   // 메서드나 클래스에 붙일 수 있다
@Retention(RetentionPolicy.RUNTIME)                // 실행 중에도 남아 있어야 인터셉터가 읽을 수 있다
@Documented
public @interface RequireLevel {

    UnlockFeature value();
}
