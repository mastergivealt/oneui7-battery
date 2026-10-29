package dev.claude.oneui7pill;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;

import java.util.Map;

/**
 * Holds the module settings (int values, keys "oneui7pill_*") in the module app's own storage.
 * SystemUI reads them through this provider; only the module app (same signature) can write.
 * Replaces Settings.System, which newer Samsung firmware rejects with IllegalArgumentException.
 */
public class ConfigProvider extends ContentProvider {

    private SharedPreferences prefs() {
        Context c = getContext();
        Context dp = c.createDeviceProtectedStorageContext(); // readable before first unlock
        return dp.getSharedPreferences("config", Context.MODE_PRIVATE);
    }

    @Override public boolean onCreate() { return true; }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
                        String sortOrder) {
        MatrixCursor c = new MatrixCursor(new String[] { "key", "value" });
        for (Map.Entry<String, ?> e : prefs().getAll().entrySet()) {
            if (e.getValue() instanceof Integer) c.addRow(new Object[] { e.getKey(), e.getValue() });
        }
        return c;
    }

    private boolean store(ContentValues v) {
        if (v == null) return false;
        String key = v.getAsString("key");
        Integer val = v.getAsInteger("value");
        if (key == null || val == null || !key.startsWith(PillConfig.P) || key.length() > 64) return false;
        prefs().edit().putInt(key, val).apply();
        getContext().getContentResolver().notifyChange(
                Uri.parse("content://" + PillConfig.AUTH + "/" + key), null);
        return true;
    }

    @Override public Uri insert(Uri uri, ContentValues values) { return store(values) ? uri : null; }
    @Override public int update(Uri uri, ContentValues values, String s, String[] a) { return store(values) ? 1 : 0; }
    @Override public int delete(Uri uri, String s, String[] a) { return 0; }
    @Override public String getType(Uri uri) { return null; }
}
