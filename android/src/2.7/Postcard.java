package app.elaina.wordbook;

import android.app.*;
import android.content.*;
import android.database.*;
import android.net.Uri;
import android.os.*;
import android.provider.OpenableColumns;
import android.util.Base64;
import android.view.*;
import android.webkit.*;
import android.widget.Toast;
import java.io.*;
import java.util.*;

public class Postcard extends ContentProvider implements Application.ActivityLifecycleCallbacks {
    static final String AUTH="app.elaina.wordbook.postcards";
    public boolean onCreate(){
        ((Application)getContext().getApplicationContext()).registerActivityLifecycleCallbacks(this);
        return true;
    }
    private WebView find(View v){
        if(v instanceof WebView)return (WebView)v;
        if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){WebView w=find(g.getChildAt(i));if(w!=null)return w;}}
        return null;
    }
    private final WeakHashMap<Activity,Boolean> attached=new WeakHashMap<>();
    public void onActivityResumed(Activity a){
        if(attached.containsKey(a))return;
        WebView w=find(a.getWindow().getDecorView());if(w==null)return;
        attached.put(a,true);w.addJavascriptInterface(new OnlineBooks(), "ElainaBooks");w.addJavascriptInterface(new Share(a),"ElainaPostcard");
        w.evaluateJavascript("typeof ElainaPostcard",v->{if("\"undefined\"".equals(v)&&!a.isFinishing()&&!a.isDestroyed())w.reload();});
    }
    public void onActivityCreated(Activity a,Bundle b){}
    public void onActivityPaused(Activity a){}
    public void onActivityStarted(Activity a){}
    public void onActivityStopped(Activity a){}
    public void onActivityDestroyed(Activity a){attached.remove(a);}
    public void onActivitySaveInstanceState(Activity a,Bundle b){}
    private static File dir(Context c){return new File(c.getCacheDir(),"postcards");}
    public static class Share{
        final Activity host;
        Share(Activity a){host=a;}
        @JavascriptInterface public void send(String jpeg){
            try{
                if(jpeg.length()>6000000)throw new IOException();
                File d=dir(host);d.mkdirs();
                File[] files=d.listFiles();if(files!=null)for(File f:files)if(f.lastModified()<System.currentTimeMillis()-86400000L)f.delete();
                File image=File.createTempFile("card-", ".jpg",d);
                try(FileOutputStream out=new FileOutputStream(image)){out.write(Base64.decode(jpeg,Base64.DEFAULT));}
                Uri uri=new Uri.Builder().scheme("content").authority(AUTH).appendPath(image.getName()).build();
                Intent share=new Intent(Intent.ACTION_SEND).setType("image/jpeg").putExtra(Intent.EXTRA_STREAM,uri);
                share.setClipData(ClipData.newRawUri("打卡明信片",uri));share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                host.runOnUiThread(()->{try{host.startActivity(Intent.createChooser(share,"分享打卡"));}catch(Exception e){error();}});
            }catch(Exception e){host.runOnUiThread(()->error());}
        }
        void error(){Toast.makeText(host,"未能分享图片，请重试",Toast.LENGTH_SHORT).show();}
    }
    private File file(Uri u){String n=u.getLastPathSegment();if(n==null||!n.matches("card-[a-zA-Z0-9-]+[.]jpg"))throw new IllegalArgumentException();return new File(dir(getContext()),n);}
    public String getType(Uri u){return "image/jpeg";}
    public ParcelFileDescriptor openFile(Uri u,String mode)throws FileNotFoundException{if(!"r".equals(mode))throw new FileNotFoundException();return ParcelFileDescriptor.open(file(u),ParcelFileDescriptor.MODE_READ_ONLY);}
    public Cursor query(Uri u,String[] cols,String s,String[] args,String order){
        File f=file(u);if(cols==null)cols=new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE};
        MatrixCursor c=new MatrixCursor(cols);MatrixCursor.RowBuilder r=c.newRow();
        for(String k:cols){if(OpenableColumns.SIZE.equals(k))r.add(f.length());else if(OpenableColumns.DISPLAY_NAME.equals(k))r.add("魔法单词本打卡.jpg");else r.add(null);}return c;
    }
    public Uri insert(Uri u,ContentValues v){throw new UnsupportedOperationException();}
    public int delete(Uri u,String s,String[] args){throw new UnsupportedOperationException();}
    public int update(Uri u,ContentValues v,String s,String[] args){throw new UnsupportedOperationException();}
}
