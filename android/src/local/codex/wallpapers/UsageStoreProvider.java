package local.codex.wallpapers;
import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import org.json.JSONObject;

/** Private IPC boundary: only this process owns snapshots and character preferences. */
public class UsageStoreProvider extends ContentProvider {
    public boolean onCreate(){return true;}
    public synchronized Bundle call(String method,String arg,Bundle b){
        Context c=getContext();Bundle out=new Bundle();
        try {
            switch(method){
                case "read":out.putString("json",UsageLocalData.read(c).toString());break;
                case "write":UsageLocalData.write(c,new JSONObject(b.getString("json")));break;
                case "cloud":UsageCloud.merge(c,new JSONObject(b.getString("json")));break;
                case "quota":UsageLocalData.phoneQuota(c,b.getDouble("remaining"),b.getString("reset"),b.getLong("now"));break;
                case "selected":out.putInt("character",UsageLocalData.selected(c,b.getInt("id")));break;
                case "select":UsageLocalData.select(c,b.getInt("id"),b.getInt("character"));break;
                default:throw new IllegalArgumentException("Unknown usage operation");
            }
            return out;
        }catch(Exception e){throw new IllegalStateException("Usage storage failed",e);}
    }
    public Cursor query(Uri u,String[] p,String s,String[] a,String sort){return null;}
    public String getType(Uri u){return null;}
    public Uri insert(Uri u,ContentValues v){throw new UnsupportedOperationException();}
    public int delete(Uri u,String s,String[] a){throw new UnsupportedOperationException();}
    public int update(Uri u,ContentValues v,String s,String[] a){throw new UnsupportedOperationException();}
}
