package com.just.test.spy.mapper;

import com.just.test.annotation.CaseSource;
import com.just.test.annotation.JustSpy;
import com.just.test.annotation.JustTest;
import com.just.test.context.CaseContext;
import com.just.test.lifecycle.JustTestLifecycle;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.mapper.MapperFactoryBean;
import org.springframework.aop.framework.Advised;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;

import javax.sql.DataSource;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;

/**
 * MapperFactoryBean 暴露的是 MyBatis JDK 代理。{@code @JustSpy} 用 delegatesTo 委托给该代理，
 * 因此未 stub 的方法会查到 prepare.yaml / H2，而不是 Mockito 的默认返回值。
 * Feign 客户端同样是接口上的 JDK 代理，委托路径与这里一致；纯 JDK 代理另见
 * {@code com.just.test.spy.jdkproxy.JustSpyJdkProxyJustTest}。
 */
@JustTest
@Execution(ExecutionMode.SAME_THREAD)
@ContextConfiguration(classes = JustSpyMapperJustTest.TestConfiguration.class)
class JustSpyMapperJustTest implements JustTestLifecycle {

    private static final AtomicBoolean STUBBED_CASE_RAN = new AtomicBoolean();

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private ProductGateway productGateway;

    @JustSpy
    private ProductMapper productMapper;

    @CaseSource
    void partialStubUsesPrepareDataAndDoesNotLeak(CaseContext context) {
        assertRealMapperIsHidden();
        assertSame(productMapper, unwrap(applicationContext.getBean(ProductMapper.class)));

        if ("stubbed".equals(context.getString("mode"))) {
            doReturn(42).when(productMapper).countProducts();
            assertEquals("Coffee", productGateway.findName(1L));
            assertEquals(42, productGateway.countProducts());
            STUBBED_CASE_RAN.set(true);
            return;
        }

        assertTrue(STUBBED_CASE_RAN.get(), "clean case must run after the stubbed case on this thread");
        assertEquals("Coffee", productGateway.findName(1L));
        assertEquals(1, productGateway.countProducts());
    }

    private void assertRealMapperIsHidden() {
        ConfigurableListableBeanFactory factory =
                ((ConfigurableApplicationContext) applicationContext).getBeanFactory();
        boolean hidden = false;
        for (String name : factory.getBeanDefinitionNames()) {
            if (name.startsWith("justSpy.target.")) {
                hidden = true;
                assertFalse(factory.getBeanDefinition(name).isAutowireCandidate());
            }
        }
        assertTrue(hidden);
    }

    private static Object unwrap(Object bean) {
        try {
            if (bean instanceof Advised) {
                return ((Advised) bean).getTargetSource().getTarget();
            }
            return bean;
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    public interface ProductMapper {
        @Select("SELECT name FROM demo_product WHERE id = #{id}")
        String findName(@Param("id") long id);

        @Select("SELECT COUNT(*) FROM demo_product")
        int countProducts();
    }

    static class ProductGateway {
        private final ProductMapper productMapper;

        ProductGateway(ProductMapper productMapper) {
            this.productMapper = productMapper;
        }

        String findName(long id) {
            return productMapper.findName(id);
        }

        int countProducts() {
            return productMapper.countProducts();
        }
    }

    @Configuration
    static class TestConfiguration {
        @Bean
        SqlSessionFactory sqlSessionFactory(DataSource dataSource) throws Exception {
            SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
            factoryBean.setDataSource(dataSource);
            return factoryBean.getObject();
        }

        @Bean
        MapperFactoryBean<ProductMapper> productMapper(SqlSessionFactory sqlSessionFactory) {
            MapperFactoryBean<ProductMapper> factoryBean = new MapperFactoryBean<ProductMapper>();
            factoryBean.setMapperInterface(ProductMapper.class);
            factoryBean.setSqlSessionFactory(sqlSessionFactory);
            return factoryBean;
        }

        @Bean
        ProductGateway productGateway(ProductMapper productMapper) {
            return new ProductGateway(productMapper);
        }
    }
}
