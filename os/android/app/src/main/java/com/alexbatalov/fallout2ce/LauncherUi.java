package com.alexbatalov.fallout2ce;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

final class LauncherUi {
    static final int GOLD = Color.rgb(230, 190, 91);
    static final int MUTED = Color.rgb(185, 191, 183);
    static final int BACKGROUND = 0xFF0C1210;

    static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    static final class Screen {
        final LinearLayout root, body, footer;
        final ScrollView scroll;
        Screen(Activity activity, String title) {
            root = new LinearLayout(activity);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setBackgroundColor(BACKGROUND);
            LinearLayout header = new LinearLayout(activity);
            header.setGravity(Gravity.CENTER_VERTICAL);
            header.setPadding(dp(activity, 16), dp(activity, 8), dp(activity, 16), 0);
            Button back = action(activity, "‹ Back", false, GOLD);
            back.setTag("navigate-back");
            back.setOnClickListener(v -> activity.onBackPressed());
            header.addView(back, new LinearLayout.LayoutParams(-2, -2));
            TextView heading = new TextView(activity);
            heading.setText(title);
            heading.setTextSize(22);
            heading.setTextColor(GOLD);
            heading.setPadding(dp(activity, 16), 0, 0, 0);
            header.addView(heading, new LinearLayout.LayoutParams(0, -2, 1));
            root.addView(header);
            scroll = new ScrollView(activity);
            scroll.setTag("page-scroll");
            scroll.setFillViewport(true);
            body = new LinearLayout(activity);
            body.setOrientation(LinearLayout.VERTICAL);
            body.setFocusableInTouchMode(true);
            int padding = dp(activity, 24);
            body.setPadding(padding, dp(activity, 12), padding, padding);
            scroll.addView(body);
            root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
            footer = new LinearLayout(activity);
            footer.setPadding(dp(activity, 16), dp(activity, 8), dp(activity, 16), dp(activity, 12));
            footer.setBackgroundColor(0xFF18201B);
            footer.setVisibility(View.GONE);
            root.addView(footer, new LinearLayout.LayoutParams(-1, -2));
            activity.setContentView(root);
            body.requestFocus();
        }
    }

    static LinearLayout page(Activity activity, String title) {
        return new Screen(activity, title).body;
    }

    static GradientDrawable surface(Activity activity, int color, int border) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(dp(activity, 12));
        shape.setStroke(dp(activity, 1), border);
        return shape;
    }

    static Button action(Activity activity, String label, boolean primary, int accent) {
        Button button = new Button(activity);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(16);
        button.setMinHeight(dp(activity, 56));
        button.setMinimumWidth(0);
        button.setMinWidth(0);
        button.setPadding(dp(activity, 12), dp(activity, 8), dp(activity, 12), dp(activity, 8));
        button.setTextColor(new ColorStateList(new int[][]{
                new int[]{-android.R.attr.state_enabled}, new int[]{}},
                new int[]{0xFF778378, primary ? 0xFF182018 : 0xFFE6E7DC}));
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(0x446D8E74),
                surface(activity, primary ? accent : 0xFF253127, primary ? accent : 0xFF425443), null));
        return button;
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
        Button button = action(activity, label, false, GOLD);
        button.setOnClickListener(listener);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(activity, 8);
        body.addView(button, params);
        return button;
    }

    static void error(Activity activity, String message) {
        new android.app.AlertDialog.Builder(activity)
                .setTitle("Could not complete the action")
                .setMessage(message)
                .setPositiveButton("OK", null).show();
    }
}
