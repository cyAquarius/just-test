package com.just.test.smarttest.lifecycle;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SmartTestExtensionTest {

    @Test
    void skipsOnlyWhenJdbcTemplateIsAbsent() {
        GenericApplicationContext context = new GenericApplicationContext();
        context.refresh();
        try {
            assertNull(SmartTestExtension.resolveJdbcTemplate(context));
        } finally {
            context.close();
        }
    }

    @Test
    void exposesAmbiguousJdbcTemplateConfiguration() {
        GenericApplicationContext context = new GenericApplicationContext();
        context.getBeanFactory().registerSingleton("firstJdbcTemplate", new JdbcTemplate());
        context.getBeanFactory().registerSingleton("secondJdbcTemplate", new JdbcTemplate());
        context.refresh();
        try {
            assertThrows(NoUniqueBeanDefinitionException.class,
                    () -> SmartTestExtension.resolveJdbcTemplate(context));
        } finally {
            context.close();
        }
    }
}
