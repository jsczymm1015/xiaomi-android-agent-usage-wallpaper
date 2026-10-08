package local.codex.wallpapers;

import android.app.Activity;
import android.app.WallpaperManager;
import android.content.Intent;
import android.content.ComponentName;
import android.os.Bundle;
import android.view.View;
import android.graphics.Canvas;
import android.widget.*;
import android.net.Uri;
import android.graphics.Bitmap;

public class MainActivity extends Activity {
    int mode=0;
    Preview preview;
    TextView applied;Button gifExport;
    public void onCreate(Bundle state){super.onCreate(state);UsageCloud.schedule(this);
        LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(18,42,18,18);
        TechUi.header(root,"PERSONAL SPACE / 01","CodeX","主题壁纸与 Agent 用量控制中心");
        TechUi.section(root,"角色主题");
        LinearLayout themes=new LinearLayout(this);root.addView(themes);
        String[] themeLabels={"M5\n保健老师","L1D\n城市探索","L3D\n静谧书库"};
        for(int i=0;i<3;i++){final int chosen=i;Button item=new Button(this);item.setText(themeLabels[i]);item.setTag("theme");item.setSelected(i==selectedTheme());item.setOnClickListener(v->{getSharedPreferences("wallpaper",0).edit().putInt("character",chosen).apply();for(int j=0;j<themes.getChildCount();j++)themes.getChildAt(j).setSelected(j==chosen);preview.invalidate();updateExportState();});themes.addView(item,new LinearLayout.LayoutParams(0,TechUi.dp(this,78),1));}
        TechUi.section(root,"01  /  主题预览");
        preview=new Preview();preview.setBackground(TechUi.box(TechUi.PANEL,0xff44517b,TechUi.dp(this,20)));preview.setClipToOutline(true);root.addView(preview,new LinearLayout.LayoutParams(-1,TechUi.dp(this,310)));

        LinearLayout modes=new LinearLayout(this);root.addView(modes);
        String[] labels={"桌面","锁屏","息屏素材","背屏素材"};
        for(int i=0;i<4;i++){final int m=i;Button b=new Button(this);b.setText(labels[i]);b.setTag("segment");b.setSelected(i==mode);b.setOnClickListener(v->{mode=m;for(int j=0;j<modes.getChildCount();j++)modes.getChildAt(j).setSelected(j==m);preview.started=android.os.SystemClock.uptimeMillis();preview.invalidate();updateExportState();});modes.addView(b,new LinearLayout.LayoutParams(0,-2,1));}
        TechUi.section(root,"02  /  桌面与锁屏");
        LinearLayout actions=new LinearLayout(this);root.addView(actions);
        Button home=new Button(this);home.setText("设置桌面");home.setOnClickListener(v->openWallpaper(false));actions.addView(home,new LinearLayout.LayoutParams(0,-2,1));
        Button lock=new Button(this);lock.setText("设置锁屏");lock.setOnClickListener(v->openWallpaper(true));actions.addView(lock,new LinearLayout.LayoutParams(0,-2,1));
        Button both=new Button(this);both.setText("设置桌面和锁屏");both.setOnClickListener(v->{if(selectedTheme()>0){applyStatic(WallpaperManager.FLAG_SYSTEM|WallpaperManager.FLAG_LOCK);return;}getSharedPreferences("wallpaper",0).edit().putString("requested","both").apply();openPicker(CharacterWallpaper.class,"请在系统弹窗选择“桌面和锁屏”。设置后桌面为午后，锁屏为月夜。");});root.addView(both);
        TechUi.section(root,"03  /  导出图片素材");
        TextView mediaTip=new TextView(this);mediaTip.setText("息屏和背屏仅提供图片素材。保存后，在支持自定义图片的系统页面自行选择；是否支持 GIF 由手机系统决定。");root.addView(mediaTip);
        Button export=new Button(this);export.setText("保存当前预览 PNG 到相册");export.setOnClickListener(v->exportCurrent(false));root.addView(export);
        gifExport=new Button(this);gifExport.setOnClickListener(v->exportCurrent(true));root.addView(gifExport);updateExportState();
        Button exportAll=new Button(this);exportAll.setText("保存当前主题四张 PNG 到相册");exportAll.setOnClickListener(v->exportAll());root.addView(exportAll);
        applied=new TextView(this);applied.setTextSize(12);root.addView(applied);TechUi.panel(applied);
        GuideActivity.addButton(root,0);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.addView(root);setContentView(scroll);TechUi.apply(this,root);
    }
    int selectedTheme(){return Math.max(0,Math.min(Scene.IDS.length-1,getSharedPreferences("wallpaper",0).getInt("character",0)));}
    void applyStatic(int flags){final int chosen=selectedTheme();new Thread(()->{Scene scene=new Scene(this);scene.override=chosen;try{WallpaperManager wm=WallpaperManager.getInstance(this);for(int m=0;m<2;m++){int flag=m==0?WallpaperManager.FLAG_SYSTEM:WallpaperManager.FLAG_LOCK;if((flags&flag)==0)continue;Bitmap b=scene.read(scene.imageAsset(m));try{wm.setBitmap(b,null,true,flag);}finally{b.recycle();}}android.util.Log.i("CodexWallpaper","STATIC_APPLIED theme="+Scene.IDS[chosen]+" flags="+flags);runOnUiThread(()->{Toast.makeText(this,"已设置 "+Scene.NAMES[chosen],Toast.LENGTH_LONG).show();onResume();});}catch(Exception e){android.util.Log.e("CodexWallpaper","Static apply failed",e);runOnUiThread(()->Toast.makeText(this,"设置失败，请从相册手动设置："+e.getMessage(),Toast.LENGTH_LONG).show());}finally{scene.close();}},"StaticWallpaperApply").start();}
    void openWallpaper(boolean lock){
        if(selectedTheme()>0){applyStatic(lock?WallpaperManager.FLAG_LOCK:WallpaperManager.FLAG_SYSTEM);return;}
        mode=lock?1:0;preview.invalidate();updateExportState();getSharedPreferences("wallpaper",0).edit().putString("requested",lock?"lock":"home").apply();
        openPicker(lock?LockWallpaper.class:CharacterWallpaper.class,lock?"请在系统弹窗选择“锁屏”。":"请在系统弹窗选择“桌面”。");
    }
    void openPicker(Class<?> service,String tip){
        try{Intent intent=new Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER);intent.putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,new ComponentName(this,service));
            Toast.makeText(this,tip,Toast.LENGTH_LONG).show();startActivity(intent);
        }catch(Exception e){Toast.makeText(this,"设置页面无法打开："+e.getMessage(),Toast.LENGTH_LONG).show();}
    }
    void updateExportState(){if(gifExport==null)return;boolean eligible=selectedTheme()==0&&mode>=2;gifExport.setEnabled(eligible);gifExport.setText(eligible?"保存当前 M5 素材循环 GIF 到相册":selectedTheme()>0?"当前主题为静态 · 可保存 PNG":"切换到 M5 息屏或背屏素材以保存 GIF");}
    void exportCurrent(boolean animated){final int chosen=selectedTheme(),current=mode;new Thread(()->{
        Scene scene=new Scene(this);scene.override=chosen;
        try{String kind=new String[]{"home","lock","ambient","rear"}[current];String name=Scene.IDS[chosen]+"-"+kind+(animated?"-loop.gif":".png");
            if(animated)WallpaperMedia.gif(this,scene,current,name);else WallpaperMedia.png(this,scene,current,name);
            getSharedPreferences("wallpaper",0).edit().putBoolean("media_saved",true).apply();runOnUiThread(()->{Toast.makeText(this,"已保存到相册 AgentWallpapers："+name+"。请在支持的系统页面自行选用。",Toast.LENGTH_LONG).show();onResume();});
        }catch(Exception e){android.util.Log.e("AgentWallpaper","Media export failed",e);runOnUiThread(()->Toast.makeText(this,"保存失败："+e.getMessage(),Toast.LENGTH_LONG).show());}
        finally{scene.close();}
    },"WallpaperMediaExport").start();}
    String componentName(android.app.WallpaperInfo info){
        if(info==null)return "静态或系统壁纸";
        ComponentName component=info.getComponent();
        if(component.getPackageName().equals(getPackageName()))return "M5 动态壁纸";
        return "其他系统／应用壁纸";
    }
    protected void onResume(){super.onResume();if(applied==null)return;
        try{WallpaperManager wm=WallpaperManager.getInstance(this);android.app.WallpaperInfo home=wm.getWallpaperInfo();
            String homeState=componentName(home),lockState="系统未提供独立状态";
            if(android.os.Build.VERSION.SDK_INT>=34)lockState=componentName(wm.getWallpaperInfo(WallpaperManager.FLAG_LOCK));
            boolean saved=getSharedPreferences("wallpaper",0).getBoolean("media_saved",false);
            applied.setText("系统绑定：桌面 · "+homeState+"；锁屏 · "+lockState+"\n息屏／背屏素材 · "+(saved?"已保存到相册，系统应用状态不读取":"可导出 PNG；M5 另提供循环 GIF"));
            android.util.Log.i("CodexWallpaper","BINDING home="+(home==null?"static":home.getComponent())+" lock="+lockState);
        }catch(Exception e){applied.setText("系统绑定状态暂无法读取，请查看实际桌面和锁屏。");android.util.Log.w("CodexWallpaper","binding read failed",e);}
    }
    void exportAll(){final int chosen=selectedTheme();new Thread(()->{
        Scene scene=new Scene(this);scene.override=chosen;
        try{String[] kinds={"home","lock","aod","rear"};for(int m=0;m<4;m++){WallpaperMedia.png(this,scene,m,Scene.IDS[chosen]+"-"+kinds[m]+".png");}getSharedPreferences("wallpaper",0).edit().putBoolean("media_saved",true).apply();runOnUiThread(()->{Toast.makeText(this,"已保存到相册 AgentWallpapers",Toast.LENGTH_LONG).show();onResume();});}
        catch(Exception e){android.util.Log.e("CodexWallpaper","export failed",e);runOnUiThread(()->Toast.makeText(this,"保存失败："+e.getMessage(),Toast.LENGTH_LONG).show());}
        finally{scene.close();}
    },"WallpaperExport").start();}
    class Preview extends View {
        final Scene scene=new Scene(MainActivity.this);long started=android.os.SystemClock.uptimeMillis();
        Preview(){super(MainActivity.this);}
        protected void onDraw(Canvas c){c.drawColor(TechUi.PANEL);int width=mode==3?976:1200,height=mode==3?596:2608;float scale=Math.min(getWidth()/(float)width,getHeight()/(float)height);c.save();c.translate((getWidth()-width*scale)/2,(getHeight()-height*scale)/2);c.scale(scale,scale);c.clipRect(0,0,width,height);scene.draw(c,width,height,mode,android.os.SystemClock.uptimeMillis()-started);c.restore();if(scene.selected()==0)postInvalidateDelayed(33);}
        protected void onDetachedFromWindow(){scene.close();super.onDetachedFromWindow();}
    }
}
