package com.just.test.internal.mock;

import org.mockito.Mockito;
import org.mockito.internal.configuration.plugins.Plugins;
import org.mockito.plugins.InlineMockMaker;
import org.mockito.plugins.MockMaker;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URISyntaxException;
import java.security.CodeSource;
import java.util.Locale;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

/**
 * Boot 2 默认的 subclass MockMaker 盖不住具体类上的 public / protected final 实例方法，
 * 这些方法会在未初始化的 mock 上执行。只在 Boot 2 且当前 MockMaker 不是 inline 时失败，
 * 并给出配置指引。不安装、不切换 MockMaker。Mockito 5（Boot 3）不走这条失败。
 *
 * <p>适用前提（#140）：本守卫在 Boot 2 使用 Mockito 4.x、且当前 maker 是预期的
 * subclass 或 inline 实现时才可靠。Boot 2 线用版本是否以 {@code 4.} 开头来判断，
 * inline 用 {@link InlineMockMaker} 或类名包含 {@code inline} 来判断。
 * 消费方把 Boot 2 的 Mockito 覆盖成 2.x / 3.x 时版本判断为假，守卫不会执行。
 * 类名里碰巧带 {@code inline} 的非 inline maker，或类名不含 {@code inline}
 * 且未实现 {@link InlineMockMaker} 的 inline maker，都会被分错，守卫可能漏报。
 * 这里不探测 MockMaker 能力，JustTest 也不选定 MockMaker。</p>
 */
final class FinalMethodMockGuard {

    private FinalMethodMockGuard() {
    }

    static void check(Class<?> mockType) {
        if (!boot2Line()) {
            return;
        }
        if (shouldFail(mockType, inlineMockMaker())) {
            throw failure(mockType);
        }
    }

    /**
     * {@code inline} 为 true 时表示当前 MockMaker 已是 inline。
     * 测试可以传入假设值，不必改全局 MockMaker。
     */
    static boolean shouldFail(Class<?> mockType, boolean inline) {
        if (!boot2Line() || inline || !concreteClass(mockType)) {
            return false;
        }
        return hasRelevantFinalInstanceMethod(mockType);
    }

    /**
     * Boot 2 线的版本代理：Mockito 实现版本以 {@code 4.} 开头。
     * 覆盖成 Mockito 2.x / 3.x 时返回 false，{@link #check(Class)} 直接返回。
     */
    static boolean boot2Line() {
        String version = mockitoVersion();
        return version != null && version.startsWith("4.");
    }

    static String mockitoVersion() {
        Package pkg = Mockito.class.getPackage();
        if (pkg != null && pkg.getImplementationVersion() != null) {
            return pkg.getImplementationVersion();
        }
        if (pkg != null && pkg.getSpecificationVersion() != null) {
            return pkg.getSpecificationVersion();
        }
        return manifestVersion();
    }

    /**
     * 查询当前 MockMaker，不改插件、不创建 mock，也不探测 maker 能不能 mock final。
     * 实现 {@link InlineMockMaker} 时视为 inline。类名包含 {@code inline} 只是启发式，
     * 自定义 MockMaker 可能因此被漏判或误判。
     */
    static boolean inlineMockMaker() {
        MockMaker maker = Plugins.getMockMaker();
        if (maker instanceof InlineMockMaker) {
            return true;
        }
        return maker.getClass().getName().toLowerCase(Locale.ROOT).indexOf("inline") >= 0;
    }

    static boolean hasRelevantFinalInstanceMethod(Class<?> type) {
        if (type == null) {
            return false;
        }
        Class<?> current = type;
        while (current != null && current != Object.class) {
            for (Method method : current.getDeclaredMethods()) {
                if (relevant(method)) {
                    return true;
                }
            }
            current = current.getSuperclass();
        }
        return false;
    }

    private static String manifestVersion() {
        try {
            CodeSource source = Mockito.class.getProtectionDomain().getCodeSource();
            if (source == null || source.getLocation() == null) {
                return null;
            }
            File file = new File(source.getLocation().toURI());
            if (!file.isFile()) {
                return null;
            }
            JarFile jar = new JarFile(file);
            try {
                Manifest manifest = jar.getManifest();
                if (manifest == null) {
                    return null;
                }
                String version = manifest.getMainAttributes().getValue("Implementation-Version");
                if (version == null || version.length() == 0) {
                    version = manifest.getMainAttributes().getValue("Bundle-Version");
                }
                return version;
            } finally {
                jar.close();
            }
        } catch (IOException ex) {
            return null;
        } catch (URISyntaxException ex) {
            return null;
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static boolean concreteClass(Class<?> type) {
        return type != null
                && !type.isInterface()
                && !type.isPrimitive()
                && !type.isArray()
                && !Modifier.isAbstract(type.getModifiers());
    }

    private static boolean relevant(Method method) {
        int modifiers = method.getModifiers();
        if (!Modifier.isFinal(modifiers) || Modifier.isStatic(modifiers)) {
            return false;
        }
        return Modifier.isPublic(modifiers) || Modifier.isProtected(modifiers);
    }

    private static IllegalStateException failure(Class<?> mockType) {
        return new IllegalStateException("[JustMock] " + mockType.getName()
                + " is a concrete class with public or protected final instance methods,"
                + " and the current MockMaker is not inline."
                + " Those methods cannot be overridden, so they run on the mock instance."
                + " Configure the inline mock maker in"
                + " src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker"
                + " (the file contains mock-maker-inline), or add the mockito-inline dependency"
                + " that matches mockito-core."
                + " JustTest does not select a MockMaker.");
    }
}
