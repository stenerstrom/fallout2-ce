package com.alexbatalov.fallout2ce;

import android.content.Context;
import android.graphics.*;
import android.view.View;

// Resolution-independent cover illustrations; no network or bitmap allocation.
final class WastelandArt extends View {
    private final GameProfile profile;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    WastelandArt(Context context, GameProfile profile) { super(context); this.profile=profile; setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); }
    @Override protected void onDraw(Canvas canvas) {
        float w=getWidth(), h=getHeight();
        canvas.save(); canvas.scale(w/400f,h/210f);
        int top=profile==GameProfile.SONORA ? 0xFF593F39 : profile==GameProfile.NEVADA ? 0xFF244341 : 0xFF43473B;
        paint.setShader(new LinearGradient(0,0,0,210,new int[]{top,0xFF181D1C},null,Shader.TileMode.CLAMP));
        canvas.drawRect(0,0,400,210,paint); paint.setShader(null);
        paint.setColor(profile.accent); paint.setAlpha(180);
        canvas.drawCircle(290,63,34,paint);
        paint.setAlpha(25);
        canvas.drawCircle(290,63,49,paint); canvas.drawCircle(290,63,66,paint);
        paint.setAlpha(255);
        mountain(canvas,0xFF34423B,new float[]{0,133,52,77,91,109,143,64,211,131,259,101,323,129,364,87,400,114});
        mountain(canvas,0xFF242F2A,new float[]{0,155,64,139,108,152,180,122,217,156,290,135,342,148,400,132});
        mountain(canvas,0xFF18211E,new float[]{0,183,67,172,135,181,220,158,315,181,400,166});
        paint.setColor(0xFF101917);
        if (profile==GameProfile.SONORA) {
            paint.setStrokeWidth(8); paint.setStrokeCap(Paint.Cap.ROUND);
            canvas.drawLine(90,191,90,114,paint); canvas.drawLine(72,141,72,158,paint);
            canvas.drawLine(72,158,90,164,paint); canvas.drawLine(109,132,109,151,paint); canvas.drawLine(109,151,90,156,paint);
        } else if (profile==GameProfile.NEVADA) {
            canvas.drawRect(71,119,110,188,paint); canvas.drawRect(116,140,145,188,paint);
            paint.setStrokeWidth(3); canvas.drawLine(91,80,91,120,paint); canvas.drawLine(72,100,110,100,paint);
            paint.setColor(profile.accent); paint.setAlpha(95);
            for (int y=129;y<174;y+=14) { canvas.drawRect(78,y,84,y+5,paint); canvas.drawRect(95,y,101,y+5,paint); }
        } else {
            paint.setStrokeWidth(4); canvas.drawLine(104,92,75,191,paint); canvas.drawLine(104,92,133,191,paint);
            canvas.drawLine(82,160,126,160,paint); canvas.drawLine(90,132,118,132,paint);
            canvas.drawLine(71,117,137,117,paint); canvas.drawLine(82,101,126,101,paint);
            paint.setStrokeWidth(1); canvas.drawLine(0,117,71,117,paint); canvas.drawLine(137,117,210,132,paint);
        }
        paint.setAlpha(255); paint.setStrokeCap(Paint.Cap.BUTT);
        paint.setShader(new LinearGradient(0,135,0,210,new int[]{0x00141917,0xFF141917},null,Shader.TileMode.CLAMP));
        canvas.drawRect(0,130,400,210,paint); paint.setShader(null);
        paint.setColor(0x0DFFFFFF); paint.setStrokeWidth(1);
        for (int y=0;y<210;y+=5) canvas.drawLine(0,y,400,y,paint);
        canvas.restore();
    }
    private void mountain(Canvas c,int color,float[] xy) {
        Path p=new Path(); p.moveTo(0,210);
        for(int i=0;i<xy.length;i+=2)p.lineTo(xy[i],xy[i+1]);
        p.lineTo(400,210); p.close(); paint.setColor(color); c.drawPath(p,paint);
    }
}
