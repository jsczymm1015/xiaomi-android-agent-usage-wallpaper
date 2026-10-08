package local.codex.wallpapers;
import android.content.*;
import android.app.job.*;
import android.security.keystore.*;
import android.util.Base64;
import java.security.KeyStore;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import java.net.*;
import java.io.*;
import org.json.*;

final class UsageCloud {
    static final String ALIAS="agent-usage-read-v1";
    static final int JOB=22007;
    static final int NEXT_JOB=22008;
    static final long INTERVAL_MS=10*60*1000L;
    static final long MAX_TIMESTAMP=253402300799L;
    static final class SyncFailure extends IOException {SyncFailure(String text){super(text);}}
    static SharedPreferences prefs(Context c){return c.getSharedPreferences("usage-cloud",0);}
    static javax.crypto.SecretKey key()throws Exception{
        KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
        if(!store.containsAlias(ALIAS)){
            KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());generator.generateKey();
        }
        return (javax.crypto.SecretKey)store.getKey(ALIAS,null);
    }
    static UsageEndpoint endpoint(Context c){
        SharedPreferences p=prefs(c);if(p.getInt("configVersion",0)!=1)return null;
        try{return "github".equals(p.getString("mode",""))?
            UsageEndpoint.github(p.getString("owner",""),p.getString("repo",""),p.getString("ref",""),p.getString("file",""),p.getString("auth","none")):
            "https".equals(p.getString("mode",""))?UsageEndpoint.https(p.getString("url",""),p.getString("auth","none")):null;
        }catch(IllegalArgumentException invalid){return null;}
    }
    // An old encrypted token without an explicit endpoint never enables synchronization.
    static boolean configured(Context c){UsageEndpoint e=endpoint(c);return e!=null&&(UsageEndpoint.NONE.equals(e.auth)||prefs(c).contains("secret"));}
    static synchronized void save(Context c,UsageEndpoint endpoint,String token)throws Exception{
        String value=null;
        if(!UsageEndpoint.NONE.equals(endpoint.auth)){
            endpoint.headerValue(token);
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key());cipher.updateAAD(endpoint.url.getBytes("UTF-8"));
            value=Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP)+":"+Base64.encodeToString(cipher.doFinal(token.trim().getBytes("UTF-8")),Base64.NO_WRAP);
        }
        // Empty only statistics, through the private IPC owner. Character/theme preferences remain.
        // Do this before committing a new endpoint so no old-account statistics can appear as new.
        UsageData.write(c,new JSONObject());
        SharedPreferences.Editor edit=prefs(c).edit().clear().putInt("configVersion",1).putString("mode",endpoint.mode)
            .putString("owner",endpoint.owner).putString("repo",endpoint.repo).putString("ref",endpoint.ref).putString("file",endpoint.file)
            .putString("url",endpoint.url).putString("auth",endpoint.auth).putString("status","配置已保存；旧统计已清除，等待首次同步");
        if(value!=null)edit.putString("secret",value);
        if(!edit.commit())throw new SyncFailure("配置保存失败，请重试");
        UsageWidgetProvider.updateAll(c);schedule(c);
    }
    static String token(Context c,UsageEndpoint endpoint)throws Exception{
        if(UsageEndpoint.NONE.equals(endpoint.auth))return "";
        String[] parts=prefs(c).getString("secret","").split(":");
        if(parts.length!=2)throw new SyncFailure("请重新配置此数据源的只读授权");
        try{
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.decode(parts[0],Base64.NO_WRAP)));cipher.updateAAD(endpoint.url.getBytes("UTF-8"));
            return new String(cipher.doFinal(Base64.decode(parts[1],Base64.NO_WRAP)),"UTF-8");
        }catch(Exception invalid){throw new SyncFailure("授权无法读取，请重新保存此数据源的只读令牌");}
    }
    static void schedule(Context c){
        if(!configured(c))return;
        JobScheduler scheduler=c.getSystemService(JobScheduler.class);
        for(int id:new int[]{JOB,NEXT_JOB}){JobInfo existing=scheduler.getPendingJob(id);if(existing!=null&&existing.isPeriodic())scheduler.cancel(id);}
        if(scheduler.getPendingJob(JOB)==null&&scheduler.getPendingJob(NEXT_JOB)==null)enqueue(c,JOB);
    }
    static void enqueue(Context c,int id){
        if(!configured(c))return;
        int result=c.getSystemService(JobScheduler.class).schedule(new JobInfo.Builder(id,new ComponentName(c,UsageCloudJob.class))
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setMinimumLatency(INTERVAL_MS).setPersisted(true).build());
        if(result!=JobScheduler.RESULT_SUCCESS)state(c,"系统未接受后台同步任务");
    }
    static synchronized void stop(Context c){
        prefs(c).edit().clear().commit();
        JobScheduler scheduler=c.getSystemService(JobScheduler.class);scheduler.cancel(JOB);scheduler.cancel(NEXT_JOB);
    }
    static void state(Context c,String text){prefs(c).edit().putString("status",text).apply();}
    static String status(Context c){return prefs(c).getString("status",configured(c)?"后台同步已配置":"请配置自己的仓库或 HTTPS JSON 数据源");}
    static String failure(Exception e){return e instanceof SyncFailure?e.getMessage():"同步未完成，请检查网络、统计格式和只读授权；保留上次有效数据";}
    static void sync(Context c)throws Exception{sync(c,false);}
    static synchronized void sync(Context c,boolean force)throws Exception{
        UsageEndpoint endpoint=endpoint(c);if(endpoint==null||!configured(c))throw new SyncFailure("请先配置数据源及所需只读授权");
        HttpURLConnection connection=null;
        try{
            connection=(HttpURLConnection)new URL(endpoint.requestUrl()).openConnection();SharedPreferences prefs=prefs(c);
            String etag=prefs.getString("appliedEtag","");long appliedAt=prefs.getLong("appliedObservedAt",0);
            boolean cached=appliedAt>0&&UsageData.read(c).optLong("cloudObservedAt")==appliedAt;
            connection.setUseCaches(false);if(!force&&cached&&!etag.isEmpty())connection.setRequestProperty("If-None-Match",etag);
            connection.setInstanceFollowRedirects(false);connection.setConnectTimeout(15000);connection.setReadTimeout(20000);
            if(endpoint.headerName()!=null)connection.setRequestProperty(endpoint.headerName(),endpoint.headerValue(token(c,endpoint)));
            connection.setRequestProperty("Accept",endpoint.github()?"application/vnd.github.raw+json":"application/json");
            if(endpoint.github())connection.setRequestProperty("X-GitHub-Api-Version","2022-11-28");connection.setRequestProperty("User-Agent","Agent-Usage-Wallpaper");
            int code=connection.getResponseCode();
            if(code==HttpURLConnection.HTTP_NOT_MODIFIED){
                if(!cached||etag.isEmpty())throw new SyncFailure("版本校验缺少本地数据，请强制同步");
                UsageWidgetProvider.updateAll(c);prefs.edit().putLong("lastCheckedAt",System.currentTimeMillis()/1000).putInt("lastHttpCode",304).apply();
                state(c,"检查完成 · "+UsageWidgetRenderer.stamp(System.currentTimeMillis()/1000)+"；无新版本，保留当前数据");return;
            }
            if(code>=300&&code<400)throw new SyncFailure("数据源发生重定向；请填写最终 HTTPS JSON 地址，授权不会转发");
            if(code==404)throw new SyncFailure("文件不可见（HTTP 404）；请检查仓库、分支、JSON 路径和此仓库的只读授权");
            if(code==401||code==403)throw new SyncFailure("授权无效或权限不足；请检查此仓库的只读令牌和所选授权方式");
            if(code!=200)throw new SyncFailure("数据源读取失败 HTTP "+code+"；请检查网络及只读授权");
            JSONObject data;try(InputStream input=connection.getInputStream()){data=new JSONObject(UsageLocalData.readText(input));}
            try{validate(data);}catch(Exception invalid){throw new SyncFailure("统计 JSON 格式或时间无效，保留上次有效数据");}
            android.os.Bundle b=new android.os.Bundle();b.putString("json",data.toString());UsageData.call(c,"cloud",b);UsageWidgetProvider.updateAll(c);
            // Only acknowledge an accepted snapshot. A server returning an older version cannot
            // attach a new ETag to newer local data and hide future recovery downloads.
            if(UsageData.read(c).optLong("cloudObservedAt")!=data.getLong("observedAt"))throw new SyncFailure("收到较旧统计，已保留较新的本地数据");
            prefs.edit().putString("appliedEtag",connection.getHeaderField("ETag")).putLong("appliedObservedAt",data.getLong("observedAt"))
                .putLong("lastCheckedAt",System.currentTimeMillis()/1000).putInt("lastHttpCode",200).commit();
            state(c,"同步成功 · "+UsageWidgetRenderer.stamp(System.currentTimeMillis()/1000)+"；数据截至 "+UsageWidgetRenderer.stamp(data.getLong("observedAt")));
        }catch(SyncFailure known){throw known;}catch(Exception failure){throw new SyncFailure("同步未完成，请检查网络、统计格式和只读授权；保留上次有效数据");}
        finally{if(connection!=null)connection.disconnect();}
    }
    static long integer(Object value,String label)throws IOException{
        if(!(value instanceof Byte||value instanceof Short||value instanceof Integer||value instanceof Long))throw new IOException(label+"必须是整数");return ((Number)value).longValue();
    }
    static long time(Object value,long ceiling)throws IOException{
        long result=integer(value,"统计时间");if(result<=0||result>MAX_TIMESTAMP||result>ceiling)throw new IOException("统计时间无效");return result;
    }
    static String provider(JSONObject data)throws JSONException{if(data.has("provider")){Object value=data.get("provider");if(!(value instanceof String))throw new JSONException("Provider must be a string");return (String)value;}return "codex_app_server".equals(data.optString("source"))?"Codex":"Agent";}
    static void validate(JSONObject data)throws Exception{
        long now=System.currentTimeMillis()/1000;
        if(integer(data.get("schemaVersion"),"协议版本")!=1||!("codex_app_server".equals(data.get("source"))||"agent_usage".equals(data.get("source")))||!"live".equals(data.get("chartMode")))throw new IOException("不支持的统计格式");
        long observed=time(data.get("observedAt"),now+600);
        String provider=provider(data);if(provider.codePointCount(0,provider.length())<1||provider.codePointCount(0,provider.length())>32||!provider.equals(provider.trim()))throw new IOException("用量来源名称无效");
        for(int i=0;i<provider.length();i++)if(Character.isISOControl(provider.charAt(i)))throw new IOException("用量来源名称无效");
        resetCards(data,now);JSONObject quota=data.getJSONObject("quota");Object remaining=quota.get("remaining");
        if(remaining!=JSONObject.NULL){if(!(remaining instanceof Number))throw new IOException("无效剩余额度");double value=((Number)remaining).doubleValue();if(!Double.isFinite(value)||value<0||value>100)throw new IOException("无效剩余额度");}
        if(quota.has("window")){Object window=quota.get("window");if(!("weekly".equals(window)||"daily".equals(window)||"monthly".equals(window)||"other".equals(window)||"unknown".equals(window)))throw new IOException("无效额度窗口");}
        if(quota.has("observedAt"))time(quota.get("observedAt"),observed);
        if(quota.has("resetsAt")&&!quota.isNull("resetsAt"))time(quota.get("resetsAt"),MAX_TIMESTAMP);
        JSONObject daily=data.getJSONObject("daily");if(daily.has("observedAt"))time(daily.get("observedAt"),observed);
        Object buckets=daily.get("buckets");if(buckets!=JSONObject.NULL){
            if(!(buckets instanceof JSONArray))throw new IOException("无效日用量");JSONArray rows=(JSONArray)buckets;
            if(rows.length()>10000)throw new IOException("数据过大");java.util.HashSet<String> dates=new java.util.HashSet<>();
            for(int i=0;i<rows.length();i++){JSONObject row=rows.getJSONObject(i);Object rawDate=row.get("date");if(!(rawDate instanceof String)||!((String)rawDate).matches("[0-9]{4}-[0-9]{2}-[0-9]{2}"))throw new IOException("无效日用量日期");String date=(String)rawDate;if(java.time.LocalDate.parse(date).getYear()<1)throw new IOException("无效日用量日期");long tokens=integer(row.get("tokens"),"日用量");if(!dates.add(date)||tokens<0||tokens>9007199254740991L)throw new IOException("无效日用量");}
        }
    }
    static UsageResetCards resetCards(JSONObject data,long now)throws Exception{
        if(!data.has("resetCards"))return null;
        JSONObject cards=data.getJSONObject("resetCards");Object count=cards.get("availableCount"),expires=cards.get("expiresAt");
        Object status=cards.get("expiryStatus"),source=cards.get("source");if(!(status instanceof String)||!(source instanceof String))throw new IOException("重置卡格式无效");
        return UsageResetCards.fromValues(count==JSONObject.NULL?null:count,expires==JSONObject.NULL?null:expires,(String)status,cards.get("observedAt"),(String)source,integer(data.get("observedAt"),"统计时间"),now);
    }
    static void merge(Context c,JSONObject incoming)throws Exception{JSONObject merged=mergedSnapshot(UsageLocalData.read(c),incoming);if(merged!=null)UsageLocalData.write(c,merged);}
    static JSONObject mergedSnapshot(JSONObject old,JSONObject incoming)throws Exception{
        validate(incoming);boolean sameProvider=old.optString("source").equals(incoming.optString("source"))&&provider(old).equals(provider(incoming));
        if(sameProvider&&incoming.getLong("observedAt")<old.optLong("cloudObservedAt"))return null;
        JSONObject oldQuota=old.optJSONObject("quota"),newQuota=incoming.getJSONObject("quota");
        if(sameProvider&&oldQuota!=null&&oldQuota.optLong("observedAt")>newQuota.optLong("observedAt",incoming.getLong("observedAt")))incoming.put("quota",oldQuota);
        JSONObject oldCards=old.optJSONObject("resetCards"),newCards=incoming.optJSONObject("resetCards");
        if(sameProvider&&oldCards!=null&&(newCards==null||oldCards.optLong("observedAt")>newCards.optLong("observedAt")))incoming.put("resetCards",oldCards);
        incoming.put("cloudObservedAt",incoming.getLong("observedAt"));return incoming;
    }
}
