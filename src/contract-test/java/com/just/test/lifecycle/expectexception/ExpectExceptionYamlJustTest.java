package com.just.test.lifecycle.expectexception;

import com.just.test.annotation.CaseSource;
import com.just.test.annotation.JustTest;
import com.just.test.context.CaseContext;
import com.just.test.lifecycle.JustTestLifecycle;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;

@JustTest
@ContextConfiguration(classes = ExpectExceptionYamlJustTest.EmptyConfiguration.class)
class ExpectExceptionYamlJustTest implements JustTestLifecycle {

    @CaseSource
    void throwsBusinessExceptionDeclaredInYaml(CaseContext context) {
        throw new InvoiceNotFoundException("invoice-42 not found");
    }

    @Configuration
    static class EmptyConfiguration {
    }
}

class InvoiceNotFoundException extends RuntimeException {
    InvoiceNotFoundException(String message) {
        super(message);
    }
}
