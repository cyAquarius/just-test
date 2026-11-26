package com.just.test.internal.mock.review.primary.fixtures;

import com.just.test.internal.mock.review.BindingConfig.Mailer;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Primary
@Component
public class PrimaryMailer implements Mailer {
    @Override
    public String send() {
        return "primary";
    }
}
