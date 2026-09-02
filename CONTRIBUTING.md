# 贡献指南

本仓库是可复用的 SmartTest 测试工具库。协作默认使用中文，代码标识符与命令保持英文。产品说明见 [README.zh-CN.md](README.zh-CN.md)。

## 不要用 `[skip ci]` 跳过结构或依赖变更

GitHub 的 `[skip ci]` / `[ci skip]` 会跳过本仓库的验证与发布工作流。下列变更**不得**带该标记：

- 模块结构、`pom.xml`、依赖或 BOM 版本
- `just-test-core` / `just-test-boot2` / `just-test-boot3` 的源码
- 契约测试、消费烟测、CI workflow

不要用 `[skip ci]` 代替文档-only 发布跳过。纯 Markdown / `LICENSE` 路径的 push 仍会跑双产品线 verify；发布工作流只在确认没有非文档文件变更时跳过 GitHub Packages 发布。

## SNAPSHOT 与正式版本

当前坐标为 `1.0.0-SNAPSHOT`。`main` 上的非文档提交在 verify 通过后发布 SNAPSHOT 到 GitHub Packages。

发布正式版本时：

1. 去掉 `-SNAPSHOT`，并同步 README 中的版本示例；
2. 在真实 JDK 上跑通 Boot 2（Java 8 + Spring Boot 2.7.18）与 Boot 3（Java 17 + Spring Boot 3.5.16）；
3. 打 tag（例如 `v1.0.0`）并合并到 `main`，不要使用 `[skip ci]`。

## 如何构建双产品线

消费烟测使用独立 POM，不要折入父 reactor。

仅有 Java 8 时可验证 Boot 2：

```bash
JAVA_HOME=/path/to/jdk8 PATH="$JAVA_HOME/bin:$PATH" \
  mvn --batch-mode --no-transfer-progress -pl just-test-boot2 -am clean install
JAVA_HOME=/path/to/jdk8 PATH="$JAVA_HOME/bin:$PATH" \
  mvn --batch-mode --no-transfer-progress -f smoke-tests/boot2/pom.xml clean test
```

Java 17 环境可验证 Boot 3：

```bash
JAVA_HOME=/path/to/jdk17 PATH="$JAVA_HOME/bin:$PATH" \
  mvn --batch-mode --no-transfer-progress -pl just-test-boot3 -am clean install
JAVA_HOME=/path/to/jdk17 PATH="$JAVA_HOME/bin:$PATH" \
  mvn --batch-mode --no-transfer-progress -f smoke-tests/boot3/pom.xml clean test
```

两条线共用 `src/contract-test`。Java 17 下的 `mvn clean install` 不能替代真实 Java 8 验证。发布使用 `-Pdual-jdk-release`，需要 Java 8/17 Maven Toolchains。
