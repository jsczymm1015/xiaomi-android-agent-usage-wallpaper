# macOS 开发与电脑同步

普通用户可直接安装 [Release APK](https://github.com/jsczymm1015/xiaomi-android-agent-usage-wallpaper/releases/tag/v0.29.0)，无需 Android SDK。电脑统计工具使用 Python 3.10+ 和 Git；构建 APK 才需要 JDK 与 Android SDK。Windows / Linux 也可使用 Python 入口，路径和凭据管理按本机设置。

## 构建环境

克隆本仓库并进入根目录。安装 Python 3.10+、JDK 17+，在 Android Studio 的 SDK Manager 安装 Platform 35、Build Tools 35.0.0；USB 安装另需 Platform Tools。阅读并接受你使用的 SDK 许可。

```bash
export JAVA_HOME="$(/usr/libexec/java_home -v 17)"
export ANDROID_HOME="$HOME/Library/Android/sdk"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH"
python3 scripts/package_setup.py
python3 scripts/run_tests.py
python3 scripts/build_android.py --check
python3 scripts/build_android.py
```

构建工具识别 `ANDROID_HOME`、`ANDROID_SDK_ROOT`，或使用 `--sdk PATH`。采用 javac → D8 → aapt2 → zipalign → apksigner；结果为 `build/CodeX.apk`。SDK/JDK 不随仓库或 APK 分发。

## 签名与升级

不指定签名文件时，构建工具在 `.local/` 生成仅供本机测试的密钥。相同包名覆盖升级需要相同签名；换电脑或自行构建后，不能假设新密钥与现装版本一致。

使用自己已有的签名密钥时，通过环境变量交给构建工具，不把口令写入命令历史：

```bash
read -s CODEX_KEYSTORE_PASSWORD
export CODEX_KEYSTORE_PASSWORD
python3 scripts/build_android.py --keystore "/private/path/app.keystore"
unset CODEX_KEYSTORE_PASSWORD
```

库口令与密钥口令不同时，另设 `CODEX_KEY_PASSWORD`；需要时用 `--key-alias` 指定别名。密钥通过自己的安全方式保管，不进入 Git 或工具包。没有兼容签名时，先处理数据迁移需求，不要自动卸载用户手机 App。

## USB 安装

手机解锁并打开 USB 调试，接受电脑调试授权；部分系统另需允许 USB 安装。

```bash
adb devices
python3 scripts/install_android.py --serial YOUR_ADB_SERIAL
```

设备应显示 `device`。安装弹窗由用户在手机确认，脚本不修改开发者选项。标准静态 / 动态壁纸的最终应用范围由手机系统选择器决定。

## 电脑统计与 Git 发布

先按 [建库说明](REPOSITORY_SETUP.md) 建立自己的私有统计仓库并克隆到本机。Git 远端可以是 GitHub、GitLab、Gitea 或其他 Git 服务；发布程序复用本机 Git 认证，不要求手机拥有写权限。

Codex 登录由你在本机完成，其他 Agent 使用各自的正常授权数据源。只读取有权访问的可信统计；不复制登录文件到 Git，也不根据模型回答猜测额度。工具发现不到 Codex 时可设置 `CODEX_BINARY` 为本机程序路径。没有对应官方可读数据的指标输出 `null`。

Codex 可用内置导出器读取本机官方统计：

```bash
python3 desktop/export_usage.py --output /path/to/private/usage-latest.json
```

让 Agent 根据 [统一格式](FEATURES.md) 生成私有位置的 `usage-latest.json`，然后：

```bash
python3 desktop/snapshot_schema.py /path/to/private/usage-latest.json
python3 desktop/publish_snapshot.py --repo-dir /path/to/your-stats-repo --input /path/to/private/usage-latest.json
```

发布路径、分支和手机读取路径必须保持一致；发布命令的默认文件为仓库内 `usage-latest.json`，可用 `--file`、`--remote`、`--branch` 明确指定。数据仓库需至少有一份已推送的初始提交，工作区改动和未推送提交会被拒绝。不要把本 App 的公开源码检出当作个人统计仓库。

## 可选定时任务

把 [定时提示词 TXT](../prompts/scheduled-usage-task.txt) 中的路径、数据源和仓库替换成自己的配置，手动跑通一次后，再明确授权所用调度器创建任务。可按需要每 30 分钟执行一次；没有固定个人时段要求。

TXT 不会自行创建任务；不同 Agent 是否能定时运行取决于其调度能力。需要常驻电脑的任务只能在电脑开机、联网并可读取本机授权时执行。迁移电脑时先确认旧任务状态，避免两台机器同时写入同一文件。

手机与电脑独立调度：电脑负责读取并上传，手机负责拉取和刷新。仅升级手机不能使旧电脑导出器自动支持新的字段；两端应使用相容的格式。验证结果以 [验证记录](VERIFICATION.md) 为准。
