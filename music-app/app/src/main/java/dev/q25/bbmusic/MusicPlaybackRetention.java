package dev.q25.bbmusic;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.util.Log;

import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * One persisted Media3 queue/position for both the app and Android playback
 * resumption. Only SAF documents under still-selected, read-authorized folder
 * trees can be restored. No MediaStore scan or other-player state is involved.
 */
final class MusicPlaybackRetention {
    private static final String TAG = "BBMusic-Retention";
    private static final String PREFS = "bbmusic_playback";
    private static final String QUEUE = "queue_v1";
    private static final String INDEX = "index";
    private static final String POSITION = "position_ms";
    private static final String SHUFFLE = "shuffle";
    private static final String REPEAT = "repeat";
    private static final String URI = "uri";
    private static final String TITLE = "title";
    private static final String ARTIST = "artist";
    private static final String ALBUM = "album";

    static final class Saved {
        final ArrayList<MediaItem> items;
        final int index;
        final long positionMs;
        final boolean shuffle;
        final int repeat;

        Saved(ArrayList<MediaItem> items, int index, long positionMs,
                boolean shuffle, int repeat) {
            this.items = items;
            this.index = index;
            this.positionMs = positionMs;
            this.shuffle = shuffle;
            this.repeat = repeat;
        }
    }

    private MusicPlaybackRetention() { }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String text(CharSequence value) {
        return value == null ? "" : value.toString();
    }

    /** Check the exact selected tree identity, not merely a content:// scheme. */
    private static boolean allowed(Uri file, List<Uri> selectedTrees) {
        if (file == null || !"content".equals(file.getScheme())) return false;
        try {
            if (!DocumentsContract.isTreeUri(file)) return false;
            String fileTree = DocumentsContract.getTreeDocumentId(file);
            // SAF document IDs are opaque provider-defined values. Do not
            // assume they encode paths or have hierarchical prefixes.
            if (fileTree == null || DocumentsContract.getDocumentId(file) == null
                    || !file.getPathSegments().contains("document")) {
                return false;
            }
            for (Uri tree : selectedTrees) {
                if (tree.getAuthority() != null
                        && tree.getAuthority().equals(file.getAuthority())
                        && fileTree.equals(DocumentsContract.getTreeDocumentId(tree))) {
                    return true;
                }
            }
        } catch (RuntimeException exception) {
            Log.w(TAG, "Cannot verify saved music document", exception);
        }
        return false;
    }

    /**
     * Queue changes are infrequent. Store metadata and stable SAF URIs once,
     * then checkpoint position separately without reserializing every track.
     */
    static void saveQueue(Context context, Player player) {
        if (player.getMediaItemCount() == 0
                || player.getCurrentMediaItemIndex() < 0) return;
        List<Uri> selected = MusicFolders.selectedTrees(context);
        JSONArray queue = new JSONArray();
        for (int i = 0; i < player.getMediaItemCount(); i++) {
            MediaItem item = player.getMediaItemAt(i);
            Uri uri = item.localConfiguration == null ? null
                    : item.localConfiguration.uri;
            if (!allowed(uri, selected)) {
                // Never persist an unselected/external document.
                Log.w(TAG, "Refusing to retain a queue outside selected folders");
                clear(context);
                return;
            }
            JSONObject entry = new JSONObject();
            try {
                entry.put(URI, uri.toString());
                entry.put(TITLE, text(item.mediaMetadata.title));
                entry.put(ARTIST, text(item.mediaMetadata.artist));
                entry.put(ALBUM, text(item.mediaMetadata.albumTitle));
            } catch (JSONException exception) {
                Log.w(TAG, "Cannot encode Media3 queue", exception);
                return;
            }
            queue.put(entry);
        }
        prefs(context).edit()
                .putString(QUEUE, queue.toString())
                .putInt(INDEX, player.getCurrentMediaItemIndex())
                .putLong(POSITION, Math.max(0L, player.getCurrentPosition()))
                .putBoolean(SHUFFLE, player.getShuffleModeEnabled())
                .putInt(REPEAT, player.getRepeatMode())
                .apply();
    }

    /** Fast, small checkpoint; commit on explicit pause/teardown for durability. */
    static void checkpoint(Context context, Player player, boolean durable) {
        if (player.getMediaItemCount() == 0
                || player.getCurrentMediaItemIndex() < 0) return;
        SharedPreferences.Editor editor = prefs(context).edit()
                .putInt(INDEX, player.getCurrentMediaItemIndex())
                .putLong(POSITION, Math.max(0L, player.getCurrentPosition()))
                .putBoolean(SHUFFLE, player.getShuffleModeEnabled())
                .putInt(REPEAT, player.getRepeatMode());
        if (durable) {
            if (!editor.commit()) Log.w(TAG, "Playback checkpoint not committed");
        } else {
            editor.apply();
        }
    }

    /** A deliberate Stop must not create a system playback-resumption card. */
    static void clear(Context context) {
        if (!prefs(context).edit().clear().commit()) {
            Log.w(TAG, "Unable to clear previous playback state");
        }
    }

    static Saved restore(Context context) {
        SharedPreferences preferences = prefs(context);
        String encoded = preferences.getString(QUEUE, null);
        if (encoded == null || encoded.isEmpty()) return null;
        List<Uri> selected = MusicFolders.selectedTrees(context);
        try {
            JSONArray queue = new JSONArray(encoded);
            int oldIndex = preferences.getInt(INDEX, 0);
            if (oldIndex < 0 || oldIndex >= queue.length()) {
                clear(context);
                return null;
            }
            ArrayList<MediaItem> restored = new ArrayList<>();
            int restoredIndex = -1;
            for (int i = 0; i < queue.length(); i++) {
                JSONObject entry = queue.getJSONObject(i);
                Uri uri = Uri.parse(entry.getString(URI));
                if (!allowed(uri, selected)) {
                    if (i == oldIndex) {
                        // Never silently switch to a different song.
                        clear(context);
                        return null;
                    }
                    continue;
                }
                if (i == oldIndex) restoredIndex = restored.size();
                MediaMetadata metadata = new MediaMetadata.Builder()
                        .setTitle(entry.optString(TITLE, ""))
                        .setArtist(entry.optString(ARTIST, ""))
                        .setAlbumTitle(entry.optString(ALBUM, ""))
                        .setIsPlayable(true)
                        .setIsBrowsable(false)
                        .build();
                restored.add(new MediaItem.Builder()
                        .setMediaId(uri.toString())
                        .setUri(uri)
                        .setMediaMetadata(metadata)
                        .build());
            }
            if (restored.isEmpty() || restoredIndex < 0) {
                clear(context);
                return null;
            }
            int repeat = preferences.getInt(REPEAT, Player.REPEAT_MODE_OFF);
            if (repeat != Player.REPEAT_MODE_OFF
                    && repeat != Player.REPEAT_MODE_ONE
                    && repeat != Player.REPEAT_MODE_ALL) repeat = Player.REPEAT_MODE_OFF;
            return new Saved(restored, restoredIndex,
                    Math.max(0L, preferences.getLong(POSITION, 0L)),
                    preferences.getBoolean(SHUFFLE, false), repeat);
        } catch (JSONException | RuntimeException exception) {
            Log.w(TAG, "Invalid saved playback context; ignoring", exception);
            clear(context);
            return null;
        }
    }
}
