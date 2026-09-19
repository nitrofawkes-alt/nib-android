package app.byshawn.nib.overlay;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import java.util.*;

public class NibChatGPTAccessibilityService extends AccessibilityService {
 private static final String PREFS="nib_overlay", CHATGPT="com.openai.chatgpt";
 private final Handler handler=new Handler(Looper.getMainLooper());
 private String lastCandidate=""; private long stableSince=0L; private Runnable stabilityCheck; private Runnable navigationResume;
 @Override protected void onServiceConnected(){ super.onServiceConnected(); getSharedPreferences(PREFS,MODE_PRIVATE).edit().putBoolean("bridgeServiceConnected",true).apply(); note("accessibility_ready","Nib Accessibility is connected"); }
 @Override public void onDestroy(){ if(stabilityCheck!=null)handler.removeCallbacks(stabilityCheck); if(navigationResume!=null)handler.removeCallbacks(navigationResume); getSharedPreferences(PREFS,MODE_PRIVATE).edit().putBoolean("bridgeServiceConnected",false).apply(); super.onDestroy(); }
 @Override public void onInterrupt(){}
 @Override public void onAccessibilityEvent(AccessibilityEvent event){
  if(event==null||event.getPackageName()==null||!CHATGPT.contentEquals(event.getPackageName()))return;
  android.content.SharedPreferences p=getSharedPreferences(PREFS,MODE_PRIVATE); String prompt=p.getString("bridgePendingPrompt",""); if(prompt==null||prompt.isEmpty())return;
  AccessibilityNodeInfo root=chatGptRoot(); if(root==null)return;
  if(!p.getBoolean("bridgePromptSent",false)){ if(!p.getBoolean("bridgePromptPrepared",false)){long readyAt=p.getLong("bridgeNavigationReadyAt",0L);if(readyAt>System.currentTimeMillis()){scheduleNavigationResume(readyAt);return;}tryPrepareSend(root,prompt);} return; }
  long sent=p.getLong("bridgeSentAt",0L); if(System.currentTimeMillis()-sent<900)return;
  if(isChatGptGenerating(root)){ note("response_streaming","ChatGPT is still generating"); return; }
  String before=p.getString("bridgeBeforeText",""); String candidate=extractNewReadableText(root,prompt,before);
  if(candidate.length()<3)return; long now=System.currentTimeMillis(); if(candidate.equals(lastCandidate)){ if(stableSince>0&&now-stableSince>1100){note("response_stable",candidate.length()+" readable chars");finishReply(p,candidate);} } else { lastCandidate=candidate; stableSince=now; note("response_detected",candidate.length()+" readable chars"); scheduleStabilityCheck(candidate); }
 }
 private void scheduleNavigationResume(long readyAt){
  long delay=Math.max(120L,readyAt-System.currentTimeMillis());
  if(navigationResume!=null)handler.removeCallbacks(navigationResume);
  note("dedicated_chat_loading","Waiting for the saved Nib conversation to replace the previous ChatGPT screen");
  navigationResume=()->{
   navigationResume=null;
   android.content.SharedPreferences p=getSharedPreferences(PREFS,MODE_PRIVATE);
   String prompt=p.getString("bridgePendingPrompt","");
   if(prompt==null||prompt.isEmpty()||p.getBoolean("bridgePromptSent",false)||p.getBoolean("bridgePromptPrepared",false))return;
   AccessibilityNodeInfo root=chatGptRoot();
   if(root==null){scheduleNavigationResume(System.currentTimeMillis()+450L);return;}
   p.edit().remove("bridgeNavigationReadyAt").apply();
   note("dedicated_chat_ready","Navigation grace finished; preparing the prompt");
   tryPrepareSend(root,prompt);
  };
  handler.postDelayed(navigationResume,delay);
 }
 private void scheduleStabilityCheck(String expected){
  if(stabilityCheck!=null)handler.removeCallbacks(stabilityCheck);
  stabilityCheck=()->{
   android.content.SharedPreferences p=getSharedPreferences(PREFS,MODE_PRIVATE); String prompt=p.getString("bridgePendingPrompt",""); if(prompt==null||prompt.isEmpty()||!p.getBoolean("bridgePromptSent",false))return;
   AccessibilityNodeInfo root=chatGptRoot(); if(root==null){handler.postDelayed(stabilityCheck,500L);return;}
   if(isChatGptGenerating(root)){ note("response_streaming","ChatGPT is still generating"); handler.postDelayed(stabilityCheck,500L); return; }
   String current=extractNewReadableText(root,prompt,p.getString("bridgeBeforeText","")); if(current.length()<3){handler.postDelayed(stabilityCheck,500L);return;}
   if(current.equals(expected)&&current.equals(lastCandidate)){note("response_stable",current.length()+" readable chars");finishReply(p,current);return;}
   lastCandidate=current; stableSince=System.currentTimeMillis(); note("response_detected",current.length()+" readable chars"); scheduleStabilityCheck(current);
  };
  handler.postDelayed(stabilityCheck,1350L);
 }
 private void finishReply(android.content.SharedPreferences p,String reply){
  if(stabilityCheck!=null)handler.removeCallbacks(stabilityCheck); stabilityCheck=null;
  String safe=reply==null?"":reply.trim();
  if(isTransientStatus(safe)){note("response_transient_ignored",safe);lastCandidate="";stableSince=0L;handler.postDelayed(()->pollForReply(),500L);return;}
  boolean backstage=p.getBoolean("bridgeBackstage",false);
  p.edit().remove("bridgePendingPrompt").remove("bridgeBeforeText").remove("bridgeImageCount").remove("bridgeBackstage").remove("bridgeNavigationReadyAt").putBoolean("bridgePromptPrepared",false).putBoolean("bridgePromptSent",false).apply();
  lastCandidate=""; stableSince=0L; if(!safe.isEmpty()){note("reply_handed_off",safe.length()+" chars handed to Nib");if(backstage){NibFloatingWindowPlugin.deliverChatGptReply(safe);}else{performGlobalAction(GLOBAL_ACTION_HOME);handler.postDelayed(()->NibFloatingWindowPlugin.deliverChatGptReply(safe),420L);}}
 }
 private void tryPrepareSend(AccessibilityNodeInfo root,String prompt){
  AccessibilityNodeInfo editor=findEditor(root); if(editor==null)return;
  getSharedPreferences(PREFS,MODE_PRIVATE).edit().putString("bridgeBeforeText",collectReadableText(root)).apply();
  Bundle args=new Bundle(); args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,prompt);
  if(!editor.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,args))return;
  getSharedPreferences(PREFS,MODE_PRIVATE).edit().putBoolean("bridgePromptPrepared",true).apply();
  note("composer_filled",prompt.length()+" prompt chars");
  handler.postDelayed(()->clickSendWhenReady(prompt,0),300L);
 }
 private AccessibilityNodeInfo findEditor(AccessibilityNodeInfo root){
  AccessibilityNodeInfo editor=null; if(root==null)return null;
  for(AccessibilityNodeInfo n:flatten(root))if(n!=null&&n.isEditable())editor=n;
  return editor;
 }
 private void clickSendWhenReady(String prompt,int attempt){
  AccessibilityNodeInfo root=chatGptRoot(); AccessibilityNodeInfo editor=findEditor(root); boolean clicked=false;
  if(root!=null){AccessibilityNodeInfo send=findSend(root,editor); if(send!=null)clicked=clickNodeOrAncestor(send);}
  if(!clicked&&attempt>=5&&editor!=null&&android.os.Build.VERSION.SDK_INT>=30)clicked=editor.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.getId());
  if(clicked){note("send_clicked","attempt "+(attempt+1));handler.postDelayed(()->verifyPromptWasSent(prompt,0),380L);return;}
  if(attempt<8){handler.postDelayed(()->clickSendWhenReady(prompt,attempt+1),260L);return;}
  getSharedPreferences(PREFS,MODE_PRIVATE).edit().putBoolean("bridgePromptPrepared",false).apply();
  note("send_failed","No clickable Send control responded");
  NibFloatingWindowPlugin.failChatGptBridge("Nib filled the ChatGPT composer but could not press Send.");
 }
 private void verifyPromptWasSent(String prompt,int check){
  AccessibilityNodeInfo root=chatGptRoot(); AccessibilityNodeInfo editor=findEditor(root);
  String composer=editor==null||editor.getText()==null?"":editor.getText().toString().trim();
  String wanted=prompt==null?"":prompt.trim();
  if(editor!=null&&(composer.isEmpty()||!composer.equals(wanted))){markPromptSent();return;}
  if(check<4){handler.postDelayed(()->verifyPromptWasSent(prompt,check+1),320L);return;}
  // Do not automatically press Send again after ambiguous verification: it can duplicate a user action.
  getSharedPreferences(PREFS,MODE_PRIVATE).edit().putBoolean("bridgePromptPrepared",false).apply();
  note("send_verification_failed","Prompt remained in composer");
  NibFloatingWindowPlugin.failChatGptBridge("ChatGPT kept the prompt in the composer instead of sending it.");
 }
 private void markPromptSent(){
  android.content.SharedPreferences p=getSharedPreferences(PREFS,MODE_PRIVATE);
  p.edit().remove("bridgeNavigationReadyAt").putBoolean("bridgePromptPrepared",false).putBoolean("bridgePromptSent",true).putLong("bridgeSentAt",System.currentTimeMillis()).apply();
  if(stabilityCheck!=null)handler.removeCallbacks(stabilityCheck); stabilityCheck=null; lastCandidate=""; stableSince=0L;
  note("send_verified","Composer cleared after send");
  handler.postDelayed(()->pollForReply(),950L);
 }
 private void pollForReply(){
  android.content.SharedPreferences p=getSharedPreferences(PREFS,MODE_PRIVATE);
  String prompt=p.getString("bridgePendingPrompt","");
  if(prompt==null||prompt.isEmpty()||!p.getBoolean("bridgePromptSent",false))return;
  AccessibilityNodeInfo root=chatGptRoot();
  if(root==null){handler.postDelayed(()->pollForReply(),500L);return;}
  if(isChatGptGenerating(root)){ note("response_streaming","ChatGPT is still generating"); handler.postDelayed(()->pollForReply(),500L); return; }
  String current=extractNewReadableText(root,prompt,p.getString("bridgeBeforeText",""));
  if(current.length()<2){handler.postDelayed(()->pollForReply(),500L);return;}
  if(!current.equals(lastCandidate)){lastCandidate=current;stableSince=System.currentTimeMillis();note("response_detected",current.length()+" readable chars");}
  scheduleStabilityCheck(current);
 }
 private boolean clickNodeOrAncestor(AccessibilityNodeInfo node){
  AccessibilityNodeInfo current=node; int hops=0;
  while(current!=null&&hops++<5){if(current.isClickable()&&current.performAction(AccessibilityNodeInfo.ACTION_CLICK))return true;current=current.getParent();}
  return node!=null&&node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
 }
 private AccessibilityNodeInfo findSend(AccessibilityNodeInfo root,AccessibilityNodeInfo editor){
  android.graphics.Rect er=new android.graphics.Rect(); if(editor!=null)editor.getBoundsInScreen(er);
  android.graphics.Rect rr=new android.graphics.Rect(); root.getBoundsInScreen(rr);
  AccessibilityNodeInfo spatial=null; int bestX=Integer.MIN_VALUE;
  for(AccessibilityNodeInfo n:flatten(root)){
   if(n==null)continue;
   String text=n.getText()==null?"":n.getText().toString(); String desc=n.getContentDescription()==null?"":n.getContentDescription().toString(); String id=n.getViewIdResourceName()==null?"":n.getViewIdResourceName();
   String s=(text+" "+desc+" "+id).trim().toLowerCase(Locale.US);
   if(s.equals("send")||s.contains("send message")||s.contains("send_button")||s.contains("sendbutton")||s.contains("submit")||s.endsWith("/send"))return n;
   if(!n.isClickable())continue;
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
  for(AccessibilityNodeInfo n:flatten(root)){
   if(n==null||n.isEditable())continue;
   CharSequence text=n.getText(); if(text!=null)addCandidate(out,old,prompt,text.toString());
   CharSequence desc=n.getContentDescription(); if(desc!=null)addCandidate(out,old,prompt,desc.toString());
  }
  StringBuilder b=new StringBuilder(); for(String value:out){ if(b.length()>0)b.append("\n"); b.append(value); } return b.toString().trim();
 }
 private void addCandidate(LinkedHashSet<String> out,LinkedHashSet<String> old,String prompt,String raw){
  if(raw==null)return; String value=raw.trim(); if(value.length()<2||value.equals(prompt)||old.contains(value)||isChrome(value)||isTransientStatus(value))return; out.add(value);
 }
 private boolean isChatGptGenerating(AccessibilityNodeInfo root){
  if(root==null)return false;
  for(AccessibilityNodeInfo n:flatten(root)){
   if(n==null)continue;
   String text=n.getText()==null?"":n.getText().toString().trim().toLowerCase(Locale.US);
   String desc=n.getContentDescription()==null?"":n.getContentDescription().toString().trim().toLowerCase(Locale.US);
   String both=(text+" "+desc).trim();
   if(isTransientStatus(text)||isTransientStatus(desc)||both.equals("stop")||both.equals("stop generating")||both.contains("stop generating")||both.contains("stop response"))return true;
  }
  return false;
 }
 private boolean isTransientStatus(String raw){
  if(raw==null)return false;
  String x=raw.toLowerCase(Locale.US).replace("…","...").replaceAll("\\s+"," ").trim();
  if(x.isEmpty()||x.length()>80)return false;
  while(x.endsWith("."))x=x.substring(0,x.length()-1).trim();
  if(x.equals("thinking")||x.startsWith("thinking for ")||x.equals("working")||x.equals("reasoning")||x.equals("analyzing")||x.equals("analysing")||x.equals("generating")||x.equals("loading")||x.equals("reading")||x.equals("browsing")||x.equals("searching")||x.equals("using tools")||x.equals("using tool"))return true;
  return x.startsWith("searching the web")||x.startsWith("searching files")||x.startsWith("working on it")||x.startsWith("reading the web");
 }
 private boolean isChrome(String s){
  String x=s.toLowerCase(Locale.US).trim();
  if(x.equals("chatgpt")||x.equals("new chat")||x.equals("share")||x.equals("regenerate")||x.equals("copy")||x.equals("copy response")||x.equals("good response")||x.equals("bad response")||x.equals("read aloud")||x.equals("more actions")||x.equals("retry")||x.equals("send")||x.equals("reply to chatgpt")||x.equals("navigate up")||x.equals("edit")||x.equals("menu")||x.equals("image")||x.equals("message attachment")||x.equals("dictation")||x.equals("follow up")||x.equals("stop")||x.contains("stop generating")||x.contains("chatgpt can make mistakes"))return true;
  String[] chrome={"navigate up"," edit "," menu ","send message","message attachment","adjust effort"," selected","dictation","reply to chatgpt","more actions","read aloud","copy response"}; int hits=0; String padded=" "+x+" "; for(String token:chrome)if(padded.contains(token))hits++; return hits>=2;
 }
 private String collectReadableText(AccessibilityNodeInfo root){
  LinkedHashSet<String> set=new LinkedHashSet<>();
  for(AccessibilityNodeInfo n:flatten(root)){
   if(n==null)continue;
   CharSequence text=n.getText(); if(text!=null){String s=text.toString().trim();if(!s.isEmpty())set.add(s);}
   CharSequence desc=n.getContentDescription(); if(desc!=null){String s=desc.toString().trim();if(!s.isEmpty())set.add(s);}
  }
  return String.join("\n",set);
 }
 private AccessibilityNodeInfo chatGptRoot(){
  try{
   List<AccessibilityWindowInfo> windows=getWindows();
   if(windows!=null)for(AccessibilityWindowInfo window:windows){
    if(window==null)continue; AccessibilityNodeInfo root=window.getRoot(); if(root==null)continue; CharSequence pkg=root.getPackageName(); if(pkg!=null&&CHATGPT.contentEquals(pkg))return root;
   }
  }catch(Exception ignored){}
  try{AccessibilityNodeInfo active=super.getRootInActiveWindow(); if(active!=null&&active.getPackageName()!=null&&CHATGPT.contentEquals(active.getPackageName()))return active;}catch(Exception ignored){}
  return null;
 }
 private void note(String stage,String detail){android.content.SharedPreferences p=getSharedPreferences(PREFS,MODE_PRIVATE);long now=System.currentTimeMillis();String safeStage=stage==null?"unknown":stage.trim();String safeDetail=detail==null?"":detail.replace('\n',' ').trim();String old=p.getString("bridgeDiagTrace","");String line=now+" | "+safeStage+(safeDetail.isEmpty()?"":" | "+safeDetail);ArrayList<String> lines=new ArrayList<>();if(old!=null&&!old.trim().isEmpty())lines.addAll(Arrays.asList(old.split("\n")));lines.add(line);while(lines.size()>16)lines.remove(0);String trace=String.join("\n",lines);if(trace.length()>3500)trace=trace.substring(trace.length()-3500);p.edit().putString("bridgeDiagStage",safeStage).putString("bridgeDiagDetail",safeDetail).putString("bridgeDiagTrace",trace).putLong("bridgeDiagAt",now).apply();}
 private List<AccessibilityNodeInfo> flatten(AccessibilityNodeInfo root){ List<AccessibilityNodeInfo> out=new ArrayList<>(); ArrayDeque<AccessibilityNodeInfo> q=new ArrayDeque<>(); q.add(root); int guard=0; while(!q.isEmpty()&&guard++<1400){ AccessibilityNodeInfo n=q.removeFirst(); out.add(n); for(int i=0;i<n.getChildCount();i++){ AccessibilityNodeInfo c=n.getChild(i); if(c!=null)q.addLast(c); } } return out; }
}
