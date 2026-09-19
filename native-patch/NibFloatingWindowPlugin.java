package app.byshawn.nib.overlay;

import android.Manifest;
import android.app.Activity;
import android.app.role.RoleManager;
import android.content.Context;
import android.content.Intent;
import android.content.ClipData;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.speech.SpeechRecognizer;
import android.util.Base64;
import androidx.activity.result.ActivityResult;
import androidx.core.content.FileProvider;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.ActivityCallback;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;

@CapacitorPlugin(name="NibFloatingWindow", permissions=@Permission(strings={Manifest.permission.RECORD_AUDIO}, alias="microphone"))
public class NibFloatingWindowPlugin extends Plugin {
 private static final String PREFS="nib_overlay", CHATGPT_PACKAGE="com.openai.chatgpt";
 private static PluginCall pendingChatGptCall;
 private static final Handler bridgeHandler=new Handler(Looper.getMainLooper());
 private static Context bridgeContext;
 @PluginMethod public void checkOverlayPermission(PluginCall call){call.resolve(permissionResult());}
 @PluginMethod public void requestOverlayPermission(PluginCall call){if(Build.VERSION.SDK_INT<Build.VERSION_CODES.M||Settings.canDrawOverlays(getContext())){call.resolve(permissionResult());return;}Intent i=new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getContext().getPackageName()));startActivityForResult(call,i,"overlayPermissionResult");}
 @ActivityCallback private void overlayPermissionResult(PluginCall call,ActivityResult result){if(call!=null)call.resolve(permissionResult());}
 @PluginMethod public void checkMicrophonePermission(PluginCall call){JSObject o=new JSObject();o.put("granted",getPermissionState("microphone")==PermissionState.GRANTED);o.put("onDeviceAvailable",Build.VERSION.SDK_INT>=31&&SpeechRecognizer.isOnDeviceRecognitionAvailable(getContext()));call.resolve(o);}
 @PluginMethod public void requestMicrophonePermission(PluginCall call){if(getPermissionState("microphone")==PermissionState.GRANTED){checkMicrophonePermission(call);return;}requestPermissionForAlias("microphone",call,"microphonePermissionResult");}
 @PermissionCallback private void microphonePermissionResult(PluginCall call){if(call!=null)checkMicrophonePermission(call);}
 @PluginMethod public void showFloatingWindow(PluginCall call){if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.M&&!Settings.canDrawOverlays(getContext())){call.reject("Display over other apps permission is required.");return;}String url=call.getString("url","");if(url==null||url.isEmpty()){call.reject("url is required");return;}android.content.SharedPreferences.Editor e=prefs().edit().putBoolean("enabled",true).putString("url",url).putInt("sizeDp",call.getInt("sizeDp",112)).putBoolean("pinned",call.getBoolean("pinned",false)).putBoolean("roaming",call.getBoolean("roaming",true));e.apply();Intent i=command(NibOverlayService.ACTION_SHOW);i.putExtra("url",url);i.putExtra("sizeDp",call.getInt("sizeDp",112));i.putExtra("pinned",call.getBoolean("pinned",false));i.putExtra("roaming",call.getBoolean("roaming",true));startOverlayCommand(i);if("running".equals(prefs().getString("wakeMode","off"))||"always".equals(prefs().getString("wakeMode","off")))startWakeIfAllowed();call.resolve(stateResult());}
 @PluginMethod public void closeFloatingWindow(PluginCall call){prefs().edit().putBoolean("enabled",false).apply();getContext().stopService(new Intent(getContext(),NibOverlayService.class));if("running".equals(prefs().getString("wakeMode","off")))stopWake();call.resolve();}
 @PluginMethod public void setPinned(PluginCall call){boolean value=call.getBoolean("pinned",false);prefs().edit().putBoolean("pinned",value).apply();if(prefs().getBoolean("enabled",false)){Intent i=command(NibOverlayService.ACTION_CONFIG);i.putExtra("hasPinned",true);i.putExtra("pinned",value);startOverlayCommand(i);}call.resolve(stateResult());}
 @PluginMethod public void setRoaming(PluginCall call){boolean value=call.getBoolean("roaming",true);prefs().edit().putBoolean("roaming",value).apply();if(prefs().getBoolean("enabled",false)){Intent i=command(NibOverlayService.ACTION_CONFIG);i.putExtra("hasRoaming",true);i.putExtra("roaming",value);startOverlayCommand(i);}call.resolve(stateResult());}
 @PluginMethod public void setChatter(PluginCall call){boolean value=call.getBoolean("chatter",true);prefs().edit().putBoolean("chatter",value).apply();if(prefs().getBoolean("enabled",false)){Intent i=command(NibOverlayService.ACTION_CONFIG);i.putExtra("hasChatter",true);i.putExtra("chatter",value);startOverlayCommand(i);}call.resolve(stateResult());}
 @PluginMethod public void moveFloatingWindow(PluginCall call){if(prefs().getBoolean("enabled",false)){Intent i=command(NibOverlayService.ACTION_MOVE);i.putExtra("x",call.getInt("x",0));i.putExtra("y",call.getInt("y",0));i.putExtra("animated",call.getBoolean("animated",true));startOverlayCommand(i);}call.resolve(stateResult());}
 @PluginMethod public void openPanel(PluginCall call){try{Intent i=new Intent(getContext(),NibPanelActivity.class);i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_NO_ANIMATION);i.putExtra("panelUrl",panelUrl());getContext().startActivity(i);}catch(Exception ignored){}call.resolve();}
 @PluginMethod public void closePanel(PluginCall call){Activity a=getActivity();if(a instanceof NibPanelActivity)a.finish();else NibPanelActivity.closeIfOpen();call.resolve();}
 @PluginMethod public void finishHost(PluginCall call){Activity a=getActivity();if(a!=null&&!(a instanceof NibPanelActivity))a.finish();call.resolve();}
 @PluginMethod public void setPanelSize(PluginCall call){Activity a=getActivity();String size=call.getString("size","compact");if(a instanceof NibPanelActivity)((NibPanelActivity)a).setPanelSize(size);call.resolve();}
 @PluginMethod public void setWakeMode(PluginCall call){String mode=call.getString("mode","off");if(!"off".equals(mode)&&!"running".equals(mode)&&!"always".equals(mode)){call.reject("mode must be off, running, or always");return;}prefs().edit().putString("wakeMode",mode).apply();if("off".equals(mode))stopWake();else if(getPermissionState("microphone")!=PermissionState.GRANTED){call.reject("Microphone permission is required before wake listening can be enabled.");return;}else if("running".equals(mode)&&!prefs().getBoolean("enabled",false))stopWake();else startWakeIfAllowed();call.resolve(stateResult());}
 @PluginMethod public void setWakePhrase(PluginCall call){String phrase=call.getString("phrase","Hey Nib");if(phrase==null||phrase.trim().length()<2){call.reject("wake phrase is too short");return;}prefs().edit().putString("wakePhrase",phrase.trim()).apply();notifyWakeConfig();call.resolve(stateResult());}
 @PluginMethod public void setWakeAckMode(PluginCall call){String mode=call.getString("mode","speak");if(!"speak".equals(mode)&&!"sound".equals(mode)&&!"silent".equals(mode)){call.reject("mode must be speak, sound, or silent");return;}prefs().edit().putString("wakeAckMode",mode).apply();notifyWakeConfig();call.resolve(stateResult());}
 @PluginMethod public void consumePendingVoiceCommand(PluginCall call){android.content.SharedPreferences p=prefs();String text=p.getString("pendingVoiceCommand","");long at=p.getLong("pendingVoiceAt",0L);p.edit().remove("pendingVoiceCommand").remove("pendingVoiceAt").apply();JSObject o=new JSObject();o.put("command",text==null?"":text);o.put("timestamp",at);call.resolve(o);}
 @PluginMethod public void getAssistantRoleState(PluginCall call){call.resolve(assistantRoleResult());}
 @PluginMethod public void requestAssistantRole(PluginCall call){if(Build.VERSION.SDK_INT<29){call.resolve(assistantRoleResult());return;}RoleManager rm=(RoleManager)getContext().getSystemService(Context.ROLE_SERVICE);if(rm==null||!rm.isRoleAvailable(RoleManager.ROLE_ASSISTANT)||rm.isRoleHeld(RoleManager.ROLE_ASSISTANT)){call.resolve(assistantRoleResult());return;}startActivityForResult(call,rm.createRequestRoleIntent(RoleManager.ROLE_ASSISTANT),"assistantRoleResult");}
 @ActivityCallback private void assistantRoleResult(PluginCall call,ActivityResult result){if(call!=null)call.resolve(assistantRoleResult());}
 @PluginMethod public void getChatGPTBridgeState(PluginCall call){call.resolve(chatGptBridgeResult());}
 @PluginMethod public void requestChatGPTAccessibility(PluginCall call){Intent i=new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);getContext().startActivity(i);JSObject o=new JSObject();o.put("opened",true);call.resolve(o);}
 @PluginMethod public void getChatGPTBridgeDiagnostics(PluginCall call){android.content.SharedPreferences p=prefs();JSObject o=new JSObject();o.put("stage",p.getString("bridgeDiagStage","idle"));o.put("detail",p.getString("bridgeDiagDetail",""));o.put("trace",p.getString("bridgeDiagTrace",""));o.put("updatedAt",p.getLong("bridgeDiagAt",0L));boolean pending=pendingChatGptCall!=null||!p.getString("bridgePendingPrompt","").isEmpty();o.put("pending",pending);call.resolve(o);}
 @PluginMethod public void completeChatGPTBridgeFromRelay(PluginCall call){
  String reply=call.getString("reply","");
  if(reply==null||reply.trim().isEmpty()){call.reject("Relay reply is required.");return;}
  Context app=getContext().getApplicationContext();
  boolean pending;
  synchronized(NibFloatingWindowPlugin.class){pending=pendingChatGptCall!=null;}
  if(!pending){JSObject o=new JSObject();o.put("accepted",false);call.resolve(o);return;}
  app.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit()
   .remove("bridgePendingPrompt").remove("bridgeBeforeText").remove("bridgeImageCount")
   .putBoolean("bridgePromptPrepared",false).putBoolean("bridgePromptSent",false).apply();
  recordBridgeStage(app,"relay_delivered",reply.trim().length()+" reply chars arrived through Nib Relay");
  JSObject o=new JSObject();o.put("accepted",true);call.resolve(o);
  deliverChatGptReply(reply.trim());
 }
 @PluginMethod public void cancelChatGPTBridge(PluginCall call){Context app=getContext().getApplicationContext();boolean pending; synchronized(NibFloatingWindowPlugin.class){pending=pendingChatGptCall!=null;} recordBridgeStage(app,"cancelled","Nib stopped waiting for the return path"); if(pending)clearPendingBridge("Nib stopped waiting for ChatGPT to hand the reply back."); else {app.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().remove("bridgePendingPrompt").remove("bridgeBeforeText").remove("bridgeImageCount").putBoolean("bridgePromptPrepared",false).putBoolean("bridgePromptSent",false).apply();hideBridgeCurtain(app);} call.resolve();}
 @PluginMethod public void repairFloatingWindow(PluginCall call){android.content.SharedPreferences p=prefs();if(p.getBoolean("enabled",false)&&(Build.VERSION.SDK_INT<Build.VERSION_CODES.M||Settings.canDrawOverlays(getContext()))){Intent i=command(NibOverlayService.ACTION_SHOW);i.putExtra("url",p.getString("url",""));i.putExtra("sizeDp",p.getInt("sizeDp",112));i.putExtra("pinned",p.getBoolean("pinned",false));i.putExtra("roaming",p.getBoolean("roaming",true));startOverlayCommand(i);}call.resolve(stateResult());}
 @PluginMethod public void openMainApp(PluginCall call){Context app=getContext().getApplicationContext();Intent launch=app.getPackageManager().getLaunchIntentForPackage(app.getPackageName());if(launch==null){call.reject("Nib main app could not be opened.");return;}launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_REORDER_TO_FRONT|Intent.FLAG_ACTIVITY_NO_ANIMATION);call.resolve();bridgeHandler.postDelayed(()->{try{NibPanelActivity.closeIfOpen();app.startActivity(launch);}catch(Exception ignored){}},80L);}
 @PluginMethod public void sendChatGPTPrompt(PluginCall call){
  String prompt=call.getString("prompt","");
  if(prompt==null||prompt.trim().isEmpty()){call.reject("prompt is required");return;}
  JSObject state=chatGptBridgeResult();
  if(!state.optBoolean("ready",false)){call.reject("Personal ChatGPT bridge is not ready. Enable Nib Accessibility and install/open the official ChatGPT app.");return;}
  synchronized(NibFloatingWindowPlugin.class){if(pendingChatGptCall!=null){call.reject("A ChatGPT bridge request is already running.");return;}pendingChatGptCall=call;call.setKeepAlive(true);}
  final Context app=getContext().getApplicationContext(); bridgeContext=app;
  prefs().edit().remove("bridgeDiagTrace").remove("bridgeDiagStage").remove("bridgeDiagDetail").remove("bridgeDiagAt").apply();
  recordBridgeStage(app,"bridge_started","Opening the official ChatGPT app");
  long timeout=Math.max(10000,Math.min(120000,call.getInt("timeoutMs",90000)));
  prefs().edit().putString("bridgePendingPrompt",prompt.trim()).putInt("bridgeImageCount",0).putBoolean("bridgePromptPrepared",false).putBoolean("bridgePromptSent",false).remove("bridgeBeforeText").remove("bridgeResultStatus").remove("bridgeResultText").remove("bridgeResultAt").apply();
  showBridgeCurtain(app);
  Intent launch=app.getPackageManager().getLaunchIntentForPackage(CHATGPT_PACKAGE);
  if(launch==null){clearPendingBridge("ChatGPT app could not be opened.");return;}
  launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
  bridgeHandler.postDelayed(()->{try{app.startActivity(launch);}catch(Exception e){clearPendingBridge("ChatGPT app could not be opened.");}},180L);
  bridgeHandler.postDelayed(()->{synchronized(NibFloatingWindowPlugin.class){if(pendingChatGptCall==call)clearPendingBridge("Timed out waiting for ChatGPT. The app UI may have changed.");}},timeout);
 }
 @PluginMethod public void sendChatGPTPromptWithImages(PluginCall call){
  String prompt=call.getString("prompt","");
  JSArray images=call.getArray("images");
  if(prompt==null||prompt.trim().isEmpty()){call.reject("prompt is required");return;}
  if(images==null||images.length()==0){sendChatGPTPrompt(call);return;}
  if(images.length()>4){call.reject("Nib can pass at most 4 images to ChatGPT at once.");return;}
  JSObject state=chatGptBridgeResult();
  if(!state.optBoolean("ready",false)){call.reject("Personal ChatGPT bridge is not ready. Enable Nib Accessibility and install/open the official ChatGPT app.");return;}

  final Context app=getContext().getApplicationContext();
  final ArrayList<Uri> uris=new ArrayList<>();
  try{
   File dir=new File(app.getCacheDir(),"nib_share");
   if(!dir.exists()&&!dir.mkdirs())throw new Exception("Could not create Nib image cache.");
   File[] old=dir.listFiles(); if(old!=null)for(File f:old)try{f.delete();}catch(Exception ignored){}
   for(int idx=0;idx<images.length();idx++){
    String data=images.optString(idx,"");
    if(data==null||data.trim().isEmpty())continue;
    int comma=data.indexOf(',');
    if(comma>=0)data=data.substring(comma+1);
    byte[] bytes=Base64.decode(data,Base64.DEFAULT);
    if(bytes.length==0)continue;
    File outFile=new File(dir,"nib-"+System.currentTimeMillis()+"-"+idx+".jpg");
    try(FileOutputStream out=new FileOutputStream(outFile)){out.write(bytes);}
    Uri uri=FileProvider.getUriForFile(app,app.getPackageName()+".fileprovider",outFile);
    uris.add(uri);
   }
  }catch(Exception e){call.reject("Nib could not prepare the photo for ChatGPT: "+e.getMessage());return;}
  if(uris.isEmpty()){call.reject("Nib could not prepare a readable photo for ChatGPT.");return;}

  synchronized(NibFloatingWindowPlugin.class){if(pendingChatGptCall!=null){call.reject("A ChatGPT bridge request is already running.");return;}pendingChatGptCall=call;call.setKeepAlive(true);}
  bridgeContext=app;
  prefs().edit().remove("bridgeDiagTrace").remove("bridgeDiagStage").remove("bridgeDiagDetail").remove("bridgeDiagAt").apply();
  long timeout=Math.max(10000,Math.min(120000,call.getInt("timeoutMs",110000)));
  prefs().edit().putString("bridgePendingPrompt",prompt.trim()).putInt("bridgeImageCount",uris.size()).putBoolean("bridgePromptPrepared",false).putBoolean("bridgePromptSent",false).remove("bridgeBeforeText").remove("bridgeResultStatus").remove("bridgeResultText").remove("bridgeResultAt").apply();
  recordBridgeStage(app,"image_share_started",uris.size()+" image(s) being shared into ChatGPT");
  showBridgeCurtain(app);

  Intent share=new Intent(uris.size()>1?Intent.ACTION_SEND_MULTIPLE:Intent.ACTION_SEND);
  share.setPackage(CHATGPT_PACKAGE);
  share.setType("image/*");
  share.putExtra(Intent.EXTRA_TEXT,prompt.trim());
  if(uris.size()==1)share.putExtra(Intent.EXTRA_STREAM,uris.get(0));
  else share.putParcelableArrayListExtra(Intent.EXTRA_STREAM,uris);
  ClipData clip=new ClipData("Nib photo",new String[]{"image/*"},new ClipData.Item(uris.get(0)));
  for(int i=1;i<uris.size();i++)clip.addItem(new ClipData.Item(uris.get(i)));
  share.setClipData(clip);
  share.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_REORDER_TO_FRONT|Intent.FLAG_GRANT_READ_URI_PERMISSION);
  for(Uri uri:uris)try{app.grantUriPermission(CHATGPT_PACKAGE,uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}

  bridgeHandler.postDelayed(()->{
   try{app.startActivity(share);recordBridgeStage(app,"image_share_opened","ChatGPT received the Android share intent");}
   catch(Exception e){clearPendingBridge("ChatGPT could not receive the shared image. "+e.getMessage());}
  },180L);
  bridgeHandler.postDelayed(()->{synchronized(NibFloatingWindowPlugin.class){if(pendingChatGptCall==call)clearPendingBridge("Timed out waiting for ChatGPT. The image share or app UI may have changed.");}},timeout);
 }
 @PluginMethod public void consumeChatGPTBridgeResult(PluginCall call){android.content.SharedPreferences p=prefs();String status=p.getString("bridgeResultStatus","none");if(status==null||status.isEmpty())status="none";JSObject o=new JSObject();o.put("status",status);if("reply".equals(status))o.put("reply",p.getString("bridgeResultText",""));else if("error".equals(status))o.put("error",p.getString("bridgeResultText",""));o.put("timestamp",p.getLong("bridgeResultAt",0L));if(!"none".equals(status)){recordBridgeStage(getContext(),"nib_consumed","reply".equals(status)?"Nib consumed the native reply mailbox":"Nib consumed a bridge error");p.edit().remove("bridgeResultStatus").remove("bridgeResultText").remove("bridgeResultAt").apply();}call.resolve(o);}
 @PluginMethod public void getState(PluginCall call){call.resolve(stateResult());}
 private android.content.SharedPreferences prefs(){return getContext().getSharedPreferences(PREFS,Context.MODE_PRIVATE);}
 private Intent command(String action){Intent i=new Intent(getContext(),NibOverlayService.class);i.setAction(action);return i;}
 private void startOverlayCommand(Intent i){if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.O)getContext().startForegroundService(i);else getContext().startService(i);}
 private void startWakeIfAllowed(){if(getPermissionState("microphone")!=PermissionState.GRANTED)return;Intent i=new Intent(getContext(),NibWakeService.class);i.setAction(NibWakeService.ACTION_START);if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.O)getContext().startForegroundService(i);else getContext().startService(i);}
 private void stopWake(){getContext().stopService(new Intent(getContext(),NibWakeService.class));}
 private void notifyWakeConfig(){String mode=prefs().getString("wakeMode","off");if("off".equals(mode))return;if("running".equals(mode)&&!prefs().getBoolean("enabled",false))return;if(getPermissionState("microphone")!=PermissionState.GRANTED)return;Intent i=new Intent(getContext(),NibWakeService.class);i.setAction(NibWakeService.ACTION_CONFIG);if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.O)getContext().startForegroundService(i);else getContext().startService(i);}
 private String panelUrl(){String overlay=prefs().getString("url","");try{Uri u=Uri.parse(overlay);if(u.getScheme()!=null&&u.getAuthority()!=null)return u.getScheme()+"://"+u.getAuthority()+"/?nibPanel=1";}catch(Exception ignored){}return "https://nib-companion.floot.app/?nibPanel=1";}
 private JSObject permissionResult(){JSObject o=new JSObject();o.put("granted",Build.VERSION.SDK_INT<Build.VERSION_CODES.M||Settings.canDrawOverlays(getContext()));return o;}
 private JSObject assistantRoleResult(){JSObject o=new JSObject();if(Build.VERSION.SDK_INT<29){o.put("available",false);o.put("held",false);return o;}RoleManager rm=(RoleManager)getContext().getSystemService(Context.ROLE_SERVICE);boolean available=rm!=null&&rm.isRoleAvailable(RoleManager.ROLE_ASSISTANT);o.put("available",available);o.put("held",available&&rm.isRoleHeld(RoleManager.ROLE_ASSISTANT));return o;}
 public static void deliverChatGptReply(String reply){
  bridgeHandler.post(()->{
   PluginCall c; Context ctx;
   synchronized(NibFloatingWindowPlugin.class){c=pendingChatGptCall;pendingChatGptCall=null;ctx=bridgeContext;bridgeContext=null;}
   final String safeReply=reply==null?"":reply;
   if(ctx!=null){ctx.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putString("bridgeResultStatus","reply").putString("bridgeResultText",safeReply).putLong("bridgeResultAt",System.currentTimeMillis()).apply();recordBridgeStage(ctx,"mailbox_written",safeReply.length()+" reply chars stored");}
   if(c!=null){c.setKeepAlive(false);JSObject o=new JSObject();o.put("reply",safeReply);o.put("timestamp",System.currentTimeMillis());c.resolve(o);if(ctx!=null)recordBridgeStage(ctx,"callback_resolved","Capacitor callback resolved");}
   if(ctx!=null){bridgeHandler.postDelayed(()->returnToNib(ctx),120L);bridgeHandler.postDelayed(()->hideBridgeCurtain(ctx),420L);}
  });
 }
 public static void failChatGptBridge(String message){bridgeHandler.post(()->clearPendingBridge(message));}
 private static void clearPendingBridge(String message){
  PluginCall c; Context ctx;
  synchronized(NibFloatingWindowPlugin.class){c=pendingChatGptCall;pendingChatGptCall=null;ctx=bridgeContext;bridgeContext=null;}
  final String safeMessage=message==null?"ChatGPT bridge failed.":message;
  if(ctx!=null)recordBridgeStage(ctx,"failed",safeMessage);
  if(ctx!=null)ctx.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putString("bridgeResultStatus","error").putString("bridgeResultText",safeMessage).putLong("bridgeResultAt",System.currentTimeMillis()).remove("bridgePendingPrompt").putBoolean("bridgePromptPrepared",false).putBoolean("bridgePromptSent",false).apply();
  if(c!=null){c.setKeepAlive(false);c.reject(safeMessage);}
  if(ctx!=null){bridgeHandler.postDelayed(()->returnToNib(ctx),120L);bridgeHandler.postDelayed(()->hideBridgeCurtain(ctx),420L);}
 }
 private static void recordBridgeStage(Context context,String stage,String detail){if(context==null)return;android.content.SharedPreferences p=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE);long now=System.currentTimeMillis();String safeStage=stage==null?"unknown":stage.trim();String safeDetail=detail==null?"":detail.replace('\n',' ').trim();String old=p.getString("bridgeDiagTrace","");String line=now+" | "+safeStage+(safeDetail.isEmpty()?"":" | "+safeDetail);java.util.ArrayList<String> lines=new java.util.ArrayList<>();if(old!=null&&!old.trim().isEmpty())lines.addAll(java.util.Arrays.asList(old.split("\n")));lines.add(line);while(lines.size()>16)lines.remove(0);String trace=android.text.TextUtils.join("\n",lines);if(trace.length()>3500)trace=trace.substring(trace.length()-3500);p.edit().putString("bridgeDiagStage",safeStage).putString("bridgeDiagDetail",safeDetail).putString("bridgeDiagTrace",trace).putLong("bridgeDiagAt",now).apply();}
 private boolean chatGptInstalled(){try{return getContext().getPackageManager().getLaunchIntentForPackage(CHATGPT_PACKAGE)!=null;}catch(Exception e){return false;}}
 private boolean accessibilityEnabled(){try{String enabled=Settings.Secure.getString(getContext().getContentResolver(),Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);return Settings.Secure.getInt(getContext().getContentResolver(),Settings.Secure.ACCESSIBILITY_ENABLED,0)==1&&enabled!=null&&enabled.contains("NibChatGPTAccessibilityService");}catch(Exception e){return false;}}
 private JSObject chatGptBridgeResult(){JSObject o=new JSObject();boolean installed=chatGptInstalled(),access=accessibilityEnabled();o.put("available",true);o.put("chatGptInstalled",installed);o.put("accessibilityEnabled",access);o.put("ready",installed&&access);o.put("imageSharingSupported",true);return o;}
 private JSObject stateResult(){android.content.SharedPreferences p=prefs();JSObject o=new JSObject();o.put("supported",true);o.put("permissionGranted",Build.VERSION.SDK_INT<Build.VERSION_CODES.M||Settings.canDrawOverlays(getContext()));o.put("active",p.getBoolean("enabled",false));o.put("pinned",p.getBoolean("pinned",false));o.put("roaming",p.getBoolean("roaming",true));o.put("chatter",p.getBoolean("chatter",true));o.put("x",p.getInt("x",-1));o.put("y",p.getInt("y",-1));o.put("wakeMode",p.getString("wakeMode","off"));o.put("wakePhrase",p.getString("wakePhrase","Hey Nib"));o.put("wakeAckMode",p.getString("wakeAckMode","speak"));o.put("microphoneGranted",getPermissionState("microphone")==PermissionState.GRANTED);o.put("onDeviceRecognition",Build.VERSION.SDK_INT>=31&&SpeechRecognizer.isOnDeviceRecognitionAvailable(getContext()));JSObject a=assistantRoleResult();o.put("assistantRoleAvailable",a.optBoolean("available",false));o.put("assistantRoleHeld",a.optBoolean("held",false));return o;}
 private static void showBridgeCurtain(Context context){try{Intent i=new Intent(context,NibBridgeCurtainService.class).setAction(NibBridgeCurtainService.ACTION_SHOW);context.startService(i);}catch(Exception ignored){}}
 private static void hideBridgeCurtain(Context context){try{Intent i=new Intent(context,NibBridgeCurtainService.class).setAction(NibBridgeCurtainService.ACTION_HIDE);context.startService(i);}catch(Exception ignored){try{context.stopService(new Intent(context,NibBridgeCurtainService.class));}catch(Exception ignoredAgain){}}}
 private static void returnToNib(Context context){try{Intent i=new Intent(context,NibPanelActivity.class);i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_REORDER_TO_FRONT|Intent.FLAG_ACTIVITY_NO_ANIMATION);i.putExtra("panelUrl",bridgePanelUrl(context));context.startActivity(i);}catch(Exception ignored){try{Intent i=context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());if(i!=null){i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);context.startActivity(i);}}catch(Exception ignoredAgain){}}}
 private static String bridgePanelUrl(Context context){String overlay=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getString("url","");try{Uri u=Uri.parse(overlay);if(u.getScheme()!=null&&u.getAuthority()!=null)return u.getScheme()+"://"+u.getAuthority()+"/?nibPanel=1";}catch(Exception ignored){}return "https://nib-companion.floot.app/?nibPanel=1";}
}
