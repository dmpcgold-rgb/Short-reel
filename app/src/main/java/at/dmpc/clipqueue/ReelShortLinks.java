package at.dmpc.clipqueue;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;

/** Saves ReelShort page references as bookmarks. A bookmark is NOT an offline video. */
public final class ReelShortLinks {
    private ReelShortLinks() {}
    private static final String PREFS = "clipqueue_reelshort_links_v1";
    private static final String KEY = "links";

    public static final class Link {
        public final String url;
        public final String title;
        Link(String url, String title) { this.url = url; this.title = title; }
    }

    public static ArrayList<Link> load(Context context) {
        ArrayList<Link> result = new ArrayList<>();
        try {
            String stored = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]");
            JSONArray array = new JSONArray(stored);
            for (int i = 0; i < array.length(); i++) {
                JSONObject row = array.optJSONObject(i);
                if (row == null) continue;
                String url = row.optString("url", "");
                if (UrlRules.isReelShortLink(url)) result.add(new Link(url, row.optString("title", "ReelShort-Serie")));
            }
        } catch (Exception ignored) { /* Broken bookmarks cannot affect video library. */ }
        return result;
    }

    public static boolean save(Context context, ArrayList<Link> links) {
        JSONArray array = new JSONArray();
        try {
            for (Link link : links) {
                JSONObject row = new JSONObject();
                row.put("url", link.url);
                row.put("title", link.title);
                array.put(row);
            }
            return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit().putString(KEY, array.toString()).commit();
        } catch (Exception e) { return false; }
    }
}
