package local.codex.wallpapers;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.*;
import android.os.Handler;
import java.util.*;

/** Reads only the observed ChatGPT usage page; no network, credentials, or chat logging. */
public class UsagePageService extends AccessibilityService {
    final Handler handler=new Handler();String previous="";long lastSaved;
    final Runnable readPage=this::capture;
    public void onAccessibilityEvent(AccessibilityEvent event){
        if(event.getPackageName()==null||!"com.openai.chatgpt".contentEquals(event.getPackageName()))return;
        handler.removeCallbacks(readPage);handler.postDelayed(readPage,700);
    }
    void capture(){
        AccessibilityNodeInfo root=getRootInActiveWindow();if(root==null)return;
        try{
            if(root.getPackageName()==null||!"com.openai.chatgpt".contentEquals(root.getPackageName()))return;
            ArrayList<String> labels=new ArrayList<>();collect(root,labels,0);
            Double value=UsagePolicy.phoneWeeklyRemaining(labels);if(value==null)return;
            String reset="";for(String s:labels)if(s.startsWith("重置时间：")||s.startsWith("Resets ")){reset=s;break;}
            String key=value+"|"+reset;long now=System.currentTimeMillis()/1000;
            if(!key.equals(previous)||now-lastSaved>=60){UsageData.phoneQuota(this,value,reset,now);previous=key;lastSaved=now;UsageWidgetProvider.updateAll(this);}
        }catch(Exception e){android.util.Log.e("CodexUsage","Cannot save quota snapshot",e);}
        finally{root.recycle();}
    }
    void collect(AccessibilityNodeInfo n,List<String> out,int depth){if(depth>40||out.size()>2000)return;CharSequence text=n.getText();if(text!=null&&!text.toString().trim().isEmpty())out.add(text.toString().trim());for(int i=0;i<n.getChildCount();i++){AccessibilityNodeInfo child=n.getChild(i);if(child!=null){collect(child,out,depth+1);child.recycle();}}}
    protected void onServiceConnected(){handler.postDelayed(readPage,700);}
    public void onInterrupt(){handler.removeCallbacks(readPage);}
    public void onDestroy(){handler.removeCallbacks(readPage);super.onDestroy();}
}
