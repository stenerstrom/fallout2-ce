package com.alexbatalov.fallout2ce;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

// Caller holds the global GameSession lock. No game may start during a transaction.
final class ManagedGameInstaller {
    static final String RECORD=".installed-content";
    private static final String WORK=".content-update";
    static final class Entry {
        final BundledGameExtractor.Entry file;
        final boolean seed;
        final List<String> previous;
        Entry(BundledGameExtractor.Entry file,boolean seed,List<String> previous) {
            this.file=file; this.seed=seed; this.previous=previous;
        }
    }
    static boolean current(File root,String id) throws IOException {
        return !new File(root,BundledGameExtractor.IN_PROGRESS).exists()
                && !new File(new File(root,WORK),"journal").exists()
                && id.equals(read(new File(root,RECORD)).trim());
    }
    static void install(File root,String id,List<Entry> entries,BundledGameExtractor.Source source,
            BundledGameExtractor.Progress progress) throws IOException {
        if(!id.matches("[0-9a-f]{64}")||entries.isEmpty())throw new IOException("Invalid content manifest.");
        mkdir(root); root=root.getCanonicalFile();
        File work=new File(root,WORK);
        if(!work.getCanonicalPath().equals(work.getAbsolutePath()))throw new IOException("Invalid update folder.");
        mkdir(work);
        File journal=new File(work,"journal");
        // A complete staged release can always finish before adopting a newer release.
        if(journal.isFile())commit(root,work);
        if(current(root,id))return;
        Set<String> paths=new HashSet<>(); long total=0;
        for(Entry entry:entries){
            BundledGameExtractor.Entry f=entry.file;
            target(root,f.path);
            if(!paths.add(f.path)||f.size<0||!f.sha256.matches("[0-9a-f]{64}"))throw new IOException("Invalid content manifest.");
            total=Math.addExact(total,f.size);
        }
        atomic(new File(root,BundledGameExtractor.IN_PROGRESS),id);
        List<Entry> changed=new ArrayList<>();
        long needed=0,done=0;
        for(Entry entry:entries){
            File existing=target(root,entry.file.path);
            if(existing.exists()){
                if(!existing.isFile())throw new IOException("A game file is a folder: "+entry.file.path);
                if(entry.seed){done+=entry.file.size;continue;}
                String old=hash(existing);
                if(old.equals(entry.file.sha256)){done+=entry.file.size;continue;}
                if(!entry.previous.contains(old))
                    throw new IOException("This game file was changed outside the app: "+entry.file.path
                            +". Keep your changes or restore the original file before updating.");
            }
            changed.add(entry); needed=Math.addExact(needed,entry.file.size);
        }
        if(root.getUsableSpace()<needed+64L*1024*1024)throw new IOException("More free space is needed to prepare this game.");
        progress.update(done,total);
        StringBuilder log=new StringBuilder(id).append('\n');
        for(Entry entry:changed){
            File stage=target(new File(work,"files"),entry.file.path);
            mkdir(stage.getParentFile());
            if(!stage.isFile()||stage.length()!=entry.file.size||!hash(stage).equals(entry.file.sha256)){
                try(InputStream input=source.open(entry.file.path);FileOutputStream output=new FileOutputStream(stage)){
                    byte[] buffer=new byte[256*1024]; int n; long count=0;
                    while((n=input.read(buffer))!=-1){
                        count+=n;
                        if(count>entry.file.size)throw new IOException("Incorrect size: "+entry.file.path);
                        output.write(buffer,0,n); progress.update(done+count,total);
                    }
                    output.getFD().sync();
                    if(count!=entry.file.size)throw new IOException("Incomplete game file: "+entry.file.path);
                }
                if(!hash(stage).equals(entry.file.sha256))throw new IOException("Damaged game file: "+entry.file.path);
            }
            done+=entry.file.size; progress.update(done,total);
            log.append(entry.file.path).append('\t').append(entry.file.sha256).append('\n');
        }
        // Journal is published only after every replacement is durable and verified.
        atomic(journal,log.toString());
        commit(root,work);
        progress.update(total,total);
    }
    private static void commit(File root,File work) throws IOException {
        String[] lines=read(new File(work,"journal")).split("\\n");
        if(lines.length==0||!lines[0].matches("[0-9a-f]{64}"))throw new IOException("Invalid update journal.");
        for(int i=1;i<lines.length;i++){
            String[] fields=lines[i].split("\\t",-1);
            if(fields.length!=2||!fields[1].matches("[0-9a-f]{64}"))throw new IOException("Invalid update journal.");
            File dest=target(root,fields[0]),stage=target(new File(work,"files"),fields[0]),backup=target(new File(work,"backup"),fields[0]);
            if(dest.isFile()&&hash(dest).equals(fields[1]))continue;
            if(!stage.isFile()||!hash(stage).equals(fields[1]))throw new IOException("An update file is missing or damaged: "+fields[0]);
            mkdir(dest.getParentFile()); mkdir(backup.getParentFile());
            if(dest.exists()){
                if(backup.exists())throw new IOException("An update conflict needs attention: "+fields[0]);
                if(!dest.renameTo(backup))throw new IOException("Could not back up "+fields[0]);
            }
            if(!stage.renameTo(dest))throw new IOException("Could not activate "+fields[0]+". Reopen the app to retry.");
        }
        atomic(new File(root,RECORD),lines[0]+"\n");
        // Remove the journal before backups; interrupted cleanup cannot replay a completed release.
        File journal=new File(work,"journal");
        if(!journal.delete())throw new IOException("Could not complete the update.");
        if(!new File(root,BundledGameExtractor.IN_PROGRESS).delete()
                &&new File(root,BundledGameExtractor.IN_PROGRESS).exists())throw new IOException("Could not complete game preparation.");
        deleteTree(work);
    }
    static File target(File root,String path) throws IOException {
        if(path.indexOf('\t')>=0||path.indexOf('\n')>=0||path.indexOf('\r')>=0)throw new IOException("Invalid game path.");
        return BundledGameExtractor.destination(root.getCanonicalFile(),path);
    }
    private static void mkdir(File dir)throws IOException{
        if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("Could not create game folder.");
    }
    static String hash(File file)throws IOException{
        try{
            MessageDigest digest=MessageDigest.getInstance("SHA-256");
            try(InputStream input=new FileInputStream(file)){byte[] b=new byte[256*1024];int n;while((n=input.read(b))!=-1)digest.update(b,0,n);}
            StringBuilder s=new StringBuilder();for(byte b:digest.digest())s.append(String.format(Locale.ROOT,"%02x",b&255));return s.toString();
        }catch(java.security.NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}
    }
    private static String read(File file)throws IOException{
        if(!file.exists())return "";
        if(file.length()>8L*1024*1024)throw new IOException("Update record is too large.");
        try(InputStream in=new FileInputStream(file);ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)out.write(b,0,n);return new String(out.toByteArray(),StandardCharsets.UTF_8);
        }
    }
    private static void atomic(File file,String text)throws IOException{
        File temp=new File(file.getPath()+".tmp");mkdir(file.getParentFile());
        try(FileOutputStream out=new FileOutputStream(temp)){out.write(text.getBytes(StandardCharsets.UTF_8));out.getFD().sync();}
        if(!temp.renameTo(file))throw new IOException("Could not save update state.");
    }
    private static void deleteTree(File file){
        if(file.isDirectory()) {
            try { if(file.getCanonicalPath().equals(file.getAbsolutePath())){
                File[] children=file.listFiles();if(children!=null)for(File child:children)deleteTree(child);
            }}catch(IOException ignored){}
        }
        file.delete();
    }
}
