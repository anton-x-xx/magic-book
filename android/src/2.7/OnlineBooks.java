package app.elaina.wordbook;

import android.webkit.JavascriptInterface;
import java.io.*;
import java.net.*;
import java.util.zip.*;
import org.json.*;

public class OnlineBooks {
    private static final String[] FILES = {
        "1523620217431_CET4luan_1", "1524052539052_CET4luan_2",
        "1521164660466_CET6luan_1", "1524052554766_CET6_2",
        "1521164661106_KaoYanluan_1", "1521164654696_KaoYan_2"
    };
    private static final int[] COUNTS = {1162,3739,1228,2078,1341,4533};
    private static final String[] SOURCES = {
        "https://ydschool-online.nos.netease.com/",
        "https://cdn.jsdelivr.net/gh/kajweb/dict@master/book/",
        "https://raw.githubusercontent.com/kajweb/dict/master/book/"
    };
    private volatile int state;
    private volatile String result = "";
    @JavascriptInterface public synchronized boolean start(final int i) {
        if (i < 0 || i >= FILES.length || state == 1) return false;
        state = 1; result = "";
        new Thread(() -> {
            for (String source : SOURCES) {
                try {
                    result = fetch(new URL(source + FILES[i] + ".zip"), COUNTS[i]);
                    state = 2; return;
                } catch (Exception ignored) {}
            }
            state = -1;
        }).start();
        return true;
    }
    @JavascriptInterface public int status() { return state; }
    @JavascriptInterface public String part(int offset) {
        String s = result;
        return offset >= 0 && offset < s.length() ? s.substring(offset, Math.min(s.length(),offset+16384)) : "";
    }
    @JavascriptInterface public void clear() { if (state != 1) result = ""; }
    static String fetch(URL url, int count) throws Exception {
        HttpURLConnection c = (HttpURLConnection)url.openConnection();
        c.setConnectTimeout(8000); c.setReadTimeout(12000);
        try (ZipInputStream zip = new ZipInputStream(c.getInputStream())) {
            ZipEntry entry;
            while ((entry=zip.getNextEntry())!=null) {
                if (!entry.getName().endsWith(".json") || entry.getName().startsWith("__MACOSX")) continue;
                BufferedReader reader=new BufferedReader(new InputStreamReader(zip,"UTF-8"));
                JSONArray rows=new JSONArray(); String line;
                while ((line=reader.readLine())!=null) {
                    if(line.trim().isEmpty()) continue;
                    JSONObject w=new JSONObject(line);
                    JSONObject d=w.getJSONObject("content").getJSONObject("word").getJSONObject("content");
                    JSONArray ts=d.getJSONArray("trans");
                    StringBuilder meaning=new StringBuilder();
                    for(int j=0;j<ts.length();j++){
                        JSONObject t=ts.getJSONObject(j);
                        if(j>0)meaning.append("；");
                        meaning.append((t.optString("pos")+" "+t.optString("tranCn")).trim());
                    }
                    JSONObject row=new JSONObject().put("phrase",w.getString("headWord")).put("meaning",meaning.toString());
                    JSONArray examples=new JSONArray();
                    JSONObject section=d.optJSONObject("sentence");
                    JSONArray pairs=section==null?null:section.optJSONArray("sentences");
                    for(int j=0;pairs!=null && j<pairs.length();j++){
                        JSONObject p=pairs.getJSONObject(j);
                        examples.put(new JSONObject().put("en",p.optString("sContent")).put("zh",p.optString("sCn")));
                    }
                    rows.put(row.put("sentences",examples));
                    if(rows.length()>count)throw new IOException();
                }
                if(rows.length()!=count)throw new IOException();
                return rows.toString();
            }
            throw new IOException();
        } finally { c.disconnect(); }
    }
}
