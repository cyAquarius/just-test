package com.just.test.mock.intercept.fixtures;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class InterceptClockConfiguration {

    @Bean
    public EasternClock easternClock() {
        return new EasternClock();
    }

    @Bean
    public WesternClock westernClock() {
        return new WesternClock();
    }

    @Bean
    public AdvisedClock advisedClock() {
        AdvisedClock target = new AdvisedClock();
        ProxyFactory factory = new ProxyFactory(target);
        factory.setProxyTargetClass(true);
        factory.addAdvice(new MethodInterceptor() {
            @Override
            public Object invoke(MethodInvocation invocation) throws Throwable {
                return "advised-" + invocation.proceed();
            }
        });
        return (AdvisedClock) factory.getProxy();
    }

    @Bean
    public ClockReader clockReader(EasternClock easternClock, WesternClock westernClock, AdvisedClock advisedClock) {
        return new ClockReader(easternClock, westernClock, advisedClock);
    }
}
