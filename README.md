# اردو سرخیاں (BBC Urdu) 📰

An unofficial reader for the headlines of bbcurdu.com (bbc.com/urdu). Urdu, right-to-left, portrait only.

- Main feed plus Pakistan, India, World, Sport, Science and Entertainment sections, merged into "تمام خبریں" with duplicates removed (up to 500 stories, newest first).
- Pictures, a short summary, time ("5 منٹ پہلے") and a 🔴 تازہ tag for stories under 30 minutes old.
- Tap a story: it opens in the app's own browser (back / forward / reload / share / open in your phone browser).
- Search box, long-press to share, refreshes every 5 minutes, saves the last headlines for offline reading.
- Each section uses the BBC's RSS feed first; if that address does not answer it falls back to a Google News search of bbc.com/urdu.

News and pictures belong to the BBC. This app only shows the headlines from the public RSS feeds, for personal use.
Brought to you By: Black Falcon 🦅

## Build
Push to GitHub -> Actions -> "Build APK" -> download `BbcUrduNews-APK`.
Local: `gradle assembleDebug` (JDK 17, Gradle 8.7+).
