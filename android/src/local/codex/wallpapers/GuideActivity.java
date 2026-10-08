package local.codex.wallpapers;
import android.app.Activity;
import android.os.Bundle;
import android.widget.*;

/** Bundled offline instructions contain no account or repository defaults. */
public class GuideActivity extends Activity {
    public void onCreate(Bundle state){super.onCreate(state);
        ScrollView scroll=new ScrollView(this);LinearLayout root=new LinearLayout(this);root.setOrientation(1);scroll.addView(root);setContentView(scroll);
        TechUi.header(root,"FIELD GUIDE / 04","使用说明","Android 11+ · 主题与 Agent 用量");
        Button back=new Button(this);back.setText("返回上一页");back.setOnClickListener(v->finish());root.addView(back);
        Button tools=new Button(this);tools.setText("导出部署工具与定时任务提示词");tools.setOnClickListener(v->ReleaseResources.export(this));root.addView(tools);
        String[] titles={"主题与标准壁纸","息屏与背屏素材","桌面组件","数据来源与未知值","自己的仓库与自动同步","授权与隐私","兼容性与常见问题"};
        String[] bodies={
            "首页选择 M5、城市探索或静谧书库，再切换桌面、锁屏、息屏素材或背屏素材预览。预览不会改变系统配置。\n\nM5 桌面和锁屏使用标准 Android 动态壁纸服务；其他两款使用静态原图。设置桌面／锁屏会进入系统支持的流程；独立锁屏动态壁纸是否可用取决于系统。系统未提供相应选项时，可保存 PNG 再手动选择。",
            "本 App 只预览和导出息屏／背屏图片，不启动厂商专用设置，不识别或控制第二块屏幕。\n\n1. 切换到息屏素材或背屏素材。\n2. 点击保存当前预览 PNG 到相册。\n3. M5 另外支持保存循环 GIF：息屏为轻微呼吸，背屏为点头；城市探索和静谧书库当前为静态素材。\n4. 文件位于相册 AgentWallpapers；在手机支持的自定义图片页面自行选择。\n\nGIF 是实际多帧循环文件，采用缩小尺寸与有限色彩以便导出。系统是否接受 PNG／GIF、是否播放动画由机型决定；没有息屏或背屏的设备仍能保存图片，不能获得硬件上不存在的功能。保存成功不代表系统已应用。",
            "进入用量桌面组件，选择三个人物之一，点击添加到桌面并接受系统确认。已有组件可点击编辑，保存人物选择。也可以在桌面长按空白处，从系统小部件列表添加。\n\n开心、平静、疲惫按钮只作表情预览，不修改真实数据。剩余≥80% 显示开心，40%至不足80%显示平静，不足40%显示疲惫。\n\n动画帧尺寸和数量按屏幕预算调整；宿主拒绝动画更新时回退静态图片。各品牌桌面的尺寸、刷新、省电及动画策略仍可能不同。",
            "左侧是提供方报告的剩余额度；周期按数据标注为日、周、月或其他周期。数据未知显示破折号，不编造账号额度。右侧是每日 Token 统计，单位为1亿；缺失日期显示破折号，不当作零。接入前若显示模拟图会明确标注。\n\n可用重置卡显示数量和最近到期时间。到期后显示待更新，不直接把全部卡片视为失效。数据没有提供这些字段时显示未知。\n\nCodex 官方账号读取器可以读取支持的账号统计；其他 Agent／LLM 必须根据统一 JSON 格式提供它实际有权获取的数据。模型自己无法观察的统计应填 null。\n\n可选的 ChatGPT 用量页读取仅在你启用辅助功能并打开用量与限额页面后识别页面，不替代云端脚本。",
            "数据链路为：你的电脑／Agent → 你的统计仓库 → 手机。登录某个 LLM 账号不会自动授予本 App 用量访问权限。\n\n先阅读仓库提供的使用说明、部署创建指令和定时任务提示词。由你在自己的 GitHub 或其他 Git 仓库配置脚本与授权；建议用独立私有统计仓库，避免公开账号统计。\n\n在自动同步页面输入自己的仓库地址或支持的 JSON URL，以及该端点所需的只读授权。配置并启用后，手机用 Android 后台任务定期拉取；手动立即同步可重新下载最新 JSON。手机拉取数据，不在 Android 内执行任意仓库代码。\n\n电脑定时上传与手机后台拉取分别设置。任务频率由你选择，电脑需开机联网；Android 省电和网络会造成延迟，不保证准点。停止并清除手机授权不会取消电脑任务或删除仓库。",
            "手机仅配置你拥有访问权限的仓库或 HTTPS 端点。GitHub 建议使用只允许指定统计仓库、Contents 只读权限的细粒度令牌；电脑推送端另行授予所需的最小写权限。\n\n不要把令牌、私钥、Cookie、原始接口响应或聊天内容放入提示词、截图或公开仓库。手机授权使用 Android Keystore 加密保存；授权输入页面禁止系统截图。撤销或过期后需重新配置。\n\n统一统计 JSON 只接受用量字段。脚本不会替你创建默认账号仓库，不默认连接作者的私有仓库。应用内的主题图片与离线说明无需网络。",
            "最低 Android 11，使用标准 Android 壁纸、相册和小部件接口，基础功能不限品牌。并非对每台 Android 设备的全部功能作保证：系统必须提供对应的动态壁纸／组件／自定义图片能力；其他品牌需要实机验证。\n\n添加组件未弹窗：从桌面的小部件列表添加，并查看系统应用权限。\n\n同步 401／403／404：检查自己的端点地址、仓库路径、分支、读取权限与授权有效期。\n\n数据不更新：检查数据截至时间、电脑脚本是否运行、手机网络与后台省电限制。同步失败会保留旧数据；旧数据不代表实时额度。\n\n息屏／背屏没有动画：检查系统是否支持 GIF。可改用 PNG；本 App 不接管厂商息屏或背屏服务。"
        };
        TextView[] sections=new TextView[titles.length];LinearLayout toc=new LinearLayout(this);toc.setOrientation(1);root.addView(toc);
        for(int i=0;i<titles.length;i++){final int n=i;Button link=new Button(this);link.setText(String.format("%02d  /  %s",i+1,titles[i]));link.setOnClickListener(v->scroll.smoothScrollTo(0,sections[n].getTop()));toc.addView(link);}
        for(int i=0;i<titles.length;i++){TechUi.section(root,String.format("%02d  /  %s",i+1,titles[i]));sections[i]=(TextView)root.getChildAt(root.getChildCount()-1);TextView body=new TextView(this);body.setText(bodies[i]);body.setTextSize(15);body.setTextIsSelectable(true);root.addView(body);}
        TechUi.apply(this,root);int section=getIntent().getIntExtra("section",-1);if(section>=0&&section<sections.length)scroll.post(()->scroll.scrollTo(0,sections[section].getTop()));
    }
    static void addButton(LinearLayout root,int section){Button help=new Button(root.getContext());help.setText("使用说明  ↗");help.setOnClickListener(v->root.getContext().startActivity(new android.content.Intent(root.getContext(),GuideActivity.class).putExtra("section",section)));root.addView(help);}
}
