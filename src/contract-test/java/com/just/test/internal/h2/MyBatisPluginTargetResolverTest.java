package com.just.test.internal.h2;

import org.apache.ibatis.executor.statement.StatementHandler;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;

class MyBatisPluginTargetResolverTest {

    @Test
    void unwrapsNestedMyBatisPluginsWithoutReflectingOnJdkProxyFields() {
        StatementHandler target = mock(StatementHandler.class);
        Object firstProxy = new H2MySqlIfInterceptor().plugin(target);
        Object secondProxy = new H2MySqlStringLiteralInterceptor().plugin(firstProxy);

        assertSame(target, MyBatisPluginTargetResolver.unwrap(secondProxy));
    }
}
