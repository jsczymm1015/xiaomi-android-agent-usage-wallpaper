# 自己的统计仓库与授权

公开 App 仓库用于下载程序；统计必须发布到你自己指定的独立仓库或端点。建议使用私有库。GitHub、GitLab、Gitea 或其他 Git 远端均可用于电脑发布，前提是手机能通过受支持的 HTTPS 原始 JSON 地址和认证方式读取文件。

## 可直接交给 Agent 的建库指令

将下面文本中的服务、账户和本地路径替换为自己的实际值，再交给已授权的 Agent。它只创建新的私有统计库，不改变已有仓库可见性。

```text
请在我已认证的【Git 服务：GitHub / GitLab / Gitea / 其他】账户【我的账户或组织】下，
新建名为 agent-usage-data 的私有仓库，用于我自己的 Android 用量组件。

我授权你仅为这次用途创建该私有库、初始化 main 分支并克隆到【统计库本地路径】。
先确认当前账户、名称是否占用和私有仓库能力；名称已占用时停止，告诉我现有库情况，
不要改写、删除或公开已有仓库。组织内创建需要按该组织现有权限进行。

初始化简短 README 和 .gitignore，并将初始化提交推送到 main。README 说明只保存 schemaVersion 1 的 usage-latest.json。
忽略 token、密钥、登录文件、原始接口响应、聊天、日志和临时文件。
暂不生成虚假的账户统计，不将示例数据作为正式 usage-latest.json 上传。
不要使用壁纸 App 的公开源码仓库保存我的统计。

电脑写入使用本机 Git 凭据管理器。协助我找出手机只读访问所需的最小授权步骤，
令牌由我在服务网页创建并直接输入手机；不要要求我把令牌发到聊天，也不写进 URL 或源码。
给我仓库远端 URL、实际分支、JSON 文件路径、手机原始 JSON 配置方式，核对仓库保持私有。
没有工具、认证或权限时，完成能做的本地准备并明确报告剩余手动步骤，不假报建库成功。
这一步不要创建或启用定时任务。
```

也可在服务网页手动创建 Private 仓库，初始化分支并克隆。发布工具要求当前分支已有至少一份推送过的提交，例如初始化 README；不直接发布空仓库，也不代为推送已有的无关本地提交。用于发布的本地目录应独立且工作区干净。已有改动或冲突先处理，不要强推。

## 电脑端写权限

给电脑现有 Git 登录对这个统计库的必要写权限，使用服务推荐的凭据管理器、SSH Agent 或本机认证方式。先在本地检查远端 `origin` 和当前分支，确保它们是自己的统计库。

Agent 生成真实统计后在 App 工具目录执行：

```bash
python3 desktop/snapshot_schema.py /path/to/private/usage-latest.json
python3 desktop/publish_snapshot.py --repo-dir /path/to/your-stats-repo --input /path/to/private/usage-latest.json
```

仓库文件默认 `usage-latest.json`。使用不同目录、文件或分支时，以发布工具实际参数和手机配置为准。推送成功还需确认远端文件可以读取；电脑远端成功不等于手机已经拉取。

## 手机只读授权

电脑的写凭据不复制到手机。手机只需读取你指定的文件和仓库。令牌由你在服务网页创建、限定资源与有效期，然后直接填入 App；不要放在聊天、截图、URL、Git 或提示词里。

| 服务 | 手机配置 |
| --- | --- |
| GitHub | 优先用“GitHub 仓库”模式。细粒度令牌仅选择自己的统计仓库，仓库 Contents 设为 Read-only；必要元数据读权限由服务自动提供。资源所有者是你自己的用户或组织。见 [GitHub 文件读取权限](https://docs.github.com/en/rest/repos/contents#get-repository-content)。 |
| GitLab | 选择“HTTPS 原始 JSON”；使用能读取该私有项目文件 API 的受限令牌，授权方式 `PRIVATE-TOKEN`。可用权限取决于令牌类型和实例规则。见 [GitLab 文件 API 与读取范围](https://docs.gitlab.com/api/repository_files/)。 |
| Gitea | 选择“HTTPS 原始 JSON”；使用该私有仓库的受限读取令牌，授权方式 `token`。具体权限按服务版本选择。见 [Gitea API 认证与权限](https://docs.gitea.com/1.27/development/api-usage/)。 |
| 其他服务 | 提供最终 HTTPS JSON 地址，且支持无认证、Bearer、PRIVATE-TOKEN 或 token 之一；只授予所需读权限。 |

组织 SSO、实例策略和不同服务版本可能有额外步骤。遇到 401 / 403 / 404 先核对服务授权和文件地址，不通过扩大到所有仓库写权限解决。

## 原始 JSON 地址示例

下面都是占位示例，需要替换。App 拒绝重定向，填写最终返回 JSON 内容的 HTTPS API 地址，不填仓库网页或登录页。不要把令牌放入查询参数。

| 服务 | 地址示例 |
| --- | --- |
| GitHub | App“GitHub 仓库”模式直接填自己的 owner / repo / ref / file，该模式按 GitHub 原始内容格式发送请求。 |
| GitLab 文件 API | `https://gitlab.example.com/api/v4/projects/PROJECT_ID/repository/files/usage-latest.json/raw?ref=main`；见 [官方原始文件端点](https://docs.gitlab.com/api/repository_files/#retrieve-a-raw-file-from-a-repository)。 |
| Gitea 文件 API | `https://gitea.example.com/api/v1/repos/OWNER/REPO/raw/usage-latest.json?ref=main`；以实例 Swagger 文件 API 为准，入口见 [官方 API 使用说明](https://docs.gitea.com/1.27/development/api-usage/)。 |
| 自己的 HTTPS 端点 | `https://stats.example.com/usage-latest.json` |

GitLab 项目可使用数字 ID；文件位于子目录时路径按服务要求进行 URL 编码。Gitea 的实际 API 形式以你的实例为准；若请求重定向，按服务文档找到最终端点再配置。网页可见不表示返回的是原始 JSON，先检查内容类型和响应内容。

## 维护

令牌过期后在手机更新，泄露后立即在服务端撤销再重新创建。手机清除配置只删除本地授权；撤销服务端令牌、删除仓库和停止电脑计划任务都需在相应位置操作。

本 App 没有代替用户创建的公共统计库，也不会把不同账户的额度合并。用户更换手机数据源后需要重新同步，避免显示旧源统计。
