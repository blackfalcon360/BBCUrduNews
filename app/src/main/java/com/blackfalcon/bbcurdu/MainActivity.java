package com.blackfalcon.bbcurdu;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.LruCache;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    private static final int RED = 0xFFE57373, GREEN = 0xFF2ECC71, GRAY = 0xFF9E9E9E, CARD = 0xFF121212, EDGE = 0xFF2A2A2A, BBC = 0xFFBB1919;

    private SharedPreferences sp;
    private final Map<String, List<Feeds.Item>> data = new HashMap<>();
    private final Map<String, String> status = new HashMap<>();
    private final List<Feeds.Item> visible = new ArrayList<>();
    private String filter = "all", query = "";
    private int generation = 0, finished = 0;
    private long lastUpdated = 0;
    private final ExecutorService pool = Executors.newFixedThreadPool(4);
    private final ExecutorService imgPool = Executors.newFixedThreadPool(3);
    private final LruCache<String, Bitmap> thumbs = new LruCache<>(80);
    private final Handler handler = new Handler(Looper.getMainLooper());

    private LinearLayout chipRow;
    private TextView statusTv;
    private ListView list;
    private ItemAdapter adapter;

    private final Runnable autoRefresh = new Runnable() {
        @Override public void run() { refresh(); handler.postDelayed(this, 5 * 60 * 1000L); }
    };

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sp = getSharedPreferences("bbcurdu", MODE_PRIVATE);
        for (Source s : Source.ALL) loadCache(s.id);
        buildUi();
        rebuildChips();
        rebuildList();
    }

    @Override protected void onResume() { super.onResume(); handler.post(autoRefresh); }
    @Override protected void onPause() { super.onPause(); handler.removeCallbacks(autoRefresh); }
    @Override protected void onDestroy() { super.onDestroy(); pool.shutdownNow(); imgPool.shutdownNow(); }

    // ----------------------------------------------------------------- cache
    private static String flat(String s) { return s == null ? "" : s.replace('\t', ' ').replace('\n', ' ').replace('\r', ' '); }

    private void saveCache(String id, List<Feeds.Item> items) {
        StringBuilder sb = new StringBuilder();
        for (Feeds.Item it : items) {
            sb.append(it.ts).append('\t').append(flat(it.title)).append('\t').append(flat(it.link)).append('\t')
                    .append(flat(it.thumb)).append('\t').append(flat(it.desc)).append('\n');
        }
        sp.edit().putString("cache_" + id, sb.toString()).apply();
    }

    private void loadCache(String id) {
        String raw = sp.getString("cache_" + id, "");
        List<Feeds.Item> items = new ArrayList<>();
        for (String line : raw.split("\n")) {
            String[] p = line.split("\t", -1);
            if (p.length >= 5) {
                Feeds.Item it = new Feeds.Item();
                it.sourceId = id;
                try { it.ts = Long.parseLong(p[0]); } catch (NumberFormatException e) { it.ts = 0; }
                it.title = p[1]; it.link = p[2]; it.thumb = p[3]; it.desc = p[4];
                if (!it.title.isEmpty() && !it.link.isEmpty()) items.add(it);
            }
        }
        if (!items.isEmpty()) data.put(id, items);
    }

    // --------------------------------------------------------------- loading
    private void refresh() {
        generation++;
        final int gen = generation;
        finished = 0;
        for (Source s : Source.ALL) status.put(s.id, "loading");
        updateStatus();
        for (final Source s : Source.ALL) {
            pool.execute(() -> {
                List<Feeds.Item> items = null;
                try { items = Feeds.load(s, 60); } catch (Exception ignored) { }
                final List<Feeds.Item> res = items;
                runOnUiThread(() -> {
                    if (gen != generation || isFinishing()) return;
                    finished++;
                    if (res != null && !res.isEmpty()) {
                        data.put(s.id, res);
                        status.put(s.id, "ok");
                        saveCache(s.id, res);
                        lastUpdated = System.currentTimeMillis();
                    } else {
                        status.put(s.id, "error");
                    }
                    rebuildList();
                });
            });
        }
    }

    // ------------------------------------------------------------------- UI
    private TextView tv(int size, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setTextSize(size);
        v.setTextColor(color);
        if (bold) v.setTypeface(Typeface.DEFAULT_BOLD);
        return v;
    }

    private GradientDrawable round(int fill, int stroke, int r) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setStroke(dp(1), stroke);
        g.setCornerRadius(dp(r));
        return g;
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(BBC);
        bar.setPadding(dp(16), dp(10), dp(8), dp(10));
        TextView title = tv(21, Color.WHITE, true);
        title.setText("بی بی سی اردو • سرخیاں");
        TextView refresh = tv(22, Color.WHITE, true);
        refresh.setText("\u21BB");
        refresh.setPadding(dp(14), dp(4), dp(14), dp(4));
        refresh.setClickable(true);
        refresh.setOnClickListener(v -> refresh());
        bar.addView(title, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        bar.addView(refresh);

        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        chipRow = new LinearLayout(this);
        chipRow.setPadding(dp(8), dp(8), dp(8), dp(4));
        hs.addView(chipRow);

        EditText search = new EditText(this);
        search.setHint("سرخیوں میں تلاش کریں");
        search.setTextColor(Color.WHITE);
        search.setHintTextColor(0xFF707070);
        search.setTextSize(15);
        search.setSingleLine(true);
        search.setPadding(dp(16), dp(8), dp(16), dp(8));
        search.setBackground(round(CARD, EDGE, 20));
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) { query = s.toString().trim(); rebuildList(); }
        });
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        sl.setMargins(dp(12), dp(4), dp(12), dp(2));

        statusTv = tv(12, GRAY, false);
        statusTv.setPadding(dp(16), dp(4), dp(16), dp(4));

        list = new ListView(this);
        list.setDivider(null);
        list.setDividerHeight(0);
        list.setPadding(dp(10), 0, dp(10), dp(6));
        list.setClipToPadding(false);
        adapter = new ItemAdapter();
        list.setAdapter(adapter);
        list.setOnItemClickListener((p, v, pos, id) -> openStory(visible.get(pos)));
        list.setOnItemLongClickListener((p, v, pos, id) -> {
            Feeds.Item it = visible.get(pos);
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType("text/plain");
            i.putExtra(Intent.EXTRA_TEXT, it.title + "\n" + it.link);
            startActivity(Intent.createChooser(i, null));
            return true;
        });

        TextView note = tv(11, 0xFF707070, false);
        note.setText("غیر سرکاری ایپ • خبریں بی بی سی اردو کے آر ایس ایس فیڈ سے • کہانی دبائیں تو ایپ کے اندر کھلتی ہے");
        note.setPadding(dp(14), dp(4), dp(14), 0);
        TextView credit = tv(12, Color.WHITE, true);
        credit.setText("By: Black Falcon \uD83E\uDD85");
        credit.setGravity(Gravity.RIGHT);
        credit.setPadding(dp(14), dp(2), dp(14), dp(6));
        credit.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);

        root.addView(bar);
        root.addView(hs);
        root.addView(search, sl);
        root.addView(statusTv);
        root.addView(list, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        root.addView(note);
        root.addView(credit);
        setContentView(root);
    }

    private void openStory(Feeds.Item it) {
        Intent i = new Intent(this, BrowserActivity.class);
        i.putExtra("url", it.link);
        i.putExtra("title", it.title);
        startActivity(i);
    }

    private void rebuildChips() {
        chipRow.removeAllViews();
        addChip("all", "تمام خبریں");
        for (Source s : Source.ALL) addChip(s.id, s.nameUr);
    }

    private void addChip(final String id, String label) {
        boolean on = id.equals(filter);
        TextView b = tv(13, on ? Color.WHITE : RED, true);
        b.setText(label);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(14), dp(8), dp(14), dp(8));
        b.setBackground(round(on ? BBC : 0xFF101010, BBC, 18));
        b.setClickable(true);
        b.setOnClickListener(v -> { filter = id; rebuildChips(); rebuildList(); list.setSelection(0); });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(3), 0, dp(3), 0);
        chipRow.addView(b, lp);
    }

    private static String key(String title) { return title.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim(); }

    private static int score(Feeds.Item it) { return (it.link.contains("bbc") ? 2 : 0) + (it.thumb.isEmpty() ? 0 : 1); }

    private void rebuildList() {
        visible.clear();
        Map<String, Feeds.Item> merged = new LinkedHashMap<>();
        for (Source s : Source.ALL) {
            if (!filter.equals("all") && !filter.equals(s.id)) continue;
            List<Feeds.Item> items = data.get(s.id);
            if (items == null) continue;
            for (Feeds.Item it : items) {
                String k = key(it.title);
                Feeds.Item old = merged.get(k);
                if (old == null || score(it) > score(old)) merged.put(k, it);
            }
        }
        String q = query.toLowerCase(Locale.ROOT);
        for (Feeds.Item it : merged.values()) {
            if (!q.isEmpty() && !it.title.toLowerCase(Locale.ROOT).contains(q)) continue;
            visible.add(it);
        }
        Collections.sort(visible, new Comparator<Feeds.Item>() {
            @Override public int compare(Feeds.Item a, Feeds.Item b) { return Long.compare(b.ts, a.ts); }
        });
        while (visible.size() > 500) visible.remove(visible.size() - 1);
        adapter.notifyDataSetChanged();
        updateStatus();
    }

    private void updateStatus() {
        int loading = 0;
        StringBuilder bad = new StringBuilder();
        for (Source s : Source.ALL) {
            String st = status.get(s.id);
            if ("loading".equals(st)) loading++;
            else if ("error".equals(st)) bad.append(bad.length() > 0 ? "، " : "").append(s.nameUr);
        }
        StringBuilder sb = new StringBuilder();
        if (loading > 0) sb.append("لوڈ ہو رہا ہے… ").append(finished).append("/").append(Source.ALL.length).append("   ");
        if (lastUpdated > 0) sb.append("اپڈیٹ ").append(new SimpleDateFormat("HH:mm", Locale.US).format(new Date(lastUpdated))).append("   ");
        sb.append(visible.size()).append(" خبریں");
        if (loading == 0 && bad.length() > 0) sb.append("   •   لوڈ نہیں ہوا: ").append(bad).append(" (محفوظ خبریں دکھائی جا رہی ہیں)");
        statusTv.setText(sb.toString());
    }

    // ------------------------------------------------------------------ list
    private void loadThumb(final ImageView iv, final String url) {
        iv.setTag(url);
        Bitmap cached = thumbs.get(url);
        if (cached != null) { iv.setImageBitmap(cached); return; }
        iv.setImageDrawable(null);
        imgPool.execute(() -> {
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                c.setConnectTimeout(10000);
                c.setReadTimeout(10000);
                Bitmap bm = BitmapFactory.decodeStream(c.getInputStream());
                c.disconnect();
                if (bm != null) {
                    final Bitmap fin = bm;
                    thumbs.put(url, fin);
                    runOnUiThread(() -> { if (url.equals(iv.getTag())) iv.setImageBitmap(fin); });
                }
            } catch (Exception ignored) { }
        });
    }

    private class ItemAdapter extends BaseAdapter {
        @Override public int getCount() { return visible.size(); }
        @Override public Object getItem(int i) { return visible.get(i); }
        @Override public long getItemId(int i) { return i; }

        @Override
        public View getView(int pos, View convert, ViewGroup parent) {
            if (convert == null) {
                LinearLayout wrap = new LinearLayout(MainActivity.this);
                wrap.setPadding(0, dp(4), 0, dp(4));
                LinearLayout card = new LinearLayout(MainActivity.this);
                card.setGravity(Gravity.CENTER_VERTICAL);
                card.setPadding(dp(12), dp(10), dp(12), dp(10));
                card.setBackground(round(CARD, EDGE, 14));
                LinearLayout col = new LinearLayout(MainActivity.this);
                col.setOrientation(LinearLayout.VERTICAL);
                TextView title = tv(17, Color.WHITE, true);
                title.setLineSpacing(0, 1.15f);
                title.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);
                TextView desc = tv(13, GRAY, false);
                desc.setMaxLines(2);
                desc.setPadding(0, dp(4), 0, 0);
                desc.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);
                TextView meta = tv(11, RED, true);
                meta.setPadding(0, dp(6), 0, 0);
                col.addView(title);
                col.addView(desc);
                col.addView(meta);
                ImageView img = new ImageView(MainActivity.this);
                img.setScaleType(ImageView.ScaleType.CENTER_CROP);
                img.setBackground(round(0xFF1C1C1C, 0xFF1C1C1C, 10));
                img.setClipToOutline(true);
                img.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
                card.addView(col, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
                LinearLayout.LayoutParams il = new LinearLayout.LayoutParams(dp(96), dp(72));
                il.setMarginStart(dp(10));
                card.addView(img, il);
                wrap.addView(card, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
                wrap.setTag(new View[]{title, desc, meta, img});
                convert = wrap;
            }
            View[] v = (View[]) convert.getTag();
            TextView title = (TextView) v[0], desc = (TextView) v[1], meta = (TextView) v[2];
            ImageView img = (ImageView) v[3];
            Feeds.Item it = visible.get(pos);
            title.setText(it.title);
            if (it.desc.isEmpty()) desc.setVisibility(View.GONE);
            else { desc.setVisibility(View.VISIBLE); desc.setText(it.desc); }
            long now = System.currentTimeMillis();
            Source s = Source.byId(it.sourceId);
            boolean fresh = now - it.ts < 30 * 60000L;
            meta.setText((s == null ? "" : s.nameUr + "  •  ") + Dates.ago(it.ts, now, 1) + (fresh ? "  \uD83D\uDD34 تازہ" : ""));
            if (it.thumb.isEmpty()) { img.setVisibility(View.GONE); img.setTag(null); }
            else { img.setVisibility(View.VISIBLE); loadThumb(img, it.thumb); }
            return convert;
        }
    }
}
