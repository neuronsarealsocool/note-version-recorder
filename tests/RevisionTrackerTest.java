package io.github.neuronsarealsocool.noterecorder;
import java.time.*;
public final class RevisionTrackerTest {
    static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    public static void main(String[] args){RevisionTracker t=new RevisionTracker("device");ZoneId zone=ZoneId.of("Australia/Sydney");long monday=Instant.parse("2026-11-23T12:59:59Z").toEpochMilli();
        RevisionTracker.Event first=t.observe("Notes/a.md","First",monday,5,monday,zone);check(first.kind.equals("baseline"),"first observation is baseline");t.commit(first);
        check(t.observe("Notes/a.md","First",monday,5,monday+1,zone)==null,"unchanged saves do not duplicate");
        RevisionTracker.Event edit=t.observe("Notes/a.md","Second",monday+2000,6,monday+3000,zone);check(edit.previousContent.equals("First"),"previous full version retained");check(edit.day.equals("2026-11-24"),"phone timezone and midnight retained");
        check(t.snapshots.get("Notes/a.md").text.equals("First"),"failed writes cannot advance baseline");t.commit(edit);
        RevisionTracker.Event next=t.observe("Notes/a.md","Third",monday+4000,5,monday+5000,zone);check(next.previousContent.equals("Second"),"intermediate version chain retained");check(!next.id.equals(edit.id),"immutable event identities");
        check(t.needsRead("Notes/a.md",edit.modifiedAt,6,true),"periodic verification catches same-stat changes");
        check(!RevisionTracker.validPath("../escape.md")&&!RevisionTracker.validPath("_mobile-edit-history/a.md")&&!RevisionTracker.validPath(".obsidian/config.md"),"internal paths excluded");
        check(RevisionTracker.noteName("UPPER.MD")&&!RevisionTracker.noteName("photo.jpg"),"text file scope");System.out.println("RevisionTracker: 8 regression checks passed");
    }
}
