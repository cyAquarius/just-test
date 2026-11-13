package com.just.test.internal.mock;

import com.just.test.spy.finalmethod.FinalMethodSpyCases;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.testkit.engine.EngineExecutionResults;
import org.junit.platform.testkit.engine.EngineTestKit;
import org.junit.platform.testkit.engine.Event;

import java.util.Optional;

/**
 * 独立 JVM 入口。调用方把 subclass MockMaker 放在 classpath 最前面，
 * 用来覆盖「消费方没有配置 inline」时的 {@code @JustSpy} 启动失败。
 */
public final class FinalMethodSpySubclassMain {

    private FinalMethodSpySubclassMain() {
    }

    public static void main(String[] args) {
        try {
            if (!FinalMethodMockGuard.boot2Line() || FinalMethodMockGuard.inlineMockMaker()) {
                throw new IllegalStateException("subclass-maker probe expected Boot 2 without inline, version="
                        + FinalMethodMockGuard.mockitoVersion()
                        + " inline=" + FinalMethodMockGuard.inlineMockMaker());
            }
            EngineExecutionResults results = EngineTestKit.engine("junit-jupiter")
                    .selectors(DiscoverySelectors.selectClass(FinalMethodSpyCases.class))
                    .execute();
            String diagnostic = firstMessage(results);
            if (!diagnostic.contains("mockito-extensions/org.mockito.plugins.MockMaker")
                    || !diagnostic.contains("mockito-inline")
                    || !diagnostic.contains("mock-maker-inline")
                    || !diagnostic.contains("RateBean")) {
                throw new IllegalStateException("missing inline configuration guidance:\n" + diagnostic);
            }
            if (results.testEvents().failed().count() != 0
                    || results.testEvents().succeeded().count() != 0) {
                throw new IllegalStateException("final-method spy must fail once at startup, diagnostic:\n"
                        + diagnostic);
            }
        } catch (Throwable failure) {
            failure.printStackTrace(System.out);
            System.exit(1);
        }
    }

    private static String firstMessage(EngineExecutionResults results) {
        String fallback = "";
        for (Event event : results.allEvents().failed().list()) {
            Optional<TestExecutionResult> result = event.getPayload(TestExecutionResult.class);
            if (!result.isPresent() || !result.get().getThrowable().isPresent()) {
                continue;
            }
            String message = flatten(result.get().getThrowable().get());
            if (message.contains("mockito-inline")) {
                return message;
            }
            if (fallback.isEmpty()) {
                fallback = message;
            }
        }
        return fallback;
    }

    private static String flatten(Throwable throwable) {
        StringBuilder text = new StringBuilder();
        Throwable current = throwable;
        while (current != null) {
            if (current.getMessage() != null) {
                text.append(current.getMessage()).append('\n');
            }
            current = current.getCause();
        }
        return text.toString();
    }
}
