package local.codex.wallpapers;

import android.service.wallpaper.WallpaperService;
import android.view.SurfaceHolder;
import android.graphics.Canvas;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.app.KeyguardManager;
import android.util.Log;
import android.app.WallpaperManager;
import android.os.Build;

public class CharacterWallpaper extends WallpaperService {
    protected int previewMode(){return 0;}
    public Engine onCreateEngine(){return new CharacterEngine();}
    class CharacterEngine extends Engine {
        final HandlerThread drawingThread=new HandlerThread("CodexWallpaperDraw");
        final Handler handler;
        final Scene scene=new Scene(CharacterWallpaper.this);
        volatile boolean surface=false, destroyed=false;
        boolean visible=false;
        int lastMode=-1, frames=0;
        long started=0, sampleStarted=0, drawTotal=0;
        final Runnable tick=()->render();
        CharacterEngine(){drawingThread.start();handler=new Handler(drawingThread.getLooper());}
        String service(){return CharacterWallpaper.this.getClass().getSimpleName();}
        int mode(){
            if(isPreview())return previewMode();
            int flags=Build.VERSION.SDK_INT>=34?getWallpaperFlags():3;
            if(flags==WallpaperManager.FLAG_LOCK)return 1;
            if(flags==WallpaperManager.FLAG_SYSTEM)return 0;
            return ((KeyguardManager)getSystemService(KEYGUARD_SERVICE)).isKeyguardLocked()?1:0;
        }
        void requestDraw(){handler.post(()->{handler.removeCallbacks(tick);render();});}
        void render(){
            handler.removeCallbacks(tick);
            if(!visible||!surface||destroyed)return;
            long begin=SystemClock.uptimeMillis();Canvas c=null;boolean drawn=false;
            try{
                scene.override=0;scene.load(); // Decode outside the Surface lock and the main thread.
                c=getSurfaceHolder().lockCanvas();
                if(c!=null&&surface&&!destroyed){
                    int chosen=mode();
                    if(chosen!=lastMode){lastMode=chosen;Log.i("CodexWallpaper","SCENE service="+service()+" preview="+isPreview()+" flags="+(Build.VERSION.SDK_INT>=34?getWallpaperFlags():3)+" mode="+chosen);}
                    scene.draw(c,c.getWidth(),c.getHeight(),chosen,begin-started);drawn=true;
                }
            }catch(RuntimeException e){Log.w("CodexWallpaper","draw failed service="+service(),e);}
            finally{if(c!=null)try{getSurfaceHolder().unlockCanvasAndPost(c);}catch(RuntimeException e){Log.w("CodexWallpaper","post failed",e);}}
            long end=SystemClock.uptimeMillis();
            if(drawn){frames++;drawTotal+=end-begin;}
            if(end-sampleStarted>=3000){Log.i("CodexWallpaper","FRAMES service="+service()+" mode="+lastMode+" frames="+frames+" elapsedMs="+(end-sampleStarted)+" avgDrawMs="+(frames>0?drawTotal/frames:0));sampleStarted=end;frames=0;drawTotal=0;}
            if(visible&&surface&&!destroyed)handler.postDelayed(tick,Math.max(1,33-(end-begin)));
        }
        public void onVisibilityChanged(boolean value){
            Log.i("CodexWallpaper","VISIBLE service="+service()+" value="+value+" preview="+isPreview());
            handler.post(()->{boolean resumed=value&&!visible;visible=value;handler.removeCallbacks(tick);if(resumed){started=sampleStarted=SystemClock.uptimeMillis();frames=0;drawTotal=0;}if(value)render();});
        }
        public void onSurfaceCreated(SurfaceHolder h){super.onSurfaceCreated(h);surface=true;requestDraw();}
        public void onSurfaceChanged(SurfaceHolder h,int f,int w,int he){super.onSurfaceChanged(h,f,w,he);requestDraw();}
        public void onWallpaperFlagsChanged(int flags){super.onWallpaperFlagsChanged(flags);Log.i("CodexWallpaper","FLAGS service="+service()+" value="+flags);requestDraw();}
        public void onSurfaceRedrawNeeded(SurfaceHolder h){super.onSurfaceRedrawNeeded(h);requestDraw();}
        public void onSurfaceDestroyed(SurfaceHolder h){surface=false;handler.removeCallbacks(tick);super.onSurfaceDestroyed(h);}
        public void onDestroy(){destroyed=true;handler.removeCallbacks(tick);handler.post(()->{scene.close();drawingThread.quitSafely();});super.onDestroy();}
    }
}
