# 安装与使用

数据链路：你的 Agent / LLM → 你的统计 Git 仓库或 HTTPS JSON → 手机 CodeX → 桌面组件。公开源码仓库只分发 App、工具和文档。

## 1. 下载并安装 APK

在 [v0.29.0 Release](https://github.com/jsczymm1015/xiaomi-android-agent-usage-wallpaper/releases/tag/v0.29.0) 下载 APK，需要时对照 Release 的 SHA-256 校验文件。手机最低 Android 11；按系统提示允许安装当前下载来源的应用，安装后打开 CodeX。

标准壁纸和组件无需绑定仓库即可预览。预览中的演示数字不是账户统计；尚未同步真实数据时，不把演示当作实际额度。

## 2. 建立自己的统计仓库

按 [仓库与授权](REPOSITORY_SETUP.md) 在自己的 GitHub、GitLab、Gitea 或其他服务创建独立私有统计库。里面只保存白名单 JSON，不放聊天、登录信息或密钥。不要将自己的统计推送到本 App 的公开源码仓库。

电脑需要该统计库的写权限，手机只需要读权限。两端分别授权，互不复制凭据。若你主动选择公开 JSON，则任何人都可能读取这些统计，App 无法将公开数据变成私密。

## 3. 准备电脑工具与 Agent

在 App“使用说明”点击“导出部署工具与定时任务提示词”，文件保存到 `Downloads/AgentUsageSetup/agent-tools-v0.29.zip`。把 ZIP 传到自己的电脑并解压，或直接克隆本源码仓库。电脑准备 Python 3.10+、Git，并按所用 Agent 的方式正常登录；Git 远端认证由本机凭据管理器处理。

将自己的统计库克隆到独立目录。工具目录与统计库目录是不同的路径。下面示例中，替换为自己的位置，不要原样使用占位路径：

```bash
git clone YOUR_STATS_GIT_REMOTE /path/to/your-stats-repo
python3 desktop/snapshot_schema.py /path/to/private/usage-latest.json
python3 desktop/publish_snapshot.py --repo-dir /path/to/your-stats-repo --input /path/to/private/usage-latest.json
```

`usage-latest.json` 先由你的 Agent 根据可信来源生成；字段要求见 [格式说明](FEATURES.md)。最后两条命令分别校验与发布，不会替你登录或获得统计。统计库需已初始化并至少有一份推送过的提交，例如新建时的 README。工作区改动、冲突和未推送提交应先由你处理。

Codex 用户可先读取本机已登录的官方统计，再校验和发布；在工具目录运行：

```bash
python3 desktop/export_usage.py --output /path/to/private/usage-latest.json
```

其他 Agent 使用同一 JSON 格式，自行接入其可信统计来源。没有权限或没有可读统计时，先解决数据源问题，不用猜测值填表。离线包中的 `examples/usage-example.json` 是合成演示，不可当作真实账户数据上传。

## 4. 手动验证，再配置定时任务

打开 [scheduled-usage-task.txt](../prompts/scheduled-usage-task.txt)，填入工具目录、私有输出文件和统计仓库本地路径。把完整文本交给 Codex、其他 Agent 或 LLM，先执行一轮生成、校验、Git 推送和远端核对。

确认手动流程成功后，再明确要求所用调度器按自己的计划运行，例如每 30 分钟一次。提示词只规定每轮做什么；TXT 和 App 本身不会在电脑建立任务。没有定时能力的 LLM 可以生成统计，但仍需要你选择实际调度方式。

电脑停机、登录失效或上游统计不可读时，任务可能失败；手机会保留最后成功数据。查看数据截至时间判断新旧，不将后台任务存在视为实时保证。

## 5. 配置手机数据源

打开用量组件页面中的云同步。首次配置不预填其他人的账户或仓库。

### GitHub 仓库模式

选择“GitHub 仓库”，填写：

| 字段 | 填写内容 |
| --- | --- |
| 用户或组织 | 你的 GitHub 用户或组织。 |
| 仓库名称 | 你自己创建的统计库名称。 |
| 分支或提交 | 实际保存 JSON 的分支，默认 `main`；固定提交不会跟随新上传更新。 |
| JSON 文件路径 | 仓库中的文件路径，默认 `usage-latest.json`，区分大小写。 |
| 授权方式 | 公开库可选“无认证”；私有库选“Bearer”，在手机输入对此仓库的只读令牌。 |

### HTTPS 原始 JSON 模式

选择“HTTPS 原始 JSON”，填写最终直接返回 JSON 的地址。可使用 GitLab / Gitea 等服务的原始文件 API；不同服务地址形式见 [仓库说明](REPOSITORY_SETUP.md)。不要填写仓库主页、网页预览或会跳转到登录页的地址。

| 服务需求 | 授权选项 |
| --- | --- |
| 公开 JSON | 无认证（公开 JSON） |
| GitHub 或接受 Bearer 的服务 | Bearer（GitHub / 通用） |
| 接受 GitLab 访问令牌的原始文件 API | PRIVATE-TOKEN（GitLab） |
| 接受 Gitea 访问令牌的原始文件 API | token（Gitea） |

服务不支持这些认证方式时，需要由自己提供可兼容的最终 HTTPS JSON 端点。App 拒绝重定向，不能通过交互登录页面读取数据。令牌只在手机填写，不发送到聊天或写进 URL。

离线包还包含电脑端原始 JSON 拉取脚本，用于自行检查最终端点或其他工作流：

```bash
python3 desktop/pull_snapshot.py --url "https://stats.example.com/usage-latest.json" --output /path/to/private/readback.json
```

私有源通过 `--auth bearer`、`--auth private_token` 或 `--auth token` 选择认证，并由本机安全地设置 `AGENT_USAGE_TOKEN` 环境变量；不要把令牌值放在命令参数或日志里。该脚本不替代 APK 内的自动拉取。

点击“保存配置并启用自动同步”，再点“立即强制同步”检查。成功后查看数据截至时间和组件。每次保存配置都会清除原有统计与版本缓存，以免混用账户；人物和主题选择保留。同一私有源重新保存时，也必须重新输入只读令牌，App 不回显或自动沿用原令牌。

## 6. 添加组件与设置素材

在组件页选择角色，添加到桌面，并按系统确认。不同桌面的添加、尺寸和刷新方式可能不同。额度颜色与表情不表示电池电量；重置卡最近期限不代表所有卡同时到期。

首页选择主题后，桌面、锁屏使用系统标准壁纸操作。息屏 / 背屏是素材预览：导出 PNG，M5 可选 GIF，然后在手机自己的设置中选择文件。没有背屏的手机仍可导出；系统不支持 GIF 时用 PNG。导出成功不表示已经应用为系统息屏或背屏。

## 停止同步与故障排查

点击“停止自动同步并清除配置”停止手机检查并删除本机保存的数据源授权；最后成功统计暂时保留，重新配置时清除。该操作不删除统计仓库，也不取消电脑计划任务；电脑端需在原调度器单独停用。

| 情况 | 检查方法 |
| --- | --- |
| 401 / 403 | 令牌是否有效、是否被组织策略限制、读权限是否覆盖指定仓库。 |
| 404 | 用户、仓库、分支、文件路径是否正确；私有库无权访问也可能返回 404。 |
| URL 被拒绝 | 是否为 HTTPS，是否会重定向；改填服务提供的最终原始 JSON/API 地址。 |
| JSON 校验失败 | 在电脑运行校验工具，检查字段类型和未知值；不要把 HTML 错误页当 JSON。 |
| 手机有旧数据 | 先看截至时间，再检查电脑是否推送成功；点击强制同步区分手机连接和上游未更新。 |
| 后台没有准时刷新 | 检查网络、省电和后台限制；约 10 分钟是目标间隔，系统可延迟。 |
| 有效期已到 / 重置卡待更新 | 等待可信的新统计；不要将其他卡全部清零或延后旧期限。 |
| 覆盖安装签名不匹配 | 自行构建和 Release 可能签名不同；先确认数据迁移方案，不直接卸载绕过。 |

[格式说明](FEATURES.md) · [兼容性](COMPATIBILITY.md) · [Mac 开发](MACOS.md)

## 随包的 Git 代码拉取脚本

工具包包含 `desktop/pull_repository.py`。若需要定时拉取代码，先用自己的 Git 账号与凭据管理器克隆目标代码仓库，再运行：

```bash
python3 desktop/pull_repository.py --repo-dir /path/to/your-code-repo
```

脚本在电脑执行，只下载并快进到当前分支的远端版本；不执行仓库代码。默认读取该检出的 `origin`，也可用 `--remote`、`--branch` 指定已配置的远端和当前分支。它会拒绝本地改动、分歧、未推送提交和包含凭据的远端 URL。启用 Git LFS、自定义内容过滤器或外部文件监视器的检出需手动拉取。需要重复拉取时，由你授权的调度器按所需周期执行此命令。手机的后台任务负责拉取统计 JSON。
