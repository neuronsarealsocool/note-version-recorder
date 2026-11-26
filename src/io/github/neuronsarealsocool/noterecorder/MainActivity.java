package io.github.neuronsarealsocool.noterecorder;

import android.app.*;
import android.os.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import org.json.*;

public class MainActivity extends Activity {
    private LinearLayout layout,folders;private TextView status;private Button startButton,stopButton;private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable refresh=new Runnable(){public void run(){refreshStatus();handler.postDelayed(this,1000);}};
    private void refreshStatus(){if(status==null)return;android.content.SharedPreferences p=RecorderService.prefs(this);boolean enabled=p.getBoolean("enabled",false);long heartbeat=p.getLong("lastHeartbeat",0);String text=p.getString("status","Stopped. Choose each shared vault folder, then start.");if(enabled&&heartbeat>0&&System.currentTimeMillis()-heartbeat>90_000)text="No recent scan progress. Android or the folder provider may have paused recording. Tap Stop, then Start.\n\n"+text;if(!status.getText().toString().equals(text))status.setText(text);startButton.setEnabled(!RecorderService.running);String label=RecorderService.running?"Recording started":"Start recording";if(!startButton.getText().toString().equals(label))startButton.setText(label);stopButton.setEnabled(enabled);}
    private void text(String s,int size){TextView v=new TextView(this);v.setText(s);v.setTextSize(size);v.setPadding(0,12,0,12);layout.addView(v);}
    private Button button(String title,View.OnClickListener click){Button b=new Button(this);b.setText(title);b.setOnClickListener(click);layout.addView(b);return b;}
    @Override public void onCreate(Bundle state){super.onCreate(state);getWindow().setStatusBarColor(0xff152b39);getWindow().setNavigationBarColor(0xff152b39);
        ScrollView scroll=new ScrollView(this);layout=new LinearLayout(this);layout.setOrientation(LinearLayout.VERTICAL);int p=(int)(20*getResources().getDisplayMetrics().density);layout.setPadding(p,p,p,p);scroll.addView(layout);setContentView(scroll);
        scroll.setOnApplyWindowInsetsListener((v,insets)->{layout.setPadding(p,p+insets.getSystemWindowInsetTop(),p,p+insets.getSystemWindowInsetBottom());return insets;});
        text("Note Version Recorder",26);text("Local saved-note history for Zettel Notes and other Android editors",16);
        status=new TextView(this);status.setTextSize(16);status.setPadding(0,12,0,12);layout.addView(status);
        startButton=button("Start recording",v->{if(RecorderService.roots(this).length()==0){toast("Choose a shared note folder first.");return;}if(Build.VERSION.SDK_INT>=33&&checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},11);return;}startRecorder();});
        stopButton=button("Stop recording",v->{RecorderService.prefs(this).edit().putBoolean("enabled",false).putString("status","Stopped by you. Saved history is retained.").apply();stopService(new Intent(this,RecorderService.class));refreshStatus();});
        text("Choose the actual shared vault folder used by your editor and Resilio. The recorder writes _mobile-edit-history inside it. Let Resilio sync that whole folder to the corresponding desktop vault.",15);
        folders=new LinearLayout(this);folders.setOrientation(LinearLayout.VERTICAL);layout.addView(folders);showFolders();
        button("Add shared note folder",v->{Intent pick=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);pick.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION|Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);startActivityForResult(pick,10);});
        text("Check interval",16);Spinner interval=new Spinner(this);interval.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"2 seconds · more battery use","5 seconds · default","15 seconds · less battery use"}));
        int seconds=RecorderService.prefs(this).getInt("interval",5);interval.setSelection(seconds==2?0:seconds==15?2:1);interval.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onNothingSelected(android.widget.AdapterView<?> a){}public void onItemSelected(android.widget.AdapterView<?> a,View v,int pos,long id){RecorderService.prefs(MainActivity.this).edit().putInt("interval",pos==0?2:pos==2?15:5).apply();}});layout.addView(interval);
        button("Battery settings",v->{try{startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));}catch(Exception e){toast("Open Android battery settings and allow background use for this app.");}});
        Switch reboot=new Switch(this);reboot.setText("Resume after phone reboot");reboot.setChecked(RecorderService.prefs(this).getBoolean("resumeAfterBoot",true));reboot.setOnCheckedChangeListener((b,on)->RecorderService.prefs(this).edit().putBoolean("resumeAfterBoot",on).apply());layout.addView(reboot);
        text("Records versions saved to disk and observed during checks, not keystrokes. Saves overwritten between checks or while Android stops the service cannot be recovered. First scan saves a baseline; existing notes do not count as new writing. No network permission: Resilio handles syncing. Keep only one recorder watching each folder.",14);
        if(RecorderService.prefs(this).getBoolean("enabled",false)&&RecorderService.roots(this).length()>0&&(Build.VERSION.SDK_INT<33||checkSelfPermission("android.permission.POST_NOTIFICATIONS")==PackageManager.PERMISSION_GRANTED))startRecorder();
    }
    private void startRecorder(){try{RecorderService.prefs(this).edit().putBoolean("enabled",true).putString("status","Starting recording… Your selected folders are being checked.").putLong("lastHeartbeat",System.currentTimeMillis()).apply();refreshStatus();startForegroundService(new Intent(this,RecorderService.class));}catch(Exception e){RecorderService.prefs(this).edit().putBoolean("enabled",false).putString("status","Could not start: "+e.getMessage()).apply();refreshStatus();toast("Could not start: "+e.getMessage());}}
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){super.onRequestPermissionsResult(request,permissions,results);if(request==11){if(results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED)startRecorder();else toast("Allow notifications to keep recording status and Stop visible.");}}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    private void showFolders(){folders.removeAllViews();JSONArray roots=RecorderService.roots(this);for(int i=0;i<roots.length();i++){final String uri=roots.optString(i);TextView t=new TextView(this);t.setText(Uri.decode(DocumentsContract.getTreeDocumentId(Uri.parse(uri))));t.setPadding(0,8,0,4);folders.addView(t);Button remove=new Button(this);remove.setText("Remove this folder");remove.setOnClickListener(v->{if(RecorderService.prefs(this).getBoolean("enabled",false)){toast("Stop recording before changing folders.");return;}JSONArray next=new JSONArray();for(int j=0;j<roots.length();j++)if(!roots.optString(j).equals(uri))next.put(roots.optString(j));RecorderService.prefs(this).edit().putString("roots",next.toString()).apply();showFolders();});folders.addView(remove);}}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request!=10||result!=RESULT_OK||data==null||data.getData()==null)return;
        if(RecorderService.prefs(this).getBoolean("enabled",false)){toast("Stop recording before changing folders.");return;}
        Uri uri=data.getData();String id=DocumentsContract.getTreeDocumentId(uri);JSONArray roots=RecorderService.roots(this);
        for(int i=0;i<roots.length();i++){Uri other=Uri.parse(roots.optString(i));String previous=DocumentsContract.getTreeDocumentId(other);if(uri.getAuthority().equals(other.getAuthority())&&(id.equals(previous)||id.startsWith(previous+"/")||previous.startsWith(id+"/"))){toast("This folder overlaps one already selected. Choose each vault root once.");return;}}
        try{int flags=data.getFlags()&(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);if((flags&Intent.FLAG_GRANT_WRITE_URI_PERMISSION)==0)throw new SecurityException("Folder is read-only");getContentResolver().takePersistableUriPermission(uri,flags);roots.put(uri.toString());RecorderService.prefs(this).edit().putString("roots",roots.toString()).apply();showFolders();}catch(Exception e){toast("Folder access failed: "+e.getMessage());}}
    @Override protected void onResume(){super.onResume();handler.post(refresh);}
    @Override protected void onPause(){handler.removeCallbacks(refresh);super.onPause();}
}
