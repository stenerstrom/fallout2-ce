package com.alexbatalov.fallout2ce;

import android.content.Context;
import java.io.*;
import java.security.MessageDigest;
import java.util.*;
import org.json.*;

final class BundledGame {
    private static String manifestPath(Context context,GameProfile profile) {
        String path="bundled-games/"+profile.id+"/manifest.json";
        try(InputStream ignored=context.getAssets().open(path)){return path;}catch(IOException absent){}
        if(profile==GameProfile.RPU)return "bundled-game/manifest.json";
        return path;
    }
    static boolean available(Context context){return available(context,GameProfiles.current(context));}
    static boolean available(Context context,GameProfile profile) {
        try(InputStream ignored=context.getAssets().open(manifestPath(context,profile))){return true;}
        catch(IOException absent){return false;}
    }
    private static final class Bundle {
        String id; boolean pooled;
        List<ManagedGameInstaller.Entry> entries=new ArrayList<>();
        Map<String,String> assets=new HashMap<>();
    }
    private static Bundle load(Context context,GameProfile profile)throws IOException {
        String path=manifestPath(context,profile);
        try(InputStream input=context.getAssets().open(path);ByteArrayOutputStream output=new ByteArrayOutputStream()){
            byte[] buffer=new byte[4096];int n;while((n=input.read(buffer))!=-1)output.write(buffer,0,n);
            byte[] bytes=output.toByteArray();JSONObject manifest=new JSONObject(new String(bytes,"UTF-8"));
            int format=manifest.optInt("format",1);
            if(format!=1&&format!=2)throw new IOException("Unsupported game bundle format.");
            Bundle bundle=new Bundle();bundle.pooled=format==2;
            StringBuilder id=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(bytes))id.append(String.format(Locale.ROOT,"%02x",b&255));bundle.id=id.toString();
            JSONArray files=manifest.getJSONArray("files");Set<String> names=new HashSet<>();
            for(int i=0;i<files.length();i++){
                JSONObject file=files.getJSONObject(i);String name=file.getString("path"),sha=file.getString("sha256");
                if(!names.add(name)||!sha.matches("[0-9a-f]{64}"))throw new IOException("Invalid game manifest.");
                List<String> previous=new ArrayList<>();JSONArray before=file.optJSONArray("previous_sha256");
                if(before!=null)for(int j=0;j<before.length();j++)previous.add(before.getString(j));
                boolean seed=file.optBoolean("seed",name.endsWith(".ini")||name.endsWith(".cfg"));
                bundle.entries.add(new ManagedGameInstaller.Entry(new BundledGameExtractor.Entry(name,file.getLong("size"),sha),seed,previous));
                bundle.assets.put(name,bundle.pooled?"bundled-pool/"+sha:"bundled-game/files/"+name);
            }
            for(String required:new String[]{"master.dat","critter.dat","ce.dat","fallout2.cfg"})
                if(!names.contains(required))throw new IOException("The game bundle is missing "+required);
            return bundle;
        }catch(JSONException malformed){throw new IOException("Could not read game manifest.",malformed);}
        catch(java.security.NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}
    }
    static boolean needsInstall(Context context)throws IOException {
        return needsInstall(context,GameProfiles.current(context));
    }
    static boolean needsInstall(Context context,GameProfile profile)throws IOException {
        if(!available(context,profile))return false;
        Bundle bundle=load(context,profile);
        return !ManagedGameInstaller.current(new SettingsRepository(context,profile).gameDirectory(),bundle.id);
    }
    static void install(Context context,BundledGameExtractor.Progress progress)throws IOException {
        Bundle bundle=load(context,GameProfiles.current(context));
        ManagedGameInstaller.install(new SettingsRepository(context).gameDirectory(),bundle.id,bundle.entries,
                path->context.getAssets().open(bundle.assets.get(path)),progress);
    }
}
