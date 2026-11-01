package ru.kompik24.help;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.net.*;

public class MainActivity extends Activity {
    private final int orange=0xFFFF7900, dark=0xFF171717, panel=0xFF292929;
    private LinearLayout content;
    private TextView heading, status;
    private String selectedCategory;
    private long downloadId=-1;

    private final BroadcastReceiver receiver=new BroadcastReceiver(){
        @Override public void onReceive(Context c, Intent i){
            if(i.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID,-1)!=downloadId)return;
            DownloadManager dm=(DownloadManager)getSystemService(DOWNLOAD_SERVICE);
            DownloadManager.Query q=new DownloadManager.Query().setFilterById(downloadId);
            android.database.Cursor cur=dm.query(q);
            if(cur!=null && cur.moveToFirst()){
                int s=cur.getInt(cur.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
                if(s==DownloadManager.STATUS_SUCCESSFUL) installApk(dm.getUriForDownloadedFile(downloadId));
                else showStatus("Не удалось скачать APK");
                cur.close();
            }
        }
    };

    @Override public void onCreate(Bundle b){
        super.onCreate(b); buildUi();
        registerReceiver(receiver,new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
        loadCategories();
    }
    @Override protected void onDestroy(){try{unregisterReceiver(receiver);}catch(Exception ignored){} super.onDestroy();}

    private void buildUi(){
        LinearLayout root=new LinearLayout(this); root.setOrientation(1); root.setPadding(24,20,24,20); root.setBackgroundColor(dark);
        TextView brand=new TextView(this); brand.setText("HELP"); brand.setTextColor(orange); brand.setTextSize(30); brand.setPadding(8,4,8,12); root.addView(brand);
        heading=new TextView(this); heading.setTextColor(-1); heading.setTextSize(23); heading.setPadding(8,8,8,12); root.addView(heading);
        status=new TextView(this); status.setTextColor(0xFFBBBBBB); status.setTextSize(14); status.setPadding(8,4,8,8); root.addView(status);
        ScrollView scroll=new ScrollView(this); content=new LinearLayout(this); content.setOrientation(1); scroll.addView(content);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1)); setContentView(root);
    }
    private Button button(String text){
        Button b=new Button(this); b.setText(text); b.setAllCaps(false); b.setTextSize(18); b.setTextColor(-1); b.setPadding(18,18,18,18);
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(panel)); b.setFocusable(true);
        b.setOnFocusChangeListener((v,f)->v.setBackgroundTintList(android.content.res.ColorStateList.valueOf(f?orange:panel)));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2); p.setMargins(4,5,4,5); b.setLayoutParams(p); return b;
    }
    private void showStatus(String s){runOnUiThread(()->status.setText(s));}
    private String api(String p){return BuildConfig.API_BASE_URL+p;}
    private String get(String path)throws Exception{
        HttpURLConnection c=(HttpURLConnection)new URL(api(path)).openConnection(); c.setConnectTimeout(12000); c.setReadTimeout(15000);
        try{int code=c.getResponseCode(); if(code<200||code>=300)throw new Exception("HTTP "+code);
            BufferedReader r=new BufferedReader(new InputStreamReader(c.getInputStream(),"UTF-8")); StringBuilder out=new StringBuilder(); String line;
            while((line=r.readLine())!=null)out.append(line); r.close(); return out.toString();
        }finally{c.disconnect();}
    }
    private void loadCategories(){
        selectedCategory=null; heading.setText("Категории приложений"); content.removeAllViews(); showStatus("Загрузка каталога…");
        new Thread(()->{try{
            JSONArray a=new JSONObject(get("api/categories")).optJSONArray("categories"); if(a==null)a=new JSONArray(); JSONArray items=a;
            runOnUiThread(()->{content.removeAllViews(); status.setText(items.length()==0?"Категорий пока нет":"");
                for(int n=0;n<items.length();n++){JSONObject o=items.optJSONObject(n); if(o==null)continue;
                    String id=o.optString("id"), name=o.optString("name","Категория"); Button b=button(name+"   ›");
                    b.setOnClickListener(v->loadApps(id,name)); content.addView(b);
                }});
        }catch(Exception e){showStatus("Нет связи с сервером. Проверьте адрес API.");}}).start();
    }
    private void loadApps(String id,String categoryName){
        selectedCategory=id; heading.setText(categoryName); content.removeAllViews(); Button back=button("‹  Все категории");
        back.setOnClickListener(v->loadCategories()); content.addView(back); showStatus("Загрузка приложений…");
        new Thread(()->{try{
            JSONArray a=new JSONObject(get("api/apps?categoryId="+Uri.encode(id))).optJSONArray("apps"); if(a==null)a=new JSONArray(); JSONArray items=a;
            runOnUiThread(()->{while(content.getChildCount()>1)content.removeViewAt(1); status.setText(items.length()==0?"Приложений пока нет":"");
                for(int n=0;n<items.length();n++){JSONObject o=items.optJSONObject(n);if(o==null)continue;
                    String appId=o.optString("id"),name=o.optString("name","Приложение"),version=o.optString("version",""),desc=o.optString("description","");
                    Button b=button(name+(version.isEmpty()?"":"  ·  "+version)); b.setOnClickListener(v->showApp(appId,name,version,desc));content.addView(b);
                }});
        }catch(Exception e){showStatus("Ошибка загрузки списка приложений");}}).start();
    }
    private void showApp(String id,String name,String version,String description){
        content.removeAllViews(); Button back=button("‹  Назад"); back.setOnClickListener(v->loadApps(selectedCategory,heading.getText().toString())); content.addView(back);
        TextView info=new TextView(this); info.setText(name+(version.isEmpty()?"":"\nВерсия: "+version)+(description.isEmpty()?"":"\n\n"+description));
        info.setTextColor(-1);info.setTextSize(19);info.setPadding(12,20,12,24);content.addView(info);
        Button install=button("УСТАНОВИТЬ"); install.setBackgroundTintList(android.content.res.ColorStateList.valueOf(orange));
        install.setOnClickListener(v->download(id,name));content.addView(install);
    }
    private void download(String id,String name){
        try{DownloadManager.Request r=new DownloadManager.Request(Uri.parse(api("api/apps/"+Uri.encode(id)+"/download")));
            r.setTitle("HELP — "+name);r.setDescription("Загрузка APK");r.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            r.setDestinationInExternalFilesDir(this,Environment.DIRECTORY_DOWNLOADS,"help-"+id+".apk");
            downloadId=((DownloadManager)getSystemService(DOWNLOAD_SERVICE)).enqueue(r);showStatus("APK загружается…");
        }catch(Exception e){showStatus("Не удалось начать загрузку");}
    }
    private void installApk(Uri apk){
        if(apk==null){showStatus("Файл APK не найден");return;}
        if(Build.VERSION.SDK_INT>=26&&!getPackageManager().canRequestPackageInstalls()){
            startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+getPackageName())));
            Toast.makeText(this,"Разрешите установку из этого источника, затем нажмите «Установить» ещё раз",Toast.LENGTH_LONG).show();return;
        }
        Intent i=new Intent(Intent.ACTION_VIEW);i.setDataAndType(apk,"application/vnd.android.package-archive");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_ACTIVITY_NEW_TASK);
        try{startActivity(i);}catch(Exception e){showStatus("Не удалось открыть установщик Android");}
    }
}
