package io.github.neuronsarealsocool.noterecorder;
import android.content.*;
public class BootReceiver extends BroadcastReceiver {
    public void onReceive(Context context,Intent intent){if(!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction()))return;
        android.content.SharedPreferences p=RecorderService.prefs(context);
        if(p.getBoolean("enabled",false)&&p.getBoolean("resumeAfterBoot",true))try{context.startForegroundService(new Intent(context,RecorderService.class));}catch(Exception e){p.edit().putString("status","Android blocked restart. Open Note Version Recorder and tap Start.").apply();}}
}
