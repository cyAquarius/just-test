package com.just.test.internal.mock;

import com.just.test.spy.finalmethod.FinalMethodSpyCases;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.testkit.engine.EngineExecutionResults;
import org.junit.platform.testkit.engine.EngineTestKit;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FinalMethodSpyFailFastTest {

    @BeforeEach
    void resetSeen() {
        FinalMethodSpyCases.SEEN.set(null);
    }

    @Test
    void concreteFinalInstanceMethodSpyFailsFastWhenBoot2MockMakerIsNotInline() throws Exception {
        Assumptions.assumeTrue(FinalMethodMockGuard.boot2Line());
        Path configHome = Files.createTempDirectory("justtest-subclass-mock-maker");
        Path extension = configHome.resolve("mockito-extensions");
        Files.createDirectories(extension);
        Path makerFile = extension.resolve("org.mockito.plugins.MockMaker");
        Files.write(makerFile, "mock-maker-subclass\n".getBytes(StandardCharsets.UTF_8));
        Path logFile = Files.createTempFile("justtest-final-method-spy", ".log");
        try {
            String classpath = configHome.toAbsolutePath()
                    + File.pathSeparator
                    + System.getProperty("java.class.path");
            ProcessBuilder builder = new ProcessBuilder(
                    javaBinary(),
                    "-cp",
                    classpath,
                    FinalMethodSpySubclassMain.class.getName());
            builder.redirectErrorStream(true);
            builder.redirectOutput(logFile.toFile());
            Process process = builder.start();
            boolean finished = process.waitFor(90, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
            }
            String output = new String(Files.readAllBytes(logFile), StandardCharsets.UTF_8);
            assertTrue(finished, output);
            assertEquals(0, process.exitValue(), output);
        } finally {
            Files.deleteIfExists(makerFile);
            Files.deleteIfExists(extension);
            Files.deleteIfExists(configHome);
            Files.deleteIfExists(logFile);
        }
    }

    @Test
    void concreteFinalInstanceMethodSpyStartsOnBoot3() {
        String version = FinalMethodMockGuard.mockitoVersion();
        Assumptions.assumeTrue(version != null && version.startsWith("5."));
        EngineExecutionResults results = EngineTestKit.engine("junit-jupiter")
                .selectors(DiscoverySelectors.selectClass(FinalMethodSpyCases.class))
                .execute();
        assertEquals(0, results.testEvents().failed().count());
        assertEquals(1, results.testEvents().succeeded().count());
        assertEquals("stub-label", FinalMethodSpyCases.SEEN.get());
    }

    private static String javaBinary() {
        String home = System.getProperty("java.home");
        return home + File.separator + "bin" + File.separator + "java";
    }
}
