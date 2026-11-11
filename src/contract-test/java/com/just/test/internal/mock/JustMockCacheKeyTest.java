package com.just.test.internal.mock;

import com.just.test.annotation.JustMock;
import com.just.test.annotation.JustSpy;
import com.just.test.annotation.JustTest;
import com.just.test.demo.project.feignautoapp.JustTestApplication;
import com.just.test.demo.project.feignautoapp.bind.demo.DemoFeignBindJustTest;
import com.just.test.demo.project.feignautoapp.bind.other.OtherFeignBindJustTest;
import com.just.test.demo.project.fixtures.feign.ExcludedFeignClient;
import com.just.test.mock.bind.BindMockConfiguration;
import com.just.test.mock.bind.BindMockConfiguration.GlobalCos;
import com.just.test.mock.bind.BindMockConfiguration.GlobalRedis;
import com.just.test.mock.bind.BindMockConfiguration.RealClock;
import com.just.test.mock.bind.cos.CosSubsetBindJustTest;
import com.just.test.mock.bind.real.RealBeanJustMockJustTest;
import com.just.test.mock.bind.redis.RedisSubsetBindJustTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.ContextCustomizer;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class JustMockCacheKeyTest {

    private final JustMockContextCustomizerFactory factory = new JustMockContextCustomizerFactory();

    @Test
    void threadScopedSubsetsShareACacheKeyAndARealBeanDoesNot() {
        ContextCustomizer redis = customizer(RedisSubsetBindJustTest.class);
        ContextCustomizer cos = customizer(CosSubsetBindJustTest.class);
        ContextCustomizer real = customizer(RealBeanJustMockJustTest.class);
        ContextCustomizer bare = customizer(BareBindJustTest.class);

        assertEquals(redis, cos);
        assertEquals(redis.hashCode(), cos.hashCode());
        assertEquals(redis, bare);
        assertNotEquals(redis, real);
    }

    @Test
    void feignAutoMockSubsetsShareACacheKey() {
        ContextCustomizer demo = customizer(DemoFeignBindJustTest.class);
        ContextCustomizer other = customizer(OtherFeignBindJustTest.class);

        assertEquals(demo, other);
        assertEquals(demo.hashCode(), other.hashCode());
        assertNotEquals(demo, customizer(ExcludedFeignJustTest.class));
    }

    @Test
    void qualifierAndUnmatchedNameStayInTheCacheKey() {
        ContextCustomizer redis = customizer(RedisSubsetBindJustTest.class);

        assertNotEquals(redis, customizer(QualifiedRedisJustTest.class));
        assertNotEquals(redis, customizer(MissingNameJustTest.class));
    }

    @Test
    void spiesStayInTheCacheKeyAndDoNotHideAConflictingMock() {
        ContextCustomizer spyRedis = customizer(SpyClockAndRedisJustTest.class);
        ContextCustomizer spyCos = customizer(SpyClockAndCosJustTest.class);

        assertEquals(spyRedis, spyCos);
        assertEquals(spyRedis.hashCode(), spyCos.hashCode());
        assertNotEquals(customizer(SpyRedisJustTest.class), customizer(SpyAndMockRedisJustTest.class));
    }

    private ContextCustomizer customizer(Class<?> testClass) {
        return factory.createContextCustomizer(testClass, Collections.emptyList());
    }

    @JustTest
    @ContextConfiguration(classes = BindMockConfiguration.class)
    static class BareBindJustTest {
    }

    @JustTest
    @ContextConfiguration(classes = BindMockConfiguration.class)
    static class QualifiedRedisJustTest {
        @JustMock
        @Qualifier("blue")
        private GlobalRedis redis;
    }

    @JustTest
    @ContextConfiguration(classes = BindMockConfiguration.class)
    static class MissingNameJustTest {
        @JustMock(name = "missing")
        private GlobalRedis redis;
    }

    @JustTest
    @ContextConfiguration(classes = BindMockConfiguration.class)
    static class SpyClockAndRedisJustTest {
        @JustSpy
        private RealClock realClock;

        @JustMock
        private GlobalRedis redis;
    }

    @JustTest
    @ContextConfiguration(classes = BindMockConfiguration.class)
    static class SpyClockAndCosJustTest {
        @JustSpy
        private RealClock realClock;

        @JustMock
        private GlobalCos cos;
    }

    @JustTest
    @ContextConfiguration(classes = BindMockConfiguration.class)
    static class SpyRedisJustTest {
        @JustSpy
        private GlobalRedis redis;
    }

    @JustTest
    @ContextConfiguration(classes = BindMockConfiguration.class)
    static class SpyAndMockRedisJustTest {
        @JustSpy
        private GlobalRedis redis;

        @JustMock
        private GlobalRedis anotherRedis;
    }

    @JustTest
    @ContextConfiguration(classes = JustTestApplication.class)
    static class ExcludedFeignJustTest {
        @JustMock
        private ExcludedFeignClient excludedFeignClient;
    }
}
