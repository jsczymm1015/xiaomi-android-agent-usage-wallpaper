package local.codex.wallpapers;

import android.app.Activity;
import android.content.ContentValues;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.widget.Toast;
import java.io.InputStream;
import java.io.OutputStream;

/** Exports bundled, credential-free desktop tools. Never reads local usage or secrets. */
final class ReleaseResources {
    static void export(Activity activity) {
        Toast.makeText(activity,"正在导出部署工具…",Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            Uri created=null;
            try {
                ContentValues values=new ContentValues();
                values.put(MediaStore.Downloads.DISPLAY_NAME,"agent-tools-v0.29.zip");
                values.put(MediaStore.Downloads.MIME_TYPE,"application/zip");
                values.put(MediaStore.Downloads.RELATIVE_PATH,Environment.DIRECTORY_DOWNLOADS+"/AgentUsageSetup");
                values.put(MediaStore.Downloads.IS_PENDING,1);
                created=activity.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,values);
                if(created==null)throw new java.io.IOException("Cannot create download");
                try(InputStream input=activity.getAssets().open("setup/agent-tools.zip");
                    OutputStream output=activity.getContentResolver().openOutputStream(created)) {
                    if(output==null)throw new java.io.IOException("Cannot open download");
                    byte[] buffer=new byte[16384];int count;
                    while((count=input.read(buffer))!=-1)output.write(buffer,0,count);
                }
                ContentValues done=new ContentValues();done.put(MediaStore.Downloads.IS_PENDING,0);
                if(activity.getContentResolver().update(created,done,null,null)!=1)throw new java.io.IOException("Cannot finish download");
                activity.runOnUiThread(() -> Toast.makeText(activity,"已导出到 下载/AgentUsageSetup/agent-tools-v0.29.zip",Toast.LENGTH_LONG).show());
            } catch(Exception failure) {
                if(created!=null)activity.getContentResolver().delete(created,null,null);
                activity.runOnUiThread(() -> Toast.makeText(activity,"导出未完成，请检查可用存储空间后重试",Toast.LENGTH_LONG).show());
            }
        },"export-setup-resources").start();
    }
}
