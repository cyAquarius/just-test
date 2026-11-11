package com.just.test.mock.bind;

import com.just.test.annotation.ThreadScopedMock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.mockito.Mockito.mock;

/**
 * 两个全局线程级 mock，外加一个真实 Bean。不同测试类只声明其中一部分 {@code @JustMock}。
 */
@Configuration
public class BindMockConfiguration {

    @Bean
    @ThreadScopedMock
    public GlobalRedis redis() {
        return mock(GlobalRedis.class);
    }

    @Bean
    @ThreadScopedMock
    public GlobalCos cos() {
        return mock(GlobalCos.class);
    }

    @Bean
    public RealClock realClock() {
        return new RealClock() {
            @Override
            public String now() {
                return "live";
            }
        };
    }

    @Bean
    public RedisReader redisReader(GlobalRedis redis) {
        return new RedisReader(redis);
    }

    @Bean
    public CosReader cosReader(GlobalCos cos) {
        return new CosReader(cos);
    }

    @Bean
    public ClockReader clockReader(RealClock realClock) {
        return new ClockReader(realClock);
    }

    public interface GlobalRedis {
        String read();
    }

    public interface GlobalCos {
        String read();
    }

    public interface RealClock {
        String now();
    }

    public static class RedisReader {
        private final GlobalRedis redis;

        public RedisReader(GlobalRedis redis) {
            this.redis = redis;
        }

        public String read() {
            return redis.read();
        }
    }

    public static class CosReader {
        private final GlobalCos cos;

        public CosReader(GlobalCos cos) {
            this.cos = cos;
        }

        public String read() {
            return cos.read();
        }
    }

    public static class ClockReader {
        private final RealClock realClock;

        public ClockReader(RealClock realClock) {
            this.realClock = realClock;
        }

        public String now() {
            return realClock.now();
        }
    }
}
