package com.just.test.internal.project;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 在不破坏现有 {@code @Primary} 语义的前提下，为 JustTest 主数据源 / 事务管理器
 * 注册应用常用的次要 Bean 名（默认 {@code dataSource}、{@code transactionManager}），
 * 以及 {@code @JustTestProject} 声明的兼容别名。
 *
 * <p>目标名已被 Bean 定义或别名占用时跳过，避免与双数据源契约冲突，并输出 INFO 诊断。</p>
 */
public class JustTestProjectDataSourceAliasPostProcessor implements BeanDefinitionRegistryPostProcessor {

    static final String BEAN_NAME = "justTestDataSourceAliasPostProcessor";
    static final String DATA_SOURCE_BEAN = "justTestDataSource";
    static final String TRANSACTION_MANAGER_BEAN = "justTestTransactionManager";
    static final String DEFAULT_DATA_SOURCE_ALIAS = "dataSource";
    static final String DEFAULT_TRANSACTION_MANAGER_ALIAS = "transactionManager";

    private static final Logger log = LoggerFactory.getLogger(JustTestProjectDataSourceAliasPostProcessor.class);

    private final String[] extraDataSourceAliases;
    private final String[] extraTransactionManagerAliases;

    public JustTestProjectDataSourceAliasPostProcessor() {
        this(new String[0], new String[0]);
    }

    public JustTestProjectDataSourceAliasPostProcessor(String[] extraDataSourceAliases,
                                                       String[] extraTransactionManagerAliases) {
        this.extraDataSourceAliases = uniqueNonBlank(extraDataSourceAliases);
        this.extraTransactionManagerAliases = uniqueNonBlank(extraTransactionManagerAliases);
    }

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        for (String alias : mergeWithDefault(DEFAULT_DATA_SOURCE_ALIAS, extraDataSourceAliases)) {
            aliasIfAbsent(registry, DATA_SOURCE_BEAN, alias);
        }
        for (String alias : mergeWithDefault(DEFAULT_TRANSACTION_MANAGER_ALIAS, extraTransactionManagerAliases)) {
            aliasIfAbsent(registry, TRANSACTION_MANAGER_BEAN, alias);
        }
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        // aliases are registered against the definition registry
    }

    static void register(BeanDefinitionRegistry registry) {
        register(registry, new String[0], new String[0]);
    }

    static void register(BeanDefinitionRegistry registry,
                         String[] dataSourceAliases,
                         String[] transactionManagerAliases) {
        if (registry.containsBeanDefinition(BEAN_NAME)) {
            return;
        }
        RootBeanDefinition definition = new RootBeanDefinition(JustTestProjectDataSourceAliasPostProcessor.class);
        definition.getConstructorArgumentValues().addIndexedArgumentValue(0, uniqueNonBlank(dataSourceAliases));
        definition.getConstructorArgumentValues().addIndexedArgumentValue(1, uniqueNonBlank(transactionManagerAliases));
        registry.registerBeanDefinition(BEAN_NAME, definition);
    }

    static void aliasIfAbsent(BeanDefinitionRegistry registry, String canonicalName, String alias) {
        if (!StringUtils.hasText(canonicalName) || !StringUtils.hasText(alias)) {
            return;
        }
        String trimmedAlias = alias.trim();
        if (!registry.containsBeanDefinition(canonicalName) || canonicalName.equals(trimmedAlias)) {
            return;
        }
        if (alreadyAliasedToCanonical(registry, canonicalName, trimmedAlias)) {
            return;
        }
        if (registry.containsBeanDefinition(trimmedAlias)) {
            log.info("[JustTest] Skipping alias '{}' for '{}': a bean definition with that name already exists",
                    trimmedAlias, canonicalName);
            return;
        }
        if (registry.isAlias(trimmedAlias)) {
            log.info("[JustTest] Skipping alias '{}' for '{}': an alias with that name already exists",
                    trimmedAlias, canonicalName);
            return;
        }
        registry.registerAlias(canonicalName, trimmedAlias);
    }

    static String[] uniqueNonBlank(String[] values) {
        if (values == null || values.length == 0) {
            return new String[0];
        }
        Set<String> unique = new LinkedHashSet<String>();
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                unique.add(value.trim());
            }
        }
        return unique.toArray(new String[0]);
    }

    static String[] mergeWithDefault(String defaultAlias, String[] extras) {
        Set<String> names = new LinkedHashSet<String>();
        if (StringUtils.hasText(defaultAlias)) {
            names.add(defaultAlias.trim());
        }
        if (extras != null) {
            for (String extra : extras) {
                if (StringUtils.hasText(extra)) {
                    names.add(extra.trim());
                }
            }
        }
        return names.toArray(new String[0]);
    }

    private static boolean alreadyAliasedToCanonical(BeanDefinitionRegistry registry,
                                                     String canonicalName,
                                                     String alias) {
        String[] aliases = registry.getAliases(canonicalName);
        for (int i = 0; i < aliases.length; i++) {
            if (alias.equals(aliases[i])) {
                return true;
            }
        }
        return false;
    }
}
