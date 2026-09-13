package com.alexbatalov.fallout2ce;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
public class LibraryTests {
 static int count;
 interface Action{void run()throws Exception;}
 static void check(boolean b){count++;if(!b)throw new AssertionError("Check "+count);}
 static void rejects(Action a)throws Exception{count++;try{a.run();}catch(IOException e){return;}throw new AssertionError("Expected rejection");}
 static String hash(String s)throws Exception{StringBuilder b=new StringBuilder();for(byte v:MessageDigest.getInstance("SHA-256").digest(s.getBytes("UTF-8")))b.append(String.format("%02x",v&255));return b.toString();}
 static ManagedGameInstaller.Entry entry(String path,String text,boolean seed,String... old)throws Exception{return new ManagedGameInstaller.Entry(new BundledGameExtractor.Entry(path,text.length(),hash(text)),seed,Arrays.asList(old));}
 static void write(File r,String path,String text)throws Exception{File f=new File(r,path);f.getParentFile().mkdirs();Files.write(f.toPath(),text.getBytes("UTF-8"));}
 static String read(File r,String path)throws Exception{return new String(Files.readAllBytes(new File(r,path).toPath()),"UTF-8");}
 // Model a device with only the reserved margin left after staging completed.
 static final class LimitedSpace extends File {
  LimitedSpace(File file){super(file.getPath());}
  @Override public File getCanonicalFile()throws IOException{return new LimitedSpace(new File(getCanonicalPath()));}
  @Override public long getUsableSpace(){return 64L*1024*1024;}
 }
 public static void main(String[]args)throws Exception{
  File root=Files.createTempDirectory("library-tests").toFile().getCanonicalFile();
  BundledGameExtractor.Progress quiet=(done,total)->{if(done>total)throw new AssertionError("Progress");};
  try{
   check(GameProfile.RPU.directory(root).equals(root));
   check(GameProfile.SONORA.directory(root).equals(new File(root,"games/sonora")));
   check(GameProfile.NEVADA.directory(root).equals(new File(root,"games/nevada")));
   check(GameProfile.fromId(null)==GameProfile.RPU);
   try{GameProfile.fromId("../rpu");throw new AssertionError();}catch(IllegalArgumentException expected){count++;}
   write(root,"master.dat","old");write(root,"fallout2.cfg","custom");write(root,"data/savegame/slot01/save.dat","my save");
   String id=hash("release1");
   List<ManagedGameInstaller.Entry> files=Arrays.asList(entry("master.dat","new",false,hash("old")),entry("mods/rpu.dat","new mod",false),entry("fallout2.cfg","defaults",true));
   rejects(()->ManagedGameInstaller.install(root,id,files,path->{if(path.equals("mods/rpu.dat"))throw new IOException("Interrupted");return new ByteArrayInputStream("new".getBytes("UTF-8"));},quiet));
   check(read(root,"master.dat").equals("old"));check(read(root,"fallout2.cfg").equals("custom"));
   check(!new File(root,"mods/rpu.dat").exists());check(!ManagedGameInstaller.current(root,id));
   ManagedGameInstaller.install(root,id,files,path->new ByteArrayInputStream((path.equals("master.dat")?"new":"new mod").getBytes("UTF-8")),quiet);
   check(read(root,"master.dat").equals("new"));check(read(root,"mods/rpu.dat").equals("new mod"));
   check(read(root,"data/savegame/slot01/save.dat").equals("my save"));check(read(root,"fallout2.cfg").equals("custom"));check(ManagedGameInstaller.current(root,id));
   ManagedGameInstaller.install(root,id,files,path->{throw new AssertionError("Repeated extraction");},quiet);count++;
   write(root,"master.dat","user mod");
   rejects(()->ManagedGameInstaller.install(root,hash("release2"),files,path->{throw new AssertionError();},quiet));
   check(read(root,"master.dat").equals("user mod"));
   File nv=GameProfile.NEVADA.directory(root);nv.mkdirs();String next=hash("nevada1"),wanted="new archive";
   write(nv,".content-update/backup/master.dat","old archive");write(nv,".content-update/files/master.dat",wanted);
   write(nv,".content-update/journal",next+"\nmaster.dat\t"+hash(wanted)+"\n");write(nv,".bundle-installing",next);
   List<ManagedGameInstaller.Entry> nfiles=Collections.singletonList(entry("master.dat",wanted,false));
   ManagedGameInstaller.install(nv,next,nfiles,path->{throw new AssertionError("Recovery must use staged data");},quiet);
   check(read(nv,"master.dat").equals(wanted));check(ManagedGameInstaller.current(nv,next));check(read(root,"master.dat").equals("user mod"));
   File sonora=GameProfile.SONORA.directory(root);
   rejects(()->ManagedGameInstaller.install(sonora,hash("sonora1"),nfiles,path->new ByteArrayInputStream("bad".getBytes("UTF-8")),quiet));
   check(!new File(sonora,"master.dat").exists());
   File resumed=new File(root,"resumed");resumed.mkdirs();
   String staged="already downloaded";
   write(resumed,".content-update/files/master.dat",staged);
   List<ManagedGameInstaller.Entry> resumedFiles=Collections.singletonList(entry("master.dat",staged,false));
   ManagedGameInstaller.install(new LimitedSpace(resumed),hash("resumed"),resumedFiles,
     path->{throw new AssertionError("Verified staged bytes must be reused");},quiet);
   check(read(resumed,"master.dat").equals(staged));check(ManagedGameInstaller.current(resumed,hash("resumed")));
   File damaged=new File(root,"damaged-stage");damaged.mkdirs();
   write(damaged,".content-update/files/master.dat","corrupt");
   rejects(()->ManagedGameInstaller.install(new LimitedSpace(damaged),hash("damaged"),resumedFiles,
     path->{throw new AssertionError("Not enough room to replace corrupt staged bytes");},quiet));
   check(!new File(damaged,"master.dat").exists());
   for(String path:new String[]{"../escape","mods/../../escape","/absolute","mods\\escape","mods/./file","x\nmaster.dat","x\tmaster.dat"})rejects(()->ManagedGameInstaller.target(root,path));
   File link=new File(root,"games/redirect");Files.createSymbolicLink(link.toPath(),root.getParentFile().toPath());rejects(()->ManagedGameInstaller.target(root,"games/redirect/outside"));
   check(read(root,"data/savegame/slot01/save.dat").equals("my save"));
  }finally{try(java.util.stream.Stream<Path> paths=Files.walk(root.toPath())){paths.sorted(Comparator.reverseOrder()).forEach(path->{try{Files.delete(path);}catch(IOException e){throw new RuntimeException(e);}});}}
  System.out.println("Library tests passed: "+count+" checks; profiles, updates, recovery and preservation.");
 }
}
