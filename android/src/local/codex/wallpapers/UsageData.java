package local.codex.wallpapers;
import android.content.Context;
import android.os.Bundle;
import android.net.Uri;
import org.json.JSONObject;
final class UsageData {
    static Bundle call(Context c,String method,Bundle input){return c.getContentResolver().call(Uri.parse("content://"+c.getPackageName()+".usage"),method,null,input);}
    static JSONObject read(Context c){try{return new JSONObject(call(c,"read",null).getString("json","{}"));}catch(Exception e){return new JSONObject();}}
    static void write(Context c,JSONObject data)throws Exception{Bundle b=new Bundle();b.putString("json",data.toString());call(c,"write",b);}
    static void phoneQuota(Context c,double value,String reset,long now)throws Exception{Bundle b=new Bundle();b.putDouble("remaining",value);b.putString("reset",reset);b.putLong("now",now);call(c,"quota",b);}
    static Double remaining(JSONObject data){return UsageLocalData.remaining(data);}
    static int selected(Context c,int id){Bundle b=new Bundle();b.putInt("id",id);return call(c,"selected",b).getInt("character");}
    static void select(Context c,int id,int character){Bundle b=new Bundle();b.putInt("id",id);b.putInt("character",character);call(c,"select",b);}
}
