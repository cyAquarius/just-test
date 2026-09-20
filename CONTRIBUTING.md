# 贡献指南

本仓库是可复用的 JustTest 测试工具库。协作默认使用中文，代码标识符与命令保持英文。产品说明见 [README.zh-CN.md](README.zh-CN.md)。

## 不要用 `[skip ci]` 跳过结构或依赖变更

GitHub 的 `[skip ci]` / `[ci skip]` 会跳过本仓库的验证与发布工作流。下列变更**不得**带该标记：

- 模块结构、`pom.xml`、依赖或 BOM 版本
- `just-test-core` / `just-test-boot2` / `just-test-boot3` 的源码
- 契约测试、消费烟测、CI workflow

不要用 `[skip ci]` 代替本应执行的验证。纯 Markdown / `LICENSE` 路径的 push 仍会跑双产品线 verify。

## SNAPSHOT 与正式版本

当前坐标为 `io.github.cyaquarius:*:1.0.0-SNAPSHOT`，供本地 `mvn install` 与契约/烟测使用。正式版发到 Maven Central，只上传 `just-test-boot2` 与 `just-test-boot3`（core 打进 JAR），不走 GitHub Packages。

发布正式版本时：

1. `main` 上验证通过，不要使用 `[skip ci]`；
2. 打 tag（例如 `v1.0.0`）并 push。CI 会去掉 SNAPSHOT、签名并上传 Central；
3. 仓库需已配置 Secrets：`CENTRAL_USERNAME`、`CENTRAL_PASSWORD`、`GPG_PRIVATE_KEY`、`GPG_PASSPHRASE`。

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

两条线共用 `src/contract-test`。Java 17 下的 `mvn clean install` 不能替代真实 Java 8 验证。本地同时用 Java 8/17 Toolchains 构建时可用 `-Pdual-jdk-release`。
