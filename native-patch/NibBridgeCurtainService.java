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
 private WindowManager wm; private View backdrop; private WebView curtain;
 @Override public void onCreate(){super.onCreate();wm=(WindowManager)getSystemService(WINDOW_SERVICE);}
 @Override public int onStartCommand(Intent intent,int flags,int startId){String action=intent==null?ACTION_SHOW:intent.getAction();if(ACTION_HIDE.equals(action)){removeCurtain();stopSelf();return START_NOT_STICKY;}showCurtain();return START_NOT_STICKY;}
 private void showCurtain(){
  if(curtain!=null)return; if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.M&&!Settings.canDrawOverlays(this))return;
  int type=Build.VERSION.SDK_INT>=Build.VERSION_CODES.O?WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY:WindowManager.LayoutParams.TYPE_PHONE;
  int flags=WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN|WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;

  backdrop=new View(getApplicationContext());
  backdrop.setBackgroundColor(Color.rgb(3,2,6));
  WindowManager.LayoutParams backdropLp=new WindowManager.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT,WindowManager.LayoutParams.MATCH_PARENT,type,flags,PixelFormat.OPAQUE);
  backdropLp.gravity=Gravity.TOP|Gravity.START;backdropLp.alpha=1f;
  try{wm.addView(backdrop,backdropLp);}catch(Exception ignored){backdrop=null;}

  curtain=new WebView(getApplicationContext());
  curtain.setBackgroundColor(Color.rgb(5,3,9));
  curtain.setLayerType(View.LAYER_TYPE_HARDWARE,null);
  WebSettings s=curtain.getSettings();s.setJavaScriptEnabled(true);s.setDomStorageEnabled(false);s.setSupportZoom(false);s.setBuiltInZoomControls(false);s.setDisplayZoomControls(false);
  String html="<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1,maximum-scale=1'><style>"+
   "*{box-sizing:border-box}html,body{margin:0;width:100%;height:100%;overflow:hidden;background:#050309!important;color:#f8f5ff;font-family:system-ui,-apple-system,sans-serif}"+
   "body{position:relative;background:radial-gradient(circle at 50% 40%,#1d1030 0,#0c0714 34%,#050309 68%)!important}"+
   ".label{position:absolute;top:20%;left:0;right:0;text-align:center;font:700 11px/1.2 ui-monospace,monospace;letter-spacing:.22em;color:#d8c5ff;text-transform:uppercase;text-shadow:0 0 12px rgba(209,183,255,.35)}"+
   ".stage{position:absolute;left:9%;right:9%;top:30%;height:176px;overflow:hidden}"+
   ".track{position:absolute;inset:0 0 20px;border-radius:999px;background:radial-gradient(ellipse at center,rgba(145,93,255,.16),rgba(145,93,255,0) 68%)}"+
   ".nib{position:absolute;left:0;bottom:30px;width:90px;height:90px;animation:pace 4.5s ease-in-out infinite;filter:brightness(1.48) saturate(1.22) drop-shadow(0 0 8px rgba(236,226,255,.9)) drop-shadow(0 0 23px rgba(158,104,255,.9))}"+
   ".nib img{width:100%;height:100%;object-fit:contain;clip-path:polygon(31% 15%,40% 23%,46% 20%,55% 20%,61% 23%,70% 15%,68% 35%,77% 45%,76% 60%,68% 67%,72% 83%,61% 82%,55% 70%,50% 88%,45% 70%,39% 82%,28% 83%,32% 67%,24% 60%,23% 45%,32% 35%)}"+
   ".dots{position:absolute;left:32px;top:0;display:flex;gap:4px}.dots i{width:5px;height:5px;border-radius:50%;background:#eee5ff;box-shadow:0 0 8px rgba(218,194,255,.85);animation:dot 1s ease-in-out infinite}.dots i:nth-child(2){animation-delay:.14s}.dots i:nth-child(3){animation-delay:.28s}"+
   ".copy{position:absolute;left:26px;right:26px;top:58%;text-align:center}.copy h1{font-size:21px;line-height:1.2;margin:0 0 7px}.copy p{margin:0;color:#b8acc8;font-size:13px;line-height:1.4}"+
   ".hint{position:absolute;left:28px;right:28px;bottom:10%;text-align:center;color:#786d89;font:10px/1.4 ui-monospace,monospace}"+
   "@keyframes pace{0%,7%{left:0;transform:scaleX(1) translateY(0)}43%{left:calc(100% - 90px);transform:scaleX(1) translateY(-2px)}50%{left:calc(100% - 90px);transform:scaleX(-1) translateY(0)}93%{left:0;transform:scaleX(-1) translateY(-2px)}100%{left:0;transform:scaleX(1) translateY(0)}}"+
   "@keyframes dot{0%,100%{transform:translateY(1px);opacity:.35}45%{transform:translateY(-3px);opacity:1}}"+
   "</style></head><body><div class='label'>Nib · thinking</div><div class='stage'><div class='track'></div><div class='nib'><div class='dots'><i></i><i></i><i></i></div><img src='https://nib-companion.floot.app/_cdn/static/nib-expression-skeptical.png' alt='Nib'></div></div><div class='copy'><h1 id='main'>One sec. I’m borrowing the big brain.</h1><p id='detail'>Doing the boring machinery underneath…</p></div><div class='hint'>ChatGPT is hidden behind the curtain. Nib will reappear when the answer is ready.</div><script>"+
   "function msg(a,b){document.getElementById('main').textContent=a;document.getElementById('detail').textContent=b;}"+
   "setTimeout(function(){msg('Still thinking. This one has layers.','I’m here — the answer just isn’t finished yet.');},8000);"+
   "setTimeout(function(){msg('Okay, you gave me homework.','Still working. Nothing crashed.');},20000);"+
   "setTimeout(function(){msg('This is taking a minute.','I’m still working behind the curtain.');},35000);"+
   "setTimeout(function(){msg('Yep. Officially a long one.','Still waiting for the answer instead of pretending I’m done.');},60000);"+
   "</script></body></html>";
  curtain.loadDataWithBaseURL("https://nib-companion.floot.app/",html,"text/html","UTF-8",null);
  WindowManager.LayoutParams lp=new WindowManager.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT,WindowManager.LayoutParams.MATCH_PARENT,type,flags,PixelFormat.OPAQUE);
  lp.gravity=Gravity.TOP|Gravity.START;lp.alpha=1f;
  try{wm.addView(curtain,lp);}catch(Exception ignored){curtain.destroy();curtain=null;removeBackdrop();}
 }
 private void removeBackdrop(){if(backdrop==null)return;try{wm.removeView(backdrop);}catch(Exception ignored){}backdrop=null;}
 private void removeCurtain(){if(curtain!=null){try{wm.removeView(curtain);}catch(Exception ignored){}curtain.destroy();curtain=null;}removeBackdrop();}
 @Override public void onDestroy(){removeCurtain();super.onDestroy();}
 @Override public IBinder onBind(Intent intent){return null;}
}
