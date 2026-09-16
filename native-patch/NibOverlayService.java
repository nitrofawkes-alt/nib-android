package app.byshawn.nib.overlay;

import android.animation.ValueAnimator;
import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.webkit.*;
import java.util.Random;

public class NibOverlayService extends Service {
    public static final String ACTION_SHOW="app.byshawn.nib.SHOW", ACTION_CONFIG="app.byshawn.nib.CONFIG", ACTION_MOVE="app.byshawn.nib.MOVE", ACTION_HIDE="app.byshawn.nib.HIDE";
    private static final String PREFS="nib_overlay", CHANNEL="nib_companion";
    private WindowManager wm;
    private WebView bubble;
    private BroadcastReceiver wakeReceiver;
    private WindowManager.LayoutParams params;
    private Handler handler;
    private Random random;
    private ValueAnimator animator;
    private boolean pinned=false, roaming=true;
    private long manualPauseUntil=0;
    private int sizePx=0, roamGeneration=0;

    private final Runnable roamTask = new Runnable() {
        @Override public void run() { tryRoam(); scheduleRoam(); }
    };

    @Override public void onCreate() {
        super.onCreate();
        wakeReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) {
                if (bubble != null) bubble.post(() -> bubble.evaluateJavascript("window.dispatchEvent(new CustomEvent('nib-native-wake'))", null));
                nudge();
            }
        };
        IntentFilter wf = new IntentFilter(NibWakeService.BROADCAST_WAKE);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(wakeReceiver, wf, Context.RECEIVER_NOT_EXPORTED); else registerReceiver(wakeReceiver, wf);
        wm = (WindowManager)getSystemService(WINDOW_SERVICE);
        handler = new Handler(Looper.getMainLooper());
        random = new Random();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        ensureForeground();
        String action = intent == null ? ACTION_SHOW : intent.getAction();
        if (ACTION_HIDE.equals(action)) {
            prefs().edit().putBoolean("enabled", false).apply();
            stopSelf();
            return START_NOT_STICKY;
        } else if (ACTION_CONFIG.equals(action)) applyConfig(intent);
        else if (ACTION_MOVE.equals(action)) {
            if (params != null) animateTo(intent.getIntExtra("x", params.x), intent.getIntExtra("y", params.y), intent.getBooleanExtra("animated", true) ? 650 : 0);
        } else showFromIntent(intent);
        return START_STICKY;
    }

    private android.content.SharedPreferences prefs() { return getSharedPreferences(PREFS, MODE_PRIVATE); }

    private void ensureForeground() {
        NotificationManager nm = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel c = new NotificationChannel(CHANNEL, "Nib companion", NotificationManager.IMPORTANCE_LOW);
            c.setDescription("Keeps Nib's floating companion alive");
            c.setShowBadge(false);
            nm.createNotificationChannel(c);
        }
        Intent open = getPackageManager().getLaunchIntentForPackage(getPackageName());
        if (open != null) open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        PendingIntent openPi = open == null ? null : PendingIntent.getActivity(this,7,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Intent hide = new Intent(this, NibOverlayService.class).setAction(ACTION_HIDE);
        PendingIntent hidePi = PendingIntent.getService(this,8,hide,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ? new Notification.Builder(this,CHANNEL) : new Notification.Builder(this);
        b.setSmallIcon(R.drawable.ic_nib_status).setContentTitle("Nib is hanging out").setContentText("Tiny menace mode is active.").setOngoing(true).setOnlyAlertOnce(true).setCategory(Notification.CATEGORY_SERVICE).setPriority(Notification.PRIORITY_LOW).addAction(new Notification.Action.Builder(0,"Hide Nib",hidePi).build());
        if (openPi != null) b.setContentIntent(openPi);
        startForeground(7331,b.build());
    }

    private void showFromIntent(Intent intent) {
        android.content.SharedPreferences p = prefs();
        String url = intent != null && intent.hasExtra("url") ? intent.getStringExtra("url") : p.getString("url", "");
        if (url == null || url.isEmpty()) { stopSelf(); return; }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) { stopSelf(); return; }
        int sizeDp = intent != null && intent.hasExtra("sizeDp") ? intent.getIntExtra("sizeDp",112) : p.getInt("sizeDp",112);
        pinned = intent != null && intent.hasExtra("pinned") ? intent.getBooleanExtra("pinned",false) : p.getBoolean("pinned",false);
        roaming = intent != null && intent.hasExtra("roaming") ? intent.getBooleanExtra("roaming",true) : p.getBoolean("roaming",true);
        p.edit().putBoolean("enabled",true).putString("url",url).putInt("sizeDp",sizeDp).putBoolean("pinned",pinned).putBoolean("roaming",roaming).apply();
        if (bubble == null) createBubble(url,sizeDp); else if (!url.equals(bubble.getUrl())) bubble.loadUrl(url);
        scheduleRoam();
    }

    private void createBubble(String url, int sizeDp) {
        sizePx = dp(sizeDp);
        bubble = new WebView(getApplicationContext());
        bubble.setBackgroundColor(Color.TRANSPARENT);
        bubble.setLayerType(View.LAYER_TYPE_SOFTWARE,null);
        bubble.setOverScrollMode(View.OVER_SCROLL_NEVER);
        WebSettings s = bubble.getSettings();
        s.setJavaScriptEnabled(true); s.setDomStorageEnabled(true); s.setMediaPlaybackRequiresUserGesture(false); s.setSupportZoom(false); s.setBuiltInZoomControls(false); s.setDisplayZoomControls(false);
        bubble.setWebViewClient(new WebViewClient(){ @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r){ Uri u=r.getUrl(); return !("http".equals(u.getScheme())||"https".equals(u.getScheme())); }});
        params = new WindowManager.LayoutParams(sizePx,sizePx,Build.VERSION.SDK_INT>=Build.VERSION_CODES.O?WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY:WindowManager.LayoutParams.TYPE_PHONE,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL|WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP|Gravity.START;
        int[] wh = screen();
        android.content.SharedPreferences p = prefs();
        int dx = Math.max(0,wh[0]-sizePx-dp(10)), dy = Math.max(dp(70),wh[1]/3);
        params.x = clamp(p.getInt("x",dx),0,Math.max(0,wh[0]-sizePx));
        params.y = clamp(p.getInt("y",dy),dp(28),Math.max(dp(28),wh[1]-sizePx-dp(48)));
        bubble.setOnTouchListener(new View.OnTouchListener(){
            int sx,sy; float tx,ty; boolean moved;
            @Override public boolean onTouch(View v, MotionEvent ev){
                switch(ev.getActionMasked()){
                    case MotionEvent.ACTION_DOWN:
                        cancelMotion(); sx=params.x; sy=params.y; tx=ev.getRawX(); ty=ev.getRawY(); moved=false; manualPauseUntil=System.currentTimeMillis()+12000L; v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); return true;
                    case MotionEvent.ACTION_MOVE:
                        float mx=ev.getRawX()-tx,my=ev.getRawY()-ty; if(Math.hypot(mx,my)>dp(5)) moved=true;
                        if(moved){ int[] d=screen(); params.x=clamp(sx+(int)mx,0,Math.max(0,d[0]-sizePx)); params.y=clamp(sy+(int)my,dp(28),Math.max(dp(28),d[1]-sizePx-dp(48))); safeUpdate(); }
                        return true;
                    case MotionEvent.ACTION_UP:
                        if(moved){ savePosition(); snapToEdge(); scheduleRoam(); } else { v.performClick(); onTap(); }
                        return true;
                    default: return true;
                }
            }
        });
        wm.addView(bubble,params);
        bubble.loadUrl(url);
    }

    private void onTap() {
        manualPauseUntil = System.currentTimeMillis()+3500L;
        if (bubble != null) bubble.post(() -> bubble.evaluateJavascript("window.dispatchEvent(new CustomEvent('nib-native-tap'))",null));
        if (NibPanelActivity.closeIfOpen()) return;
        handler.postDelayed(this::openPanel,100);
    }

    private void openPanel() {
        try {
            Intent i = new Intent(this,NibPanelActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_NO_ANIMATION);
            i.putExtra("panelUrl",panelUrl());
            startActivity(i);
        } catch(Exception ignored) { openMainApp(); }
    }

    private String panelUrl() {
        String overlay = prefs().getString("url","");
        try { Uri u=Uri.parse(overlay); if(u.getScheme()!=null&&u.getAuthority()!=null) return u.getScheme()+"://"+u.getAuthority()+"/?nibPanel=1"; } catch(Exception ignored){}
        return "https://nib-companion.floot.app/?nibPanel=1";
    }

    private void openMainApp() {
        Intent i=getPackageManager().getLaunchIntentForPackage(getPackageName());
        if(i!=null){ i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_REORDER_TO_FRONT); startActivity(i); }
    }

    private void applyConfig(Intent i) {
        if(i==null) return;
        android.content.SharedPreferences.Editor e=prefs().edit();
        if(i.getBooleanExtra("hasPinned",false)){ pinned=i.getBooleanExtra("pinned",false); e.putBoolean("pinned",pinned); }
        if(i.getBooleanExtra("hasRoaming",false)){ roaming=i.getBooleanExtra("roaming",true); e.putBoolean("roaming",roaming); }
        e.apply(); cancelMotion(); if(pinned) snapToEdge(); scheduleRoam();
    }

    private void scheduleRoam() {
        handler.removeCallbacks(roamTask);
        if(bubble==null||pinned||!roaming) return;
        handler.postDelayed(roamTask, 2600L + random.nextInt(3901));
    }

    private void tryRoam() {
        if(bubble==null||params==null||pinned||!roaming||System.currentTimeMillis()<manualPauseUntil) return;
        PowerManager pm=(PowerManager)getSystemService(POWER_SERVICE);
        if(pm!=null&&!pm.isInteractive()) return;
        int[] d=screen();
        int minY=dp(48), maxY=Math.max(minY,d[1]-sizePx-dp(68));
        int maxX=Math.max(0,d[0]-sizePx);
        int roll=random.nextInt(100);
        if(roll<14){ dispatchMotion("flip", "none"); return; }
        if(roll<24){ dispatchMotion("hop", "none"); nudge(); return; }

        int targetX=random.nextInt(maxX+1);
        int targetY=minY+(maxY>minY?random.nextInt(maxY-minY+1):0);
        if(Math.abs(targetX-params.x)<dp(90)) targetX = params.x < maxX/2 ? Math.max(0,maxX-dp(8)) : Math.min(maxX,dp(8));
        String direction=targetX<params.x?"left":"right";
        dispatchMotion("run",direction);
        int duration=780+random.nextInt(720);
        animateTo(targetX,targetY,duration);

        if(random.nextInt(100)<22){
            final String dir=direction;
            handler.postDelayed(() -> { if(!pinned&&roaming) dispatchMotion(random.nextBoolean()?"flip":"hop",dir); }, duration+120L);
        }
    }

    private void dispatchMotion(String kind, String direction) {
        if(bubble==null) return;
        String js="window.dispatchEvent(new CustomEvent('nib-native-motion',{detail:{kind:'"+kind+"',direction:'"+direction+"'}}))";
        bubble.post(() -> bubble.evaluateJavascript(js,null));
    }

    private void nudge() {
        if(params==null) return;
        int[] d=screen();
        int tx=clamp(params.x+(random.nextBoolean()?dp(42):-dp(42)),0,Math.max(0,d[0]-sizePx));
        dispatchMotion("hop",tx<params.x?"left":"right");
        animateTo(tx,params.y,260);
    }

    private void snapToEdge() {
        if(params==null) return;
        int[] d=screen();
        int x=params.x+sizePx/2<d[0]/2?0:Math.max(0,d[0]-sizePx);
        animateTo(x,params.y,260);
    }

    private void animateTo(int targetX,int targetY,int duration) {
        if(params==null) return;
        if(animator!=null) animator.cancel();
        int[] d=screen();
        targetX=clamp(targetX,0,Math.max(0,d[0]-sizePx));
        targetY=clamp(targetY,dp(28),Math.max(dp(28),d[1]-sizePx-dp(48)));
        if(duration<=0){ params.x=targetX; params.y=targetY; safeUpdate(); savePosition(); return; }
        final int ax=params.x,ay=params.y,tx=targetX,ty=targetY;
        animator=ValueAnimator.ofFloat(0f,1f);
        animator.setDuration(duration);
        animator.setInterpolator(new AccelerateDecelerateInterpolator());
        animator.addUpdateListener(a->{ float f=(float)a.getAnimatedValue(); params.x=ax+Math.round((tx-ax)*f); params.y=ay+Math.round((ty-ay)*f); safeUpdate(); if(f>=.999f) savePosition(); });
        animator.start();
    }

    private void safeUpdate(){ try{ if(bubble!=null&&params!=null) wm.updateViewLayout(bubble,params); }catch(Exception ignored){} }
    private void savePosition(){ if(params!=null) prefs().edit().putInt("x",params.x).putInt("y",params.y).apply(); }
    private int[] screen(){ if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.R){ android.view.WindowMetrics m=wm.getCurrentWindowMetrics(); Rect b=m.getBounds(); return new int[]{b.width(),b.height()}; } android.util.DisplayMetrics m=new android.util.DisplayMetrics(); wm.getDefaultDisplay().getMetrics(m); return new int[]{m.widthPixels,m.heightPixels}; }
    private int dp(int v){ return Math.round(v*getResources().getDisplayMetrics().density); }
    private int clamp(int v,int lo,int hi){ return Math.max(lo,Math.min(hi,v)); }
    private void cancelMotion(){ roamGeneration++; handler.removeCallbacks(roamTask); if(animator!=null){ animator.cancel(); animator=null; } }
    private void closeBubble(){ cancelMotion(); if(bubble!=null){ try{wm.removeView(bubble);}catch(Exception ignored){} bubble.destroy(); bubble=null; } }
    @Override public void onDestroy(){ closeBubble(); try{if(wakeReceiver!=null)unregisterReceiver(wakeReceiver);}catch(Exception ignored){} super.onDestroy(); }
    @Override public android.os.IBinder onBind(Intent intent){ return null; }
}
