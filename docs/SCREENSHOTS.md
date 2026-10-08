# App 界面截图

展示版本：v0.29.0。设备：小米 17 Pro Max（2509FPN0BC），Android 16 / API 36，澎湃 OS 3。

五张图片已在真机导出并逐张检查，保存在 [screenshots](screenshots/)，供 README 引用。

| 文件 | 内容 |
| --- | --- |
| [theme-m5.png](screenshots/theme-m5.png) | M5 保健老师主题的 App 内壁纸预览。 |
| [theme-l1d.png](screenshots/theme-l1d.png) | L1D 城市探索主题的 App 内壁纸预览。 |
| [theme-l3d.png](screenshots/theme-l3d.png) | L3D 静谧书库主题的 App 内壁纸预览。 |
| [desktop-widget.png](screenshots/desktop-widget.png) | 组件配置与预览，包括演示额度、重置卡张数与最近到期时间。 |
| [cloud-sync.png](screenshots/cloud-sync.png) | 云同步授权与同步控制，令牌框为空，同步状态为演示文字。 |

## 数据与捕获方式

截图由独立 Android Instrumentation 测试工具启动与发布版相同源码的隔离 QA 应用 Activity，在主线程导出真实 View 的绘制结果，去除系统栏，不是设计稿。三套主题只切换内存中的预览，未修改用户主题偏好或系统壁纸。

组件使用内存中的合成数据和正式渲染器：60% 演示额度、3张演示重置卡、2030年的演示期限及模拟柱状图。演示数据不写入用量快照，也不更新桌面组件。图片中的统计不代表真实账号。

云同步页的 `FLAG_SECURE` 保持启用。测试工具清空令牌输入框，将状态文本替换为演示文字后，仅导出这个 App 自己的视图；不读取、复制或导出已保存的授权。未点击同步、保存授权或清除授权。

测试 runner 与 QA 应用分开安装，只包含捕获工具。QA 应用包名为 `local.codex.wallpapers.qa`，与手机原有 CodeX 分开；测试不访问生产包私有数据，生成后卸载 runner。原始账号快照、手机转储、测试 APK 与本地日志不放入展示目录。

## 重新生成

工具入口是 [capture_documentation.py](../scripts/capture_documentation.py)，测试代码位于 [DocumentationInstrumentation.java](../tests/android/DocumentationInstrumentation.java)。需连接已授权调试的手机，并先构建、安装与当前源码一致的可调试 QA 包。

```bash
python3 scripts/capture_documentation.py --help
python3 scripts/build_android.py --application-id local.codex.wallpapers.qa --debuggable --output build/CodeX-QA.apk
adb -s DEVICE_SERIAL install -r build/CodeX-QA.apk
python3 scripts/capture_documentation.py \
  --sdk /path/to/android-sdk \
  --keystore /path/to/qa.keystore \
  --adb /path/to/adb \
  --serial DEVICE_SERIAL \
  --target-package local.codex.wallpapers.qa \
  --output .local/documentation --run
```

将示例路径和序列号替换为本机值。口令通过环境变量 `CODEX_KEYSTORE_PASSWORD` 提供，不写入脚本或文档。工具通过固定白名单启动三个页面并等待界面创建，已在上述设备自动重跑通过。先输出到 `.local/`，人工核对图片后，只将上表五张 PNG 放入 `docs/screenshots/`。

截图仅展示上述设备上的 App 内渲染效果。桌面组件图不是系统启动器放置截图，主题图不是系统壁纸应用验收；其他品牌与 ROM 的支持范围见 [兼容性说明](COMPATIBILITY.md)。
