package local.codex.wallpapers;

import android.content.Context;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

final class UsageLocalData {
    static final String FILE="usage-snapshot.json";
    static synchronized JSONObject read(Context context) {
        try(InputStream in=new android.util.AtomicFile(new File(context.getFilesDir(),FILE)).openRead()){return new JSONObject(readText(in));}
        catch(Exception missing){return new JSONObject();}
    }
    static String readText(InputStream in)throws IOException {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buf=new byte[4096];int n;
        while((n=in.read(buf))!=-1){if(bytes.size()+n>1024*1024)throw new IOException("Snapshot too large");bytes.write(buf,0,n);}
        return new String(bytes.toByteArray(),StandardCharsets.UTF_8);
    }
    static synchronized void write(Context context,JSONObject data)throws Exception {
        android.util.AtomicFile file=new android.util.AtomicFile(new File(context.getFilesDir(),FILE));FileOutputStream out=null;
        try{out=file.startWrite();out.write(data.toString().getBytes(StandardCharsets.UTF_8));file.finishWrite(out);}
        catch(Exception e){if(out!=null)file.failWrite(out);throw e;}
    }
    static synchronized void phoneQuota(Context context,double remaining,String reset,long now)throws Exception {
        JSONObject data=read(context);
        JSONObject previous=data.optJSONObject("quota");long resetAt=previous==null?0:previous.optLong("resetsAt");
        JSONObject quota=new JSONObject().put("remaining",remaining).put("window","weekly").put("resetLabel",reset).put("observedAt",now).put("source","phone_chatgpt_usage_page");
        if(resetAt>now){quota.put("resetsAt",resetAt).put("resetSource",previous.optString("resetSource"));}
        data.put("quota",quota);
        write(context,data);
    }
    static Double remaining(JSONObject data) {
        JSONObject q=data.optJSONObject("quota");if(q==null||q.isNull("remaining"))return null;
        double value=q.optDouble("remaining",Double.NaN);return Double.isFinite(value)&&value>=0&&value<=100?value:null;
    }
    static int selected(Context context,int widgetId) {android.content.SharedPreferences prefs=context.getSharedPreferences("usage-widget",0);return Math.max(0,Math.min(2,prefs.getInt("character_"+widgetId,prefs.getInt("character_-1",0))));}
    static void select(Context context,int widgetId,int character) {context.getSharedPreferences("usage-widget",0).edit().putInt("character_"+widgetId,Math.max(0,Math.min(2,character))).apply();}
}
