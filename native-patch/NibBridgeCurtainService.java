package app.byshawn.nib.overlay;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import java.util.Locale;

public class NibBridgeCurtainService extends Service {
 public static final String ACTION_SHOW="app.byshawn.nib.BRIDGE_CURTAIN_SHOW", ACTION_HIDE="app.byshawn.nib.BRIDGE_CURTAIN_HIDE";
 private static final String PREFS="nib_overlay";
 private WindowManager wm;
 private View backdrop;
 private WebView curtain;

 @Override public void onCreate(){
  super.onCreate();
  wm=(WindowManager)getSystemService(WINDOW_SERVICE);
 }

 @Override public int onStartCommand(Intent intent,int flags,int startId){
  String action=intent==null?ACTION_SHOW:intent.getAction();
  if(ACTION_HIDE.equals(action)){
   removeCurtain();
   stopSelf();
   return START_NOT_STICKY;
  }
  showCurtain();
  return START_NOT_STICKY;
 }

 private void showCurtain(){
  if(curtain!=null)return;
  if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.M&&!Settings.canDrawOverlays(this))return;

  SharedPreferences prefs=getSharedPreferences(PREFS,MODE_PRIVATE);
  prefs.edit().putBoolean("bridgeBackstage",false).apply();
  String prompt=prefs.getString("bridgePendingPrompt","");
  String category=classify(prompt);

  int type=Build.VERSION.SDK_INT>=Build.VERSION_CODES.O?WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY:WindowManager.LayoutParams.TYPE_PHONE;
  int backdropFlags=WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN|WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
  int curtainFlags=WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN|WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;

  backdrop=new View(getApplicationContext());
  backdrop.setBackgroundColor(Color.rgb(4,3,7));
  WindowManager.LayoutParams backdropLp=new WindowManager.LayoutParams(
   WindowManager.LayoutParams.MATCH_PARENT,
   WindowManager.LayoutParams.MATCH_PARENT,
   type,
   backdropFlags,
   PixelFormat.OPAQUE
  );
  backdropLp.gravity=Gravity.TOP|Gravity.START;
  backdropLp.alpha=1f;
  try{wm.addView(backdrop,backdropLp);}catch(Exception ignored){backdrop=null;}

  curtain=new WebView(getApplicationContext());
  curtain.setBackgroundColor(Color.rgb(4,3,7));
  curtain.setLayerType(View.LAYER_TYPE_HARDWARE,null);
  curtain.setFocusable(true);
  curtain.setFocusableInTouchMode(true);

  WebSettings s=curtain.getSettings();
  s.setJavaScriptEnabled(true);
  s.setDomStorageEnabled(true);
  s.setSupportZoom(false);
  s.setBuiltInZoomControls(false);
  s.setDisplayZoomControls(false);

  curtain.setWebViewClient(new WebViewClient(){
   private boolean handle(Uri uri){
    if(uri==null)return false;
    if("nib".equalsIgnoreCase(uri.getScheme())&&"backstage".equalsIgnoreCase(uri.getHost())){
     goBackstage();
     return true;
    }
    return false;
   }
   @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){
    return request!=null&&handle(request.getUrl());
   }
   @Override public boolean shouldOverrideUrlLoading(WebView view,String url){
    try{return handle(Uri.parse(url));}catch(Exception ignored){return false;}
   }
  });

  curtain.setOnKeyListener((v,keyCode,event)->{
   if(keyCode==KeyEvent.KEYCODE_BACK&&event!=null&&event.getAction()==KeyEvent.ACTION_UP){
    goBackstage();
    return true;
   }
   return keyCode==KeyEvent.KEYCODE_BACK;
  });

  Uri brainUrl=new Uri.Builder()
   .scheme("https")
   .authority("nib-companion.floot.app")
   .path("brain-hq")
   .appendQueryParameter("native","1")
   .appendQueryParameter("category",category)
   .appendQueryParameter("name","Nib")
   .build();
  curtain.loadUrl(brainUrl.toString());

  WindowManager.LayoutParams lp=new WindowManager.LayoutParams(
   WindowManager.LayoutParams.MATCH_PARENT,
   WindowManager.LayoutParams.MATCH_PARENT,
   type,
   curtainFlags,
   PixelFormat.OPAQUE
  );
  lp.gravity=Gravity.TOP|Gravity.START;
  lp.alpha=1f;
  try{
   wm.addView(curtain,lp);
   curtain.requestFocus();
  }catch(Exception ignored){
   curtain.destroy();
   curtain=null;
   removeBackdrop();
  }
 }

 private String classify(String raw){
  String p=raw==null?"":raw.toLowerCase(Locale.US);
  if(hasAny(p,"code","coding","bug","error","debug","java","javascript","typescript","python","android","github","api","build","apk","function","component","database","sql","css","html"))return "coding";
  if(hasAny(p,"design","ui","ux","logo","layout","color","font","screen","interface","branding","visual","style","image"))return "design";
  if(hasAny(p,"plan","planning","schedule","roadmap","organize","organise","priority","priorities","strategy","steps","workflow","project","todo","to-do"))return "planning";
  if(hasAny(p,"write","rewrite","story","poem","song","creative","brainstorm","name","caption","script","character","idea","metaphor"))return "creative";
  if(hasAny(p,"advice","should i","what should","relationship","feel","feeling","help me decide","recommend","choice","choices"))return "advice";
  return "general";
 }

 private boolean hasAny(String value,String... needles){
  if(value==null||value.isEmpty())return false;
  for(String needle:needles)if(value.contains(needle))return true;
  return false;
 }

 private void goBackstage(){
  try{getSharedPreferences(PREFS,MODE_PRIVATE).edit().putBoolean("bridgeBackstage",true).apply();}catch(Exception ignored){}
  removeCurtain();
  stopSelf();
 }

 private void removeBackdrop(){
  if(backdrop==null)return;
  try{wm.removeView(backdrop);}catch(Exception ignored){}
  backdrop=null;
 }

 private void removeCurtain(){
  if(curtain!=null){
   try{wm.removeView(curtain);}catch(Exception ignored){}
   try{curtain.stopLoading();}catch(Exception ignored){}
   curtain.destroy();
   curtain=null;
  }
  removeBackdrop();
 }

 @Override public void onDestroy(){removeCurtain();super.onDestroy();}
 @Override public IBinder onBind(Intent intent){return null;}
}
