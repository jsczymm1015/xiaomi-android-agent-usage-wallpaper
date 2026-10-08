package local.codex.wallpapers;

import android.content.Context;
import android.graphics.*;
import org.json.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

final class UsageWidgetRenderer {
    static final int WIDTH=900,HEIGHT=420;
    static final ZoneId ZONE=ZoneId.of("Asia/Shanghai");
    static Bitmap render(Context context,int character,JSONObject data,Double demonstration) {return render(context,character,data,demonstration,true);}
    static Bitmap render(Context context,int character,JSONObject data,Double demonstration,boolean drawCharacter) {
        return render(context,character,data,demonstration,drawCharacter,WIDTH,HEIGHT);
    }
    static Bitmap render(Context context,int character,JSONObject data,Double demonstration,boolean drawCharacter,int width,int height) {
        Bitmap out=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(out);c.scale(width/(float)WIDTH,height/(float)HEIGHT);Paint p=new Paint(3);
        p.setColor(Color.BLACK);c.drawRoundRect(0,0,WIDTH,HEIGHT,48,48,p);
        Double remaining=demonstration!=null?demonstration:UsageData.remaining(data);
        String mood=UsagePolicy.mood(remaining);int accent=UsagePolicy.quotaColor(remaining);
        if(drawCharacter)try(java.io.InputStream in=context.getAssets().open("widget/"+Scene.IDS[Math.max(0,Math.min(2,character))]+"-"+mood+".png")) {
            BitmapFactory.Options opts=new BitmapFactory.Options();opts.inSampleSize=2;Bitmap art=BitmapFactory.decodeStream(in,null,opts);
            float size=272; c.drawBitmap(art,null,new RectF(18,14,18+size,14+size),p);art.recycle();
        }catch(Exception e){text(c,p,"角色素材未加载",148,140,23,0xffaaaaaa,Paint.Align.CENTER);}
        text(c,p,remaining==null?"—":String.format(Locale.US,"%.0f%%",remaining),148,334,62,accent,Paint.Align.CENTER);
        String provider=data.optString("provider", "agent_usage".equals(data.optString("source"))?"Agent":"Codex");
        JSONObject quotaInfo=data.optJSONObject("quota");String window=quotaInfo==null?"unknown":quotaInfo.optString("window","unknown");
        String period="weekly".equals(window)?"周":"daily".equals(window)?"日":"monthly".equals(window)?"月":"other".equals(window)?"周期":"";
        fittedText(c,p,provider+" "+period+"额度剩余",30,365,21,236,0xffa7b0b8);
        text(c,p,resetDate(data),148,394,21,0xffa7b0b8,Paint.Align.CENTER);
        boolean simulated=!"live".equals(data.optString("chartMode"));
        text(c,p,"Daily Tokens / 100M",333,57,26,0xffb9c5ce,Paint.Align.LEFT);
        if(simulated)text(c,p,"模拟",852,57,20,0xffc4aa75,Paint.Align.RIGHT);
        JSONObject daily=data.optJSONObject("daily");JSONArray buckets=daily==null?null:daily.optJSONArray("buckets");Map<String,Double> values=new HashMap<>();
        if(buckets!=null)for(int i=0;i<buckets.length();i++){JSONObject row=buckets.optJSONObject(i);if(row==null||row.isNull("tokens"))continue;double value=row.optDouble("tokens",-1);if(Double.isFinite(value)&&value>=0)values.put(row.optString("date"),value);}
        LocalDate today=LocalDate.now(ZONE);
        if(simulated){values.clear();double[] example={.6,.2,1.3,.5,1.5,.9,2.8};for(int i=0;i<7;i++)values.put(today.minusDays(6-i).toString(),example[i]*100000000);}
        double max=1;for(int i=6;i>=0;i--) {Double v=values.get(today.minusDays(i).toString());if(v!=null)max=Math.max(max,v/100000000d);}
        boolean any=false;float base=282,step=70,start=356;
        for(int i=0;i<7;i++){
            LocalDate date=today.minusDays(6-i);Double v=values.get(date.toString());float x=start+i*step;
            text(c,p,date.format(DateTimeFormatter.ofPattern("M/d")),x,315,20,0xff89969f,Paint.Align.CENTER);
            if(v==null){text(c,p,"—",x,103,20,0xff66737c,Paint.Align.CENTER);p.setColor(0xff283138);c.drawRoundRect(x-12,base-3,x+12,base,2,2,p);continue;}
            any=true;double millions=v/100000000d;text(c,p,String.format(Locale.US,"%.1f",millions),x,103,20,0xffdce5eb,Paint.Align.CENTER);
            float bar=(float)(millions/max*151);p.setColor(i==6?0xff70d6eb:0xff75da92);c.drawRoundRect(x-12,base-Math.max(2,bar),x+12,base,3,3,p);
        }
        if(!any)text(c,p,"每日用量尚未接入",566,200,24,0xff8c9aa4,Paint.Align.CENTER);
        String source=simulated?"近 7 天 · 模拟柱状图":daily==null?"日用量待接入":("agent_usage".equals(data.optString("source"))?provider+" 统计 · ":"官方账号统计 · ")+stamp(daily.optLong("observedAt"));
        text(c,p,source,333,354,19,0xff7f909d,Paint.Align.LEFT);
        JSONObject q=data.optJSONObject("quota");String quota=q==null?"等待手机用量页":("desktop_codex_app_server".equals(q.optString("source"))||"agent_report".equals(q.optString("source"))?"云端额度 · ":"手机额度 · ")+stamp(q.optLong("observedAt"));
        text(c,p,demonstration!=null?"表情预览 · 非实时额度":quota,333,383,19,0xff7f909d,Paint.Align.LEFT);
        long now=System.currentTimeMillis()/1000;UsageResetCards cards=null;
        try{cards=UsageCloud.resetCards(data,now);}catch(Exception invalid){/* Unreadable cards remain explicitly unknown. */}
        fittedText(c,p,UsageResetCards.caption(cards,now),333,410,17,519,0xffa7b0b8);
        return out;
    }
    static String stamp(long seconds){if(seconds<=0)return "时间未知";return Instant.ofEpochSecond(seconds).atZone(ZONE).format(DateTimeFormatter.ofPattern("M/d HH:mm"));}
    static String resetDate(JSONObject data){
        JSONObject quota=data.optJSONObject("quota");if(quota==null)return "重置日待读取";
        long now=System.currentTimeMillis()/1000,resetAt=quota.optLong("resetsAt");
        if(resetAt>now)return Instant.ofEpochSecond(resetAt).atZone(ZONE).format(DateTimeFormatter.ofPattern("M/d"))+" 重置";
        String label=quota.optString("resetLabel");java.util.regex.Matcher match=java.util.regex.Pattern.compile("(\\d+)天后").matcher(label);
        if(match.find()){long observed=quota.optLong("observedAt");if(observed>0)return "约 "+Instant.ofEpochSecond(observed).atZone(ZONE).toLocalDate().plusDays(Integer.parseInt(match.group(1))).format(DateTimeFormatter.ofPattern("M/d"))+" 重置";}
        return "重置日待更新";
    }
    static void fittedText(Canvas c,Paint p,String value,float x,float y,float size,float maxWidth,int color){
        p.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));p.setTextSize(size);
        float width=p.measureText(value);if(width>maxWidth)size*=maxWidth/width;
        text(c,p,value,x,y,size,color,Paint.Align.LEFT);
    }
    static void text(Canvas c,Paint p,String value,float x,float y,float size,int color,Paint.Align align){p.setColor(color);p.setTextSize(size);p.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));p.setTextAlign(align);c.drawText(value,x,y,p);}
}
