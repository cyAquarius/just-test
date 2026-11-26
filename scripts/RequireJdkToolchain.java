import java.io.File;
import java.util.Collections;
import java.util.StringJoiner;

import org.apache.maven.toolchain.RequirementMatcherFactory;
import org.apache.maven.toolchain.io.DefaultToolchainsReader;
import org.apache.maven.toolchain.model.ToolchainModel;

final class RequireJdkToolchain {
    public static void main(String[] args) throws Exception {
        File file = new File(args[0]);
        String range = args[1];
        StringJoiner versions = new StringJoiner(" ");
        for (ToolchainModel toolchain : new DefaultToolchainsReader()
                .read(file, Collections.emptyMap()).getToolchains()) {
            if (!"jdk".equals(toolchain.getType())) {
                continue;
            }
            String version = toolchain.getProvides().getProperty("version");
            if (version != null && !version.isEmpty()) {
                if (RequirementMatcherFactory.createVersionMatcher(version).matches(range)) {
                    return;
                }
                versions.add(version);
            }
        }
        System.err.println("dual-jdk-release: " + file + " has no JDK toolchain matching "
                + range + " (found: " + (versions.length() == 0 ? "none" : versions) + ").");
        System.err.println("maven-compiler-plugin 3.15.0 would compile with the current JDK.");
        System.exit(1);
    }
}
