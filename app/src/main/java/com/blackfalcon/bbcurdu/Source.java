package com.blackfalcon.bbcurdu;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

/** A section of bbcurdu.com. It tries the BBC's own RSS feed first, then a Google News search of the BBC Urdu site. */
public final class Source {
    public final String id, nameUr;
    public final String[] urls;

    Source(String id, String nameUr, String... urls) { this.id = id; this.nameUr = nameUr; this.urls = urls; }

    /** Google News RSS for stories from bbc.com/urdu (optionally about a topic word). */
    static String gn(String topic) {
        try {
            String q = "site:bbc.com/urdu" + (topic.isEmpty() ? "" : " " + topic) + " when:7d";
            return "https://news.google.com/rss/search?q=" + URLEncoder.encode(q, "UTF-8") + "&hl=ur&gl=PK&ceid=PK:ur";
        } catch (UnsupportedEncodingException e) {
            return "https://news.google.com/rss";
        }
    }

    static final String FEEDS = "https://feeds.bbci.co.uk/urdu/";

    public static final Source[] ALL = {
            new Source("top", "اہم خبریں", FEEDS + "rss.xml", gn("")),
            new Source("pakistan", "پاکستان", FEEDS + "pakistan/rss.xml", gn("پاکستان")),
            new Source("india", "انڈیا", FEEDS + "india/rss.xml", gn("انڈیا")),
            new Source("world", "دنیا", FEEDS + "world/rss.xml", FEEDS + "international/rss.xml", gn("عالمی")),
            new Source("sport", "کھیل", FEEDS + "sport/rss.xml", gn("کرکٹ")),
            new Source("science", "سائنس", FEEDS + "science/rss.xml", gn("سائنس")),
            new Source("fun", "تفریح", FEEDS + "entertainment/rss.xml", gn("فلم")),
    };

    public static Source byId(String id) {
        for (Source s : ALL) if (s.id.equals(id)) return s;
        return null;
    }
}
