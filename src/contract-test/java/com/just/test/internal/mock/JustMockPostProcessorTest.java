package com.just.test.internal.mock;

import com.just.test.annotation.JustMock;
import com.just.test.annotation.JustSpy;
import com.just.test.annotation.ThreadScopedMock;
import com.just.test.demo.project.fixtures.feign.DemoFeignClient;
import com.just.test.demo.project.fixtures.feign.DemoLocalClient;
import com.just.test.demo.project.fixtures.feign.ExcludedFeignClient;
import com.just.test.internal.project.JustTestProjectFeignAutoMock;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.mapper.MapperFactoryBean;
import org.springframework.aop.scope.ScopedProxyUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.AutowireCandidateQualifier;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ContextAnnotationAutowireCandidateResolver;

import static org.mockito.Mockito.mock;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JustMockPostProcessorTest {

    @Test
    void failsForAmbiguousCandidates() {
        DefaultListableBeanFactory beanFactory = beanFactoryWithTwoCandidates();

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> process(beanFactory, field(TestFields.class, "client")));

        assertTrue(failure.getMessage().contains("Multiple beans"));
    }

    @Test
    void failsWhenJustMockLeavesAutoConfigurationBeanOfSameType() {
        DefaultListableBeanFactory beanFactory = beanFactory();
        registerCandidate(beanFactory, "projectMock", false, true, null);
        beanFactory.registerBeanDefinition("dataAudit", autoConfigurationClient());

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> process(beanFactory, field(TestFields.class, "namedProjectMock")));

        assertTrue(failure.getMessage().contains("Conflicting beans of type"));
        assertTrue(failure.getMessage().contains(SampleClient.class.getName()));
        assertTrue(failure.getMessage().contains("projectMock"));
        assertTrue(failure.getMessage().contains("@JustMock"));
        assertTrue(failure.getMessage().contains("dataAudit"));
        assertTrue(failure.getMessage().contains("LogAutoConfiguration"));
        assertTrue(failure.getMessage().contains("excludeAutoConfiguration"));
        assertTrue(failure.getMessage().contains("remove the redundant"));
    }

    @Test
    void replacesSoleAutoConfigurationBeanWithoutConflict() {
        DefaultListableBeanFactory beanFactory = beanFactory();
        beanFactory.registerBeanDefinition("dataAudit", autoConfigurationClient());

        process(beanFactory, field(TestFields.class, "client"));

        assertTrue(beanFactory.containsBeanDefinition("scopedTarget.dataAudit"));
        assertEquals(1, countLogicalBeansOfType(beanFactory, SampleClient.class));
    }

    @Test
    void failsWhenThreadScopedMockCoexistsWithAutoConfigurationBean() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.register(ThreadScopedMockConfig.class, SampleLogAutoConfiguration.class);
        context.addBeanFactoryPostProcessor(new JustMockPostProcessor(Collections.emptySet()));

        IllegalStateException failure = assertThrows(IllegalStateException.class, context::refresh);
        try {
            assertTrue(failure.getMessage().contains("Conflicting beans of type"));
            assertTrue(failure.getMessage().contains(SampleClient.class.getName()));
            assertTrue(failure.getMessage().contains("@ThreadScopedMock"));
            assertTrue(failure.getMessage().contains("SampleLogAutoConfiguration"));
            assertTrue(failure.getMessage().contains("excludeAutoConfiguration"));
        } finally {
            context.close();
        }
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
    void doesNotReplaceUnrelatedBeanForExplicitName() {
        DefaultListableBeanFactory beanFactory = beanFactory();
        registerCandidate(beanFactory, "explicitMapper", false, true, null);
        Field field = field(TestFields.class, "explicitMapper");

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> process(beanFactory, field));

        assertTrue(failure.getMessage().contains("No bean named 'explicitMapper'"));
        assertTrue(failure.getMessage().contains("Candidates:"));
        assertEquals(SampleClient.class, beanFactory.getType("explicitMapper"));
        assertFalse(beanFactory.containsBeanDefinition("scopedTarget.explicitMapper"));
    }

    @Test
    void doesNotReplaceUnrelatedBeanForFieldNameFallback() {
        DefaultListableBeanFactory beanFactory = beanFactory();
        registerCandidate(beanFactory, "fieldMapper", false, true, null);
        Field field = field(TestFields.class, "fieldMapper");

        process(beanFactory, field);

        assertEquals(SampleClient.class, beanFactory.getType("fieldMapper"));
        String mockBeanName = beanFactory.getBean(JustMockBindings.class).getBeanName(definition(field));
        assertNotEquals("fieldMapper", mockBeanName);
        assertTrue(beanFactory.containsBeanDefinition("scopedTarget." + mockBeanName));
    }

    @Test
    void doesNotReplaceUnrelatedBeanForDefaultNameFallback() {
        DefaultListableBeanFactory beanFactory = beanFactory();
        registerCandidate(beanFactory, "sampleMapper", false, true, null);
        Field field = field(TestFields.class, "mapper");

        process(beanFactory, field);

        assertEquals(SampleClient.class, beanFactory.getType("sampleMapper"));
        String mockBeanName = beanFactory.getBean(JustMockBindings.class).getBeanName(definition(field));
        assertNotEquals("sampleMapper", mockBeanName);
        assertTrue(beanFactory.containsBeanDefinition("scopedTarget." + mockBeanName));
    }

    @Test
    void filtersCandidatesWithSpringQualifierRulesAndPreservesQualifier() {
        DefaultListableBeanFactory beanFactory = beanFactory();
        registerCandidate(beanFactory, "firstClient", false, true, "red");
        registerCandidate(beanFactory, "secondClient", false, true, "blue");
        Field field = field(TestFields.class, "qualifiedClient");

        process(beanFactory, field);

        JustMockDefinition definition = definition(field);
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
        JustMockContextCustomizer first = customizer(field(FirstTest.class, "client"));
        JustMockContextCustomizer equivalent = customizer(field(EquivalentTest.class, "client"));
        JustMockContextCustomizer different = customizer(field(DifferentQualifierTest.class, "client"));

        assertEquals(first, equivalent);
        assertEquals(first.hashCode(), equivalent.hashCode());
        assertNotEquals(first, different);
    }

    @Test
    void contextKeyIgnoresFieldNameAndQualifierWhenExplicitBeanNameSelectsTheTarget() {
        JustMockContextCustomizer first = customizer(field(ExplicitFirstTest.class, "client"));
        JustMockContextCustomizer equivalent = customizer(field(ExplicitEquivalentTest.class, "renamedClient"));

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

    @Test
    void replacesMapperFactoryBeanByNameWhenObjectTypeIsNotVisible() {
        DefaultListableBeanFactory beanFactory = beanFactory();
        AbstractBeanDefinition definition = BeanDefinitionBuilder
                .genericBeanDefinition(MapperFactoryBean.class)
                .addPropertyValue("mapperInterface", SampleMapper.class)
                .getBeanDefinition();
        beanFactory.registerBeanDefinition("sampleMapper", definition);
        assertEquals(0, beanFactory.getBeanNamesForType(SampleMapper.class, true, false).length);

        process(beanFactory, field(TestFields.class, "sampleMapper"));

        assertTrue(beanFactory.containsBeanDefinition("sampleMapper"));
        assertTrue(beanFactory.containsBeanDefinition("scopedTarget.sampleMapper"));
        assertFalse(Arrays.stream(beanFactory.getBeanDefinitionNames())
                .anyMatch(name -> name.endsWith("#0")));
    }

    @Test
    void autoMocksAnnotatedFeignClientsAndSkipsExcludesAndNameOnlyClients() {
        DefaultListableBeanFactory beanFactory = beanFactory();
        beanFactory.registerBeanDefinition("demoLocalClient",
                BeanDefinitionBuilder.genericBeanDefinition(DemoLocalClient.class).getBeanDefinition());
        JustTestProjectFeignAutoMock.register(beanFactory,
                new String[] {"com.just.test.demo.project.fixtures.feign"},
                new Class<?>[] {ExcludedFeignClient.class});

        new JustMockPostProcessor(Collections.emptySet()).postProcessBeanFactory(beanFactory);

        assertTrue(beanFactory.containsBeanDefinition("demoFeignClient"));
        assertTrue(beanFactory.containsBeanDefinition("scopedTarget.demoFeignClient"));
        assertFalse(beanFactory.containsBeanDefinition("excludedFeignClient"));
        assertFalse(beanFactory.containsBeanDefinition("scopedTarget.demoLocalClient"));
        assertEquals(DemoLocalClient.class, beanFactory.getType("demoLocalClient"));
    }

    @Test
    void replacesFeignFactoryBeanWithoutEagerInitWhenAutoMocking() {
        DefaultListableBeanFactory beanFactory = beanFactory();
        GenericBeanDefinition factoryBean = new GenericBeanDefinition();
        factoryBean.setBeanClassName(JustTestProjectFeignAutoMock.FEIGN_CLIENT_FACTORY_BEAN);
        factoryBean.getPropertyValues().add("type", DemoFeignClient.class);
        beanFactory.registerBeanDefinition("demo-feign", factoryBean);
        JustTestProjectFeignAutoMock.register(beanFactory,
                new String[] {"com.just.test.demo.project.fixtures.feign"}, new Class<?>[0]);

        new JustMockPostProcessor(Collections.emptySet()).postProcessBeanFactory(beanFactory);

        assertTrue(beanFactory.containsBeanDefinition("demo-feign"));
        assertTrue(beanFactory.containsBeanDefinition("scopedTarget.demo-feign"));
        assertEquals(DemoFeignClient.class, beanFactory.getType("scopedTarget.demo-feign"));
    }

    @Test
    void explicitJustMockWinsOverAutoFeignMock() {
        DefaultListableBeanFactory beanFactory = beanFactory();
        JustTestProjectFeignAutoMock.register(beanFactory,
                new String[] {"com.just.test.demo.project.fixtures.feign"}, new Class<?>[0]);
        Field field = field(FeignTestFields.class, "demoFeignClient");

        process(beanFactory, field);

        assertEquals("demoFeignClient",
                beanFactory.getBean(JustMockBindings.class).getBeanName(definition(field)));
        assertTrue(beanFactory.containsBeanDefinition("scopedTarget.demoFeignClient"));
        assertEquals(1, countLogicalBeansOfType(beanFactory, DemoFeignClient.class));
    }

    @Test
    void spyRearrangesDefinitionWithoutCreatingTheRealBean() {
        AtomicBoolean created = new AtomicBoolean();
        DefaultListableBeanFactory beanFactory = beanFactory();
        beanFactory.registerBeanDefinition("client", BeanDefinitionBuilder
                .genericBeanDefinition(SampleClient.class, () -> {
                    created.set(true);
                    return new SampleClient();
                })
                .getBeanDefinition());

        processSpy(beanFactory, field(SpyFields.class, "client"));

        String hidden = JustMockPostProcessor.spyTargetBeanName("client");
        assertFalse(created.get());
        assertTrue(beanFactory.containsBeanDefinition(hidden));
        assertFalse(beanFactory.getBeanDefinition(hidden).isAutowireCandidate());
        assertFalse(beanFactory.getBeanDefinition(hidden).isPrimary());
        assertEquals(BeanDefinition.ROLE_INFRASTRUCTURE, beanFactory.getBeanDefinition(hidden).getRole());
        assertTrue(beanFactory.containsBeanDefinition("scopedTarget.client"));
        assertTrue(beanFactory.getBeanDefinition("client").isAutowireCandidate());
        assertFalse(created.get());
    }

    @Test
    void spyKeepsMapperFactoryBeanAsHiddenTarget() {
        DefaultListableBeanFactory beanFactory = beanFactory();
        AbstractBeanDefinition definition = BeanDefinitionBuilder
                .genericBeanDefinition(MapperFactoryBean.class)
                .addPropertyValue("mapperInterface", SampleMapper.class)
                .getBeanDefinition();
        beanFactory.registerBeanDefinition("sampleMapper", definition);

        processSpy(beanFactory, field(SpyFields.class, "sampleMapper"));

        String hidden = JustMockPostProcessor.spyTargetBeanName("sampleMapper");
        assertTrue(beanFactory.containsBeanDefinition(hidden));
        assertEquals(MapperFactoryBean.class,
                ((AbstractBeanDefinition) beanFactory.getBeanDefinition(hidden)).getBeanClass());
        assertFalse(beanFactory.getBeanDefinition(hidden).isAutowireCandidate());
        assertTrue(beanFactory.containsBeanDefinition("scopedTarget.sampleMapper"));
        assertEquals(SampleMapper.class, beanFactory.getType("scopedTarget.sampleMapper"));
    }

    @Test
    void failsWhenJustSpyBeanIsMissing() {
        DefaultListableBeanFactory beanFactory = beanFactory();

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> processSpy(beanFactory, field(SpyFields.class, "client")));

        assertTrue(failure.getMessage().contains("No existing bean"));
        assertTrue(failure.getMessage().contains("does not create one"));
        assertFalse(beanFactory.containsBeanDefinition("client"));
    }

    @Test
    void failsWhenJustSpyAndJustMockDeclareTheSameType() {
        DefaultListableBeanFactory beanFactory = beanFactory();
        registerCandidate(beanFactory, "client", false, true, null);
        registerCandidate(beanFactory, "otherClient", false, true, null);

        IllegalStateException failure = assertThrows(IllegalStateException.class, () ->
                new JustMockPostProcessor(
                        Collections.singleton(definition(field(TestFields.class, "client"))),
                        Collections.singleton(spyDefinition(field(SpyFields.class, "namedClient"))))
                        .postProcessBeanFactory(beanFactory));

        assertTrue(failure.getMessage().contains("Conflicting beans of type"));
        assertTrue(failure.getMessage().contains(SampleClient.class.getName()));
        assertTrue(failure.getMessage().contains("@JustSpy"));
        assertTrue(failure.getMessage().contains("@JustMock"));
        assertTrue(failure.getMessage().contains("Candidates:"));
        assertTrue(beanFactory.containsBeanDefinition("client"));
        assertFalse(beanFactory.containsBeanDefinition("scopedTarget.client"));
    }

    @Test
    void failsWhenJustSpyAndJustMockResolveToTheSameBean() {
        DefaultListableBeanFactory beanFactory = beanFactory();
        beanFactory.registerBeanDefinition("concreteClient",
                BeanDefinitionBuilder.genericBeanDefinition(ConcreteClient.class).getBeanDefinition());

        IllegalStateException failure = assertThrows(IllegalStateException.class, () ->
                new JustMockPostProcessor(
                        Collections.singleton(definition(field(InterfaceMockFields.class, "client"))),
                        Collections.singleton(spyDefinition(field(ConcreteSpyFields.class, "concreteClient"))))
                        .postProcessBeanFactory(beanFactory));

        assertTrue(failure.getMessage().contains("Conflicting beans named 'concreteClient'"));
        assertTrue(failure.getMessage().contains("selected the same bean"));
        assertTrue(failure.getMessage().contains("@JustSpy"));
        assertTrue(failure.getMessage().contains("@JustMock"));
        assertFalse(beanFactory.containsBeanDefinition("scopedTarget.concreteClient"));
    }

    @Test
    void failsWhenJustSpyAndThreadScopedMockDeclareTheSameType() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.register(ThreadScopedMockConfig.class);
        context.addBeanFactoryPostProcessor(new JustMockPostProcessor(
                Collections.<JustMockDefinition>emptySet(),
                Collections.singleton(spyDefinition(field(SpyFields.class, "client")))));

        IllegalStateException failure = assertThrows(IllegalStateException.class, context::refresh);
        try {
            assertTrue(failure.getMessage().contains("Conflicting beans of type"));
            assertTrue(failure.getMessage().contains("@JustSpy"));
            assertTrue(failure.getMessage().contains("@ThreadScopedMock"));
        } finally {
            context.close();
        }
    }

    @Test
    void explicitJustSpyWinsOverAutoFeignMock() {
        DefaultListableBeanFactory beanFactory = beanFactory();
        beanFactory.registerBeanDefinition("demoFeignClient",
                BeanDefinitionBuilder.genericBeanDefinition(DemoFeignClient.class).getBeanDefinition());
        JustTestProjectFeignAutoMock.register(beanFactory,
                new String[] {"com.just.test.demo.project.fixtures.feign"}, new Class<?>[0]);

        processSpy(beanFactory, field(SpyFeignFields.class, "demoFeignClient"));

        String hidden = JustMockPostProcessor.spyTargetBeanName("demoFeignClient");
        assertTrue(beanFactory.containsBeanDefinition(hidden));
        assertEquals(DemoFeignClient.class,
                ((AbstractBeanDefinition) beanFactory.getBeanDefinition(hidden)).getBeanClass());
        assertFalse(beanFactory.getBeanDefinition(hidden).isAutowireCandidate());
        assertTrue(beanFactory.getBeanDefinition("demoFeignClient").isAutowireCandidate());
        assertEquals(1, countAutowireCandidates(beanFactory, DemoFeignClient.class));
    }

    @Test
    void contextKeySeparatesJustSpyFromJustMockAndReusesEquivalentSpies() {
        JustMockContextCustomizer mock = customizer(field(FirstTest.class, "client"));
        JustMockContextCustomizer spy = new JustMockContextCustomizer(
                Collections.<JustMockDefinition>emptySet(),
                Collections.singleton(spyDefinition(field(SpyCacheTest.class, "client"))));
        JustMockContextCustomizer equivalentSpy = new JustMockContextCustomizer(
                Collections.<JustMockDefinition>emptySet(),
                Collections.singleton(spyDefinition(field(EquivalentSpyTest.class, "client"))));

        assertNotEquals(mock, spy);
        assertEquals(spy, equivalentSpy);
        assertEquals(spy.hashCode(), equivalentSpy.hashCode());
    }

    @Test
    void replacesMapperFactoryBeanByDefaultNameWhenObjectTypeIsNotVisible() {
        DefaultListableBeanFactory beanFactory = beanFactory();
        AbstractBeanDefinition definition = BeanDefinitionBuilder
                .genericBeanDefinition(MapperFactoryBean.class)
                .addPropertyValue("mapperInterface", SampleMapper.class)
                .getBeanDefinition();
        beanFactory.registerBeanDefinition("sampleMapper", definition);
        assertEquals(0, beanFactory.getBeanNamesForType(SampleMapper.class, true, false).length);

        Field field = field(TestFields.class, "mapper");
        process(beanFactory, field);

        assertTrue(beanFactory.containsBeanDefinition("sampleMapper"));
        assertTrue(beanFactory.containsBeanDefinition("scopedTarget.sampleMapper"));
        assertFalse(Arrays.stream(beanFactory.getBeanDefinitionNames())
                .anyMatch(name -> name.endsWith("#0")));
        assertEquals("sampleMapper",
                beanFactory.getBean(JustMockBindings.class).getBeanName(definition(field)));
    }

    private JustMockContextCustomizer customizer(Field field) {
        return new JustMockContextCustomizer(Collections.singleton(definition(field)));
    }

    private void process(DefaultListableBeanFactory beanFactory, Field field) {
        new JustMockPostProcessor(Collections.singleton(definition(field)))
                .postProcessBeanFactory(beanFactory);
    }

    private void processSpy(DefaultListableBeanFactory beanFactory, Field field) {
        new JustMockPostProcessor(Collections.<JustMockDefinition>emptySet(),
                Collections.singleton(spyDefinition(field)))
                .postProcessBeanFactory(beanFactory);
    }

    private JustMockDefinition definition(Field field) {
        return JustMockDefinition.forField(field, field.getAnnotation(JustMock.class));
    }

    private JustMockDefinition spyDefinition(Field field) {
        return JustMockDefinition.forSpyField(field, field.getAnnotation(JustSpy.class));
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

    private AbstractBeanDefinition autoConfigurationClient() {
        AbstractBeanDefinition definition = BeanDefinitionBuilder
                .genericBeanDefinition(SampleClient.class).getBeanDefinition();
        definition.setResourceDescription(
                "class path resource [com/acme/LogAutoConfiguration.class]");
        return definition;
    }

    private int countLogicalBeansOfType(DefaultListableBeanFactory beanFactory, Class<?> type) {
        int count = 0;
        for (String name : beanFactory.getBeanNamesForType(type, true, false)) {
            if (!name.startsWith("scopedTarget.")
                    && !name.startsWith(JustMockPostProcessor.SPY_TARGET_PREFIX)) {
                count++;
            }
        }
        return count;
    }

    private int countAutowireCandidates(DefaultListableBeanFactory beanFactory, Class<?> type) {
        int count = 0;
        for (String name : beanFactory.getBeanNamesForType(type, true, false)) {
            if (beanFactory.containsBeanDefinition(name)
                    && beanFactory.getBeanDefinition(name).isAutowireCandidate()) {
                count++;
            }
        }
        return count;
    }

    private static class FeignTestFields {
        @JustMock
        private DemoFeignClient demoFeignClient;
    }

    private static class SpyFeignFields {
        @JustSpy
        private DemoFeignClient demoFeignClient;
    }

    private static class SpyFields {
        @JustSpy
        private SampleClient client;

        @JustSpy(name = "otherClient")
        private SampleClient namedClient;

        @JustSpy
        private SampleMapper sampleMapper;
    }

    private static class SpyCacheTest {
        @JustSpy
        @Qualifier("blue")
        private SampleClient client;
    }

    private static class EquivalentSpyTest {
        @JustSpy
        @Qualifier("blue")
        private SampleClient client;
    }

    private static class InterfaceMockFields {
        @JustMock
        private Client client;
    }

    private static class ConcreteSpyFields {
        @JustSpy
        private ConcreteClient concreteClient;
    }

    private static class TestFields {
        @JustMock
        private SampleClient client;

        @JustMock(name = "projectMock")
        private SampleClient namedProjectMock;

        @JustMock
        private SampleClient secondClient;

        @JustMock(name = "clientAlias")
        private SampleClient explicitAlias;

        @JustMock
        @Qualifier("blue")
        private SampleClient qualifiedClient;

        @JustMock
        @Region("blue")
        private SampleClient regionalClient;

        @JustMock
        private SampleClient scopedClient;

        @JustMock
        private SampleMapper sampleMapper;

        @JustMock
        private SampleMapper mapper;

        @JustMock(name = "explicitMapper")
        private SampleMapper explicitMapper;

        @JustMock
        private SampleMapper fieldMapper;
    }

    private static class FirstTest {
        @JustMock
        @Qualifier("blue")
        private SampleClient client;
    }

    private static class EquivalentTest {
        @JustMock
        @Qualifier("blue")
        private SampleClient client;
    }

    private static class DifferentQualifierTest {
        @JustMock
        @Qualifier("red")
        private SampleClient client;
    }

    private static class ExplicitFirstTest {
        @JustMock(name = "firstClient")
        private SampleClient client;
    }

    private static class ExplicitEquivalentTest {
        @JustMock(name = "firstClient")
        @Qualifier("ignoredForExplicitSelection")
        private SampleClient renamedClient;
    }

    private static class ConcreteTypeTest {
        @JustMock
        private Client client;
    }

    private interface Client {
    }

    private static class ConcreteClient implements Client {
    }

    private static class SampleClient {
    }

    @Configuration
    static class ThreadScopedMockConfig {
        @Bean
        @ThreadScopedMock
        SampleClient mockClient() {
            return mock(SampleClient.class);
        }
    }

    @Configuration
    static class SampleLogAutoConfiguration {
        @Bean
        SampleClient dataAudit() {
            return new SampleClient();
        }
    }

    private interface SampleMapper {
    }

    @Qualifier
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.FIELD, ElementType.ANNOTATION_TYPE})
    private @interface Region {
        String value();
    }
}
