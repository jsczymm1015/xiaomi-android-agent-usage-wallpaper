package local.codex.wallpapers;
import android.app.job.*;
public class UsageCloudJob extends JobService {
    volatile boolean stopped;
    public boolean onStartJob(JobParameters parameters){stopped=false;new Thread(()->{
        try{if(UsageCloud.configured(this))UsageCloud.sync(this);}
        catch(Exception e){UsageCloud.state(this,"后台同步未完成，稍后重试；请检查网络或仓库授权");}
        finally{if(!stopped){UsageCloud.enqueue(this,parameters.getJobId()==UsageCloud.JOB?UsageCloud.NEXT_JOB:UsageCloud.JOB);jobFinished(parameters,false);}}
    },"usage-cloud").start();return true;}
    public boolean onStopJob(JobParameters parameters){stopped=true;return UsageCloud.configured(this);}
}
