package com.just.test.internal.project;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.core.Ordered;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

/**
 * 注册 MapperScan。配置类和自动配置完成解析后，再决定是否补齐 MyBatis 工厂和模板。
 */
final class JustTestProjectMyBatisSupport implements BeanDefinitionRegistryPostProcessor, Ordered {

    static final String SQL_SESSION_FACTORY_BEAN_NAME = "sqlSessionFactory";
    static final String SQL_SESSION_TEMPLATE_BEAN_NAME = "sqlSessionTemplate";
    static final String MAPPER_SCANNER_BEAN_NAME = "justTestMapperScannerConfigurer";
    static final String FACTORY_REGISTRAR_BEAN_NAME = "justTestMyBatisRegistrar";

    private static final Logger log = LoggerFactory.getLogger(JustTestProjectMyBatisSupport.class);
    private static final String SQL_SESSION_FACTORY = "org.apache.ibatis.session.SqlSessionFactory";
    private static final String SQL_SESSION_TEMPLATE = "org.mybatis.spring.SqlSessionTemplate";
    private static final String MAPPER_SCANNER_CONFIGURER = "org.mybatis.spring.mapper.MapperScannerConfigurer";

    private final ClassLoader classLoader;
    private final RootBeanDefinition scanner;

    private JustTestProjectMyBatisSupport(ClassLoader classLoader, RootBeanDefinition scanner) {
        this.classLoader = classLoader;
        this.scanner = scanner;
    }

    static void register(BeanDefinitionRegistry registry, String[] mapperPackages, ClassLoader classLoader) {
        if (registry.isBeanNameInUse(MAPPER_SCANNER_BEAN_NAME)) {
            log.warn("[JustTest] Keeping existing bean '{}'; the @JustTestProject MapperScanner contribution is ignored",
                    MAPPER_SCANNER_BEAN_NAME);
            return;
        }

        // 先声明扫描器，让 MyBatis 自动配置跳过它的默认 Mapper 扫描。
        RootBeanDefinition scanner = new RootBeanDefinition();
        scanner.setBeanClassName(MAPPER_SCANNER_CONFIGURER);
        scanner.getPropertyValues().add("basePackage", StringUtils.arrayToCommaDelimitedString(mapperPackages));
        registry.registerBeanDefinition(MAPPER_SCANNER_BEAN_NAME, scanner);
        registry.registerBeanDefinition(FACTORY_REGISTRAR_BEAN_NAME,
                new RootBeanDefinition(JustTestProjectMyBatisSupport.class,
                        () -> new JustTestProjectMyBatisSupport(classLoader, scanner)));
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) {
        if (ClassUtils.isPresent("org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration", classLoader)
                || ClassUtils.isPresent("com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration",
                        classLoader)) {
            return;
        }
        String[] factoryNames = existingBeanNames(registry, SQL_SESSION_FACTORY_BEAN_NAME, SQL_SESSION_FACTORY);
        if (factoryNames.length == 0) {
            RootBeanDefinition factory = new RootBeanDefinition();
            factory.setBeanClassName(JustTestProjectRegistrar.MYBATIS_SPRING_FACTORY);
            factory.getPropertyValues().add("dataSource", new RuntimeBeanReference("justTestDataSource"));
            registry.registerBeanDefinition(SQL_SESSION_FACTORY_BEAN_NAME, factory);
            factoryNames = new String[] {SQL_SESSION_FACTORY_BEAN_NAME};
        }

        if (factoryNames.length == 1) {
            scanner.getPropertyValues().add("sqlSessionFactoryBeanName", factoryNames[0]);
        }
        if (existingBeanNames(registry, SQL_SESSION_TEMPLATE_BEAN_NAME, SQL_SESSION_TEMPLATE).length == 0) {
            RootBeanDefinition template = new RootBeanDefinition();
            template.setBeanClassName(SQL_SESSION_TEMPLATE);
            if (factoryNames.length == 1) {
                template.getConstructorArgumentValues().addIndexedArgumentValue(
                        0, new RuntimeBeanReference(factoryNames[0]));
            } else {
                template.setAutowireMode(AbstractBeanDefinition.AUTOWIRE_CONSTRUCTOR);
            }
            registry.registerBeanDefinition(SQL_SESSION_TEMPLATE_BEAN_NAME, template);
        }
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
    }

    private String[] existingBeanNames(BeanDefinitionRegistry registry, String name, String type) {
        if (registry.isBeanNameInUse(name)) {
            return new String[] {name};
        }
        if (registry instanceof ConfigurableListableBeanFactory) {
            return ((ConfigurableListableBeanFactory) registry)
                    .getBeanNamesForType(ClassUtils.resolveClassName(type, classLoader), true, false);
        }
        return new String[0];
    }
}
