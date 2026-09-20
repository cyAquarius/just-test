package com.just.test.smarttest.internal.project;

import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.util.StringUtils;

/**
 * 在 {@code mapperPackages} 非空且 mybatis-spring 可用时，
 * 将 MapperScan / SqlSessionFactory / SqlSessionTemplate 绑到 SmartTest DataSource。
 */
final class SmartTestProjectMyBatisSupport {

    static final String SQL_SESSION_FACTORY_BEAN_NAME = "sqlSessionFactory";
    static final String SQL_SESSION_TEMPLATE_BEAN_NAME = "sqlSessionTemplate";
    static final String MAPPER_SCANNER_BEAN_NAME = "smartTestMapperScannerConfigurer";
    static final String SMART_TEST_DATA_SOURCE = "smartTestDataSource";

    private static final String SQL_SESSION_FACTORY_BEAN =
            "org.mybatis.spring.SqlSessionFactoryBean";
    private static final String SQL_SESSION_TEMPLATE =
            "org.mybatis.spring.SqlSessionTemplate";
    private static final String MAPPER_SCANNER_CONFIGURER =
            "org.mybatis.spring.mapper.MapperScannerConfigurer";

    private SmartTestProjectMyBatisSupport() {
    }

    static void register(BeanDefinitionRegistry registry, String[] mapperPackages) {
        if (!registry.containsBeanDefinition(SQL_SESSION_FACTORY_BEAN_NAME)) {
            RootBeanDefinition factory = new RootBeanDefinition();
            factory.setBeanClassName(SQL_SESSION_FACTORY_BEAN);
            factory.getPropertyValues().add("dataSource",
                    new RuntimeBeanReference(SMART_TEST_DATA_SOURCE));
            registry.registerBeanDefinition(SQL_SESSION_FACTORY_BEAN_NAME, factory);
        }
        if (!registry.containsBeanDefinition(SQL_SESSION_TEMPLATE_BEAN_NAME)) {
            RootBeanDefinition template = new RootBeanDefinition();
            template.setBeanClassName(SQL_SESSION_TEMPLATE);
            template.getConstructorArgumentValues().addIndexedArgumentValue(
                    0, new RuntimeBeanReference(SQL_SESSION_FACTORY_BEAN_NAME));
            registry.registerBeanDefinition(SQL_SESSION_TEMPLATE_BEAN_NAME, template);
        }
        if (!registry.containsBeanDefinition(MAPPER_SCANNER_BEAN_NAME)) {
            RootBeanDefinition scanner = new RootBeanDefinition();
            scanner.setBeanClassName(MAPPER_SCANNER_CONFIGURER);
            scanner.getPropertyValues().add("basePackage",
                    StringUtils.arrayToCommaDelimitedString(mapperPackages));
            scanner.getPropertyValues().add("sqlSessionFactoryBeanName", SQL_SESSION_FACTORY_BEAN_NAME);
            registry.registerBeanDefinition(MAPPER_SCANNER_BEAN_NAME, scanner);
        }
    }
}
