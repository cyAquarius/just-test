package com.just.test.smarttest.lifecycle;

import com.just.test.smarttest.annotation.CaseSource;
import com.just.test.smarttest.annotation.SmartTest;
import com.just.test.smarttest.context.CaseContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;

@SmartTest
@ContextConfiguration(classes = ExpectExceptionYamlSmartTest.EmptyConfiguration.class)
class ExpectExceptionYamlSmartTest implements SmartTestLifecycle {

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
