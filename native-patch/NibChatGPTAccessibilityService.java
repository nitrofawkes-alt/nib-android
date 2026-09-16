package app.byshawn.nib.overlay;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.*;

public class NibChatGPTAccessibilityService extends AccessibilityService {
 private static final String PREFS="nib_overlay", CHATGPT="com.openai.chatgpt";
 private final Handler handler=new Handler(Looper.getMainLooper());
 private String lastCandidate=""; private long stableSince=0L; private Runnable stabilityCheck;
 @Override protected void onServiceConnected(){ super.onServiceConnected(); getSharedPreferences(PREFS,MODE_PRIVATE).edit().putBoolean("bridgeServiceConnected",true).apply(); }
 @Override public void onDestroy(){ if(stabilityCheck!=null)handler.removeCallbacks(stabilityCheck); getSharedPreferences(PREFS,MODE_PRIVATE).edit().putBoolean("bridgeServiceConnected",false).apply(); super.onDestroy(); }
 @Override public void onInterrupt(){}
 @Override public void onAccessibilityEvent(AccessibilityEvent event){
  if(event==null||event.getPackageName()==null||!CHATGPT.contentEquals(event.getPackageName()))return;
  android.content.SharedPreferences p=getSharedPreferences(PREFS,MODE_PRIVATE); String prompt=p.getString("bridgePendingPrompt",""); if(prompt==null||prompt.isEmpty())return;
  AccessibilityNodeInfo root=getRootInActiveWindow(); if(root==null)return;
  if(!p.getBoolean("bridgePromptSent",false)){ if(trySend(root,prompt)){ p.edit().putBoolean("bridgePromptSent",true).putLong("bridgeSentAt",System.currentTimeMillis()).apply(); if(stabilityCheck!=null)handler.removeCallbacks(stabilityCheck); stabilityCheck=null; lastCandidate=""; stableSince=0L; } return; }
  long sent=p.getLong("bridgeSentAt",0L); if(System.currentTimeMillis()-sent<900)return;
  String before=p.getString("bridgeBeforeText",""); String candidate=extractNewReadableText(root,prompt,before);
  if(candidate.length()<3)return; long now=System.currentTimeMillis(); if(candidate.equals(lastCandidate)){ if(stableSince>0&&now-stableSince>1100)finishReply(p,candidate); } else { lastCandidate=candidate; stableSince=now; scheduleStabilityCheck(candidate); }
 }
 private void scheduleStabilityCheck(String expected){
  if(stabilityCheck!=null)handler.removeCallbacks(stabilityCheck);
  stabilityCheck=()->{
   android.content.SharedPreferences p=getSharedPreferences(PREFS,MODE_PRIVATE); String prompt=p.getString("bridgePendingPrompt",""); if(prompt==null||prompt.isEmpty()||!p.getBoolean("bridgePromptSent",false))return;
   AccessibilityNodeInfo root=getRootInActiveWindow(); if(root==null){handler.postDelayed(stabilityCheck,500L);return;}
   String current=extractNewReadableText(root,prompt,p.getString("bridgeBeforeText","")); if(current.length()<3){handler.postDelayed(stabilityCheck,500L);return;}
   if(current.equals(expected)&&current.equals(lastCandidate)){finishReply(p,current);return;}
   lastCandidate=current; stableSince=System.currentTimeMillis(); scheduleStabilityCheck(current);
  };
  handler.postDelayed(stabilityCheck,1350L);
 }
 private void finishReply(android.content.SharedPreferences p,String reply){
  if(stabilityCheck!=null)handler.removeCallbacks(stabilityCheck); stabilityCheck=null;
  p.edit().remove("bridgePendingPrompt").remove("bridgeBeforeText").putBoolean("bridgePromptSent",false).apply();
  String safe=reply==null?"":reply.trim(); lastCandidate=""; stableSince=0L; if(!safe.isEmpty())NibFloatingWindowPlugin.deliverChatGptReply(safe);
 }
 private boolean trySend(AccessibilityNodeInfo root,String prompt){
  List<AccessibilityNodeInfo> all=flatten(root); AccessibilityNodeInfo editor=null;
  for(AccessibilityNodeInfo n:all) if(n!=null&&n.isVisibleToUser()&&n.isEditable()) editor=n;
  if(editor==null)return false;
  getSharedPreferences(PREFS,MODE_PRIVATE).edit().putString("bridgeBeforeText",collectReadableText(root)).apply();
  Bundle args=new Bundle(); args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,prompt); if(!editor.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,args))return false;
  final AccessibilityNodeInfo targetEditor=editor; handler.postDelayed(()->clickSendWhenReady(targetEditor,0),320L);
  return true;
 }
 private void clickSendWhenReady(AccessibilityNodeInfo editor,int attempt){
  AccessibilityNodeInfo fresh=getRootInActiveWindow();
  if(fresh!=null){AccessibilityNodeInfo send=findSend(fresh,editor);if(send!=null&&send.performAction(AccessibilityNodeInfo.ACTION_CLICK))return;}
  if(attempt<5){handler.postDelayed(()->clickSendWhenReady(editor,attempt+1),240L);return;}
  if(android.os.Build.VERSION.SDK_INT>=30)editor.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.getId());
 }
 private AccessibilityNodeInfo findSend(AccessibilityNodeInfo root,AccessibilityNodeInfo editor){
  android.graphics.Rect er=new android.graphics.Rect(); if(editor!=null)editor.getBoundsInScreen(er);
  android.graphics.Rect rr=new android.graphics.Rect(); root.getBoundsInScreen(rr);
  AccessibilityNodeInfo spatial=null; int bestX=Integer.MIN_VALUE;
  for(AccessibilityNodeInfo n:flatten(root)){
   if(n==null||!n.isVisibleToUser()||!n.isClickable())continue;
   String text=n.getText()==null?"":n.getText().toString(); String desc=n.getContentDescription()==null?"":n.getContentDescription().toString(); String id=n.getViewIdResourceName()==null?"":n.getViewIdResourceName();
   String s=(text+" "+desc+" "+id).trim().toLowerCase(Locale.US);
   if(s.equals("send")||s.contains("send message")||s.contains("send_button")||s.contains("sendbutton")||s.contains("submit")||s.endsWith("/send"))return n;
   android.graphics.Rect b=new android.graphics.Rect(); n.getBoundsInScreen(b); if(b.isEmpty()||er.isEmpty())continue;
   boolean sameBand=Math.abs(b.centerY()-er.centerY())<Math.max(er.height(),b.height());
   boolean rightSide=b.centerX()>er.centerX();
   boolean compact=b.width()<Math.max(72,rr.width()/3)&&b.height()<Math.max(72,rr.height()/5);
   if(sameBand&&rightSide&&compact&&b.centerX()>bestX){bestX=b.centerX();spatial=n;}
  }
  return spatial;
 }
 private String extractNewReadableText(AccessibilityNodeInfo root,String prompt,String before){
  LinkedHashSet<String> old=new LinkedHashSet<>(Arrays.asList(before.split("\n"))); LinkedHashSet<String> out=new LinkedHashSet<>();
  for(AccessibilityNodeInfo n:flatten(root)){ if(n==null||!n.isVisibleToUser()||n.getText()==null||n.isEditable()||n.isClickable())continue; String s=n.getText().toString().trim(); if(s.length()<3||s.equals(prompt)||old.contains(s)||isChrome(s))continue; out.add(s); }
  StringBuilder b=new StringBuilder(); for(String s:out){ if(b.length()>0)b.append("\n"); b.append(s); } return b.toString().trim();
 }
 private boolean isChrome(String s){ String x=s.toLowerCase(Locale.US); return x.equals("chatgpt")||x.equals("new chat")||x.equals("share")||x.equals("regenerate")||x.equals("copy")||x.equals("good response")||x.equals("bad response")||x.contains("stop generating")||x.equals("send"); }
 private String collectReadableText(AccessibilityNodeInfo root){ LinkedHashSet<String> set=new LinkedHashSet<>(); for(AccessibilityNodeInfo n:flatten(root)){ if(n!=null&&n.isVisibleToUser()&&n.getText()!=null){String s=n.getText().toString().trim();if(!s.isEmpty())set.add(s);}} return String.join("\n",set); }
 private List<AccessibilityNodeInfo> flatten(AccessibilityNodeInfo root){ List<AccessibilityNodeInfo> out=new ArrayList<>(); ArrayDeque<AccessibilityNodeInfo> q=new ArrayDeque<>(); q.add(root); int guard=0; while(!q.isEmpty()&&guard++<1400){ AccessibilityNodeInfo n=q.removeFirst(); out.add(n); for(int i=0;i<n.getChildCount();i++){ AccessibilityNodeInfo c=n.getChild(i); if(c!=null)q.addLast(c); } } return out; }
}
