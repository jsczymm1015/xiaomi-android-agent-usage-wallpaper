package local.codex.wallpapers;

import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.net.Uri;
import android.provider.MediaStore;
import java.io.IOException;
import java.io.OutputStream;

/** Export only media to the user's gallery; never launches manufacturer settings. */
final class WallpaperMedia {
    static Uri png(Context c,Scene scene,int mode,String name)throws IOException {
        boolean original=mode==2||scene.selected()>0;
        int width=mode==3?976:1200,height=mode==3?596:2608;
        Bitmap bitmap=original?scene.read(scene.imageAsset(mode)):Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);
        Uri uri=null;
        try{
            if(!original)scene.draw(new Canvas(bitmap),width,height,mode,0);
            uri=insert(c,name,"image/png");
            try(OutputStream stream=c.getContentResolver().openOutputStream(uri)){if(stream==null||!bitmap.compress(Bitmap.CompressFormat.PNG,100,stream))throw new IOException("PNG encoding failed");}
            ready(c,uri);return uri;
        }catch(RuntimeException|IOException e){if(uri!=null)c.getContentResolver().delete(uri,null,null);throw e;}
        finally{bitmap.recycle();}
    }
    static Uri gif(Context c,Scene scene,int mode,String name)throws IOException {
        if(scene.selected()!=0||(mode!=2&&mode!=3))throw new IllegalArgumentException("Only M5 ambient/rear media has a GIF animation");
        Uri uri=null;
        try{
            uri=insert(c,name,"image/gif");
            try(OutputStream stream=c.getContentResolver().openOutputStream(uri)){
                if(stream==null)throw new IOException("Cannot create GIF");writeGif(scene,mode,stream);
            }
            ready(c,uri);return uri;
        }catch(RuntimeException|IOException e){if(uri!=null)c.getContentResolver().delete(uri,null,null);throw e;}
    }
    static void writeGif(Scene scene,int mode,OutputStream stream)throws IOException {
        if(scene.selected()!=0||(mode!=2&&mode!=3))throw new IllegalArgumentException("Only M5 ambient/rear media has a GIF animation");
        int width=mode==3?488:360,height=mode==3?298:360;
        final int frames=30,delay=12;Bitmap bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);
        int[] pixels=new int[width*height];
        try{
            PortableGif gif=new PortableGif(stream,width,height);
            for(int i=0;i<frames;i++){
                scene.draw(new Canvas(bitmap),width,height,mode,i*120L);
                bitmap.getPixels(pixels,0,width,0,0,width,height);gif.frame(pixels,delay);
            }
            gif.finish();
        }finally{bitmap.recycle();}
    }
    static Uri insert(Context c,String name,String mime)throws IOException{
        ContentValues values=new ContentValues();values.put(MediaStore.Images.Media.DISPLAY_NAME,name);values.put(MediaStore.Images.Media.MIME_TYPE,mime);values.put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/AgentWallpapers");values.put(MediaStore.Images.Media.IS_PENDING,1);
        Uri uri=c.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values);if(uri==null)throw new IOException("Cannot create "+name);return uri;
    }
    static void ready(Context c,Uri uri){ContentValues values=new ContentValues();values.put(MediaStore.Images.Media.IS_PENDING,0);c.getContentResolver().update(uri,values,null,null);}
}
