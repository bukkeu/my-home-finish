package com.wanted.moneybeetle.support;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import org.slf4j.LoggerFactory;

/* comment.
*   테스트 지원 : 어떤 클래스가 남기는 로그를 모아서 확인한다 (이슈 #299).
*   사용 : try (LogCapture logs = LogCapture.of(SomeClass.class)) { ... logs.events() ... }
*   try 가 끝나면 붙인 수집기를 떼어, 다른 테스트의 로그에 영향을 주지 않는다.
*  */
public final class LogCapture implements AutoCloseable {

    private final Logger logger;
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    private LogCapture(Class<?> type) {
        this.logger = (Logger) LoggerFactory.getLogger(type);
        appender.start();
        logger.addAppender(appender);
    }

    public static LogCapture of(Class<?> type) {
        return new LogCapture(type);
    }

    /** 지금까지 남은 로그 (남긴 순서) */
    public List<ILoggingEvent> events() {
        return List.copyOf(appender.list);
    }

    @Override
    public void close() {
        logger.detachAppender(appender);
        appender.stop();
    }
}
