package id.gia.assistant;
import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.webkit.*;
import android.widget.*;
import android.widget.FrameLayout.LayoutParams;

public class MainActivity extends Activity {
 private static final int AUDIO_PERMISSION_REQUEST=41;
 private static final String APP_URL="https://alfindo149.github.io/Kawan-ai/";
 private WebView webView; private PermissionRequest pendingRequest;
 @Override public void onCreate(Bundle b){super.onCreate(b);showWebView();}
 private void showWebView(){
  webView=new WebView(this);
  FrameLayout root=new FrameLayout(this);
  root.addView(webView,new LayoutParams(-1,-1));
  Button local=new Button(this); local.setText("GIA AI LOKAL");
  local.setTextColor(Color.WHITE);
  local.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(65,87,210)));
  LayoutParams bp=new LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.END); bp.setMargins(0,0,24,28);
  root.addView(local,bp); local.setOnClickListener(v->startActivity(new Intent(this,LocalAiActivity.class)));
  setContentView(root);
  WebSettings s=webView.getSettings(); s.setJavaScriptEnabled(true); s.setDomStorageEnabled(true);
  s.setDatabaseEnabled(true); s.setMediaPlaybackRequiresUserGesture(false); s.setSupportZoom(false);
  s.setLoadsImagesAutomatically(true);
  webView.setWebViewClient(new WebViewClient(){
   @Override public void onReceivedError(WebView v,WebResourceRequest r,WebResourceError e){super.onReceivedError(v,r,e);if(r.isForMainFrame())showError("GIA online tidak tersambung. Tombol AI Lokal tetap bisa digunakan.");}
   @Override public void onReceivedHttpError(WebView v,WebResourceRequest r,WebResourceResponse p){super.onReceivedHttpError(v,r,p);if(r.isForMainFrame()&&p.getStatusCode()>=400)showError("Server GIA merespons "+p.getStatusCode()+". AI Lokal tetap tersedia.");}
  });
  webView.setWebChromeClient(new WebChromeClient(){
   @Override public boolean onConsoleMessage(ConsoleMessage m){android.util.Log.d("GIA",m.message());return true;}
   @Override public void onPermissionRequest(PermissionRequest r){runOnUiThread(()->{
    boolean audio=false;for(String x:r.getResources())if(PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(x))audio=true;
    if(!audio){r.deny();return;}
    if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED)r.grant(new String[]{PermissionRequest.RESOURCE_AUDIO_CAPTURE});
    else{pendingRequest=r;requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},AUDIO_PERMISSION_REQUEST);}
   });}
  });
  webView.loadUrl(APP_URL);
 }
 private void showError(String msg){runOnUiThread(()->Toast.makeText(this,msg,Toast.LENGTH_LONG).show());}
 @Override public void onRequestPermissionsResult(int c,String[] p,int[] g){super.onRequestPermissionsResult(c,p,g);if(c==AUDIO_PERMISSION_REQUEST&&pendingRequest!=null){if(g.length>0&&g[0]==PackageManager.PERMISSION_GRANTED)pendingRequest.grant(new String[]{PermissionRequest.RESOURCE_AUDIO_CAPTURE});else pendingRequest.deny();pendingRequest=null;}}
 @Override public void onBackPressed(){if(webView!=null&&webView.canGoBack())webView.goBack();else super.onBackPressed();}
 @Override protected void onDestroy(){if(webView!=null){webView.stopLoading();webView.destroy();webView=null;}super.onDestroy();}
}
