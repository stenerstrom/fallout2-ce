package com.alexbatalov.fallout2ce;

import android.app.Activity;
import android.graphics.Color;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

final class LauncherUi {
    static final int GOLD = Color.rgb(230, 190, 91);
    static final int MUTED = Color.rgb(185, 191, 183);

    static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    static LinearLayout page(Activity activity, String title) {
        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(true);
        LinearLayout body = new LinearLayout(activity);
        body.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(activity, 24);
        body.setPadding(padding, padding, padding, padding);
        scroll.addView(body);
        activity.setContentView(scroll);
        text(activity, body, title, 28, GOLD);
        return body;
    }

    static TextView text(Activity activity, LinearLayout body, String value, int size, int color) {
        TextView view = new TextView(activity);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setPadding(0, dp(activity, 8), 0, dp(activity, 10));
        body.addView(view, new LinearLayout.LayoutParams(-1, -2));
        return view;
    }

    static void note(Activity activity, LinearLayout body, String value) {
        text(activity, body, value, 16, MUTED);
    }

    static Button button(Activity activity, LinearLayout body, String label, View.OnClickListener listener) {
        Button button = new Button(activity);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(18);
        button.setMinHeight(dp(activity, 56));
        button.setOnClickListener(listener);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(activity, 8);
        body.addView(button, params);
        return button;
    }

    static void error(Activity activity, String message) {
        new android.app.AlertDialog.Builder(activity)
                .setTitle("Kunde inte slutföra")
                .setMessage(message)
                .setPositiveButton("OK", null).show();
    }
}
