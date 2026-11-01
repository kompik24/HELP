package ru.kompik24.help;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.util.*;

public class MainActivity extends Activity {
    private final int orange=0xFFFF7900, dark=0xFF171717, panel=0xFF292929;
    private LinearLayout content;
    private TextView heading, status;
    private String selectedCategory;
    private long downloadId=-1;
    private final LinkedHashMap<String,AppItem> selectedApps=new LinkedHashMap<>();
    private final ArrayList<AppItem> installQueue=new ArrayList<>();
    private int queueIndex=0;
    private Uri pendingApk=null;
    private boolean waitingInstallPermission=false;
    private static final int INSTALL_REQUEST=701, PERMISSION_REQUEST=702;

    private static class AppItem {
        String id,name,version,description;
        AppItem(String i,String n,String v,String d){id=i;name=n;version=v;description=d;}
    }

    private final BroadcastReceiver receiver=new BroadcastReceiver(){
        @Override public void onReceive(Context c,Intent i){
            if(i.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID,-1)!=downloadId)return;
            DownloadManager dm=(DownloadManager)getSystemService(DOWNLOAD_SERVICE);
            DownloadManager.Query q=new DownloadManager.Query().setFilterById(downloadId);
            android.database.Cursor cur=dm.query(q);
            if(cur!=null&&cur.moveToFirst()){
                int s=cur.getInt(cur.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
                if(s==DownloadManager.STATUS_SUCCESSFUL){
                    Uri uri=dm.getUriForDownloadedFile(downloadId);
                    if(uri==null){showStatus("Не удалось получить загруженный APK");nextInQueue();}else openInstaller(uri);
                }else{
                    showStatus("Ошибка загрузки: "+currentName());
                    nextInQueue();
                }
                cur.close();
            }
        }
    };

