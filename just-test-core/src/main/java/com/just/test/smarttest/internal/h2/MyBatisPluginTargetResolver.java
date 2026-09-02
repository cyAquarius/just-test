package com.just.test.smarttest.internal.h2;

import org.apache.ibatis.plugin.Plugin;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;

/** 使用 JDK 公共代理 API 解开 MyBatis Plugin 链，避免反射访问 Proxy.h。 */
final class MyBatisPluginTargetResolver {

    private MyBatisPluginTargetResolver() {
    }

    static Object unwrap(Object target) {
        Object current = target;
        while (current != null && Proxy.isProxyClass(current.getClass())) {
            InvocationHandler handler = Proxy.getInvocationHandler(current);
            if (!(handler instanceof Plugin)) {
                break;
            }
            MetaObject plugin = SystemMetaObject.forObject(handler);
            current = plugin.getValue("target");
        }
        return current;
    }
}
