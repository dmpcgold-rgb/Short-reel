package at.dmpc.clipqueue;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONException;
import java.util.ArrayList;
import java.util.List;

/** Keeps playlist ordering across restarts. Stored media remains on the device. */
public final class ClipStore {
    private static final String PREFS = "clipqueue_store_v1";
    private static final String KEY = "playlist";

    private ClipStore() {}

    public static synchronized ArrayList<Clip> load(Context context) {
        ArrayList<Clip> items = new ArrayList<>();
        String raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]");
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                try { items.add(Clip.fromJson(array.getJSONObject(i))); }
                catch (JSONException ignored) { /* Skip only broken entry; retain others. */ }
            }
        } catch (JSONException ignored) { /* Show empty list if settings are corrupted. */ }
        return items;
    }

    public static synchronized boolean save(Context context, List<Clip> items) {
        JSONArray array = new JSONArray();
        try {
            for (Clip item : items) array.put(item.toJson());
            return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit().putString(KEY, array.toString()).commit();
        } catch (JSONException ignored) { return false; }
    }
}
