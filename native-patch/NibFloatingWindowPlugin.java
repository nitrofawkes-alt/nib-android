package app.byshawn.nib.overlay;

import android.Manifest;
import android.app.Activity;
import android.app.role.RoleManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.speech.SpeechRecognizer;
import androidx.activity.result.ActivityResult;
import com.getcapacitor.JSObject;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.ActivityCallback;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;

@CapacitorPlugin(name="NibFloatingWindow", permissions=@Permission(strings={Manifest.permission.RECORD_AUDIO}, alias="microphone"))
public class NibFloatingWindowPlugin extends Plugin {
 private static final String PREFS="nib_overlay";
 @PluginMethod public void checkOverlayPermission(PluginCall call){call.resolve(permissionResult());}
 @PluginMethod public void requestOverlayPermission(PluginCall call){if(Build.VERSION.SDK_INT<Build.VERSION_CODES.M||Settings.canDrawOverlays(getContext())){call.resolve(permissionResult());return;}Intent i=new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getContext().getPackageName()));startActivityForResult(call,i,"overlayPermissionResult");}
 @ActivityCallback private void overlayPermissionResult(PluginCall call,ActivityResult result){if(call!=null)call.resolve(permissionResult());}
 @PluginMethod public void checkMicrophonePermission(PluginCall call){JSObject o=new JSObject();o.put("granted",getPermissionState("microphone")==PermissionState.GRANTED);o.put("onDeviceAvailable",Build.VERSION.SDK_INT>=31&&SpeechRecognizer.isOnDeviceRecognitionAvailable(getContext()));call.resolve(o);}
 @PluginMethod public void requestMicrophonePermission(PluginCall call){if(getPermissionState("microphone")==PermissionState.GRANTED){checkMicrophonePermission(call);return;}requestPermissionForAlias("microphone",call,"microphonePermissionResult");}
 @PermissionCallback private void microphonePermissionResult(PluginCall call){if(call!=null)checkMicrophonePermission(call);}
 @PluginMethod public void showFloatingWindow(PluginCall call){if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.M&&!Settings.canDrawOverlays(getContext())){call.reject("Display over other apps permission is required.");return;}String url=call.getString("url","");if(url==null||url.isEmpty()){call.reject("url is required");return;}android.content.SharedPreferences.Editor e=prefs().edit().putBoolean("enabled",true).putString("url",url).putInt("sizeDp",call.getInt("sizeDp",112)).putBoolean("pinned",call.getBoolean("pinned",false)).putBoolean("roaming",call.getBoolean("roaming",true));e.apply();Intent i=command(NibOverlayService.ACTION_SHOW);i.putExtra("url",url);i.putExtra("sizeDp",call.getInt("sizeDp",112));i.putExtra("pinned",call.getBoolean("pinned",false));i.putExtra("roaming",call.getBoolean("roaming",true));startOverlayCommand(i);if("running".equals(prefs().getString("wakeMode","off")))startWakeIfAllowed();call.resolve(stateResult());}
 @PluginMethod public void closeFloatingWindow(PluginCall call){prefs().edit().putBoolean("enabled",false).apply();getContext().stopService(new Intent(getContext(),NibOverlayService.class));if("running".equals(prefs().getString("wakeMode","off")))stopWake();call.resolve();}
 @PluginMethod public void setPinned(PluginCall call){boolean value=call.getBoolean("pinned",false);prefs().edit().putBoolean("pinned",value).apply();if(prefs().getBoolean("enabled",false)){Intent i=command(NibOverlayService.ACTION_CONFIG);i.putExtra("hasPinned",true);i.putExtra("pinned",value);startOverlayCommand(i);}call.resolve(stateResult());}
 @PluginMethod public void setRoaming(PluginCall call){boolean value=call.getBoolean("roaming",true);prefs().edit().putBoolean("roaming",value).apply();if(prefs().getBoolean("enabled",false)){Intent i=command(NibOverlayService.ACTION_CONFIG);i.putExtra("hasRoaming",true);i.putExtra("roaming",value);startOverlayCommand(i);}call.resolve(stateResult());}
 @PluginMethod public void moveFloatingWindow(PluginCall call){if(prefs().getBoolean("enabled",false)){Intent i=command(NibOverlayService.ACTION_MOVE);i.putExtra("x",call.getInt("x",0));i.putExtra("y",call.getInt("y",0));i.putExtra("animated",call.getBoolean("animated",true));startOverlayCommand(i);}call.resolve(stateResult());}
 @PluginMethod public void closePanel(PluginCall call){Activity a=getActivity();if(a instanceof NibPanelActivity)a.finish();call.resolve();}
 @PluginMethod public void setPanelSize(PluginCall call){Activity a=getActivity();String size=call.getString("size","compact");if(a instanceof NibPanelActivity)((NibPanelActivity)a).setPanelSize(size);call.resolve();}
 @PluginMethod public void setWakeMode(PluginCall call){String mode=call.getString("mode","off");if(!"off".equals(mode)&&!"running".equals(mode)&&!"always".equals(mode)){call.reject("mode must be off, running, or always");return;}prefs().edit().putString("wakeMode",mode).apply();if("off".equals(mode))stopWake();else if(getPermissionState("microphone")!=PermissionState.GRANTED){call.reject("Microphone permission is required before wake listening can be enabled.");return;}else if("running".equals(mode)&&!prefs().getBoolean("enabled",false))stopWake();else startWakeIfAllowed();call.resolve(stateResult());}
 @PluginMethod public void setWakePhrase(PluginCall call){String phrase=call.getString("phrase","Hey Nib");if(phrase==null||phrase.trim().length()<2){call.reject("wake phrase is too short");return;}prefs().edit().putString("wakePhrase",phrase.trim()).apply();notifyWakeConfig();call.resolve(stateResult());}
 @PluginMethod public void setWakeAckMode(PluginCall call){String mode=call.getString("mode","speak");if(!"speak".equals(mode)&&!"sound".equals(mode)&&!"silent".equals(mode)){call.reject("mode must be speak, sound, or silent");return;}prefs().edit().putString("wakeAckMode",mode).apply();notifyWakeConfig();call.resolve(stateResult());}
 @PluginMethod public void consumePendingVoiceCommand(PluginCall call){android.content.SharedPreferences p=prefs();String text=p.getString("pendingVoiceCommand","");long at=p.getLong("pendingVoiceAt",0L);p.edit().remove("pendingVoiceCommand").remove("pendingVoiceAt").apply();JSObject o=new JSObject();o.put("command",text==null?"":text);o.put("timestamp",at);call.resolve(o);}
 @PluginMethod public void getAssistantRoleState(PluginCall call){call.resolve(assistantRoleResult());}
 @PluginMethod public void requestAssistantRole(PluginCall call){if(Build.VERSION.SDK_INT<29){call.resolve(assistantRoleResult());return;}RoleManager rm=(RoleManager)getContext().getSystemService(Context.ROLE_SERVICE);if(rm==null||!rm.isRoleAvailable(RoleManager.ROLE_ASSISTANT)||rm.isRoleHeld(RoleManager.ROLE_ASSISTANT)){call.resolve(assistantRoleResult());return;}startActivityForResult(call,rm.createRequestRoleIntent(RoleManager.ROLE_ASSISTANT),"assistantRoleResult");}
 @ActivityCallback private void assistantRoleResult(PluginCall call,ActivityResult result){if(call!=null)call.resolve(assistantRoleResult());}
 @PluginMethod public void getState(PluginCall call){call.resolve(stateResult());}
 private android.content.SharedPreferences prefs(){return getContext().getSharedPreferences(PREFS,Context.MODE_PRIVATE);}
 private Intent command(String action){Intent i=new Intent(getContext(),NibOverlayService.class);i.setAction(action);return i;}
 private void startOverlayCommand(Intent i){if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.O)getContext().startForegroundService(i);else getContext().startService(i);}
 private void startWakeIfAllowed(){if(getPermissionState("microphone")!=PermissionState.GRANTED)return;Intent i=new Intent(getContext(),NibWakeService.class);i.setAction(NibWakeService.ACTION_START);if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.O)getContext().startForegroundService(i);else getContext().startService(i);}
 private void stopWake(){getContext().stopService(new Intent(getContext(),NibWakeService.class));}
 private void notifyWakeConfig(){String mode=prefs().getString("wakeMode","off");if("off".equals(mode))return;if("running".equals(mode)&&!prefs().getBoolean("enabled",false))return;if(getPermissionState("microphone")!=PermissionState.GRANTED)return;Intent i=new Intent(getContext(),NibWakeService.class);i.setAction(NibWakeService.ACTION_CONFIG);if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.O)getContext().startForegroundService(i);else getContext().startService(i);}
 private JSObject permissionResult(){JSObject o=new JSObject();o.put("granted",Build.VERSION.SDK_INT<Build.VERSION_CODES.M||Settings.canDrawOverlays(getContext()));return o;}
 private JSObject assistantRoleResult(){JSObject o=new JSObject();if(Build.VERSION.SDK_INT<29){o.put("available",false);o.put("held",false);return o;}RoleManager rm=(RoleManager)getContext().getSystemService(Context.ROLE_SERVICE);boolean available=rm!=null&&rm.isRoleAvailable(RoleManager.ROLE_ASSISTANT);o.put("available",available);o.put("held",available&&rm.isRoleHeld(RoleManager.ROLE_ASSISTANT));return o;}
 private JSObject stateResult(){android.content.SharedPreferences p=prefs();JSObject o=new JSObject();o.put("supported",true);o.put("permissionGranted",Build.VERSION.SDK_INT<Build.VERSION_CODES.M||Settings.canDrawOverlays(getContext()));o.put("active",p.getBoolean("enabled",false));o.put("pinned",p.getBoolean("pinned",false));o.put("roaming",p.getBoolean("roaming",true));o.put("x",p.getInt("x",-1));o.put("y",p.getInt("y",-1));o.put("wakeMode",p.getString("wakeMode","off"));o.put("wakePhrase",p.getString("wakePhrase","Hey Nib"));o.put("wakeAckMode",p.getString("wakeAckMode","speak"));o.put("microphoneGranted",getPermissionState("microphone")==PermissionState.GRANTED);o.put("onDeviceRecognition",Build.VERSION.SDK_INT>=31&&SpeechRecognizer.isOnDeviceRecognitionAvailable(getContext()));JSObject a=assistantRoleResult();o.put("assistantRoleAvailable",a.optBoolean("available",false));o.put("assistantRoleHeld",a.optBoolean("held",false));return o;}
}
