package local.codex.wallpapers;

import android.app.*;
import android.appwidget.*;
import android.content.*;
import android.graphics.*;
import android.os.Bundle;
import android.provider.Settings;
import android.view.*;
import android.widget.*;

public class UsageWidgetConfigActivity extends Activity {
    boolean pinRequested=false; int widgetsBeforePin;
    int widgetId,character;ImageView preview;Double demonstration=null;TextView status;Bitmap rendered;
    public void onCreate(Bundle b){super.onCreate(b);setResult(RESULT_CANCELED);
        widgetId=getIntent().getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,AppWidgetManager.INVALID_APPWIDGET_ID);if(widgetId==AppWidgetManager.INVALID_APPWIDGET_ID && getIntent().getData()!=null){try{widgetId=Integer.parseInt(getIntent().getData().getQueryParameter("widgetId"));}catch(Exception ignored){}}
        if(widgetId!=AppWidgetManager.INVALID_APPWIDGET_ID){AppWidgetProviderInfo info=AppWidgetManager.getInstance(this).getAppWidgetInfo(widgetId);if(info==null||!new ComponentName(this,UsageWidgetProvider.class).equals(info.provider)){finish();return;}}
        character=UsageData.selected(this,widgetId);
        ScrollView scroll=new ScrollView(this);LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(24,50,24,30);scroll.addView(root);setContentView(scroll);
        TechUi.header(root,"USAGE STUDIO / 02","用量组件","角色随额度变化，让进度一眼可见");TechUi.section(root,"01  /  组件预览 · 点击更换人物");
        preview=new ImageView(this);preview.setAdjustViewBounds(true);root.addView(preview,new LinearLayout.LayoutParams(-1,-2));preview.setOnLongClickListener(v->{choose();return true;});preview.setOnClickListener(v->choose());
        Button choice=new Button(this);choice.setText("选择人物 · 三选一");choice.setOnClickListener(v->choose());root.addView(choice);
        LinearLayout expressions=new LinearLayout(this);root.addView(expressions);String[] moods={"开心 ≥80%","平静 40–<80%","疲惫 <40%"};double[] examples={90,60,20};for(int i=0;i<3;i++){final double value=examples[i];Button button=new Button(this);button.setText(moods[i]);button.setTag("segment");button.setTextSize(12);button.setOnClickListener(v->{demonstration=value;for(int j=0;j<expressions.getChildCount();j++)expressions.getChildAt(j).setSelected(((Button)expressions.getChildAt(j)).getText().equals(button.getText()));refresh();});expressions.addView(button,new LinearLayout.LayoutParams(0,-2,1));}
        Button real=new Button(this);real.setText("显示已读取的真实额度");real.setOnClickListener(v->{demonstration=null;for(int j=0;j<expressions.getChildCount();j++)expressions.getChildAt(j).setSelected(false);refresh();});root.addView(real);
        status=new TextView(this);status.setTextSize(14);root.addView(status);TechUi.panel(status);
        TechUi.section(root,"02  /  数据连接");
        Button connect=new Button(this);connect.setText("连接手机 ChatGPT 用量页");connect.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("读取用量页")
            .setMessage("此功能读取 ChatGPT 用量页的额度与更新时间。是否前往系统授予辅助功能权限？详细步骤见使用说明。")
            .setPositiveButton("打开系统设置",(d,w)->startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))).setNegativeButton("取消",null).show());root.addView(connect);
        Button open=new Button(this);open.setText("打开 ChatGPT");open.setOnClickListener(v->{Intent app=getPackageManager().getLaunchIntentForPackage("com.openai.chatgpt");if(app!=null)startActivity(app);else Toast.makeText(this,"未找到 ChatGPT",Toast.LENGTH_SHORT).show();});root.addView(open);
        TechUi.section(root,"03  /  桌面设置");
        Button save=new Button(this);save.setText(widgetId==AppWidgetManager.INVALID_APPWIDGET_ID?"添加到桌面":"保存人物选择");save.setOnClickListener(v->{UsageData.select(this,widgetId,character);if(widgetId!=AppWidgetManager.INVALID_APPWIDGET_ID){UsageWidgetProvider.update(this,widgetId);setResult(RESULT_OK,new Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,widgetId));finish();}else{requestDesktopWidget();}});root.addView(save);
        Button permissions=new Button(this);permissions.setText("桌面添加权限设置");permissions.setOnClickListener(v->openDesktopPermissions());root.addView(permissions);
        Button cloud=new Button(this);cloud.setText("自动同步 · 每 10 分钟检查");cloud.setOnClickListener(v->startActivity(new Intent(this,UsageCloudActivity.class)));root.addView(cloud);
        GuideActivity.addButton(root,2);TechUi.apply(this,root);refresh();
    }
    void openDesktopPermissions(){
        new AlertDialog.Builder(this).setTitle("允许添加桌面小部件")
            .setMessage("前往应用设置管理桌面添加权限。详细步骤见使用说明。")
            .setPositiveButton("打开应用设置",(d,w)->startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.parse("package:"+getPackageName())))).setNegativeButton("取消",null).show();
    }
    void requestDesktopWidget(){
        AppWidgetManager manager=AppWidgetManager.getInstance(this);
        if(!manager.isRequestPinAppWidgetSupported()){openDesktopPermissions();return;}
        widgetsBeforePin=manager.getAppWidgetIds(new ComponentName(this,UsageWidgetProvider.class)).length;
        try{
            pinRequested=manager.requestPinAppWidget(new ComponentName(this,UsageWidgetProvider.class),null,null);
            status.setText(pinRequested?"已向系统提交请求，尚未确认添加。若未弹窗，可从桌面的小部件列表添加。":"系统未接受添加请求，请检查桌面添加权限。");
        }catch(RuntimeException e){pinRequested=false;status.setText("系统未完成添加，请检查桌面添加权限。");android.util.Log.e("CodexWidgetPin","Request failed",e);}
    }
    void checkPinResult(){
        if(!pinRequested)return;pinRequested=false;
        int count=AppWidgetManager.getInstance(this).getAppWidgetIds(new ComponentName(this,UsageWidgetProvider.class)).length;
        status.setText(count>widgetsBeforePin?"系统已创建组件，请回桌面查看。":"尚未检测到新增组件。若没有确认弹窗，可从桌面的小部件列表添加，或查看“桌面添加权限设置”。");
    }
    void choose(){new AlertDialog.Builder(this).setTitle("选择组件人物").setSingleChoiceItems(new String[]{"M5 · 保健老师","L1D-02 · 城市探索","L3D-01 · 静谧书库"},character,(dialog,which)->{character=which;UsageData.select(this,widgetId,character);if(widgetId!=AppWidgetManager.INVALID_APPWIDGET_ID)UsageWidgetProvider.update(this,widgetId);dialog.dismiss();refresh();}).setNegativeButton("取消",null).show();}
    void refresh(){
        if(preview==null)return;org.json.JSONObject data=UsageData.read(this);Bitmap old=rendered;
        rendered=UsageWidgetRenderer.render(this,character,data,demonstration);preview.setImageBitmap(rendered);if(old!=null)old.recycle();
        if(status!=null){
            org.json.JSONObject quota=data.optJSONObject("quota");
            if(quota==null)status.setText("额度尚未接入。可配置自己的云端统计，或使用可选的手机用量页读取。");
            else{String source=quota.optString("source");String provider=data.optString("provider","agent_usage".equals(data.optString("source"))?"Agent":"Codex");
                status.setText("desktop_codex_app_server".equals(source)||"agent_report".equals(source)?"额度来源："+provider+" 云端统计，按最后读取时间显示。":"额度来源：手机 ChatGPT 用量页，按最后读取时间显示。");}
        }
    }
    protected void onResume(){super.onResume();refresh();UsageWidgetProvider.updateAll(this);checkPinResult();}
    protected void onDestroy(){if(preview!=null)preview.setImageDrawable(null);if(rendered!=null)rendered.recycle();super.onDestroy();}
}
