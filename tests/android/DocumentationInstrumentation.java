package local.codex.wallpapers;

import android.app.Activity;
import android.app.Instrumentation;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ScrollView;
import java.io.File;
import java.io.FileOutputStream;
import org.json.JSONArray;
import org.json.JSONObject;

/** Documentation-only instrumentation. Never shipped in the application APK. */
public final class DocumentationInstrumentation extends Instrumentation {
    private Activity current;
    private String currentStage = "launch";

    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        start();
    }

    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            MainActivity main = launch(MainActivity.class);
            stage("main_ready");
            for (int theme = 0; theme < 3; theme++) {
                stage("theme_" + theme);
                final int selected = theme;
                runOnMainSync(() -> {
                    main.mode = 0;
                    main.preview.scene.override = selected;
                    main.preview.started = android.os.SystemClock.uptimeMillis() - 2000;
                    selectThemeButtons(content(main), selected);
                    main.gifExport.setEnabled(false);
                    main.gifExport.setText(selected==0?"切换到 M5 息屏或背屏素材以保存 GIF":"当前主题为静态 · 可保存 PNG");
                    main.applied.setText("演示预览 · 未应用到手机\n主题选择、壁纸和桌面组件配置保持原样");
                    main.preview.invalidate();
                });
                capture(main, new String[]{"theme-m5.png", "theme-l1d.png", "theme-l3d.png"}[theme]);
            }
            closeCurrent();

            UsageWidgetConfigActivity widget = launch(UsageWidgetConfigActivity.class);
            stage("widget_ready");
            final JSONObject demonstration = demonstration();
            runOnMainSync(() -> {
                Bitmap old = widget.rendered;
                widget.character = 0;
                widget.demonstration = 60d;
                widget.rendered = UsageWidgetRenderer.render(widget, 0, demonstration, 60d);
                widget.preview.setImageBitmap(widget.rendered);
                if (old != null) old.recycle();
                widget.status.setText("演示数据 · 周额度剩余 60%\n可用重置卡 3 张，最近到期 2030/1/31 08:00\n此截图未修改真实统计或桌面组件配置");
                selectMediumMood(content(widget));
            });
            capture(widget, "desktop-widget.png");
            closeCurrent();

            UsageCloudActivity cloud = launch(UsageCloudActivity.class);
            stage("cloud_ready");
            runOnMainSync(() -> {
                // Preserve FLAG_SECURE. Only this sanitized view is drawn below.
                cloud.kind.setSelection(0);
                cloud.owner.setText("your-account");
                cloud.repo.setText("agent-usage-data");
                cloud.ref.setText("main");
                cloud.file.setText("usage-latest.json");
                cloud.auth.setSelection(1);
                cloud.token.setText("");
                cloud.token.clearFocus();
                cloud.status.setText("演示状态 · 用户自行配置私有数据仓库\n按版本检查，未变化时保留已有数据");
                if ((cloud.getWindow().getAttributes().flags & WindowManager.LayoutParams.FLAG_SECURE) == 0)
                    throw new AssertionError("Cloud page must retain FLAG_SECURE");
            });
            capture(cloud, "cloud-sync.png");
            closeCurrent();
            result.putInt("captured", 5);
            result.putBoolean("demonstrationData", true);
            result.putBoolean("cloudSecureFlagRetained", true);
            stage("finished");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable failure) {
            result.putString("stage", currentStage);
            result.putString("failureType", failure.getClass().getSimpleName());
            sendStatus(0, result);
            closeCurrent();
            finish(Activity.RESULT_CANCELED, result);
        }
    }

    private void stage(String value) {
        currentStage = value;
        Bundle progress = new Bundle();
        progress.putString("stage", value);
        sendStatus(0, progress);
    }

    private <T extends Activity> T launch(Class<T> type) {
        final String launchStage;
        if (type == MainActivity.class) launchStage = "launch_main";
        else if (type == UsageWidgetConfigActivity.class) launchStage = "launch_widget";
        else if (type == UsageCloudActivity.class) launchStage = "launch_cloud";
        else throw new AssertionError("Activity is outside the documentation whitelist");
        ActivityMonitor monitor = addMonitor(type.getName(), null, false);
        try {
            // The host starts this fixed component only after this monitor exists.
            stage(launchStage);
            current = waitForMonitorWithTimeout(monitor, 15000);
            if (current == null) throw new AssertionError("Documentation activity did not start");
            waitForIdleSync();
            return type.cast(current);
        } finally {
            removeMonitor(monitor);
        }
    }

    private void closeCurrent() {
        if (current == null) return;
        final Activity activity = current;
        runOnMainSync(activity::finish);
        waitForIdleSync();
        current = null;
    }

    private static View content(Activity activity) {
        ViewGroup frame = activity.findViewById(android.R.id.content);
        if (frame.getChildCount() != 1) throw new AssertionError("Unexpected content hierarchy");
        View view = frame.getChildAt(0);
        // Draw the real scroll content, including controls below the viewport.
        if (view instanceof ScrollView) view = ((ScrollView) view).getChildAt(0);
        return view;
    }

    private void capture(Activity activity, String name) throws Exception {
        waitForIdleSync();
        final Bitmap[] captured = new Bitmap[1];
        runOnMainSync(() -> {
            View view = content(activity);
            if (view.getWidth() <= 0 || view.getHeight() <= 0)
                throw new AssertionError("Activity content is not laid out");
            Bitmap image = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(image);
            TechUi.Backdrop backdrop = new TechUi.Backdrop();
            backdrop.setBounds(0, 0, view.getWidth(), view.getHeight());
            backdrop.draw(canvas);
            view.draw(canvas);
            captured[0] = image;
        });
        File directory = new File(getTargetContext().getFilesDir(), "documentation");
        if (!directory.isDirectory() && !directory.mkdirs()) throw new AssertionError("Cannot create output directory");
        try (FileOutputStream out = new FileOutputStream(new File(directory, name))) {
            if (!captured[0].compress(Bitmap.CompressFormat.PNG, 100, out))
                throw new AssertionError("PNG encoding failed");
        } finally {
            captured[0].recycle();
        }
    }

    private static void selectThemeButtons(View view, int selected) {
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            int index = 0;
            for (int i = 0; i < group.getChildCount(); i++) {
                View child = group.getChildAt(i);
                if ("theme".equals(child.getTag())) child.setSelected(index++ == selected);
                selectThemeButtons(child, selected);
            }
        }
    }

    private static void selectMediumMood(View view) {
        if (view instanceof Button && "segment".equals(view.getTag())) {
            String label = ((Button) view).getText().toString();
            if (label.startsWith("开心") || label.startsWith("平静") || label.startsWith("疲惫"))
                view.setSelected(label.startsWith("平静"));
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) selectMediumMood(group.getChildAt(i));
        }
    }

    private static JSONObject demonstration() throws Exception {
        final long observed = 1790812800L; // Fixed synthetic timestamp, 2026-10-01.
        return new JSONObject().put("schemaVersion", 1).put("source", "codex_app_server")
            .put("chartMode", "simulated").put("observedAt", observed)
            .put("quota", new JSONObject().put("remaining", 60).put("window", "weekly").put("observedAt", observed)
                .put("source", "desktop_codex_app_server").put("resetsAt", 1894579200L))
            .put("daily", new JSONObject().put("observedAt", observed).put("buckets", new JSONArray()))
            .put("resetCards", new JSONObject().put("availableCount", 3).put("expiresAt", 1896048000L)
                .put("expiryStatus", "earliest").put("observedAt", observed).put("source", "account/rateLimits/read"));
    }
}
