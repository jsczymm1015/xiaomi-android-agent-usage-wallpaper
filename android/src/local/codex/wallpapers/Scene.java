package local.codex.wallpapers;

import android.content.Context;
import android.graphics.*;
import java.io.InputStream;

final class Scene {
    static final String[] IDS={"M5-B","L1D-02","L3D-01"};
    static final String[] NAMES={"M5 · B版保健老师（动态）","L1D-02 · 城市探索（静态）","L3D-01 · 静谧书库（静态）"};
    final Context context;
    final Paint p=new Paint(3);
    Bitmap full,chibi,background,lockFull,lockBackground,rearBackground,brooch,aodSquare;
    Rect bounds,chibiBounds,lockBounds;
    int override=-1, loaded=-1;
    int selected(){return Math.max(0,Math.min(IDS.length-1,override>=0?override:context.getSharedPreferences("wallpaper",0).getInt("character",0)));}
    String imageAsset(int mode){int n=selected();return n==0?"M5-B-aod-accessory-v2.png":IDS[n]+(mode==3?"-rear-accessory-v2.png":mode==2?"-aod-accessory-v2.png":mode==1?"-lock-accessory-v2.png":"-home-accessory-v2.png");}
    Scene(Context c){context=c;}
    Bitmap read(String name){try(InputStream in=context.getAssets().open(name)){Bitmap b=BitmapFactory.decodeStream(in);if(b==null)throw new IllegalStateException(name);return b;}catch(Exception e){throw new IllegalStateException("Missing asset "+name,e);}}
    Rect content(Bitmap bitmap,int width){int l=width,t=bitmap.getHeight(),r=0,b=0;int[] row=new int[width];for(int y=0;y<bitmap.getHeight();y++){bitmap.getPixels(row,0,width,0,y,width,1);for(int x=0;x<width;x++)if((row[x]>>>24)>48){l=Math.min(l,x);r=Math.max(r,x);t=Math.min(t,y);b=Math.max(b,y);}}if(r<=l)throw new IllegalStateException("Empty character");return new Rect(Math.max(0,l-2),Math.max(0,t-2),Math.min(width,r+3),Math.min(bitmap.getHeight(),b+3));}
    void load(){int n=selected();if(loaded==n&&full!=null)return;close();loaded=n;if(n>0){full=read(imageAsset(0));lockFull=read(imageAsset(1));chibi=read(imageAsset(2));return;}full=read("M5-B-full.png");chibi=read("M5-B-aod.png");background=read("M5-B-background.png");bounds=content(full,full.getWidth()/2);chibiBounds=content(chibi,chibi.getWidth());lockFull=read("M5-B-lock-full.png");lockBackground=read("M5-B-lock-background.png");lockBounds=content(lockFull,lockFull.getWidth()/2);}
    void draw(Canvas c,int w,int h,int mode,long time){
        if(w<=0||h<=0)return;if(mode==3){drawRear(c,w,h,(float)(.5-.5*Math.cos(time*Math.PI/1800)));return;}if(mode==2&&selected()==0){drawAod(c,w,h,time);return;}load();if(loaded>0){Bitmap b=mode==2?chibi:mode==1?lockFull:full;drawImage(c,b,w,h,mode==2);return;}if(mode==2){drawAod(c,w,h,time);return;}
        Bitmap backdrop=mode==1?lockBackground:background,actor=mode==1?lockFull:full;Rect box=mode==1?lockBounds:bounds;
        float bgScale=Math.max(w/(float)backdrop.getWidth(),h/(float)backdrop.getHeight());
        float bw=backdrop.getWidth()*bgScale,bh=backdrop.getHeight()*bgScale;
        p.setAlpha(255);c.drawBitmap(backdrop,null,new RectF((w-bw)/2,(h-bh)/2,(w+bw)/2,(h+bh)/2),p);
        // A soft upper gradient preserves contrast behind the system clock/icons.
        p.setShader(new LinearGradient(0,0,0,h*.36f,0x60312723,0x00312723,Shader.TileMode.CLAMP));c.drawRect(0,0,w,h*.36f,p);p.setShader(null);
        float scale=Math.min(h*(mode==1?.73f:.70f)/box.height(),w*.82f/box.width());
        float ground=h*.946f,cx=w*(mode==1?.50f:.55f),left=cx-box.width()*scale/2,top=ground-box.height()*scale;
        p.setColor(0x30000000);c.drawOval(cx-w*.125f,ground-h*.005f,cx+w*.125f,ground+h*.004f,p);
        c.save();c.scale(1,1+.004f*(float)Math.sin(time*Math.PI/2200),cx,ground);
        p.setAlpha(255);c.drawBitmap(actor,box,new RectF(left,top,left+box.width()*scale,ground),p);
        // Replace the original lapel emblem with one readable jewelry accessory.
        badge(c,left+(mode==1?356-box.left:433-box.left)*scale,top+(mode==1?455-box.top:247-box.top)*scale,(mode==1?40:34)*scale);
        // Begin the first blink soon after the screen becomes visible.
        long phase=time%4200;
        if(phase>=1000&&phase<1350){float a=phase<1080?(phase-1000)/80f:phase<1220?1:(1350-phase)/130f;blink(c,left,top,scale,a,mode,actor,box);}
        c.restore();p.setAlpha(255);
    }
    void blink(Canvas c,float left,float top,float scale,float alpha,int mode,Bitmap actor,Rect box){
        int[][] dst=mode==1?new int[][]{{428,94,457,117},{471,83,501,107}}:new int[][]{{450,99,482,121},{500,86,535,110}};
        int[][] src=mode==1?new int[][]{{995,99,1024,122},{1039,89,1069,113}}:new int[][]{{1026,99,1058,121},{1076,86,1111,110}};
        p.setAlpha(Math.round(alpha*255));
        for(int i=0;i<2;i++){int[] d=dst[i],s=src[i];RectF to=new RectF(left+(d[0]-box.left)*scale,top+(d[1]-box.top)*scale,left+(d[2]-box.left)*scale,top+(d[3]-box.top)*scale);Path oval=new Path();oval.addOval(to,Path.Direction.CW);c.save();c.clipPath(oval);c.drawBitmap(actor,new Rect(s[0],s[1],s[2],s[3]),to,p);c.restore();}p.setAlpha(255);
    }
    void drawImage(Canvas c,Bitmap b,int w,int h,boolean contain){c.drawColor(Color.BLACK);float sc=contain?Math.min(w/(float)b.getWidth(),h/(float)b.getHeight()):Math.max(w/(float)b.getWidth(),h/(float)b.getHeight());float bw=b.getWidth()*sc,bh=b.getHeight()*sc;p.setAlpha(255);c.drawBitmap(b,null,new RectF((w-bw)/2,(h-bh)/2,(w+bw)/2,(h+bh)/2),p);}
    void badge(Canvas c,float cx,float cy,float size){if(brooch==null)brooch=read("chatgpt-brooch-v2.png");p.setAlpha(255);p.setColor(0xfff5f2ee);c.drawOval(cx-size*.5f,cy-size*.5f,cx+size*.5f,cy+size*.5f,p);c.drawBitmap(brooch,null,new RectF(cx-size*.5f,cy-size*.5f,cx+size*.5f,cy+size*.5f),p);}
    void drawAod(Canvas c,int w,int h,long time){if(aodSquare==null)aodSquare=read(imageAsset(2));c.drawColor(Color.BLACK);c.save();float breathe=(float)(1+.012*Math.sin(time*Math.PI/1800));c.scale(breathe,breathe,w/2f,h/2f);drawImage(c,aodSquare,w,h,true);c.restore();}
    void loadRear(){
        int n=selected();if(loaded!=n){close();loaded=n;}
        // Rear media only needs the small character, rear background, and badge.
        // Keep existing home/lock bitmaps if already loaded by this preview, but
        // a fresh export Scene must not decode the unrelated wallpaper layers.
        if(n==0&&chibi==null){chibi=read("M5-B-aod.png");chibiBounds=content(chibi,chibi.getWidth());}
    }
    void drawRear(Canvas c,int w,int h,float nod){
        loadRear();if(loaded>0){if(rearBackground==null)rearBackground=read(imageAsset(3));drawImage(c,rearBackground,w,h,false);return;}if(rearBackground==null)rearBackground=read("M5-B-rear-background.png");
        float bs=Math.max(w/(float)rearBackground.getWidth(),h/(float)rearBackground.getHeight());
        float bw=rearBackground.getWidth()*bs,bh=rearBackground.getHeight()*bs;
        p.setAlpha(255);c.drawBitmap(rearBackground,null,new RectF((w-bw)/2,(h-bh)/2,(w+bw)/2,(h+bh)/2),p);
        // This landscape image is exported as media; it does not select or control a display.
        float scale=Math.min(w*.43f/chibiBounds.width(),h*.79f/chibiBounds.height());
        float cx=w*.68f,ground=h*.96f,cw=chibiBounds.width()*scale,ch=chibiBounds.height()*scale;
        RectF to=new RectF(cx-cw/2,ground-ch,cx+cw/2,ground);float neck=to.top+ch*.385f;
        p.setColor(0x45000000);c.drawOval(cx-cw*.23f,ground-3,cx+cw*.23f,ground+3,p);
        p.setAlpha(255);
        c.save();c.clipRect(0,neck,w,h);c.drawBitmap(chibi,chibiBounds,to,p);c.restore();
        // A fixed body and one source image avoid outfit, hair and leg discontinuities.
        c.save();c.scale(1,1-.045f*nod,cx,neck);c.clipRect(0,0,w,neck);c.drawBitmap(chibi,chibiBounds,to,p);c.restore();
        badge(c,to.left+(673-chibiBounds.left)*scale,to.top+(710-chibiBounds.top)*scale,58*scale);
    }
    void close(){if(full!=null){full.recycle();full=null;}if(chibi!=null){chibi.recycle();chibi=null;}if(background!=null){background.recycle();background=null;}if(lockFull!=null){lockFull.recycle();lockFull=null;}if(lockBackground!=null){lockBackground.recycle();lockBackground=null;}if(rearBackground!=null){rearBackground.recycle();rearBackground=null;}if(brooch!=null){brooch.recycle();brooch=null;}if(aodSquare!=null){aodSquare.recycle();aodSquare=null;}}
}