    @Override public void onCreate(Bundle b){
        super.onCreate(b);buildUi();
        IntentFilter filter=new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
        if(Build.VERSION.SDK_INT>=33)registerReceiver(receiver,filter,Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(receiver,filter);
        loadCategories();
    }
    @Override protected void onResume(){
        super.onResume();
        if(waitingInstallPermission&&Build.VERSION.SDK_INT>=26&&getPackageManager().canRequestPackageInstalls()){
            waitingInstallPermission=false;
            if(pendingApk!=null){Uri u=pendingApk;pendingApk=null;launchInstaller(u);}
        }
    }
    @Override protected void onDestroy(){try{unregisterReceiver(receiver);}catch(Exception ignored){}super.onDestroy();}

    private void buildUi(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(24,20,24,20);root.setBackgroundColor(dark);
        TextView brand=new TextView(this);brand.setText("HELP");brand.setTextColor(orange);brand.setTextSize(30);brand.setPadding(8,4,8,12);root.addView(brand);
        heading=new TextView(this);heading.setTextColor(-1);heading.setTextSize(23);heading.setPadding(8,8,8,12);root.addView(heading);
        status=new TextView(this);status.setTextColor(0xFFBBBBBB);status.setTextSize(14);status.setPadding(8,4,8,8);root.addView(status);
        ScrollView scroll=new ScrollView(this);content=new LinearLayout(this);content.setOrientation(1);scroll.addView(content);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);
    }
    private Button button(String text){
        Button b=new Button(this);b.setText(text);b.setAllCaps(false);b.setTextSize(18);b.setTextColor(-1);b.setPadding(18,18,18,18);
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(panel));b.setFocusable(true);
        b.setOnFocusChangeListener((v,f)->v.setBackgroundTintList(android.content.res.ColorStateList.valueOf(f?orange:panel)));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(4,5,4,5);b.setLayoutParams(p);return b;
    }
    private void showStatus(String s){runOnUiThread(()->status.setText(s));}
    private String api(String p){return BuildConfig.API_BASE_URL+p;}
    private String get(String path)throws Exception{
        Exception last=null;
        for(int attempt=1;attempt<=3;attempt++){
            HttpURLConnection c=null;
            try{
                c=(HttpURLConnection)new URL(api(path)).openConnection();c.setConnectTimeout(25000);c.setReadTimeout(30000);c.setRequestProperty("Connection","close");
                int code=c.getResponseCode();
                if(code<200||code>=300){Exception http=new Exception("HTTP "+code);if(code<500)throw http;last=http;}
                else{BufferedReader reader=new BufferedReader(new InputStreamReader(c.getInputStream(),"UTF-8"));StringBuilder out=new StringBuilder();String line;while((line=reader.readLine())!=null)out.append(line);reader.close();return out.toString();}
            }catch(Exception e){if(e.getMessage()!=null&&e.getMessage().startsWith("HTTP 4"))throw e;last=e;}
            finally{if(c!=null)c.disconnect();}
            if(attempt<3){showStatus("Сервер просыпается… повторная попытка "+(attempt+1)+" из 3");try{Thread.sleep(attempt*4000L);}catch(InterruptedException e){Thread.currentThread().interrupt();throw e;}}
        }
        throw new Exception("Сервер временно недоступен после 3 попыток",last);
    }
    private void loadCategories(){
        selectedCategory=null;heading.setText("Категории приложений");content.removeAllViews();showStatus("Загрузка каталога…");
        new Thread(()->{try{
            JSONArray items=new JSONArray(get("api/categories"));
            runOnUiThread(()->{content.removeAllViews();status.setText(items.length()==0?"Категорий пока нет":"");
                for(int n=0;n<items.length();n++){JSONObject o=items.optJSONObject(n);if(o==null)continue;String id=o.optString("id"),name=o.optString("name","Категория");Button b=button(name+"   ›");b.setOnClickListener(v->loadApps(id,name));content.addView(b);}
            });
        }catch(Exception e){runOnUiThread(()->{status.setText("Сервер пока не отвечает. Render может запускаться после простоя.");content.removeAllViews();Button retry=button("Повторить подключение");retry.setOnClickListener(v->loadCategories());content.addView(retry);});}}).start();
    }
    private void loadApps(String id,String categoryName){
        selectedCategory=id;heading.setText(categoryName);selectedApps.clear();content.removeAllViews();
        Button back=button("‹  Все категории");back.setOnClickListener(v->loadCategories());content.addView(back);
        LinearLayout actions=new LinearLayout(this);actions.setOrientation(1);
        Button installSelected=button("УСТАНОВИТЬ ВЫБРАННЫЕ (0)");installSelected.setBackgroundTintList(android.content.res.ColorStateList.valueOf(orange));installSelected.setTextColor(0xFF222222);installSelected.setEnabled(false);
        installSelected.setOnClickListener(v->beginBatchInstall());actions.addView(installSelected);
        Button selectAll=button("Выбрать все");actions.addView(selectAll);content.addView(actions);
        showStatus("Загрузка приложений…");
        new Thread(()->{try{
            JSONArray items=new JSONArray(get("api/apps?categoryId="+Uri.encode(id)));
            runOnUiThread(()->{
                while(content.getChildCount()>2)content.removeViewAt(2);
                status.setText(items.length()==0?"Приложений пока нет":"Отметьте приложения для пакетной установки");
                selectAll.setOnClickListener(v->{boolean all=selectedApps.size()!=items.length();selectedApps.clear();for(int x=0;x<items.length();x++){JSONObject o=items.optJSONObject(x);if(o!=null&&all){AppItem a=toItem(o);selectedApps.put(a.id,a);}}renderAppRows(items,installSelected,selectAll);});
                renderAppRows(items,installSelected,selectAll);
            });
        }catch(Exception e){runOnUiThread(()->{status.setText("Не удалось загрузить приложения. Сервер может ещё запускаться.");Button retry=button("Повторить загрузку");retry.setOnClickListener(v->loadApps(id,categoryName));content.addView(retry);});}}).start();
    }
    private AppItem toItem(JSONObject o){return new AppItem(o.optString("id"),o.optString("name","Приложение"),o.optString("version",""),o.optString("description",""));}
    private void renderAppRows(JSONArray items,Button installSelected,Button selectAll){
        while(content.getChildCount()>2)content.removeViewAt(2);
        installSelected.setText("УСТАНОВИТЬ ВЫБРАННЫЕ ("+selectedApps.size()+")");installSelected.setEnabled(!selectedApps.isEmpty());
        selectAll.setText(selectedApps.size()==items.length()?"Снять выделение":"Выбрать все");
        for(int n=0;n<items.length();n++){
            JSONObject o=items.optJSONObject(n);if(o==null)continue;AppItem app=toItem(o);
            LinearLayout row=new LinearLayout(this);row.setOrientation(0);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(8,8,8,8);
            CheckBox check=new CheckBox(this);check.setButtonTintList(android.content.res.ColorStateList.valueOf(orange));check.setTextColor(-1);check.setText(app.name+(app.version.isEmpty()?"":"  ·  "+app.version));check.setTextSize(17);check.setMinHeight(64);check.setFocusable(true);check.setChecked(selectedApps.containsKey(app.id));
            check.setOnCheckedChangeListener((b,checked)->{if(checked)selectedApps.put(app.id,app);else selectedApps.remove(app.id);installSelected.setText("УСТАНОВИТЬ ВЫБРАННЫЕ ("+selectedApps.size()+")");installSelected.setEnabled(!selectedApps.isEmpty());selectAll.setText(selectedApps.size()==items.length()?"Снять выделение":"Выбрать все");});
            row.addView(check,new LinearLayout.LayoutParams(0,-2,1));
            Button details=button("Подробнее");details.setTextSize(14);details.setOnClickListener(v->showApp(app));row.addView(details,new LinearLayout.LayoutParams(-2,-2));
            content.addView(row);
            View line=new View(this);line.setBackgroundColor(0xFF444444);content.addView(line,new LinearLayout.LayoutParams(-1,1));
        }
    }
    private void showApp(AppItem app){
        content.removeAllViews();Button back=button("‹  Назад");back.setOnClickListener(v->loadApps(selectedCategory,heading.getText().toString()));content.addView(back);
        TextView info=new TextView(this);info.setText(app.name+(app.version.isEmpty()?"":"\nВерсия: "+app.version)+(app.description.isEmpty()?"":"\n\n"+app.description));info.setTextColor(-1);info.setTextSize(19);info.setPadding(12,20,12,24);content.addView(info);
        Button install=button("УСТАНОВИТЬ");install.setBackgroundTintList(android.content.res.ColorStateList.valueOf(orange));install.setTextColor(0xFF222222);install.setOnClickListener(v->{installQueue.clear();installQueue.add(app);queueIndex=0;downloadCurrent();});content.addView(install);
    }
    private void beginBatchInstall(){
        if(selectedApps.isEmpty())return;
        installQueue.clear();installQueue.addAll(selectedApps.values());queueIndex=0;
        new android.app.AlertDialog.Builder(this).setTitle("Пакетная установка")
            .setMessage("Будет загружено и предложено установить приложений: "+installQueue.size()+". Android запросит подтверждение для каждого APK.")
            .setNegativeButton("Отмена",null).setPositiveButton("Продолжить",(d,w)->downloadCurrent()).show();
    }
    private String currentName(){return queueIndex<installQueue.size()?installQueue.get(queueIndex).name:"";}
    private void downloadCurrent(){
        if(queueIndex>=installQueue.size()){showStatus("Очередь завершена");Toast.makeText(this,"Пакетная установка завершена",Toast.LENGTH_LONG).show();return;}
        AppItem app=installQueue.get(queueIndex);showStatus("Загрузка "+(queueIndex+1)+" из "+installQueue.size()+": "+app.name);
        try{
            DownloadManager.Request r=new DownloadManager.Request(Uri.parse(api("api/apps/"+Uri.encode(app.id)+"/download")));
            r.setTitle("HELP — "+app.name);r.setDescription("Загрузка APK ("+(queueIndex+1)+"/"+installQueue.size()+")");
            r.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            r.setDestinationInExternalFilesDir(this,Environment.DIRECTORY_DOWNLOADS,"help-"+app.id+".apk");
            downloadId=((DownloadManager)getSystemService(DOWNLOAD_SERVICE)).enqueue(r);
        }catch(Exception e){showStatus("Не удалось начать загрузку: "+app.name);nextInQueue();}
    }
    private void openInstaller(Uri apk){
        pendingApk=apk;
        if(Build.VERSION.SDK_INT>=26&&!getPackageManager().canRequestPackageInstalls()){
            waitingInstallPermission=true;
            Toast.makeText(this,"Разрешите HELP устанавливать APK, затем вернитесь в приложение",Toast.LENGTH_LONG).show();
            startActivityForResult(new Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+getPackageName())),PERMISSION_REQUEST);
            return;
        }
        launchInstaller(apk);
    }
    private void launchInstaller(Uri apk){
        pendingApk=null;showStatus("Подтвердите установку: "+currentName());
        Intent i=new Intent(Intent.ACTION_VIEW);i.setDataAndType(apk,"application/vnd.android.package-archive");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try{startActivityForResult(i,INSTALL_REQUEST);}catch(Exception e){showStatus("Не удалось открыть установщик Android");nextInQueue();}
    }
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode==INSTALL_REQUEST){nextInQueue();}
        else if(requestCode==PERMISSION_REQUEST){
            waitingInstallPermission=false;
            if(Build.VERSION.SDK_INT<26||getPackageManager().canRequestPackageInstalls()){
                if(pendingApk!=null){Uri u=pendingApk;pendingApk=null;launchInstaller(u);}
            }else{pendingApk=null;showStatus("Разрешение не выдано. Очередь остановлена.");}
        }
    }
    private void nextInQueue(){
        queueIndex++;
        if(queueIndex<installQueue.size())downloadCurrent();
        else{showStatus("Очередь завершена");Toast.makeText(this,"Обработка выбранных APK завершена",Toast.LENGTH_LONG).show();}
    }
}
