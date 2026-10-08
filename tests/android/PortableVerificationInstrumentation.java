package local.codex.wallpapers;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.RemoteViews;
import android.widget.ViewFlipper;
import java.io.File;
import java.io.FileOutputStream;
import org.json.JSONArray;
import org.json.JSONObject;

/** Isolated QA runner. Never packaged in the release APK. No real user data is read.
 * Must target local.codex.wallpapers.qa; refuses every other application ID.
 */
public final class PortableVerificationInstrumentation extends Instrumentation {
    private Context target;
    private String sourceUrl="",stage="start";
    private int assertions;
    private boolean networkVerified;
    private int secondHttpCode;
    private interface Mutation {void change(JSONObject data)throws Exception;}

    @Override public void onCreate(Bundle arguments){
        super.onCreate(arguments);if(arguments!=null)sourceUrl=arguments.getString("sourceUrl","");start();
    }
    @Override public void onStart(){
        Bundle result=new Bundle();int resultCode=Activity.RESULT_OK;
        try{
            target=getTargetContext();
            if(!"local.codex.wallpapers.qa".equals(target.getPackageName()))throw new SecurityException("QA target required");
            stage("json_validation");jsonValidation();
            stage("source_switch");sourceSwitch();
            stage("remote_views");remoteViews();
            stage("gif_exports");gifExports();
            if(!sourceUrl.isEmpty()){stage("https_pull");httpsPull();}
            stage("complete");result.putInt("assertions",assertions);result.putBoolean("networkVerified",networkVerified);
            result.putInt("gifFiles",2);result.putBoolean("isolatedTarget",true);result.putInt("secondHttpCode",secondHttpCode);
        }catch(Throwable failure){
            result.putString("stage",stage);result.putString("failureType",failure.getClass().getSimpleName());
            result.putInt("assertions",assertions);resultCode=Activity.RESULT_CANCELED;
        }finally{if(target!=null&&"local.codex.wallpapers.qa".equals(target.getPackageName()))UsageCloud.stop(target);}
        finish(resultCode,result);
    }
    private void stage(String name){stage=name;Bundle state=new Bundle();state.putString("stage",name);state.putInt("assertions",assertions);sendStatus(0,state);}
    private void check(boolean condition,String fixedLabel){assertions++;if(!condition)throw new AssertionError(fixedLabel);}
    private static JSONObject copy(JSONObject value)throws Exception{return new JSONObject(value.toString());}
    private static JSONObject agent()throws Exception{
        long observed=System.currentTimeMillis()/1000-60;
        String date=java.time.Instant.ofEpochSecond(observed).atZone(java.time.ZoneId.of("Asia/Shanghai")).toLocalDate().toString();
        return new JSONObject().put("schemaVersion",1).put("source","agent_usage").put("provider","Demo Agent")
            .put("chartMode","live").put("observedAt",observed)
            .put("quota",new JSONObject().put("remaining",72.5).put("window","monthly").put("observedAt",observed)
                .put("source","agent_report").put("resetsAt",observed+86400))
            .put("daily",new JSONObject().put("observedAt",observed).put("buckets",new JSONArray()
                .put(new JSONObject().put("date",date).put("tokens",123456))))
            .put("resetCards",new JSONObject().put("availableCount",2).put("expiresAt",observed+172800)
                .put("expiryStatus","earliest").put("observedAt",observed).put("source","agent_report"));
    }
    private void rejects(Mutation mutation,String label)throws Exception{
        JSONObject invalid=agent();mutation.change(invalid);boolean rejected=false;
        try{UsageCloud.validate(invalid);}catch(Exception expected){rejected=true;}check(rejected,label);
    }
    private void jsonValidation()throws Exception{
        JSONObject valid=agent();UsageCloud.validate(valid);check(true,"Agent schema accepted");
        JSONObject legacy=copy(valid).put("source","codex_app_server");legacy.remove("provider");legacy.getJSONObject("quota").remove("window");
        legacy.getJSONObject("resetCards").put("source","account/rateLimits/read");UsageCloud.validate(legacy);check(true,"Legacy Codex schema accepted");
        JSONObject unknown=copy(valid);unknown.getJSONObject("quota").put("remaining",JSONObject.NULL).put("window","unknown");
        unknown.getJSONObject("daily").put("buckets",JSONObject.NULL);unknown.getJSONObject("resetCards").put("availableCount",JSONObject.NULL)
            .put("expiresAt",JSONObject.NULL).put("expiryStatus","unknown");UsageCloud.validate(unknown);check(true,"Unknown values accepted");
        rejects(j->j.put("schemaVersion",2),"Unsupported schema rejected");
        rejects(j->j.put("source","invented"),"Unsupported source rejected");
        rejects(j->j.put("chartMode","simulated"),"Simulated upload rejected");
        rejects(j->j.put("provider",13),"Numeric provider rejected");
        rejects(j->j.put("provider","Bad\nProvider"),"Control provider rejected");
        rejects(j->j.put("provider","abcdefghijklmnopqrstuvwxyz0123456789"),"Long provider rejected");
        rejects(j->j.put("observedAt",System.currentTimeMillis()/1000+3600),"Future snapshot rejected");
        rejects(j->j.getJSONObject("quota").put("remaining","72.5"),"String remaining rejected");
        rejects(j->j.getJSONObject("quota").put("remaining",101),"Excess quota rejected");
        rejects(j->j.getJSONObject("quota").put("window","annual"),"Unsupported window rejected");
        rejects(j->j.getJSONObject("quota").put("observedAt",j.getLong("observedAt")+1),"Future nested observation rejected");
        rejects(j->j.getJSONObject("daily").getJSONArray("buckets").getJSONObject(0).put("date","2026-02-30"),"Invalid calendar date rejected");
        rejects(j->j.getJSONObject("daily").getJSONArray("buckets").getJSONObject(0).put("tokens",1.5),"Fractional tokens rejected");
        rejects(j->j.getJSONObject("daily").getJSONArray("buckets").getJSONObject(0).put("tokens",-1),"Negative tokens rejected");
        rejects(j->{JSONArray rows=j.getJSONObject("daily").getJSONArray("buckets");rows.put(copy(rows.getJSONObject(0)));},"Duplicate date rejected");
        rejects(j->j.getJSONObject("resetCards").put("availableCount",-1),"Negative cards rejected");
        rejects(j->j.getJSONObject("resetCards").put("availableCount","2"),"String cards rejected");
        rejects(j->j.getJSONObject("resetCards").put("availableCount",0),"Zero cards with earliest expiry rejected");
        rejects(j->j.getJSONObject("resetCards").put("source","invented"),"Unsupported card source rejected");
        long observed=valid.getLong("observedAt");JSONObject old=copy(valid).put("cloudObservedAt",observed);
        JSONObject stale=copy(valid).put("observedAt",observed-1);stale.getJSONObject("quota").put("observedAt",observed-1);
        stale.getJSONObject("daily").put("observedAt",observed-1);stale.getJSONObject("resetCards").put("observedAt",observed-1);
        check(UsageCloud.mergedSnapshot(old,stale)==null,"Older same-provider snapshot ignored");
        JSONObject phone=copy(old);phone.getJSONObject("quota").put("observedAt",observed+20).put("remaining",80).put("source","phone_chatgpt_usage_page");
        check(UsageCloud.mergedSnapshot(phone,copy(valid)).getJSONObject("quota").getDouble("remaining")==80,"Newer phone quota retained");
        JSONObject next=copy(valid);next.remove("resetCards");
        check(UsageCloud.mergedSnapshot(old,next).getJSONObject("resetCards").getInt("availableCount")==2,"Legacy card omission retains observed cards");
    }
    private void sourceSwitch()throws Exception{
        UsageCloud.stop(target);target.getSharedPreferences("wallpaper",0).edit().putInt("character",2).commit();UsageData.select(target,-1,1);
        UsageEndpoint first=UsageEndpoint.https("https://example.com/first.json",UsageEndpoint.NONE);UsageCloud.save(target,first,"");
        check(UsageCloud.configured(target),"No-auth endpoint config accepted");
        UsageData.write(target,agent());UsageCloud.prefs(target).edit().putString("appliedEtag","synthetic-etag").putLong("appliedObservedAt",10).commit();
        UsageEndpoint second=UsageEndpoint.https("https://example.com/second.json",UsageEndpoint.BEARER);
        final String synthetic="qa_demo_not_a_credential";UsageCloud.save(target,second,synthetic);
        check(UsageData.read(target).length()==0,"Switch clears old snapshot");
        check(!UsageCloud.prefs(target).contains("appliedEtag")&&!UsageCloud.prefs(target).contains("appliedObservedAt"),"Switch clears old ETag state");
        check(target.getSharedPreferences("wallpaper",0).getInt("character",-1)==2&&UsageData.selected(target,-1)==1,"Switch preserves theme and widget preferences");
        check(synthetic.equals(UsageCloud.token(target,second)),"Keystore token roundtrip");
        check(!UsageCloud.prefs(target).getString("secret","").contains(synthetic),"Token not saved as plaintext");
        boolean aadRejected=false;
        // A different authenticated URL must not decrypt this endpoint's token.
        UsageEndpoint wrong=UsageEndpoint.https("https://example.com/other.json",UsageEndpoint.BEARER);
        try{UsageCloud.token(target,wrong);}catch(Exception expected){aadRejected=true;}check(aadRejected,"Ciphertext bound to endpoint");
        UsageCloud.stop(target);check(!UsageCloud.configured(target),"Stop clears authorization and endpoint");
    }
    private void remoteViews()throws Exception{
        final JSONObject data=agent();final Throwable[] failure=new Throwable[1];
        runOnMainSync(()->{
            try{
                for(int[] screen:new int[][]{{320,480},{1080,2400}}){
                    WidgetBitmapBudget budget=new WidgetBitmapBudget(screen[0],screen[1]);RemoteViews views=UsageWidgetProvider.base(target,42);
                    UsageMotion.attach(target,views,0,72.5,budget);Bitmap image=UsageWidgetRenderer.render(target,0,data,null,false,budget.width,budget.height);
                    try{
                        views.setImageViewBitmap(UsageWidgetProvider.resource(target,"id","usage_image"),image);
                        FrameLayout parent=new FrameLayout(target);View applied=views.apply(target,parent);
                        ViewFlipper flipper=applied.findViewById(UsageWidgetProvider.resource(target,"id","usage_motion"));
                        check(flipper!=null&&flipper.getChildCount()==budget.frameCount,"RemoteViews inflates all bounded frames");
                        check(budget.bitmapBytes()<=budget.budget&&budget.bitmapBytes()<(long)screen[0]*screen[1]*6,"RemoteViews budget leaves memory headroom");
                        check(flipper.getFlipInterval()==3600/budget.frameCount,"Host accepts frame interval method");
                    }finally{image.recycle();}
                }
                RemoteViews staticViews=UsageWidgetProvider.base(target,42);Bitmap still=UsageWidgetRenderer.render(target,0,data,null,true,160,75);
                try{staticViews.setImageViewBitmap(UsageWidgetProvider.resource(target,"id","usage_image"),still);View applied=staticViews.apply(target,new FrameLayout(target));
                    ViewFlipper flipper=applied.findViewById(UsageWidgetProvider.resource(target,"id","usage_motion"));check(flipper.getChildCount()==0,"Static fallback contains no animation children");}
                finally{still.recycle();}
            }catch(Throwable error){failure[0]=error;}
        });
        if(failure[0]!=null)throw new Exception("RemoteViews verification failed",failure[0]);
    }
    private void gifExports()throws Exception{
        File directory=new File(target.getFilesDir(),"portable-verification");if(!directory.isDirectory()&&!directory.mkdirs())throw new AssertionError("QA directory");
        Scene scene=new Scene(target);scene.override=0;
        try{
            for(int mode:new int[]{2,3}){
                File file=new File(directory,mode==2?"media-ambient.gif":"media-rear.gif");
                try(FileOutputStream stream=new FileOutputStream(file)){WallpaperMedia.writeGif(scene,mode,stream);}
                check(file.length()>1000,"Actual scene GIF generated");
                // Android Movie decodes the generated loop independently of the encoder.
                android.graphics.Movie movie=android.graphics.Movie.decodeFile(file.getAbsolutePath());
                check(movie!=null&&movie.width()==(mode==2?360:488)&&movie.height()==(mode==2?360:298),"Android decodes actual scene GIF");
                check(movie.duration()==3600,"Android reads 30 frame duration");
                Bitmap first=Bitmap.createBitmap(movie.width(),movie.height(),Bitmap.Config.ARGB_8888),middle=Bitmap.createBitmap(movie.width(),movie.height(),Bitmap.Config.ARGB_8888);
                try{movie.setTime(0);movie.draw(new android.graphics.Canvas(first),0,0);movie.setTime(900);movie.draw(new android.graphics.Canvas(middle),0,0);
                    int changed=0;for(int y=0;y<first.getHeight();y++)for(int x=0;x<first.getWidth();x++)if(first.getPixel(x,y)!=middle.getPixel(x,y))changed++;
                    check(changed>0,"Actual GIF contains visible motion");
                }finally{first.recycle();middle.recycle();}
            }
        }finally{scene.close();}
    }
    private void httpsPull()throws Exception{
        UsageEndpoint endpoint=UsageEndpoint.https(sourceUrl,UsageEndpoint.NONE);UsageCloud.save(target,endpoint,"");
        UsageCloud.sync(target,true);JSONObject pulled=UsageData.read(target);UsageCloud.validate(pulled);
        check(pulled.optLong("cloudObservedAt")>0,"HTTPS snapshot accepted through private provider");
        check(UsageCloud.prefs(target).getInt("lastHttpCode",0)==200,"HTTPS returns 200");
        long observed=pulled.getLong("cloudObservedAt");
        check(UsageCloud.prefs(target).getLong("appliedObservedAt",0)==observed,"HTTPS receipt version matches snapshot");
        UsageCloud.sync(target,false);int secondCode=UsageCloud.prefs(target).getInt("lastHttpCode",0);
        check((secondCode==200||secondCode==304)&&UsageData.read(target).getLong("cloudObservedAt")==observed,"Second HTTPS check preserves same snapshot");
        secondHttpCode=secondCode;
        networkVerified=true;
    }
}
