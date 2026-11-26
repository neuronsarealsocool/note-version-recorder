package io.github.neuronsarealsocool.noterecorder;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/** Pure recording decisions, shared by the service and JVM regression tests. */
public final class RevisionTracker {
    public static final String JOURNAL = "_mobile-edit-history";
    public static final class Snapshot {
        public final String text, id; public final long modified, size;
        public Snapshot(String text, String id, long modified, long size) {this.text=text;this.id=id;this.modified=modified;this.size=size;}
    }
    public static final class Event {
        public final String id, deviceId, path, kind, content, previousContent, capturedAt, day, basis;
        public final long modifiedAt, size; public final int offsetMinutes;
        public boolean requiresPublication(){return "edit".equals(kind);}
        Event(String device, String path, String text, Snapshot previous, long modified, long size, long now, ZoneId zone) {
            this.id=UUID.randomUUID().toString();this.deviceId=device;this.path=path;this.content=text;this.previousContent=previous==null?null:previous.text;
            this.kind=previous==null?"baseline":"edit";this.capturedAt=Instant.ofEpochMilli(now).toString();this.size=size;
            boolean valid=modified>0 && modified<=now+300_000L;
            this.modifiedAt=valid?modified:0;this.basis=valid?"file-mtime":"observed";
            ZonedDateTime source=Instant.ofEpochMilli(valid?modified:now).atZone(zone);
            this.day=source.toLocalDate().toString();this.offsetMinutes=source.getOffset().getTotalSeconds()/60;
        }
    }
    public final Map<String,Snapshot> snapshots=new HashMap<>();
    private final String deviceId;
    public RevisionTracker(String deviceId) {this.deviceId=deviceId;}
    public boolean needsRead(String path,long modified,long size,boolean verify) {
        Snapshot old=snapshots.get(path);return verify || old==null || modified<=0 || old.modified!=modified || old.size!=size;
    }
    public Event observe(String path,String text,long modified,long size,long now,ZoneId zone) {
        if (!validPath(path)) throw new IllegalArgumentException("Unsafe note path");
        Snapshot old=snapshots.get(path);
        if(old!=null && old.text.equals(text)) return null;
        return new Event(deviceId,path,text,old,modified,size,now,zone);
    }
    public void commit(Event event) {snapshots.put(event.path,new Snapshot(event.content,event.id,event.modifiedAt,event.size));}
    public static boolean validPath(String path) {
        if(path.isEmpty()||path.length()>2048||path.contains("\\")||path.contains(":"))return false;
        for(String part:path.split("/",-1))if(part.isEmpty()||part.startsWith(".")||part.equals(JOURNAL)||part.equals("node_modules")||part.equals("__pycache__"))return false;
        for(char c:path.toCharArray())if(c<32)return false;
        return true;
    }
    public static boolean noteName(String name) {String n=name.toLowerCase(java.util.Locale.ROOT);return n.endsWith(".md")||n.endsWith(".txt")||n.endsWith(".csv")||n.endsWith(".htm")||n.endsWith(".html");}
}
