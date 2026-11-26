package com.just.test.internal.mock.review.excluded.fixtures;

import com.just.test.annotation.ThreadScopedMock;
import com.just.test.internal.mock.review.BindingConfig.Mailer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@TestConfiguration
public class ExcludedMocks {
    @Bean
    @ThreadScopedMock
    Mailer excludedMailer() {
        throw new AssertionError();
    }
}
