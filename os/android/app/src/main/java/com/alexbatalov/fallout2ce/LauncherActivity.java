package com.alexbatalov.fallout2ce;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.io.IOException;

public class LauncherActivity extends Activity {
    private boolean rendering;
    private int dp(int value) { return LauncherUi.dp(this,value); }
    @Override protected void onCreate(Bundle state) { super.onCreate(state); }
    @Override protected void onResume() {
        super.onResume(); render();
        String error=getIntent().getStringExtra("launch_error");
        if(error!=null){ getIntent().removeExtra("launch_error"); LauncherUi.error(this,error); }
    }
    @Override protected void onNewIntent(Intent intent) { super.onNewIntent(intent); setIntent(intent); render(); }
    @Override public void onWindowFocusChanged(boolean focused) { super.onWindowFocusChanged(focused); if(focused)render(); }
    private TextView text(LinearLayout parent,String label,int size,int color) {
        TextView view=new TextView(this); view.setText(label); view.setTextSize(size); view.setTextColor(color);
        parent.addView(view,new LinearLayout.LayoutParams(-1,-2)); return view;
    }
    private GradientDrawable surface(int color,int border) {
        GradientDrawable shape=new GradientDrawable(); shape.setColor(color); shape.setCornerRadius(dp(16));
        shape.setStroke(dp(1),border); return shape;
    }
    private void render() {
        if(rendering || isFinishing())return; rendering=true;
        try {
            GameProfile selected=GameProfiles.current(this);
            SettingsRepository repository=new SettingsRepository(this);
            boolean running=repository.gameRunning();
            ScrollView scroll=new ScrollView(this); scroll.setFillViewport(true); scroll.setBackgroundColor(0xFF0C1210);
            LinearLayout page=new LinearLayout(this); page.setOrientation(LinearLayout.VERTICAL); page.setPadding(dp(28),dp(28),dp(28),dp(28));
            scroll.addView(page); setContentView(scroll);
            TextView eyebrow=text(page,"WASTELAND COLLECTION",12,0xFF99AC9E); eyebrow.setLetterSpacing(.22f);
            TextView title=text(page,"Choose your story.",34,0xFFF2EDDE);
            title.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL)); title.setPadding(0,dp(8),0,dp(8));
            text(page,"Your library · English editions · Separate saves",14,0xFF929F96);
            boolean wide=getResources().getConfiguration().screenWidthDp>=760;
            LinearLayout games=new LinearLayout(this); games.setOrientation(wide?LinearLayout.HORIZONTAL:LinearLayout.VERTICAL);
            LinearLayout.LayoutParams gp=new LinearLayout.LayoutParams(-1,-2); gp.topMargin=dp(24); page.addView(games,gp);
            for(GameProfile profile:GameProfile.values()){
                boolean chosen=profile==selected;
                LinearLayout card=new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL);
                card.setBackground(surface(chosen?0xFF19231D:0xFF141B17,chosen?profile.accent:0xFF2B3830));
                card.setClipToOutline(true);
                LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(wide?0:-1,-2,wide?1:0);
                if(wide)cp.setMargins(0,0,profile==GameProfile.NEVADA?0:dp(16),0); else cp.bottomMargin=dp(16);
                games.addView(card,cp);
                WastelandArt art=new WastelandArt(this,profile); card.addView(art,new LinearLayout.LayoutParams(-1,dp(wide?170:125)));
                LinearLayout words=new LinearLayout(this); words.setOrientation(LinearLayout.VERTICAL); words.setPadding(dp(20),0,dp(20),dp(20)); card.addView(words);
                text(words,profile.subtitle.toUpperCase(java.util.Locale.ROOT),11,profile.accent).setLetterSpacing(.12f);
                TextView name=text(words,profile.title,29,0xFFF2EDDE); name.setPadding(0,dp(6),0,dp(8)); name.setTypeface(Typeface.DEFAULT_BOLD);
                TextView description=text(words,profile.description,14,0xFFA7B1A8); description.setMinHeight(dp(58));
                boolean installed=new SettingsRepository(this,profile).hasGameData();
                TextView state=text(words,chosen?"SELECTED  •  "+(installed?"READY":"PREPARE TO PLAY"):(installed?"READY TO PLAY":BundledGame.available(this,profile)?"INCLUDED IN APP":"IMPORT GAME FILES"),11,chosen?profile.accent:0xFF809486);
                state.setPadding(0,dp(16),0,0);
                card.setContentDescription(profile.title+". "+profile.subtitle+(chosen?". Selected":". Select game"));
                card.setFocusable(true); card.setClickable(true); card.setEnabled(!running || chosen);
                card.setOnClickListener(v->{if(!running){GameProfiles.select(this,profile);render();}});
            }
            LinearLayout details=new LinearLayout(this); details.setOrientation(LinearLayout.VERTICAL); details.setPadding(dp(22),dp(18),dp(22),dp(20));
            details.setBackground(surface(0xFF18201B,0xFF303E33));
            LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2); params.topMargin=dp(24); page.addView(details,params);
            text(details,selected.title+" / "+selected.subtitle,18,selected.accent);
            boolean installed=repository.hasGameData();
            String note=running?"A game is running. Exit through its menu to switch adventures.":installed?"Your settings and saved games are ready.":"Game files are prepared the first time you play.";
            text(details,note,14,0xFFA7B1A8).setPadding(0,dp(8),0,dp(10));
            LinearLayout actions=new LinearLayout(this); actions.setGravity(Gravity.CENTER_VERTICAL); details.addView(actions);
            Button play=action(running?"Resume game":installed?"Play":"Prepare game",true,selected.accent);
            play.setTag("play"); actions.addView(play,new LinearLayout.LayoutParams(0,dp(54),1)); play.setOnClickListener(v->startGame());
            Button settings=action("Settings",false,selected.accent); settings.setTag("settings");
            LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(0,dp(54),1); sp.leftMargin=dp(12); actions.addView(settings,sp);
            settings.setEnabled(installed&&!running); settings.setAlpha(settings.isEnabled()?1:.4f);
            settings.setOnClickListener(v->startActivity(GameProfiles.intent(this,SettingsActivity.class)));
            TextView foot=text(page,"Each adventure keeps its own progress. Switch games after exiting to the library.",12,0xFF718377);
            foot.setPadding(0,dp(24),0,0);
        }catch(Exception error){LauncherUi.error(this,error.getMessage());}finally{rendering=false;}
    }
    private Button action(String label,boolean primary,int accent) {
        Button button=new Button(this); button.setText(label); button.setAllCaps(false); button.setTextSize(16);
        button.setTypeface(Typeface.DEFAULT_BOLD); button.setTextColor(primary?0xFF182018:0xFFE6E7DC);
        button.setBackground(surface(primary?accent:0xFF253127,primary?accent:0xFF425443)); return button;
    }
    private void startGame() {
        try {
            SettingsRepository repository=new SettingsRepository(this);
            if(repository.gameRunning()){startActivity(GameProfiles.intent(this,MainActivity.class));return;}
            if(BundledGame.needsInstall(this)){
                startActivity(GameProfiles.intent(this,BundledInstallActivity.class)); return;
            }
            if(!repository.hasGameData()){startActivity(GameProfiles.intent(this,ImportActivity.class));return;}
            SettingsValidation.validateScreen(new IniDocument(repository.read("fallout2.cfg")));
            startActivity(GameProfiles.intent(this,MainActivity.class));
        }catch(Exception error){LauncherUi.error(this,error.getMessage());}
    }
}
