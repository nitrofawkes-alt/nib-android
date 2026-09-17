package app.byshawn.nib.overlay;

import android.animation.ValueAnimator;
import android.app.*;
import android.content.*;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.webkit.*;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.Random;

public class NibOverlayService extends Service {
    public static final String ACTION_SHOW="app.byshawn.nib.SHOW", ACTION_CONFIG="app.byshawn.nib.CONFIG", ACTION_MOVE="app.byshawn.nib.MOVE", ACTION_HIDE="app.byshawn.nib.HIDE";
    private static final String PREFS="nib_overlay", CHANNEL="nib_companion";
    private static final String[] CHATTER={
        "I'm supervising.",
        "Carry on. I'm definitely helping.",
        "I have no idea what this rectangle does, but I support you.",
        "Tiny employee. Zero payroll.",
        "I was promised enrichment activities.",
        "Just checking that you haven't forgotten I'm here."
    };
    private static final String[] PET_LINES={
        "Acceptable.",
        "Okay, that was nice. Don't make it weird.",
        "Morale improved.",
        "I will allow one more."
    };

    private WindowManager wm;
    private WebView bubble;
    private BroadcastReceiver wakeReceiver;
    private WindowManager.LayoutParams params;
    private Handler handler;
    private Random random;
    private ValueAnimator animator;
    private boolean pinned=false, roaming=true, chatter=true;
    private boolean touchDown=false, touchMoved=false, longPressFired=false;
    private long manualPauseUntil=0;
    private int sizePx=0, roamGeneration=0;
    private View quickMenu;
    private TextView speechBubble;
    private DoghouseTarget doghouse;
    private WindowManager.LayoutParams doghouseParams;
    private float doghouseProximity=0f;
    private boolean doghouseDragActive=false;

