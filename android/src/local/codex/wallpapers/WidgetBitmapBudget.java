package local.codex.wallpapers;

/** Bound pixel payloads before RemoteViews crosses the host IPC bitmap budget.
 * Android hosts can impose additional limits; the provider must still retry static.
 */
final class WidgetBitmapBudget {
    final int width,height,frameSize,frameCount;
    final long budget;
    WidgetBitmapBudget(int screenWidth,int screenHeight){
        long pixels=(long)Math.max(1,screenWidth)*Math.max(1,screenHeight);
        // Framework uses roughly six bytes per screen pixel. Leave 40% headroom.
        budget=Math.min(12L*1024*1024,Math.max(4,pixels*36/10));
        double scale=Math.min(1,Math.sqrt((budget*.42)/(900d*420*4)));
        width=Math.max(1,(int)(900*scale));height=Math.max(1,(int)(420*scale));
        long remaining=Math.max(0,budget-(long)width*height*4);
        int count=20,size=(int)Math.min(160,Math.sqrt(remaining/(count*4d)));
        while(count>8&&size<64){count-=2;size=(int)Math.min(160,Math.sqrt(remaining/(count*4d)));}
        frameCount=size>=48?count:0;frameSize=frameCount>0?size:0;
    }
    long bitmapBytes(){return (long)width*height*4+(long)frameSize*frameSize*frameCount*4;}
}
