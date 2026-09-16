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
 private String lastCandidate=""; private long stableSince=0L;
 @Override protected void onServiceConnected(){ super.onServiceConnected(); getSharedPreferences(PREFS,MODE_PRIVATE).edit().putBoolean("bridgeServiceConnected",true).apply(); }
 @Override public void onDestroy(){ getSharedPreferences(PREFS,MODE_PRIVATE).edit().putBoolean("bridgeServiceConnected",false).apply(); super.onDestroy(); }
 @Override public void onInterrupt(){}
 @Override public void onAccessibilityEvent(AccessibilityEvent event){
  if(event==null||event.getPackageName()==null||!CHATGPT.contentEquals(event.getPackageName()))return;
  android.content.SharedPreferences p=getSharedPreferences(PREFS,MODE_PRIVATE); String prompt=p.getString("bridgePendingPrompt",""); if(prompt==null||prompt.isEmpty())return;
  AccessibilityNodeInfo root=getRootInActiveWindow(); if(root==null)return;
  if(!p.getBoolean("bridgePromptSent",false)){ if(trySend(root,prompt)){ p.edit().putBoolean("bridgePromptSent",true).putLong("bridgeSentAt",System.currentTimeMillis()).apply(); lastCandidate=""; stableSince=0L; } return; }
  long sent=p.getLong("bridgeSentAt",0L); if(System.currentTimeMillis()-sent<900)return;
  String before=p.getString("bridgeBeforeText",""); String candidate=extractNewReadableText(root,prompt,before);
  if(candidate.length()<3)return; long now=System.currentTimeMillis(); if(candidate.equals(lastCandidate)){ if(stableSince>0&&now-stableSince>1100){ p.edit().remove("bridgePendingPrompt").remove("bridgeBeforeText").putBoolean("bridgePromptSent",false).apply(); NibFloatingWindowPlugin.deliverChatGptReply(candidate); lastCandidate=""; stableSince=0L; } } else { lastCandidate=candidate; stableSince=now; }
 }
 private boolean trySend(AccessibilityNodeInfo root,String prompt){
  List<AccessibilityNodeInfo> all=flatten(root); AccessibilityNodeInfo editor=null;
  for(AccessibilityNodeInfo n:all) if(n!=null&&n.isVisibleToUser()&&n.isEditable()) editor=n;
  if(editor==null)return false;
  getSharedPreferences(PREFS,MODE_PRIVATE).edit().putString("bridgeBeforeText",collectReadableText(root)).apply();
  Bundle args=new Bundle(); args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,prompt); if(!editor.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,args))return false;
  handler.postDelayed(()->{ AccessibilityNodeInfo fresh=getRootInActiveWindow(); if(fresh==null)return; AccessibilityNodeInfo send=findSend(fresh); if(send!=null)send.performAction(AccessibilityNodeInfo.ACTION_CLICK); else if(android.os.Build.VERSION.SDK_INT>=30)editor.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.getId()); },220);
  return true;
 }
 private AccessibilityNodeInfo findSend(AccessibilityNodeInfo root){
  AccessibilityNodeInfo fallback=null; for(AccessibilityNodeInfo n:flatten(root)){ if(n==null||!n.isVisibleToUser()||!n.isClickable())continue; String s=((n.getText()==null?"":n.getText().toString())+" "+(n.getContentDescription()==null?"":n.getContentDescription().toString())).trim().toLowerCase(Locale.US); if(s.equals("send")||s.contains("send message")||s.startsWith("send ")||s.endsWith(" send"))return n; if(s.contains("submit"))fallback=n; } return fallback;
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
