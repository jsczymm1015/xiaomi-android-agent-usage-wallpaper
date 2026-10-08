package local.codex.wallpapers;

import android.app.*;
import android.appwidget.*;
import android.content.*;
import android.os.Bundle;
import android.graphics.Bitmap;
import android.widget.RemoteViews;

public class UsageWidgetProvider extends AppWidgetProvider {
    static int resource(Context c,String type,String name){return c.getResources().getIdentifier(name,type,c.getPackageName());}
    static void update(Context c,int id){
        AppWidgetManager manager=AppWidgetManager.getInstance(c);AppWidgetProviderInfo info=manager.getAppWidgetInfo(id);
        if(info==null||!new ComponentName(c,UsageWidgetProvider.class).equals(info.provider))return;
        android.util.DisplayMetrics metrics=c.getResources().getDisplayMetrics();
        WidgetBitmapBudget budget=new WidgetBitmapBudget(metrics.widthPixels,metrics.heightPixels);
        org.json.JSONObject data=UsageData.read(c);int character=UsageData.selected(c,id);
        RemoteViews views=base(c,id);boolean animated=budget.frameCount>0;
        if(animated)try{UsageMotion.attach(c,views,character,UsageData.remaining(data),budget);}catch(Exception e){animated=false;views.removeAllViews(resource(c,"id","usage_motion"));android.util.Log.w("AgentWidget","Animation unavailable, using static",e);}
        Bitmap image=UsageWidgetRenderer.render(c,character,data,null,!animated,budget.width,budget.height);
        try{views.setImageViewBitmap(resource(c,"id","usage_image"),image);manager.updateAppWidget(id,views);}
        catch(RuntimeException updateFailure){
            // OEM hosts can enforce stricter limits than the framework. Retry a fresh,
            // static RemoteViews so failed animated children are not retained.
            android.util.Log.w("AgentWidget","Animated widget update rejected; retrying static",updateFailure);
            int width=budget.width,height=budget.height;
            for(int attempt=0;attempt<3;attempt++){
                width=Math.max(1,width/2);height=Math.max(1,height/2);Bitmap fallback=UsageWidgetRenderer.render(c,character,data,null,true,width,height);
                try{RemoteViews safe=base(c,id);safe.setImageViewBitmap(resource(c,"id","usage_image"),fallback);manager.updateAppWidget(id,safe);return;}
                catch(RuntimeException refused){if(attempt==2)android.util.Log.e("AgentWidget","Static widget update also rejected",refused);}
                finally{fallback.recycle();}
            }
        }finally{image.recycle();}
    }
    static RemoteViews base(Context c,int id){
        RemoteViews views=new RemoteViews(c.getPackageName(),resource(c,"layout","usage_widget"));views.removeAllViews(resource(c,"id","usage_motion"));
        Intent edit=new Intent(c,UsageWidgetConfigActivity.class).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id);
        views.setOnClickPendingIntent(android.R.id.background,PendingIntent.getActivity(c,id,edit,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE));return views;
    }
    static void updateAll(Context c){for(int id:AppWidgetManager.getInstance(c).getAppWidgetIds(new ComponentName(c,UsageWidgetProvider.class)))update(c,id);}
    public void onUpdate(Context c,AppWidgetManager m,int[] ids){if(ids!=null)for(int id:ids)update(c,id);}
    public void onAppWidgetOptionsChanged(Context c,AppWidgetManager m,int id,Bundle options){update(c,id);}
    private void remap(Context c,int[] oldIds,int[] newIds){
        if(oldIds==null||newIds==null||oldIds.length!=newIds.length)return;int[] choices=new int[oldIds.length];
        for(int i=0;i<oldIds.length;i++)choices[i]=UsageData.selected(c,oldIds[i]);for(int i=0;i<newIds.length;i++)UsageData.select(c,newIds[i],choices[i]);
    }
    public void onRestored(Context c,int[] oldIds,int[] newIds){remap(c,oldIds,newIds);onUpdate(c,AppWidgetManager.getInstance(c),newIds);}
    public void onDeleted(Context c,int[] ids){for(int id:ids)c.getSharedPreferences("usage-widget",0).edit().remove("character_"+id).apply();}
}
