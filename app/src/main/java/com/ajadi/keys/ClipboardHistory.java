package com.ajadi.keys;

import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.SystemClock;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * User-controlled clipboard history for the current, selected IME.
 * Copies are retained in RAM only, not written to files, logs or network.
 * Clipboard access uses the public Android API and never bypasses OS restrictions.
 */
final class ClipboardHistory {
    static final int MAX_ITEMS = 20;
    static final int MAX_TOTAL_CHARS = 256 * 1024;
    static final long MAX_AGE_MS = 60 * 60 * 1000L;

    static final class Store {
        private static final class Entry {
            final String text;
            final long time;
            Entry(String text, long time) { this.text = text; this.time = time; }
        }
        private final List<Entry> entries = new ArrayList<>();

        void remember(String text, long now) {
            prune(now);
            if (text == null || text.isEmpty() || text.length() > MAX_TOTAL_CHARS) return;
            entries.removeIf(item -> item.text.equals(text));
            entries.add(0, new Entry(text, now));
            int chars = 0;
            for (Entry entry : entries) chars += entry.text.length();
            while (entries.size() > MAX_ITEMS || chars > MAX_TOTAL_CHARS)
                chars -= entries.remove(entries.size()-1).text.length();
        }

        List<String> recent(long now) {
            prune(now);
            List<String> result = new ArrayList<>(entries.size());
            for (Entry entry : entries) result.add(entry.text);
            return result;
        }

        void remove(String text){entries.removeIf(e->e.text.equals(text));}
        void clear() { entries.clear(); }

        private void prune(long now) {
            entries.removeIf(entry -> now - entry.time >= MAX_AGE_MS);
        }
    }

    private final ClipboardManager clipboard;
    private final BooleanSupplier isSelected,canCapture;
    private final Runnable onChanged;
    private final Store store = new Store();
    private boolean enabled;
    private boolean initialized;
    private long lastTimestamp;
    private final ClipboardManager.OnPrimaryClipChangedListener listener = () -> capture(true);

    ClipboardHistory(Context context, BooleanSupplier isSelected, BooleanSupplier canCapture,Runnable onChanged) {
        clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        this.isSelected = isSelected;this.canCapture=canCapture;
        this.onChanged = onChanged;
    }

    void setEnabled(boolean enabled) {
        if (this.enabled == enabled) return;
        this.enabled = enabled;
        if (!enabled) {
            if (clipboard != null) clipboard.removePrimaryClipChangedListener(listener);
            store.clear();
            initialized = false;
        } else if (clipboard != null) {
            clipboard.addPrimaryClipChangedListener(listener);
            capture(false);
        }
    }

    void captureCurrent() { capture(false); }

    private void capture(boolean newEvent) {
        if (!enabled || clipboard == null || !isSelected.getAsBoolean()) return;
        try {
            ClipDescription description = clipboard.getPrimaryClipDescription();
            if (description == null) return;
            android.os.PersistableBundle extras=description.getExtras();
            if(!canCapture.getAsBoolean()||(extras!=null&&(extras.getBoolean("android.content.extra.IS_SENSITIVE",false)
                    ||extras.getBoolean("android.content.extra.IS_SENSITIVE_CLIP",false)))){
                // Skip without reading data, and do not re-capture this item upon returning to a normal field.
                initialized=true;lastTimestamp=description.getTimestamp();return;
            }
            long timestamp = description.getTimestamp();
            if (!newEvent && initialized && timestamp == lastTimestamp) return;
            ClipData clip = clipboard.getPrimaryClip();
            if (clip == null) return;
            initialized = true;
            lastTimestamp = clip.getDescription().getTimestamp();
            // Only actual plain-text items copied into the system clipboard.
            // No semantic/content classification; never open content URIs or intents.
            long now = SystemClock.elapsedRealtime();
            for (int i = clip.getItemCount()-1; i >= 0; i--) {
                CharSequence text = clip.getItemAt(i).getText();
                if (text != null) store.remember(text.toString(), now);
            }
            onChanged.run();
        } catch (SecurityException deniedByAndroid) {
            // Clipboard not available in this context. Do not circumvent it.
        }
    }

    List<String> recent() { return store.recent(SystemClock.elapsedRealtime()); }
    void remove(String text){store.remove(text);}
    void clear() { store.clear(); }
    void close() { setEnabled(false); store.clear(); }
}
