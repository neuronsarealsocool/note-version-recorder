package io.github.neuronsarealsocool.noterecorder;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.*;
import android.provider.DocumentsContract;
import android.util.AtomicFile;
import android.util.Log;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

public class RecorderService extends Service {
    public static volatile boolean running;
    static final String CHANNEL="recording"; static final int NOTICE=17;
    private final ScheduledExecutorService worker=Executors.newSingleThreadScheduledExecutor();
    private final Map<String,RevisionTracker> trackers=new HashMap<>();private volatile boolean stopped;
    private final java.util.concurrent.atomic.AtomicBoolean loopStarted=new java.util.concurrent.atomic.AtomicBoolean();
    private long lastVerify=0,lastProgress=0;private int checked,recorded,pending,skipped;private String lastError="",lastWarning="",currentPhase="Idle";
    public static SharedPreferences prefs(Context c){return c.getSharedPreferences("recorder",Context.MODE_PRIVATE);}
    public static JSONArray roots(Context c){try{return new JSONArray(prefs(c).getString("roots","[]"));}catch(Exception e){return new JSONArray();}}
    private Notification notification(String status){
        Intent stop=new Intent(this,RecorderService.class).setAction("STOP");
        PendingIntent stopIntent=PendingIntent.getService(this,1,stop,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent open=PendingIntent.getActivity(this,2,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(this,CHANNEL).setSmallIcon(android.R.drawable.ic_menu_save).setContentTitle("Note Version Recorder").setContentText(status).setContentIntent(open).setOngoing(true).addAction(new Notification.Action.Builder(android.R.drawable.ic_media_pause,"Stop",stopIntent).build()).build();
    }
    @Override public void onCreate(){super.onCreate();running=true;getSystemService(NotificationManager.class).createNotificationChannel(new NotificationChannel(CHANNEL,"Local note recording",NotificationManager.IMPORTANCE_LOW));
        if(Build.VERSION.SDK_INT>=34)startForeground(NOTICE,notification("Starting folder checks…"),ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);else startForeground(NOTICE,notification("Starting folder checks…"));
        if(!prefs(this).contains("deviceId"))prefs(this).edit().putString("deviceId",UUID.randomUUID().toString()).commit();
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId){if(intent!=null&&"STOP".equals(intent.getAction())){prefs(this).edit().putBoolean("enabled",false).putString("status","Stopped by you. Saved history is retained.").apply();stopSelf();return START_NOT_STICKY;}
        if(!prefs(this).getBoolean("enabled",false)){stopSelf();return START_NOT_STICKY;}
        if(loopStarted.compareAndSet(false,true)){status("Starting recording · "+roots(this).length()+" vaults");worker.execute(()->scanAndSchedule());}
        return START_STICKY;}
    @Override public IBinder onBind(Intent intent){return null;}
    @Override public void onDestroy(){stopped=true;running=false;worker.shutdownNow();stopForeground(STOP_FOREGROUND_REMOVE);super.onDestroy();}
    private void status(String value){prefs(this).edit().putString("status",value).putLong("lastHeartbeat",System.currentTimeMillis()).apply();getSystemService(NotificationManager.class).notify(NOTICE,notification(value));}
    private void progress(String phase){currentPhase=phase;if(System.currentTimeMillis()-lastProgress<1000)return;lastProgress=System.currentTimeMillis();status(phase+" · "+checked+" notes checked · "+recorded+" changed versions saved");}
    @Override protected void dump(FileDescriptor fd,PrintWriter writer,String[] args){writer.println("phase="+currentPhase);writer.println("enabled="+prefs(this).getBoolean("enabled",false));writer.println("roots="+roots(this).length());writer.println("checked="+checked);writer.println("recorded="+recorded);writer.println("pending="+pending);writer.println("skipped="+skipped);writer.println("lastHeartbeat="+prefs(this).getLong("lastHeartbeat",0));writer.println("lastError="+lastError);writer.println("lastWarning="+lastWarning);}
    private void scanAndSchedule(){if(stopped||!prefs(this).getBoolean("enabled",false))return;
        try {
        checked=0;pending=0;skipped=0;lastError="";lastWarning="";boolean verify=lastVerify!=0&&System.currentTimeMillis()-lastVerify>60_000;if(lastVerify==0||verify)lastVerify=System.currentTimeMillis();
        JSONArray selected=roots(this);
        for(int i=0;i<selected.length()&&!stopped;i++)try{progress("Checking vault "+(i+1)+"/"+selected.length());scanRoot(Uri.parse(selected.getString(i)),verify);}catch(Exception e){lastError=e.getClass().getSimpleName()+": "+e.getMessage();Log.e("NoteRecorder","Vault scan failed",e);}
        if(!stopped){String value=lastError.isEmpty()?"Recording · "+checked+" notes checked · "+recorded+" versions this session · "+pending+" pending"+(skipped>0?" · "+skipped+" unreadable/non-UTF-8 files skipped":""):"Recording needs attention · "+lastError;
            currentPhase="Waiting for next check";status(value);}
        }catch(Throwable failure){lastError=failure.getClass().getSimpleName()+": "+failure.getMessage();Log.e("NoteRecorder","Recorder loop failed",failure);if(!stopped)status("Recording needs attention · "+lastError);if(failure instanceof Error){prefs(this).edit().putBoolean("enabled",false).apply();stopSelf();}}
        finally {if(!stopped&&prefs(this).getBoolean("enabled",false))try{worker.schedule(()->scanAndSchedule(),prefs(this).getInt("interval",5),TimeUnit.SECONDS);}catch(RejectedExecutionException ignored){}}
    }
    static String digest(String text)throws Exception{byte[] bytes=MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));StringBuilder s=new StringBuilder();for(byte b:bytes)s.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return s.toString();}
    private File privateDir(String type,String root)throws Exception{File dir=new File(getFilesDir(),type+"/"+digest(root));if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("Could not create private recording storage");return dir;}
    private void atomic(File file,String text)throws IOException{AtomicFile target=new AtomicFile(file);FileOutputStream stream=null;try{stream=target.startWrite();stream.write(text.getBytes(StandardCharsets.UTF_8));target.finishWrite(stream);}catch(IOException e){if(stream!=null)target.failWrite(stream);throw e;}}
    private String readPrivate(File file)throws Exception{try(InputStream in=new AtomicFile(file).openRead()){return readText(in,12*1024*1024);}}
    private String readText(InputStream in,int max)throws Exception{ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int n,total=0;while((n=in.read(buffer))!=-1){total+=n;if(total>max)throw new IOException("Note exceeds recording size limit (4 MiB)");out.write(buffer,0,n);}return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(out.toByteArray())).toString();}
    private RevisionTracker loadTracker(String root)throws Exception{RevisionTracker tracker=new RevisionTracker(prefs(this).getString("deviceId",""));File dir=privateDir("snapshots",root);File[] files=dir.listFiles((d,name)->name.endsWith(".json"));if(files==null)throw new IOException("Could not list recording snapshots");
        for(File file:files){if(stopped)break;progress("Loading saved baselines");JSONObject s=new JSONObject(readPrivate(file));tracker.snapshots.put(s.getString("path"),new RevisionTracker.Snapshot(s.getString("text"),s.getString("id"),s.getLong("modified"),s.getLong("size")));}
        // Recover a crash between committing an outbox event and its local snapshot.
        File[] pendingFiles=privateDir("outbox",root).listFiles((d,name)->name.endsWith(".json"));
        if(pendingFiles==null)throw new IOException("Cannot read pending versions");
        List<JSONObject> events=new ArrayList<>();for(File file:pendingFiles){if(stopped)break;progress("Recovering pending versions");events.add(new JSONObject(readPrivate(file)));}
        events.sort((a,b)->a.optString("capturedAt").compareTo(b.optString("capturedAt")));
        for(JSONObject event:events){String path=event.getString("path");RevisionTracker.Snapshot old=tracker.snapshots.get(path);
            if(old==null||old.text.equals(event.optString("previousContent",null))) {
                String text=event.getString("content"),id=event.getString("id");long modified=event.getLong("modifiedAt"),size=event.optLong("sourceSize",text.getBytes(StandardCharsets.UTF_8).length);
                tracker.snapshots.put(path,new RevisionTracker.Snapshot(text,id,modified,size));
                atomic(new File(dir,digest(path)+".json"),new JSONObject().put("path",path).put("text",text).put("id",id).put("modified",modified).put("size",size).toString());
            }
        }
        return tracker;}
    private JSONObject eventJson(RevisionTracker.Event e)throws Exception{JSONObject j=new JSONObject();j.put("schema",1).put("id",e.id).put("deviceId",e.deviceId).put("path",e.path).put("kind",e.kind).put("content",e.content).put("capturedAt",e.capturedAt).put("modifiedAt",e.modifiedAt).put("sourceSize",e.size).put("day",e.day).put("offsetMinutes",e.offsetMinutes).put("basis",e.basis);if(e.previousContent!=null)j.put("previousContent",e.previousContent);return j;}
    private void scanRoot(Uri root,boolean verify)throws Exception{String key=root.toString();RevisionTracker tracker=trackers.get(key);if(tracker==null){tracker=loadTracker(key);trackers.put(key,tracker);}Uri doc=DocumentsContract.buildDocumentUriUsingTree(root,DocumentsContract.getTreeDocumentId(root));
        File outbox=privateDir("outbox",key),snapshots=privateDir("snapshots",key);
        walk(root,doc,"",0,tracker,outbox,snapshots,verify);
        if(!stopped&&lastError.isEmpty())prefs(this).edit().putBoolean("initialized-"+digest(key),true).commit();
        if(!stopped)flush(root,doc,outbox);
    }
    private static final String[] COLUMNS={DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_DISPLAY_NAME,DocumentsContract.Document.COLUMN_MIME_TYPE,DocumentsContract.Document.COLUMN_LAST_MODIFIED,DocumentsContract.Document.COLUMN_SIZE};
    private void walk(Uri tree,Uri parent,String prefix,int depth,RevisionTracker tracker,File outbox,File snapshots,boolean verify)throws Exception{
        progress("Scanning notes");
        if(depth>64)throw new IOException("Folder nesting exceeds 64 levels");Uri children=DocumentsContract.buildChildDocumentsUriUsingTree(tree,DocumentsContract.getDocumentId(parent));
        try(Cursor cursor=getContentResolver().query(children,COLUMNS,null,null,null)){if(cursor==null)throw new IOException("Folder provider returned no listing");
            while(cursor.moveToNext()&&!stopped){String id=cursor.getString(0),name=cursor.getString(1),mime=cursor.getString(2);if(name==null||name.startsWith(".")||name.equals(RevisionTracker.JOURNAL))continue;String path=prefix+name;
                if(!RevisionTracker.validPath(path))continue;Uri child=DocumentsContract.buildDocumentUriUsingTree(tree,id);
                if(DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)){try{walk(tree,child,path+"/",depth+1,tracker,outbox,snapshots,verify);}catch(Exception e){lastError=path+": "+e.getMessage();}continue;}
                if(!RevisionTracker.noteName(name))continue;checked++;progress("Scanning notes");long modified=cursor.isNull(3)?0:cursor.getLong(3),size=cursor.isNull(4)?-1:cursor.getLong(4);if(!tracker.needsRead(path,modified,size,verify))continue;
                try{String text;try(InputStream in=getContentResolver().openInputStream(child)){if(in==null)throw new IOException("Cannot read note");text=readText(in,4*1024*1024);}
                    // Recheck metadata after reading; avoid snapshotting an in-progress write.
                    try(Cursor after=getContentResolver().query(child,new String[]{COLUMNS[3],COLUMNS[4]},null,null,null)){if(after==null||!after.moveToFirst())continue;if((!after.isNull(0)&&after.getLong(0)!=modified)||(!after.isNull(1)&&after.getLong(1)!=size))continue;}
                    if(!tracker.snapshots.containsKey(path)&&prefs(this).getBoolean("initialized-"+digest(tree.toString()),false))tracker.snapshots.put(path,new RevisionTracker.Snapshot("","",0,0));
                    RevisionTracker.Event event=tracker.observe(path,text,modified,size,System.currentTimeMillis(),ZoneId.systemDefault());
                    if(event!=null){String json=eventJson(event).toString();if(json.getBytes(StandardCharsets.UTF_8).length>12*1024*1024)throw new IOException("Encoded record exceeds 12 MiB");
                        if(event.requiresPublication()){atomic(new File(outbox,event.id+".json"),json);recorded++;} // Baselines stay private; edits include their full previous text.
                        tracker.commit(event);
                    }
                    RevisionTracker.Snapshot state=tracker.snapshots.get(path);if(state!=null){JSONObject snapshot=new JSONObject().put("path",path).put("text",text).put("id",state.id).put("modified",modified).put("size",size);atomic(new File(snapshots,digest(path)+".json"),snapshot.toString());tracker.snapshots.put(path,new RevisionTracker.Snapshot(text,state.id,modified,size));}
                }catch(Exception e){skipped++;lastWarning=path+": "+e.getClass().getSimpleName()+": "+e.getMessage();}
            }
        }
    }
    private Uri findChild(Uri tree,Uri parent,String name)throws Exception{Uri children=DocumentsContract.buildChildDocumentsUriUsingTree(tree,DocumentsContract.getDocumentId(parent));try(Cursor c=getContentResolver().query(children,new String[]{COLUMNS[0],COLUMNS[1]},null,null,null)){if(c==null)throw new IOException("Cannot list history directory");while(c.moveToNext())if(name.equals(c.getString(1)))return DocumentsContract.buildDocumentUriUsingTree(tree,c.getString(0));}return null;}
    private Uri ensureDir(Uri tree,Uri parent,String name)throws Exception{Uri child=findChild(tree,parent,name);if(child!=null)return child;child=DocumentsContract.createDocument(getContentResolver(),parent,DocumentsContract.Document.MIME_TYPE_DIR,name);if(child==null)throw new IOException("Cannot create history directory");return child;}
    private Map<String,Uri> childIndex(Uri tree,Uri parent)throws Exception{Map<String,Uri> index=new HashMap<>();Uri children=DocumentsContract.buildChildDocumentsUriUsingTree(tree,DocumentsContract.getDocumentId(parent));try(Cursor c=getContentResolver().query(children,new String[]{COLUMNS[0],COLUMNS[1]},null,null,null)){if(c==null)throw new IOException("Cannot list journal files");while(c.moveToNext())index.put(c.getString(1),DocumentsContract.buildDocumentUriUsingTree(tree,c.getString(0)));}return index;}
    private void flush(Uri tree,Uri root,File outbox)throws Exception{File[] files=outbox.listFiles((d,name)->name.endsWith(".json"));if(files==null)throw new IOException("Could not list pending records");pending+=files.length;if(files.length==0)return;
        // Old builds queued a full baseline export for every note. Keep private snapshots,
        // but retire redundant baseline queue entries without deleting published history.
        List<File> changes=new ArrayList<>();for(File file:files){if(stopped)return;progress("Checking pending versions");JSONObject event=new JSONObject(readPrivate(file));if("baseline".equals(event.optString("kind"))){if(!file.delete())throw new IOException("Cannot acknowledge saved baseline");pending--;}else changes.add(file);}
        if(changes.isEmpty())return;
        progress("Publishing changed versions");
        Uri journal=ensureDir(tree,root,RevisionTracker.JOURNAL),version=ensureDir(tree,journal,"v1"),device=ensureDir(tree,version,prefs(this).getString("deviceId",""));
        Map<String,Uri> index=childIndex(tree,device);
        int published=0;for(File file:changes){if(stopped||published>=20)return;progress("Publishing changed versions");String payload=readPrivate(file);Uri destination=index.get(file.getName());
            if(destination!=null){String existing;try(InputStream in=getContentResolver().openInputStream(destination)){existing=readText(in,12*1024*1024);}if(!payload.equals(existing)){
                    // A previously interrupted provider write may be incomplete; never replace a valid conflicting event.
                    boolean valid=false;try{new JSONObject(existing).getString("id");valid=true;}catch(JSONException ignored){}
                    if(valid)throw new IOException("Conflicting immutable history record; existing file preserved");writeDocument(destination,payload);
                }
            }else{Uri part=index.get(file.getName()+".part");if(part==null)part=DocumentsContract.createDocument(getContentResolver(),device,"application/octet-stream",file.getName()+".part");if(part==null)throw new IOException("Cannot create mobile history file");writeDocument(part,payload);
                try{destination=DocumentsContract.renameDocument(getContentResolver(),part,file.getName());}catch(Exception ignored){}if(destination==null){destination=DocumentsContract.createDocument(getContentResolver(),device,"application/json",file.getName());if(destination==null)throw new IOException("Cannot publish mobile history");writeDocument(destination,payload);DocumentsContract.deleteDocument(getContentResolver(),part);}}
            try(InputStream in=getContentResolver().openInputStream(destination)){if(!payload.equals(readText(in,12*1024*1024)))throw new IOException("History write verification failed");}
            try(Cursor name=getContentResolver().query(destination,new String[]{COLUMNS[1]},null,null,null)){if(name==null||!name.moveToFirst()||!file.getName().equals(name.getString(0)))throw new IOException("Folder provider changed the journal filename; pending version retained");}
            index.put(file.getName(),destination);
            if(!file.delete())throw new IOException("Could not acknowledge pending record");pending--;published++;
        }
    }
    private void writeDocument(Uri uri,String payload)throws Exception{try(OutputStream out=getContentResolver().openOutputStream(uri,"wt")){if(out==null)throw new IOException("History folder is not writable");out.write(payload.getBytes(StandardCharsets.UTF_8));out.flush();}}
}
