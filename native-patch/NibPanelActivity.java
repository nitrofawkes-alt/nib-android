package app.byshawn.nib.overlay;

import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.WindowManager;
import com.getcapacitor.BridgeActivity;

public class NibPanelActivity extends BridgeActivity {
    private static volatile NibPanelActivity activeInstance;
    private String panelSize = "compact";

    public static boolean closeIfOpen() {
        NibPanelActivity activity = activeInstance;
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return false;
        activity.runOnUiThread(activity::finish);
        return true;
    }

    public static boolean isOpen() {
        NibPanelActivity activity = activeInstance;
        return activity != null && !activity.isFinishing() && !activity.isDestroyed();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        activeInstance = this;
        getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

        String target = getIntent() != null ? getIntent().getStringExtra("panelUrl") : null;
        if (target == null || target.trim().isEmpty()) target = "https://nib-companion.floot.app/?nibPanel=1";
        final String panelUrl = target;
        if (bridge != null && bridge.getWebView() != null) {
            bridge.getWebView().setBackgroundColor(Color.TRANSPARENT);
            bridge.getWebView().post(() -> bridge.getWebView().loadUrl(panelUrl));
        }
        setPanelSize("compact");
    }

    @Override
    public void onDestroy() {
        if (activeInstance == this) activeInstance = null;
        super.onDestroy();
    }

    public void setPanelSize(String mode) {
        panelSize = mode == null ? "compact" : mode;
        WindowManager.LayoutParams attrs = getWindow().getAttributes();
        attrs.gravity = Gravity.CENTER;
        if ("full".equals(panelSize)) {
            attrs.width = WindowManager.LayoutParams.MATCH_PARENT;
            attrs.height = WindowManager.LayoutParams.MATCH_PARENT;
        } else {
            WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
            int width;
            int height;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                Rect bounds = wm.getCurrentWindowMetrics().getBounds();
                width = bounds.width();
                height = bounds.height();
            } else {
                android.util.DisplayMetrics metrics = new android.util.DisplayMetrics();
                wm.getDefaultDisplay().getMetrics(metrics);
                width = metrics.widthPixels;
                height = metrics.heightPixels;
            }
            float widthRatio = "large".equals(panelSize) ? 0.96f : 0.90f;
            float heightRatio = "large".equals(panelSize) ? 0.82f : 0.62f;
            attrs.width = Math.max(dp(300), Math.round(width * widthRatio));
            attrs.height = Math.max(dp(360), Math.round(height * heightRatio));
        }
        getWindow().setAttributes(attrs);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
