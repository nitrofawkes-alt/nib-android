package app.byshawn.nib.overlay;

import android.app.Service;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebSettings;
import android.webkit.WebView;

public class NibBridgeCurtainService extends Service {
 public static final String ACTION_SHOW="app.byshawn.nib.BRIDGE_CURTAIN_SHOW", ACTION_HIDE="app.byshawn.nib.BRIDGE_CURTAIN_HIDE";
 private WindowManager wm; private WebView curtain;
 @Override public void onCreate(){super.onCreate();wm=(WindowManager)getSystemService(WINDOW_SERVICE);}
 @Override public int onStartCommand(Intent intent,int flags,int startId){String action=intent==null?ACTION_SHOW:intent.getAction();if(ACTION_HIDE.equals(action)){removeCurtain();stopSelf();return START_NOT_STICKY;}showCurtain();return START_NOT_STICKY;}
 private void showCurtain(){
  if(curtain!=null)return; if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.M&&!Settings.canDrawOverlays(this))return;
  curtain=new WebView(getApplicationContext());curtain.setBackgroundColor(Color.TRANSPARENT);curtain.setLayerType(View.LAYER_TYPE_SOFTWARE,null);WebSettings s=curtain.getSettings();s.setJavaScriptEnabled(false);s.setDomStorageEnabled(false);s.setSupportZoom(false);s.setBuiltInZoomControls(false);s.setDisplayZoomControls(false);
  String html="<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1,maximum-scale=1'><style>html,body{margin:0;width:100%;height:100%;overflow:hidden;background:#09070f;color:#f6f2ff;font-family:system-ui,-apple-system,sans-serif}body{display:grid;place-items:center;background:radial-gradient(circle at 50% 36%,#2e1d49 0,#171020 28%,#09070f 67%)}.wrap{text-align:center;transform:translateY(-2vh);padding:24px}.label{font:600 11px/1.2 ui-monospace,monospace;letter-spacing:.22em;color:#bda1ff;text-transform:uppercase;margin-bottom:18px}img{width:min(46vw,220px);height:auto;filter:drop-shadow(0 18px 35px rgba(128,79,255,.32));animation:bob 1.65s ease-in-out infinite}h1{font-size:22px;margin:18px 0 6px}p{margin:0;color:#aaa0bb;font-size:14px}@keyframes bob{0%,100%{transform:translateY(3px) rotate(-1deg) scale(.99)}50%{transform:translateY(-7px) rotate(1deg) scale(1.01)}}</style></head><body><div class='wrap'><div class='label'>Nib · thinking</div><img src='https://nib-companion.floot.app/_cdn/static/nib-expression-skeptical.png' alt='Nib'><h1>One sec. I’m borrowing the big brain.</h1><p>Doing the boring machinery underneath…</p></div></body></html>";
  curtain.loadDataWithBaseURL("https://nib-companion.floot.app/",html,"text/html","UTF-8",null);
  int type=Build.VERSION.SDK_INT>=Build.VERSION_CODES.O?WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY:WindowManager.LayoutParams.TYPE_PHONE;WindowManager.LayoutParams lp=new WindowManager.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT,WindowManager.LayoutParams.MATCH_PARENT,type,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN|WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,PixelFormat.TRANSLUCENT);lp.gravity=Gravity.TOP|Gravity.START;
  try{wm.addView(curtain,lp);}catch(Exception ignored){curtain.destroy();curtain=null;}
 }
 private void removeCurtain(){if(curtain==null)return;try{wm.removeView(curtain);}catch(Exception ignored){}curtain.destroy();curtain=null;}
 @Override public void onDestroy(){removeCurtain();super.onDestroy();}
 @Override public IBinder onBind(Intent intent){return null;}
}
