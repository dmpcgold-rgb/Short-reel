package at.dmpc.clipqueue;

import org.json.JSONException;
import org.json.JSONObject;

/** A playlist entry referencing a MediaStore or Storage Access Framework content Uri. */
public final class Clip {
    public final String id;
    public final String title;
    public final String uri;
    public final String source;

    public Clip(String id, String title, String uri, String source) {
        this.id = id;
        this.title = title;
        this.uri = uri;
        this.source = source;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("id", id);
        obj.put("title", title);
        obj.put("uri", uri);
        obj.put("source", source);
        return obj;
    }

    public static Clip fromJson(JSONObject o) throws JSONException {
        return new Clip(o.getString("id"), o.optString("title", "Video"),
                o.getString("uri"), o.optString("source", "Import"));
    }
}
