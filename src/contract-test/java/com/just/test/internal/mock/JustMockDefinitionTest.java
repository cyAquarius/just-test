package com.just.test.internal.mock;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JustMockDefinitionTest {

    @Test
    void decapitalizeFollowsJavaBeansAcronymRules() {
        assertEquals("URLClient", JustMockDefinition.decapitalize("URLClient"));
        assertEquals("URL", JustMockDefinition.decapitalize("URL"));
        assertEquals("userService", JustMockDefinition.decapitalize("UserService"));
        assertEquals("a", JustMockDefinition.decapitalize("A"));
        assertEquals(null, JustMockDefinition.decapitalize(null));
    }

    @Test
    void autoMockBeanNameKeepsAcronyms() {
        JustMockDefinition definition = JustMockDefinition.forAutoMock(URLClient.class, "", "test");

        assertEquals("URLClient", definition.getExplicitBeanName());
    }

    private static class URLClient {
    }
}