    private final Runnable roamTask = new Runnable(){ @Override public void run(){ tryRoam(); scheduleRoam(); }};
    private final Runnable chatterTask = new Runnable(){ @Override public void run(){ tryChatter(); scheduleChatter(); }};
    private final Runnable selfHealTask = new Runnable(){ @Override public void run(){ trySelfHeal(); scheduleSelfHeal(); }};
    private final Runnable longPressTask = () -> {
        if (!touchDown || touchMoved || bubble == null) return;
        longPressFired = true;
        try { bubble.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS); } catch(Exception ignored) {}
        showQuickMenu();
    };

    @Override public void onCreate(){
        super.onCreate();
        wakeReceiver=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent i){
            if(bubble!=null) bubble.post(()->bubble.evaluateJavascript("window.dispatchEvent(new CustomEvent('nib-native-wake'))",null));
            nudge();
        }};
        IntentFilter wf=new IntentFilter(NibWakeService.BROADCAST_WAKE);
        if(Build.VERSION.SDK_INT>=33) registerReceiver(wakeReceiver,wf,Context.RECEIVER_NOT_EXPORTED); else registerReceiver(wakeReceiver,wf);
        wm=(WindowManager)getSystemService(WINDOW_SERVICE);
        handler=new Handler(Looper.getMainLooper());
        random=new Random();
        scheduleSelfHeal();
    }

    @Override public int onStartCommand(Intent intent,int flags,int startId){
        ensureForeground();
        String action=intent==null?ACTION_SHOW:intent.getAction();
        if(ACTION_HIDE.equals(action)){
            prefs().edit().putBoolean("enabled",false).apply();
            stopSelf();
            return START_NOT_STICKY;
        } else if(ACTION_CONFIG.equals(action)) applyConfig(intent);
        else if(ACTION_MOVE.equals(action)){
            if(params!=null) animateTo(intent.getIntExtra("x",params.x),intent.getIntExtra("y",params.y),intent.getBooleanExtra("animated",true)?650:0);
        } else showFromIntent(intent);
        scheduleSelfHeal();
        return START_STICKY;
    }

    private android.content.SharedPreferences prefs(){ return getSharedPreferences(PREFS,MODE_PRIVATE); }

    private void ensureForeground(){
        NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.O){
            NotificationChannel c=new NotificationChannel(CHANNEL,"Nib companion",NotificationManager.IMPORTANCE_LOW);
            c.setDescription("Keeps Nib's floating companion alive"); c.setShowBadge(false); nm.createNotificationChannel(c);
        }
        Intent open=getPackageManager().getLaunchIntentForPackage(getPackageName());
        if(open!=null) open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        PendingIntent openPi=open==null?null:PendingIntent.getActivity(this,7,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Intent hide=new Intent(this,NibOverlayService.class).setAction(ACTION_HIDE);
        PendingIntent hidePi=PendingIntent.getService(this,8,hide,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b=Build.VERSION.SDK_INT>=Build.VERSION_CODES.O?new Notification.Builder(this,CHANNEL):new Notification.Builder(this);
        b.setSmallIcon(R.drawable.ic_nib_status).setContentTitle("Nib is hanging out").setContentText("Tiny menace mode is active.").setOngoing(true).setOnlyAlertOnce(true).setCategory(Notification.CATEGORY_SERVICE).setPriority(Notification.PRIORITY_LOW).addAction(new Notification.Action.Builder(0,"Hide Nib",hidePi).build());
        if(openPi!=null) b.setContentIntent(openPi);
        startForeground(7331,b.build());
    }

    private void showFromIntent(Intent intent){
        android.content.SharedPreferences p=prefs();
        String url=intent!=null&&intent.hasExtra("url")?intent.getStringExtra("url"):p.getString("url","");
        if(url==null||url.isEmpty()){ stopSelf(); return; }
        if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.M&&!Settings.canDrawOverlays(this)){ stopSelf(); return; }
        int sizeDp=intent!=null&&intent.hasExtra("sizeDp")?intent.getIntExtra("sizeDp",112):p.getInt("sizeDp",112);
        pinned=intent!=null&&intent.hasExtra("pinned")?intent.getBooleanExtra("pinned",false):p.getBoolean("pinned",false);
        roaming=intent!=null&&intent.hasExtra("roaming")?intent.getBooleanExtra("roaming",true):p.getBoolean("roaming",true);
        chatter=p.getBoolean("chatter",true);
        p.edit().putBoolean("enabled",true).putString("url",url).putInt("sizeDp",sizeDp).putBoolean("pinned",pinned).putBoolean("roaming",roaming).putBoolean("chatter",chatter).apply();
        if(bubble==null) createBubble(url,sizeDp); else if(!url.equals(bubble.getUrl())) bubble.loadUrl(url);
        scheduleRoam(); scheduleChatter();
    }

    private void createBubble(String url,int sizeDp){
        sizePx=dp(sizeDp);
        bubble=new WebView(getApplicationContext());
        bubble.setBackgroundColor(Color.TRANSPARENT);
        bubble.setLayerType(View.LAYER_TYPE_SOFTWARE,null);
        bubble.setOverScrollMode(View.OVER_SCROLL_NEVER);
        WebSettings s=bubble.getSettings();
        s.setJavaScriptEnabled(true); s.setDomStorageEnabled(true); s.setMediaPlaybackRequiresUserGesture(false); s.setSupportZoom(false); s.setBuiltInZoomControls(false); s.setDisplayZoomControls(false);
        bubble.setWebViewClient(new WebViewClient(){@Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){ Uri u=r.getUrl(); return !("http".equals(u.getScheme())||"https".equals(u.getScheme())); }});
        params=new WindowManager.LayoutParams(sizePx,sizePx,Build.VERSION.SDK_INT>=Build.VERSION_CODES.O?WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY:WindowManager.LayoutParams.TYPE_PHONE,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL|WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,PixelFormat.TRANSLUCENT);
        params.gravity=Gravity.TOP|Gravity.START;
        int[] wh=screen(); android.content.SharedPreferences p=prefs();
        int dx=Math.max(0,wh[0]-sizePx-dp(10)),dy=Math.max(dp(70),wh[1]/3);
        params.x=clamp(p.getInt("x",dx),0,Math.max(0,wh[0]-sizePx));
        params.y=clamp(p.getInt("y",dy),dp(28),Math.max(dp(28),wh[1]-sizePx-dp(48)));
        bubble.setOnTouchListener(new View.OnTouchListener(){
            int sx,sy; float tx,ty;
            @Override public boolean onTouch(View v,MotionEvent ev){
                switch(ev.getActionMasked()){
                    case MotionEvent.ACTION_DOWN:
                        closeQuickMenu(); cancelMotion(); touchDown=true; touchMoved=false; longPressFired=false;
                        sx=params.x; sy=params.y; tx=ev.getRawX(); ty=ev.getRawY(); manualPauseUntil=System.currentTimeMillis()+12000L;
                        handler.removeCallbacks(longPressTask); handler.postDelayed(longPressTask,520L);
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float mx=ev.getRawX()-tx,my=ev.getRawY()-ty;
                        if(Math.hypot(mx,my)>dp(5)){
                            if(!touchMoved){
                                touchMoved=true;
                                handler.removeCallbacks(longPressTask);
                                showDoghouse();
                                doghouseDragActive=true;
                                dispatchUiEvent("nib-native-drag-start",null);
                            }
                        }
                        if(touchMoved){
                            int[] d=screen();
                            params.x=clamp(sx+(int)mx,0,Math.max(0,d[0]-sizePx));
                            params.y=clamp(sy+(int)my,dp(28),Math.max(dp(28),d[1]-sizePx-dp(48)));
                            safeUpdate();
                            updateDoghouseProximity(ev.getRawX(),ev.getRawY());
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        boolean dropped=ev.getActionMasked()==MotionEvent.ACTION_UP&&touchMoved&&doghouseProximity>=.72f;
                        touchDown=false; handler.removeCallbacks(longPressTask);
                        if(longPressFired){ longPressFired=false; hideDoghouse(); doghouseDragActive=false; return true; }
                        if(touchMoved){
                            hideDoghouse();
                            doghouseDragActive=false;
                            dispatchUiEvent("nib-native-drag-end",null);
                            if(dropped){
                                try{v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);}catch(Exception ignored){}
                                dispatchUiEvent("nib-native-doghouse",null);
                                prefs().edit().putBoolean("enabled",false).apply();
                                NibPanelActivity.closeIfOpen();
                                handler.postDelayed(()->stopSelf(),260L);
                                return true;
                            }
                            savePosition(); snapToEdge(); scheduleRoam();
                        } else { v.performClick(); onTap(); }
                        return true;
                    default:return true;
                }
            }
        });
        wm.addView(bubble,params); bubble.loadUrl(url);
    }

    private void onTap(){
        closeQuickMenu(); manualPauseUntil=System.currentTimeMillis()+3500L;
        if(bubble!=null) bubble.post(()->bubble.evaluateJavascript("window.dispatchEvent(new CustomEvent('nib-native-tap'))",null));
        if(NibPanelActivity.closeIfOpen()) return;
        handler.postDelayed(this::openPanel,100);
    }

    private void openPanel(){
        try{
            Intent i=new Intent(this,NibPanelActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_NO_ANIMATION);
            i.putExtra("panelUrl",panelUrl()); startActivity(i);
        }catch(Exception ignored){ openMainApp(); }
    }

    private String panelUrl(){
        String overlay=prefs().getString("url","");
        try{ Uri u=Uri.parse(overlay); if(u.getScheme()!=null&&u.getAuthority()!=null) return u.getScheme()+"://"+u.getAuthority()+"/?nibPanel=1"; }catch(Exception ignored){}
        return "https://nib-companion.floot.app/?nibPanel=1";
    }

    private void openMainApp(){
        Intent i=getPackageManager().getLaunchIntentForPackage(getPackageName());
        if(i!=null){ i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_REORDER_TO_FRONT); startActivity(i); }
    }

    private void applyConfig(Intent i){
        if(i==null)return;
        android.content.SharedPreferences.Editor e=prefs().edit();
        if(i.getBooleanExtra("hasPinned",false)){ pinned=i.getBooleanExtra("pinned",false); e.putBoolean("pinned",pinned); }
        if(i.getBooleanExtra("hasRoaming",false)){ roaming=i.getBooleanExtra("roaming",true); e.putBoolean("roaming",roaming); }
        if(i.getBooleanExtra("hasChatter",false)){ chatter=i.getBooleanExtra("chatter",true); e.putBoolean("chatter",chatter); }
        e.apply(); cancelMotion(); if(pinned) snapToEdge(); scheduleRoam(); scheduleChatter();
    }

    private void scheduleSelfHeal(){
        if(handler==null)return; handler.removeCallbacks(selfHealTask); if(!prefs().getBoolean("enabled",false))return; handler.postDelayed(selfHealTask,6000L);
    }

    private void trySelfHeal(){
        if(!prefs().getBoolean("enabled",false))return;
        if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.M&&!Settings.canDrawOverlays(this))return;
        boolean healthy=bubble!=null&&bubble.isAttachedToWindow()&&bubble.getUrl()!=null;
        if(healthy)return;
        if(bubble!=null){try{wm.removeView(bubble);}catch(Exception ignored){}try{bubble.destroy();}catch(Exception ignored){}bubble=null;}
        showFromIntent(null);
    }

    private void scheduleRoam(){
        handler.removeCallbacks(roamTask);
        if(bubble==null||pinned||!roaming)return;
        handler.postDelayed(roamTask,2600L+random.nextInt(3901));
    }

    private void tryRoam(){
        if(bubble==null||params==null||pinned||!roaming||System.currentTimeMillis()<manualPauseUntil)return;
        PowerManager pm=(PowerManager)getSystemService(POWER_SERVICE); if(pm!=null&&!pm.isInteractive())return;
        int[] d=screen(); int minY=dp(48),maxY=Math.max(minY,d[1]-sizePx-dp(68)),maxX=Math.max(0,d[0]-sizePx);
        if(random.nextInt(100)<13){ dispatchMotion("hop","none"); return; }

        boolean walking=random.nextInt(100)<58;
        int targetX,targetY,duration;
        if(walking){
            int step=dp(55+random.nextInt(91));
            targetX=clamp(params.x+(random.nextBoolean()?step:-step),0,maxX);
            targetY=clamp(params.y+(random.nextInt(dp(81))-dp(40)),minY,maxY);
            duration=1050+random.nextInt(551);
        } else {
            targetX=random.nextInt(maxX+1);
            targetY=minY+(maxY>minY?random.nextInt(maxY-minY+1):0);
            if(Math.abs(targetX-params.x)<dp(140)) targetX=params.x<maxX/2?Math.max(0,maxX-dp(8)):Math.min(maxX,dp(8));
            duration=650+random.nextInt(451);
        }
        String direction=targetX<params.x?"left":"right";
        dispatchMotion(walking?"walk":"run",direction);
        animateTo(targetX,targetY,duration);
        if(!walking&&random.nextInt(100)<30){
            final String dir=direction;
            handler.postDelayed(()->{ if(!pinned&&roaming&&animator!=null&&animator.isRunning()) dispatchMotion("flip",dir); },Math.max(180,duration/3));
        }
    }

    private void dispatchMotion(String kind,String direction){
        if(bubble==null)return;
        String js="window.dispatchEvent(new CustomEvent('nib-native-motion',{detail:{kind:'"+kind+"',direction:'"+direction+"'}}))";
        bubble.post(()->bubble.evaluateJavascript(js,null));
    }

    private void startChaos(){
        if(params==null)return;
        closeQuickMenu(); pinned=false; roaming=true; prefs().edit().putBoolean("pinned",false).putBoolean("roaming",true).apply();
        int[] d=screen(); int maxX=Math.max(0,d[0]-sizePx),minY=dp(48),maxY=Math.max(minY,d[1]-sizePx-dp(68));
        int targetX=params.x<maxX/2?Math.max(0,maxX-dp(8)):Math.min(maxX,dp(8));
        int targetY=minY+(maxY>minY?random.nextInt(maxY-minY+1):0);
        String dir=targetX<params.x?"left":"right";
        dispatchMotion("run",dir); animateTo(targetX,targetY,1050);
        handler.postDelayed(()->dispatchMotion("flip",dir),360);
        handler.postDelayed(()->showSpeech("Parkour. Technically."),1120);
        scheduleRoam();
    }

    private void showQuickMenu(){
        closeQuickMenu(); if(params==null)return;
        LinearLayout bar=new LinearLayout(this); bar.setOrientation(LinearLayout.HORIZONTAL); bar.setPadding(dp(5),dp(5),dp(5),dp(5));
        bar.setBackground(roundBg(Color.argb(245,28,20,38),Color.argb(170,171,142,240),14));
        addMenuButton(bar,"Pet",()->{ closeQuickMenu(); dispatchMotion("hop","none"); showSpeech(PET_LINES[random.nextInt(PET_LINES.length)]); });
        addMenuButton(bar,"Chaos",this::startChaos);
        addMenuButton(bar,pinned?"Release":"Timeout",()->{
            pinned=!pinned; prefs().edit().putBoolean("pinned",pinned).apply(); closeQuickMenu();
            if(pinned){ cancelMotion(); snapToEdge(); showSpeech("Fine. Timeout. I will reflect on nothing."); }
            else { showSpeech("PAROLE."); scheduleRoam(); }
        });
        addMenuButton(bar,chatter?"Quiet":"Chatter",()->{
            chatter=!chatter; prefs().edit().putBoolean("chatter",chatter).apply(); closeQuickMenu();
            showSpeech(chatter?"Unsolicited opinions restored.":"Fine. Mysterious silence."); scheduleChatter();
        });
        quickMenu=bar;
        int[] d=screen(); int width=dp(244); int x=clamp(params.x+sizePx/2-width/2,dp(6),Math.max(dp(6),d[0]-width-dp(6)));
        int y=params.y-dp(58); if(y<dp(32)) y=params.y+sizePx+dp(6);
        WindowManager.LayoutParams lp=new WindowManager.LayoutParams(width,WindowManager.LayoutParams.WRAP_CONTENT,Build.VERSION.SDK_INT>=Build.VERSION_CODES.O?WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY:WindowManager.LayoutParams.TYPE_PHONE,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,PixelFormat.TRANSLUCENT);
        lp.gravity=Gravity.TOP|Gravity.START; lp.x=x; lp.y=y;
        try{ wm.addView(bar,lp); }catch(Exception ignored){ quickMenu=null; }
    }

    private void addMenuButton(LinearLayout parent,String label,Runnable action){
        TextView b=new TextView(this); b.setText(label); b.setTextColor(Color.WHITE); b.setTextSize(11); b.setGravity(Gravity.CENTER); b.setPadding(dp(7),dp(8),dp(7),dp(8));
        b.setBackground(roundBg(Color.argb(70,255,255,255),Color.TRANSPARENT,10));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,WindowManager.LayoutParams.WRAP_CONTENT,1f); p.setMargins(dp(2),0,dp(2),0); parent.addView(b,p);
        b.setOnClickListener(v->action.run());
    }

    private void closeQuickMenu(){
        if(quickMenu!=null){ try{ wm.removeView(quickMenu); }catch(Exception ignored){} quickMenu=null; }
    }

    private void scheduleChatter(){
        handler.removeCallbacks(chatterTask);
        if(bubble==null||!chatter)return;
        handler.postDelayed(chatterTask,35000L+random.nextInt(50001));
    }

    private void tryChatter(){
        if(!chatter||bubble==null||NibPanelActivity.isOpen())return;
        PowerManager pm=(PowerManager)getSystemService(POWER_SERVICE); if(pm!=null&&!pm.isInteractive())return;
        showSpeech(CHATTER[random.nextInt(CHATTER.length)]);
    }

    private void showSpeech(String text){
        if(params==null||text==null||text.isEmpty())return;
        if(speechBubble!=null){ try{ wm.removeView(speechBubble); }catch(Exception ignored){} speechBubble=null; }
        TextView t=new TextView(this); t.setText(text); t.setTextColor(Color.WHITE); t.setTextSize(12); t.setPadding(dp(10),dp(7),dp(10),dp(7)); t.setMaxWidth(dp(240)); t.setBackground(roundBg(Color.argb(245,30,20,42),Color.argb(170,180,150,245),13));
        speechBubble=t;
        int[] d=screen(); int x=clamp(params.x+sizePx/2-dp(105),dp(8),Math.max(dp(8),d[0]-dp(218))); int y=params.y-dp(62); if(y<dp(32))y=params.y+sizePx+dp(6);
        WindowManager.LayoutParams lp=new WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT,WindowManager.LayoutParams.WRAP_CONTENT,Build.VERSION.SDK_INT>=Build.VERSION_CODES.O?WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY:WindowManager.LayoutParams.TYPE_PHONE,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,PixelFormat.TRANSLUCENT);
        lp.gravity=Gravity.TOP|Gravity.START; lp.x=x; lp.y=y;
        try{ wm.addView(t,lp); handler.postDelayed(()->{ if(speechBubble==t){ try{wm.removeView(t);}catch(Exception ignored){} speechBubble=null; } },4200L); }catch(Exception ignored){ speechBubble=null; }
    }

    private void dispatchUiEvent(String name,String detailJson){
        if(bubble==null)return;
        String js=detailJson==null||detailJson.isEmpty()
            ? "window.dispatchEvent(new CustomEvent('"+name+"'))"
            : "window.dispatchEvent(new CustomEvent('"+name+"',{detail:"+detailJson+"}))";
        bubble.post(()->bubble.evaluateJavascript(js,null));
    }

    private void showDoghouse(){
        if(doghouse!=null)return;
        doghouseProximity=0f;
        doghouse=new DoghouseTarget(this);
        int type=Build.VERSION.SDK_INT>=Build.VERSION_CODES.O?WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY:WindowManager.LayoutParams.TYPE_PHONE;
        doghouseParams=new WindowManager.LayoutParams(dp(116),dp(116),type,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT);
        doghouseParams.gravity=Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL;
        doghouseParams.y=dp(22);
        try{wm.addView(doghouse,doghouseParams);}catch(Exception ignored){doghouse=null;doghouseParams=null;}
    }

    private void updateDoghouseProximity(float rawX,float rawY){
        if(doghouse==null)return;
        int[] d=screen();
        float cx=d[0]/2f;
        float cy=d[1]-dp(22)-dp(58);
        float dist=(float)Math.hypot(rawX-cx,rawY-cy);
        float edge=Math.max(0f,dist-dp(52));
        doghouseProximity=Math.max(0f,Math.min(1f,1f-edge/dp(125)));
        doghouse.setProximity(doghouseProximity);
        dispatchUiEvent("nib-native-drag-progress","{proximity:"+Float.toString(doghouseProximity)+"}");
    }

    private void hideDoghouse(){
        doghouseProximity=0f;
        if(doghouse!=null){try{wm.removeView(doghouse);}catch(Exception ignored){}doghouse=null;}
        doghouseParams=null;
    }

    private final class DoghouseTarget extends View {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        private float proximity=0f;
        DoghouseTarget(Context context){super(context);setLayerType(View.LAYER_TYPE_SOFTWARE,null);}
        void setProximity(float value){proximity=Math.max(0f,Math.min(1f,value));invalidate();}
        @Override protected void onDraw(Canvas c){
            super.onDraw(c);
            float w=getWidth(),h=getHeight(),cx=w/2f;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb((int)(42+105*proximity),151,105,255));
            c.drawCircle(cx,h*.54f,w*(.42f+.05f*proximity),paint);

            paint.setColor(Color.rgb(30,21,43));
            c.drawRoundRect(new RectF(w*.22f,h*.42f,w*.78f,h*.86f),dp(9),dp(9),paint);
            Path roof=new Path();
            roof.moveTo(w*.13f,h*.47f);roof.lineTo(cx,h*.16f);roof.lineTo(w*.87f,h*.47f);roof.close();
            paint.setColor(Color.rgb(91,61,139));c.drawPath(roof,paint);
            Path roofInner=new Path();
            roofInner.moveTo(w*.23f,h*.46f);roofInner.lineTo(cx,h*.25f);roofInner.lineTo(w*.77f,h*.46f);roofInner.close();
            paint.setColor(Color.rgb(48,32,70));c.drawPath(roofInner,paint);

            paint.setColor(Color.rgb(9,7,14));
            RectF door=new RectF(w*.38f,h*.57f,w*.62f,h*.87f);
            c.drawRoundRect(door,w*.12f,w*.12f,paint);
            paint.setColor(Color.argb((int)(120+115*proximity),196,165,255));
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(proximity>.72f?3:2));
            c.drawRoundRect(new RectF(w*.2f,h*.4f,w*.8f,h*.88f),dp(10),dp(10),paint);
            paint.setStyle(Paint.Style.FILL);

            paint.setColor(Color.rgb(224,211,248));paint.setTextAlign(Paint.Align.CENTER);paint.setTextSize(dp(9));paint.setFakeBoldText(true);
            c.drawText(proximity>.72f?"DROP NIB":"DOGHOUSE",cx,h*.98f,paint);
            paint.setFakeBoldText(false);
        }
    }

    private GradientDrawable roundBg(int fill,int stroke,float radiusDp){
        GradientDrawable g=new GradientDrawable(); g.setColor(fill); g.setCornerRadius(dp((int)radiusDp)); if(stroke!=Color.TRANSPARENT)g.setStroke(dp(1),stroke); return g;
    }

    private void nudge(){
        if(params==null)return;
        int[] d=screen(); int tx=clamp(params.x+(random.nextBoolean()?dp(42):-dp(42)),0,Math.max(0,d[0]-sizePx));
        dispatchMotion("hop",tx<params.x?"left":"right"); animateTo(tx,params.y,260);
    }

    private void snapToEdge(){
        if(params==null)return;
        int[] d=screen(); int x=params.x+sizePx/2<d[0]/2?0:Math.max(0,d[0]-sizePx); animateTo(x,params.y,260);
    }

    private void animateTo(int targetX,int targetY,int duration){
        if(params==null)return; if(animator!=null)animator.cancel();
        int[] d=screen(); targetX=clamp(targetX,0,Math.max(0,d[0]-sizePx)); targetY=clamp(targetY,dp(28),Math.max(dp(28),d[1]-sizePx-dp(48)));
        if(duration<=0){ params.x=targetX; params.y=targetY; safeUpdate(); savePosition(); return; }
        final int ax=params.x,ay=params.y,tx=targetX,ty=targetY;
        animator=ValueAnimator.ofFloat(0f,1f); animator.setDuration(duration); animator.setInterpolator(new AccelerateDecelerateInterpolator());
        animator.addUpdateListener(a->{ float f=(float)a.getAnimatedValue(); params.x=ax+Math.round((tx-ax)*f); params.y=ay+Math.round((ty-ay)*f); safeUpdate(); if(f>=.999f)savePosition(); }); animator.start();
    }

    private void safeUpdate(){ try{if(bubble!=null&&params!=null)wm.updateViewLayout(bubble,params);}catch(Exception ignored){} }
    private void savePosition(){ if(params!=null)prefs().edit().putInt("x",params.x).putInt("y",params.y).apply(); }
    private int[] screen(){ if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.R){ android.view.WindowMetrics m=wm.getCurrentWindowMetrics(); Rect b=m.getBounds(); return new int[]{b.width(),b.height()}; } android.util.DisplayMetrics m=new android.util.DisplayMetrics(); wm.getDefaultDisplay().getMetrics(m); return new int[]{m.widthPixels,m.heightPixels}; }
    private int dp(int v){ return Math.round(v*getResources().getDisplayMetrics().density); }
    private int clamp(int v,int lo,int hi){ return Math.max(lo,Math.min(hi,v)); }
    private void cancelMotion(){ roamGeneration++; handler.removeCallbacks(roamTask); if(animator!=null){animator.cancel();animator=null;} }
    private void closeBubble(){ cancelMotion(); closeQuickMenu(); hideDoghouse(); doghouseDragActive=false; handler.removeCallbacks(chatterTask); if(speechBubble!=null){try{wm.removeView(speechBubble);}catch(Exception ignored){}speechBubble=null;} if(bubble!=null){try{wm.removeView(bubble);}catch(Exception ignored){}bubble.destroy();bubble=null;} }
    @Override public void onDestroy(){ if(handler!=null)handler.removeCallbacks(selfHealTask); closeBubble(); try{if(wakeReceiver!=null)unregisterReceiver(wakeReceiver);}catch(Exception ignored){} super.onDestroy(); }
    @Override public android.os.IBinder onBind(Intent intent){ return null; }
}
