package com.blackfalcon.bbcurdu;

import android.os.Build;
import android.text.Html;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/** Downloads and reads RSS feeds. */
public final class Feeds {
    private Feeds() { }

    public static final class Item {
        public String sourceId, title = "", link = "", desc = "", thumb = "";
        public long ts;
    }

    private static String clean(String t) {
        if (t == null) return "";
        String s;
        if (Build.VERSION.SDK_INT >= 24) s = Html.fromHtml(t, Html.FROM_HTML_MODE_LEGACY).toString();
        else s = legacy(t);
        return s.replace('\u00A0', ' ').replaceAll("\\s+", " ").trim();
    }

    @SuppressWarnings("deprecation")
    private static String legacy(String t) { return Html.fromHtml(t).toString(); }

    static List<Item> fetch(String urlStr, String sourceId, int max) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(urlStr).openConnection();
        c.setConnectTimeout(12000);
        c.setReadTimeout(15000);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 12) UrduHeadlines/1.0");
        c.setRequestProperty("Accept", "application/rss+xml, application/xml, text/xml, */*");
        int code = c.getResponseCode();
        if (code / 100 != 2) { c.disconnect(); throw new Exception("HTTP " + code); }
        InputStream in = new BufferedInputStream(c.getInputStream());
        try {
            return parse(in, sourceId, max, urlStr.contains("news.google.com"));
        } finally {
            try { in.close(); } catch (Exception ignored) { }
            c.disconnect();
        }
    }

    static List<Item> parse(InputStream in, String sourceId, int max, boolean google) throws Exception {
        XmlPullParser xp = Xml.newPullParser();
        xp.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
        xp.setInput(in, null);
        List<Item> out = new ArrayList<>();
        boolean inItem = false;
        String title = "", link = "", date = "", guid = "", desc = "", thumb = "";
        long base = System.currentTimeMillis();
        int ev = xp.getEventType();
        while (ev != XmlPullParser.END_DOCUMENT && out.size() < max) {
            if (ev == XmlPullParser.START_TAG) {
                String n = xp.getName();
                if ("item".equals(n) || "entry".equals(n)) {
                    inItem = true; title = ""; link = ""; date = ""; guid = ""; desc = ""; thumb = "";
                } else if (inItem) {
                    if ("title".equals(n)) title = xp.nextText();
                    else if ("link".equals(n)) link = xp.nextText();
                    else if ("guid".equals(n)) guid = xp.nextText();
                    else if ("description".equals(n)) desc = xp.nextText();
                    else if ("pubDate".equals(n) || "dc:date".equals(n)) { if (date.isEmpty()) date = xp.nextText(); }
                    else if ("media:thumbnail".equals(n) || "media:content".equals(n)) {
                        String u = xp.getAttributeValue(null, "url");
                        if (thumb.isEmpty() && u != null && u.startsWith("http")) thumb = u;
                    }
                }
            } else if (ev == XmlPullParser.END_TAG) {
                String n = xp.getName();
                if (("item".equals(n) || "entry".equals(n)) && inItem) {
                    inItem = false;
                    String l = link == null ? "" : link.trim();
                    if (l.isEmpty() && guid != null && guid.trim().startsWith("http")) l = guid.trim();
                    String t = clean(title);
                    if (!t.isEmpty() && !l.isEmpty()) {
                        Item it = new Item();
                        it.sourceId = sourceId;
                        it.title = google ? Dates.stripSource(t) : t;
                        it.link = l;
                        if (!google) {
                            String d = clean(desc);
                            it.desc = d.length() > 150 ? d.substring(0, 150).trim() + "…" : d;
                            it.thumb = thumb;
                        }
                        long ts = Dates.parse(date);
                        it.ts = ts > 0 ? Math.min(ts, base) : base - out.size() * 1000L;
                        out.add(it);
                    }
                }
            }
            ev = xp.next();
        }
        return out;
    }

    /** Tries each address of the section until one returns stories. */
    public static List<Item> load(Source s, int max) throws Exception {
        Exception last = null;
        for (String u : s.urls) {
            try {
                List<Item> items = fetch(u, s.id, max);
                if (!items.isEmpty()) return items;
            } catch (Exception e) {
                last = e;
            }
        }
        throw last != null ? last : new Exception("no stories");
    }
}
