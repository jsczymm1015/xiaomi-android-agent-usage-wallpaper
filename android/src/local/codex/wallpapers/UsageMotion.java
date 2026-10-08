package local.codex.wallpapers;
import android.content.Context;
import android.graphics.*;
import android.widget.RemoteViews;

final class UsageMotion {
    static final int SIZE=192;
    static Bitmap read(Context c,String name)throws Exception{try(java.io.InputStream in=c.getAssets().open("widget/"+name)){BitmapFactory.Options o=new BitmapFactory.Options();o.inSampleSize=2;return BitmapFactory.decodeStream(in,null,o);}}
    static Rect bounds(Bitmap b){int x0=b.getWidth(),y0=b.getHeight(),x1=0,y1=0;for(int y=0;y<b.getHeight();y++)for(int x=0;x<b.getWidth();x++){int p=b.getPixel(x,y);if(Color.red(p)>35||Color.green(p)>35||Color.blue(p)>35){x0=Math.min(x0,x);y0=Math.min(y0,y);x1=Math.max(x1,x);y1=Math.max(y1,y);}}return x1>x0?new Rect(x0,y0,x1+1,y1+1):new Rect(0,0,b.getWidth(),b.getHeight());}
    static void draw(Canvas c,Paint p,Bitmap b,Rect src,int alpha){p.setAlpha(alpha);float scale=Math.min(176f/src.width(),180f/src.height());float w=src.width()*scale,h=src.height()*scale;c.drawBitmap(b,src,new RectF((SIZE-w)/2,186-h,(SIZE+w)/2,186),p);p.setAlpha(255);}
    static void attach(Context c,RemoteViews views,int character,Double remaining,WidgetBitmapBudget budget)throws Exception{
        if(budget.frameCount==0)throw new IllegalStateException("Static widget chosen for bitmap budget");
        String role=Scene.IDS[character],mood=UsagePolicy.mood(remaining);Bitmap idle=read(c,role+"-"+mood+".png"),sheet=read(c,role+"-motion.png");
        int half=sheet.getWidth()/2,height=sheet.getHeight()/2;
        int cell=mood.equals("low")?3:mood.equals("medium")?2:0;
        Bitmap action=Bitmap.createBitmap(sheet,(cell%2)*half,(cell/2)*height,half,height),wave=Bitmap.createBitmap(sheet,half,0,half,height);
        Rect idleRect=bounds(idle),actRect=bounds(action),waveRect=bounds(wave);
        int flipper=UsageWidgetProvider.resource(c,"id","usage_motion");views.removeAllViews(flipper);
        views.setInt(flipper,"setFlipInterval",3600/budget.frameCount);
        try{for(int i=0;i<budget.frameCount;i++){
            double phase=i*2*Math.PI/budget.frameCount;
            int pose=mood.equals("low")?(i>=budget.frameCount*2/3&&i<budget.frameCount*5/6?1:0):(i==budget.frameCount/3?1:mood.equals("high")&&i>=budget.frameCount*2/3&&i<budget.frameCount*5/6?2:0);
            Bitmap frame=Bitmap.createBitmap(budget.frameSize,budget.frameSize,Bitmap.Config.ARGB_8888);
            Canvas canvas=new Canvas(frame);canvas.scale(budget.frameSize/(float)SIZE,budget.frameSize/(float)SIZE);canvas.drawColor(Color.BLACK);Paint p=new Paint(3);
            float breathe=(float)(1+.007*Math.sin(phase));canvas.scale(1,breathe,SIZE/2f,186);
            Bitmap selected=pose==1?action:pose==2?wave:idle;Rect source=pose==1?actRect:pose==2?waveRect:idleRect;
            // One opaque cel per frame avoids double silhouettes.
            if(mood.equals("high")){canvas.save();canvas.clipRect(0,0,SIZE,100);canvas.rotate((float)(.65*Math.sin(phase)),SIZE/2f,100);draw(canvas,p,selected,source,255);canvas.restore();canvas.save();canvas.clipRect(0,100,SIZE,SIZE);draw(canvas,p,selected,source,255);canvas.restore();}else draw(canvas,p,selected,source,255);
            RemoteViews child=new RemoteViews(c.getPackageName(),UsageWidgetProvider.resource(c,"layout","usage_motion_frame"));child.setImageViewBitmap(UsageWidgetProvider.resource(c,"id","motion_frame"),frame);views.addView(flipper,child);
        }}finally{idle.recycle();action.recycle();wave.recycle();sheet.recycle();}
    }
}
