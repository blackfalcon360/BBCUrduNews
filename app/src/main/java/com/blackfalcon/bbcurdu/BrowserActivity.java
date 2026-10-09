package com.blackfalcon.bbcurdu;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

/** The built-in browser: stories open here, inside the app. */
public class BrowserActivity extends Activity {

    private static final int GREEN = 0xFF2ECC71, RED = 0xFFBB1919;

    private WebView web;
    private ProgressBar bar;
    private TextView titleTv, backBtn, fwdBtn;
    private String startUrl = "";

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private TextView btn(String s, int size) {
        TextView b = new TextView(this);
        b.setText(s);
        b.setTextSize(size);
        b.setTextColor(Color.WHITE);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(14), dp(10), dp(14), dp(10));
        b.setClickable(true);
        return b;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        startUrl = getIntent().getStringExtra("url");
        if (startUrl == null) startUrl = "https://www.bbc.com/urdu";

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);

        // top bar
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setBackgroundColor(0xFF111111);
        TextView close = btn("\u2715", 18);
        close.setOnClickListener(v -> finish());
        titleTv = new TextView(this);
        titleTv.setTextColor(Color.WHITE);
        titleTv.setTextSize(14);
        titleTv.setSingleLine(true);
        titleTv.setEllipsize(android.text.TextUtils.TruncateAt.END);
        titleTv.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);
        TextView reload = btn("\u21BB", 20);
        reload.setOnClickListener(v -> web.reload());
        top.addView(close);
        top.addView(titleTv, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        top.addView(reload);

        bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        bar.setProgressTintList(android.content.res.ColorStateList.valueOf(RED));

        web = new WebView(this);
        WebSettings ws = web.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setLoadWithOverviewMode(true);
        ws.setUseWideViewPort(true);
        ws.setBuiltInZoomControls(true);
        ws.setDisplayZoomControls(false);
        ws.setSupportZoom(true);
        web.setBackgroundColor(Color.WHITE);
        web.setWebChromeClient(new WebChromeClient() {
            @Override public void onProgressChanged(WebView v, int p) {
                bar.setProgress(p);
                bar.setVisibility(p >= 100 ? View.GONE : View.VISIBLE);
            }
            @Override public void onReceivedTitle(WebView v, String t) { if (t != null && !t.isEmpty()) titleTv.setText(t); }
        });
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) { return handle(r.getUrl()); }
            @SuppressWarnings("deprecation")
            @Override public boolean shouldOverrideUrlLoading(WebView v, String url) { return handle(Uri.parse(url)); }
            @Override public void onPageStarted(WebView v, String url, Bitmap f) { bar.setVisibility(View.VISIBLE); updateNav(); }
            @Override public void onPageFinished(WebView v, String url) { updateNav(); }
        });

        // bottom bar
        LinearLayout bottom = new LinearLayout(this);
        bottom.setBackgroundColor(0xFF111111);
        backBtn = btn("\u2190", 22);
        backBtn.setOnClickListener(v -> { if (web.canGoBack()) web.goBack(); });
        fwdBtn = btn("\u2192", 22);
        fwdBtn.setOnClickListener(v -> { if (web.canGoForward()) web.goForward(); });
        TextView share = btn("\u2197", 22);
        share.setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType("text/plain");
            i.putExtra(Intent.EXTRA_TEXT, web.getTitle() + "\n" + web.getUrl());
            startActivity(Intent.createChooser(i, null));
        });
        TextView ext = btn("\uD83C\uDF10", 20);
        ext.setOnClickListener(v -> {
            try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(web.getUrl() != null ? web.getUrl() : startUrl))); }
            catch (Exception e) { Toast.makeText(this, "Browser not found", Toast.LENGTH_SHORT).show(); }
        });
        LinearLayout.LayoutParams w1 = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        bottom.addView(backBtn, w1);
        bottom.addView(fwdBtn, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        bottom.addView(share, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        bottom.addView(ext, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        root.addView(top);
        root.addView(bar, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(3)));
        root.addView(web, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        root.addView(bottom);
        setContentView(root);

        String t = getIntent().getStringExtra("title");
        if (t != null) titleTv.setText(t);
        updateNav();
        if (savedInstanceState != null) web.restoreState(savedInstanceState); else web.loadUrl(startUrl);
    }

    /** http(s) pages stay inside the app; mailto:, tel:, intent: links go to the right app. */
    private boolean handle(Uri u) {
        String s = u.getScheme();
        if ("http".equals(s) || "https".equals(s)) return false;
        try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Exception ignored) { }
        return true;
    }

    private void updateNav() {
        if (backBtn == null || web == null) return;
        backBtn.setAlpha(web.canGoBack() ? 1f : 0.35f);
        fwdBtn.setAlpha(web.canGoForward() ? 1f : 0.35f);
    }

    @Override
    protected void onSaveInstanceState(Bundle out) { super.onSaveInstanceState(out); web.saveState(out); }

    @Override
    public void onBackPressed() {
        if (web.canGoBack()) web.goBack(); else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        if (web != null) { web.stopLoading(); web.destroy(); }
        super.onDestroy();
    }
}
