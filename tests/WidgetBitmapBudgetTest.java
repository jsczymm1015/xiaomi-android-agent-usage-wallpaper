package local.codex.wallpapers;

public final class WidgetBitmapBudgetTest {
    static int checked;
    static void check(boolean value,String label){checked++;if(!value)throw new AssertionError(label);}
    public static void main(String[] args){
        int[][] screens={{240,320},{320,480},{480,800},{720,1280},{1080,2400},{1440,3200},{2160,3840}};
        for(int[] screen:screens){
            WidgetBitmapBudget b=new WidgetBitmapBudget(screen[0],screen[1]);
            check(b.bitmapBytes()<=b.budget,"Payload fits reserved budget "+screen[0]);
            check(b.bitmapBytes()<(long)screen[0]*screen[1]*6,"Headroom under Android screen bitmap limit "+screen[0]);
            check(b.width>=1&&b.width<=900&&b.height>=1&&b.height<=420,"Base bounded "+screen[0]);
            check(b.frameCount==0||(b.frameCount>=8&&b.frameCount<=20&&b.frameSize>=48&&b.frameSize<=160),"Frames bounded "+screen[0]);
        }
        WidgetBitmapBudget low=new WidgetBitmapBudget(320,480),high=new WidgetBitmapBudget(1080,2400);
        check(low.width<high.width&&low.frameSize<high.frameSize,"Small screens use smaller bitmaps");
        WidgetBitmapBudget tiny=new WidgetBitmapBudget(40,40);check(tiny.frameCount==0,"Tiny screen uses static fallback");
        System.out.println("WidgetBitmapBudgetTest: "+checked+" assertions passed");
    }
}
