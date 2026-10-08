package local.codex.wallpapers;
import android.content.*;
/** Migrate existing schedules on app upgrade and restore after reboot. */
public class UsageCloudScheduleReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        String action=intent.getAction();
        if(Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)||Intent.ACTION_BOOT_COMPLETED.equals(action)) UsageCloud.schedule(context);
    }
}
