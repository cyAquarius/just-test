package com.just.test.smarttest.mock;

import com.just.test.smarttest.annotation.SmartMock;
import org.junit.jupiter.api.Test;
import org.springframework.aop.scope.ScopedProxyUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.AutowireCandidateQualifier;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.annotation.ContextAnnotationAutowireCandidateResolver;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SmartMockPostProcessorTest {

    @Test
    void failsForAmbiguousCandidates() {
        DefaultListableBeanFactory beanFactory = beanFactoryWithTwoCandidates();

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> process(beanFactory, field(TestFields.class, "client")));

        assertTrue(failure.getMessage().contains("Multiple beans"));
    }

    @Test
    void selectsCandidateMatchingFieldName() {
        DefaultListableBeanFactory beanFactory = beanFactoryWithTwoCandidates();

        process(beanFactory, field(TestFields.class, "secondClient"));

        assertTrue(beanFactory.containsBeanDefinition("secondClient"));
        assertTrue(beanFactory.containsBeanDefinition("scopedTarget.secondClient"));
        assertTrue(beanFactory.containsBeanDefinition("firstClient"));
    }

    @Test
    void selectsExplicitBeanAlias() {
        DefaultListableBeanFactory beanFactory = beanFactoryWithTwoCandidates();
        beanFactory.registerAlias("secondClient", "clientAlias");

        process(beanFactory, field(TestFields.class, "explicitAlias"));

        assertTrue(beanFactory.containsBeanDefinition("scopedTarget.secondClient"));
        assertEquals("secondClient", beanFactory.canonicalName("clientAlias"));
    }

    @Test
    void filtersCandidatesWithSpringQualifierRulesAndPreservesQualifier() {
        DefaultListableBeanFactory beanFactory = beanFactory();
        registerCandidate(beanFactory, "firstClient", false, true, "red");
        registerCandidate(beanFactory, "secondClient", false, true, "blue");
        Field field = field(TestFields.class, "qualifiedClient");

        process(beanFactory, field);

        SmartMockDefinition definition = definition(field);
        assertTrue(beanFactory.containsBeanDefinition("scopedTarget.secondClient"));
        assertTrue(beanFactory.isAutowireCandidate("secondClient",
                definition.toDependencyDescriptor()));
    }

    @Test
    void supportsCustomQualifierAnnotations() {
        DefaultListableBeanFactory beanFactory = beanFactory();
        registerCustomQualifiedCandidate(beanFactory, "firstClient", "red");
        registerCustomQualifiedCandidate(beanFactory, "secondClient", "blue");

        process(beanFactory, field(TestFields.class, "regionalClient"));

        assertTrue(beanFactory.containsBeanDefinition("scopedTarget.secondClient"));
    }

    @Test
    void ignoresBeansThatAreNotAutowireCandidates() {
        DefaultListableBeanFactory beanFactory = beanFactory();
        registerCandidate(beanFactory, "firstClient", false, false, null);
        registerCandidate(beanFactory, "secondClient", false, true, null);

        process(beanFactory, field(TestFields.class, "client"));

        assertTrue(beanFactory.containsBeanDefinition("firstClient"));
        assertTrue(beanFactory.containsBeanDefinition("scopedTarget.secondClient"));
    }

    @Test
    void selectsAndPreservesPrimaryCandidate() {
        DefaultListableBeanFactory beanFactory = beanFactory();
        registerCandidate(beanFactory, "firstClient", false, true, null);
        registerCandidate(beanFactory, "secondClient", true, true, null);

        process(beanFactory, field(TestFields.class, "client"));

        assertTrue(beanFactory.getBeanDefinition("secondClient").isPrimary());
        assertFalse(beanFactory.getBeanDefinition("scopedTarget.secondClient").isPrimary());
    }

    @Test
    void replacesExistingScopedProxyWithoutNestingTargets() {
        DefaultListableBeanFactory beanFactory = beanFactory();
        AbstractBeanDefinition originalTarget = BeanDefinitionBuilder
                .genericBeanDefinition(SampleClient.class).getBeanDefinition();
        BeanDefinitionHolder proxy = ScopedProxyUtils.createScopedProxy(
                new BeanDefinitionHolder(originalTarget, "scopedClient"), beanFactory, true);
        beanFactory.registerBeanDefinition(proxy.getBeanName(), proxy.getBeanDefinition());

        process(beanFactory, field(TestFields.class, "scopedClient"));

        assertNotSame(originalTarget, beanFactory.getBeanDefinition("scopedTarget.scopedClient"));
        assertFalse(beanFactory.containsBeanDefinition("scopedTarget.scopedTarget.scopedClient"));
    }

    @Test
    void contextKeyReusesEquivalentDeclarationsButSeparatesDifferentQualifiers() {
        SmartMockContextCustomizer first = customizer(field(FirstTest.class, "client"));
        SmartMockContextCustomizer equivalent = customizer(field(EquivalentTest.class, "client"));
        SmartMockContextCustomizer different = customizer(field(DifferentQualifierTest.class, "client"));

        assertEquals(first, equivalent);
        assertEquals(first.hashCode(), equivalent.hashCode());
        assertNotEquals(first, different);
    }

    @Test
    void contextKeyIgnoresFieldNameAndQualifierWhenExplicitBeanNameSelectsTheTarget() {
        SmartMockContextCustomizer first = customizer(field(ExplicitFirstTest.class, "client"));
        SmartMockContextCustomizer equivalent = customizer(field(ExplicitEquivalentTest.class, "renamedClient"));

        assertEquals(first, equivalent);
        assertEquals(first.hashCode(), equivalent.hashCode());
    }

    @Test
    void preservesConcreteTargetTypeForOtherConcreteInjectionPoints() {
        DefaultListableBeanFactory beanFactory = beanFactory();
        AbstractBeanDefinition definition = BeanDefinitionBuilder
                .genericBeanDefinition(ConcreteClient.class).getBeanDefinition();
        beanFactory.registerBeanDefinition("concreteClient", definition);

        process(beanFactory, field(ConcreteTypeTest.class, "client"));

        assertEquals(ConcreteClient.class, beanFactory.getType("scopedTarget.concreteClient"));
    }

    private SmartMockContextCustomizer customizer(Field field) {
        return new SmartMockContextCustomizer(Collections.singleton(definition(field)));
    }

    private void process(DefaultListableBeanFactory beanFactory, Field field) {
        new SmartMockPostProcessor(Collections.singleton(definition(field)))
                .postProcessBeanFactory(beanFactory);
    }

    private SmartMockDefinition definition(Field field) {
        return SmartMockDefinition.forField(field, field.getAnnotation(SmartMock.class));
    }

    private Field field(Class<?> type, String name) {
        try {
            return type.getDeclaredField(name);
        } catch (NoSuchFieldException e) {
            throw new AssertionError(e);
        }
    }

    private DefaultListableBeanFactory beanFactoryWithTwoCandidates() {
        DefaultListableBeanFactory beanFactory = beanFactory();
        registerCandidate(beanFactory, "firstClient", false, true, null);
        registerCandidate(beanFactory, "secondClient", false, true, null);
        return beanFactory;
    }

    private DefaultListableBeanFactory beanFactory() {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        beanFactory.setAutowireCandidateResolver(new ContextAnnotationAutowireCandidateResolver());
        return beanFactory;
    }

    private void registerCandidate(DefaultListableBeanFactory beanFactory, String beanName,
                                   boolean primary, boolean autowireCandidate, String qualifier) {
        AbstractBeanDefinition definition = BeanDefinitionBuilder
                .genericBeanDefinition(SampleClient.class).getBeanDefinition();
        definition.setPrimary(primary);
        definition.setAutowireCandidate(autowireCandidate);
        if (qualifier != null) {
            definition.addQualifier(new AutowireCandidateQualifier(Qualifier.class, qualifier));
        }
        beanFactory.registerBeanDefinition(beanName, definition);
    }

    private void registerCustomQualifiedCandidate(DefaultListableBeanFactory beanFactory,
                                                  String beanName, String qualifier) {
        AbstractBeanDefinition definition = BeanDefinitionBuilder
                .genericBeanDefinition(SampleClient.class).getBeanDefinition();
        definition.addQualifier(new AutowireCandidateQualifier(Region.class, qualifier));
        beanFactory.registerBeanDefinition(beanName, definition);
    }

    private static class TestFields {
        @SmartMock
        private SampleClient client;

        @SmartMock
        private SampleClient secondClient;

        @SmartMock(name = "clientAlias")
        private SampleClient explicitAlias;

        @SmartMock
        @Qualifier("blue")
        private SampleClient qualifiedClient;

        @SmartMock
        @Region("blue")
        private SampleClient regionalClient;

        @SmartMock
        private SampleClient scopedClient;
    }

    private static class FirstTest {
        @SmartMock
        @Qualifier("blue")
        private SampleClient client;
    }

    private static class EquivalentTest {
        @SmartMock
        @Qualifier("blue")
        private SampleClient client;
    }

    private static class DifferentQualifierTest {
        @SmartMock
        @Qualifier("red")
        private SampleClient client;
    }

    private static class ExplicitFirstTest {
        @SmartMock(name = "firstClient")
        private SampleClient client;
    }

    private static class ExplicitEquivalentTest {
        @SmartMock(name = "firstClient")
        @Qualifier("ignoredForExplicitSelection")
        private SampleClient renamedClient;
    }

    private static class ConcreteTypeTest {
        @SmartMock
        private Client client;
    }

    private interface Client {
    }

    private static class ConcreteClient implements Client {
    }

    private static class SampleClient {
    }

    @Qualifier
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.FIELD, ElementType.ANNOTATION_TYPE})
    private @interface Region {
        String value();
    }
}
