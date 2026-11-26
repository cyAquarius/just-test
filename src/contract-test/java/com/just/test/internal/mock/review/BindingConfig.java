package com.just.test.internal.mock.review;

import com.just.test.annotation.ThreadScopedMock;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

public final class BindingConfig {
    private BindingConfig() {
    }

    public interface Repository<T> {
        String kind();
    }

    public static class User {
    }

    public static class Order {
    }

    public interface Mailer {
        String send();
    }

    @SpringBootConfiguration
    public static class Generic {
        @Bean
        Repository<User> users() {
            return () -> "users";
        }

        @Bean
        Repository<Order> orders() {
            return () -> "orders";
        }
    }

    @SpringBootConfiguration
    public static class Named {
        @Bean
        @ThreadScopedMock
        Mailer redis() {
            throw new AssertionError();
        }
    }

    @SpringBootConfiguration
    public static class Aliased {
        @Bean({"mailer", "legacyMailer"})
        @ThreadScopedMock
        Mailer mailer() {
            throw new AssertionError();
        }
    }

    @SpringBootConfiguration
    @ComponentScan(basePackages = "com.just.test.internal.mock.review.excluded.fixtures",
            excludeFilters = @ComponentScan.Filter(type = FilterType.CUSTOM, classes = TypeExcludeFilter.class))
    public static class Excluded {
    }

    @SpringBootConfiguration
    @ComponentScan("com.just.test.internal.mock.review.primary.fixtures")
    public static class PrimaryComponent {
        @Bean
        @ThreadScopedMock
        Mailer secondary() {
            throw new AssertionError();
        }
    }
}
