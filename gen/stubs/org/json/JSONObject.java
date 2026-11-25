package org.json;
public class JSONObject {
  public JSONObject put(String k, long v) throws JSONException { return this; } public JSONObject put(String k, int v) throws JSONException { return this; } public JSONObject put(String k, Object v) throws JSONException { return this; }
  public long getLong(String k) throws JSONException { return 0; } public String getString(String k) throws JSONException { return null; } public int getInt(String k) throws JSONException { return 0; }
  public JSONArray getJSONArray(String k) throws JSONException { return null; }
}
