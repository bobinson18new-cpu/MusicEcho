package com.musicecho.app;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Locale;

public class MainActivity extends Activity {
    private static class Song {
        Uri uri;
        String title;
        String streamUrl;
        String identifier;
        String license;
        boolean online;
        Song(Uri uri, String title) { this.uri = uri; this.title = title; this.online = false; }
        Song(String title, String creator, String identifier, String license) {
            this.title = title + (creator == null || creator.isEmpty() ? "" : " • " + creator);
            this.identifier = identifier;
            this.license = license;
            this.online = true;
        }
        @Override public String toString() { return title; }
    }

    private final ArrayList<Song> localSongs = new ArrayList<>();
    private final ArrayList<Song> visibleSongs = new ArrayList<>();
    private final ArrayList<Song> onlineSongs = new ArrayList<>();
    private final Handler handler = new Handler();
    private MediaPlayer player;
    private ArrayAdapter<Song> adapter;
    private TextView playingLabel, timeLabel, sectionTitle, statusLabel;
    private SeekBar seekBar;
    private Button playButton;
    private EditText search;
    private boolean onlineMode = false;
    private boolean seeking = false;
    private int currentIndex = -1;

    private final Runnable progressUpdater = new Runnable() {
        @Override public void run() {
            if (player != null && player.isPlaying() && !seeking) {
                seekBar.setProgress(player.getCurrentPosition());
                timeLabel.setText(formatTime(player.getCurrentPosition()) + " / " + formatTime(player.getDuration()));
            }
            handler.postDelayed(this, 500);
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(12, 14, 19));
        getWindow().setNavigationBarColor(Color.rgb(12, 14, 19));
        buildScreen();
        handler.post(progressUpdater);
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private TextView text(String value, int size, int color) {
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextSize(size);
        v.setTextColor(color);
        return v;
    }

    private Button makeButton(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        return b;
    }

    private void buildScreen() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(18), dp(16), dp(12));
        root.setBackgroundColor(Color.rgb(12, 14, 19));
        setContentView(root);

        TextView brand = text("MusicEcho", 30, Color.WHITE);
        brand.setTypeface(null, 1);
        root.addView(brand);
        TextView subtitle = text("Muzica ta, în ritmul tău.", 14, Color.rgb(170, 175, 185));
        root.addView(subtitle);

        LinearLayout tabs = new LinearLayout(this);
        tabs.setGravity(Gravity.CENTER);
        Button libraryTab = makeButton("Biblioteca mea");
        Button exploreTab = makeButton("Explorează online");
        tabs.addView(libraryTab, new LinearLayout.LayoutParams(0, dp(48), 1));
        tabs.addView(exploreTab, new LinearLayout.LayoutParams(0, dp(48), 1));
        LinearLayout.LayoutParams tabParams = new LinearLayout.LayoutParams(-1, -2);
        tabParams.topMargin = dp(12);
        root.addView(tabs, tabParams);

        Button addButton = makeButton("+ Adaugă melodii de pe telefon");
        addButton.setOnClickListener(v -> openAudioPicker());
        root.addView(addButton, new LinearLayout.LayoutParams(-1, dp(48)));

        search = new EditText(this);
        search.setSingleLine(true);
        search.setTextColor(Color.WHITE);
        search.setHintTextColor(Color.GRAY);
        search.setHint("Caută în biblioteca ta");
        search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(-1, dp(50));
        searchParams.topMargin = dp(8);
        root.addView(search, searchParams);

        Button searchOnlineButton = makeButton("Caută online");
        searchOnlineButton.setVisibility(View.GONE);
        root.addView(searchOnlineButton, new LinearLayout.LayoutParams(-1, dp(44)));

        sectionTitle = text("Biblioteca ta", 20, Color.WHITE);
        sectionTitle.setTypeface(null, 1);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-1, -2);
        titleParams.topMargin = dp(8);
        root.addView(sectionTitle, titleParams);

        statusLabel = text("Melodiile de pe telefon apar aici.", 12, Color.rgb(170, 175, 185));
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(-1, -2);
        statusParams.topMargin = dp(4);
        root.addView(statusLabel, statusParams);

        ListView list = new ListView(this);
        list.setDividerHeight(dp(1));
        list.setDivider(new android.graphics.drawable.ColorDrawable(Color.rgb(45, 48, 56)));
        list.setBackgroundColor(Color.TRANSPARENT);
        adapter = new ArrayAdapter<Song>(this, android.R.layout.simple_list_item_1, visibleSongs) {
            @Override public View getView(int position, View convertView, android.view.ViewGroup parent) {
                TextView row = (TextView) super.getView(position, convertView, parent);
                row.setTextColor(Color.WHITE);
                row.setTextSize(15);
                row.setPadding(dp(12), dp(12), dp(12), dp(12));
                row.setBackgroundColor(Color.TRANSPARENT);
                return row;
            }
        };
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) -> playSong(visibleSongs.get(position)));
        root.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));

        View separator = new View(this);
        separator.setBackgroundColor(Color.rgb(50, 54, 64));
        root.addView(separator, new LinearLayout.LayoutParams(-1, dp(1)));

        playingLabel = text("Nicio melodie selectată", 15, Color.WHITE);
        playingLabel.setTypeface(null, 1);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(-1, -2);
        labelParams.topMargin = dp(8);
        root.addView(playingLabel, labelParams);

        seekBar = new SeekBar(this);
        root.addView(seekBar, new LinearLayout.LayoutParams(-1, dp(32)));
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int value, boolean fromUser) {
                if (fromUser && player != null) timeLabel.setText(formatTime(value) + " / " + formatTime(player.getDuration()));
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { seeking = true; }
            @Override public void onStopTrackingTouch(SeekBar bar) {
                if (player != null) player.seekTo(bar.getProgress());
                seeking = false;
            }
        });

        LinearLayout controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER);
        Button previous = makeButton("|<");
        previous.setOnClickListener(v -> skip(-1));
        playButton = makeButton("Play");
        playButton.setOnClickListener(v -> togglePlayback());
        Button next = makeButton(">|");
        next.setOnClickListener(v -> skip(1));
        controls.addView(previous);
        controls.addView(playButton);
        controls.addView(next);
        root.addView(controls);

        timeLabel = text("0:00 / 0:00", 12, Color.rgb(170, 175, 185));
        timeLabel.setGravity(Gravity.CENTER);
        root.addView(timeLabel);

        libraryTab.setOnClickListener(v -> showLibrary(searchOnlineButton, addButton));
        exploreTab.setOnClickListener(v -> showExplore(searchOnlineButton, addButton));
        searchOnlineButton.setOnClickListener(v -> searchOnline());
        search.setOnEditorActionListener((v, actionId, event) -> {
            if (onlineMode) searchOnline();
            return onlineMode;
        });
        search.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (!onlineMode) filterLocal(s.toString());
            }
            @Override public void afterTextChanged(android.text.Editable e) {}
        });
    }

    private void showLibrary(Button onlineButton, Button addButton) {
        onlineMode = false;
        onlineButton.setVisibility(View.GONE);
        addButton.setVisibility(View.VISIBLE);
        search.setHint("Caută în biblioteca ta");
        sectionTitle.setText("Biblioteca ta");
        statusLabel.setText(localSongs.isEmpty() ? "Melodiile de pe telefon apar aici." : localSongs.size() + " melodii salvate pe telefon");
        filterLocal(search.getText().toString());
    }

    private void showExplore(Button onlineButton, Button addButton) {
        onlineMode = true;
        onlineButton.setVisibility(View.VISIBLE);
        addButton.setVisibility(View.GONE);
        search.setHint("Caută artist sau titlu");
        search.setText("");
        sectionTitle.setText("Muzică online");
        statusLabel.setText("Catalog cu înregistrări cu licență deschisă, prin Internet Archive.");
        visibleSongs.clear();
        adapter.notifyDataSetChanged();
    }

    private void filterLocal(String query) {
        visibleSongs.clear();
        String q = query.toLowerCase(Locale.ROOT).trim();
        for (Song song : localSongs) {
            if (song.title.toLowerCase(Locale.ROOT).contains(q)) visibleSongs.add(song);
        }
        adapter.notifyDataSetChanged();
    }

    private void searchOnline() {
        String term = search.getText().toString().trim();
        if (term.length() < 2) {
            statusLabel.setText("Scrie cel puțin două caractere pentru căutare.");
            return;
        }
        statusLabel.setText("Caut melodii online...");
        visibleSongs.clear();
        adapter.notifyDataSetChanged();

        new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                String safeTerm = term.replaceAll("[^\\p{L}\\p{N} ]", " ").trim();
                String query = "mediatype:audio AND licenseurl:http* AND (title:(" + safeTerm + ") OR creator:(" + safeTerm + "))";
                Uri api = Uri.parse("https://archive.org/advancedsearch.php").buildUpon()
                        .appendQueryParameter("q", query)
                        .appendQueryParameter("fl[]", "identifier")
                        .appendQueryParameter("fl[]", "title")
                        .appendQueryParameter("fl[]", "creator")
                        .appendQueryParameter("fl[]", "licenseurl")
                        .appendQueryParameter("rows", "35")
                        .appendQueryParameter("page", "1")
                        .appendQueryParameter("output", "json").build();
                connection = (HttpURLConnection) new URL(api.toString()).openConnection();
                connection.setConnectTimeout(12000);
                connection.setReadTimeout(15000);
                connection.setRequestProperty("User-Agent", "MusicEcho/1.1 (Android music player)");
                String response = readResponse(connection.getInputStream());
                JSONObject root = new JSONObject(response);
                JSONArray docs = root.getJSONObject("response").getJSONArray("docs");
                ArrayList<Song> found = new ArrayList<>();
                for (int i = 0; i < docs.length(); i++) {
                    JSONObject item = docs.getJSONObject(i);
                    String license = item.optString("licenseurl", "");
                    String lower = license.toLowerCase(Locale.ROOT);
                    boolean allowed = lower.contains("/publicdomain/zero/") ||
                            (lower.contains("creativecommons.org/licenses/by/") &&
                            !lower.contains("by-nc") && !lower.contains("by-nd") && !lower.contains("by-sa"));
                    if (!allowed) continue;
                    String identifier = item.optString("identifier", "");
                    if (identifier.isEmpty()) continue;
                    String title = item.optString("title", "Înregistrare audio");
                    String creator = item.optString("creator", "");
                    if (creator.contains("[")) creator = "";
                    found.add(new Song(title, creator, identifier, license));
                }
                runOnUiThread(() -> {
                    onlineSongs.clear();
                    onlineSongs.addAll(found);
                    visibleSongs.clear();
                    visibleSongs.addAll(found);
                    adapter.notifyDataSetChanged();
                    statusLabel.setText(found.isEmpty()
                            ? "Nu am găsit rezultate cu licență CC0 sau CC BY. Încearcă altă căutare."
                            : found.size() + " rezultate. Apasă pe o melodie pentru redare.");
                });
            } catch (Exception e) {
                runOnUiThread(() -> statusLabel.setText("Căutarea nu a reușit. Verifică internetul și încearcă din nou."));
            } finally {
                if (connection != null) connection.disconnect();
            }
        }).start();
    }

    private String readResponse(InputStream input) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(input, "UTF-8"));
        StringBuilder result = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) result.append(line);
        reader.close();
        return result.toString();
    }

    private void playSong(Song song) {
        if (player != null) { player.release(); player = null; }
        currentIndex = visibleSongs.indexOf(song);
        playingLabel.setText(song.title);
        if (song.online) {
            statusLabel.setText("Pregătesc redarea online...");
            loadOnlineSong(song);
            return;
        }
        startPlayer(song.uri);
    }

    private void loadOnlineSong(Song song) {
        new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL("https://archive.org/metadata/" + Uri.encode(song.identifier)).openConnection();
                connection.setConnectTimeout(12000);
                connection.setReadTimeout(15000);
                JSONObject metadata = new JSONObject(readResponse(connection.getInputStream()));
                JSONArray files = metadata.getJSONArray("files");
                String fileName = null;
                for (int i = 0; i < files.length(); i++) {
                    JSONObject file = files.getJSONObject(i);
                    String name = file.optString("name", "");
                    String format = file.optString("format", "").toLowerCase(Locale.ROOT);
                    if (name.toLowerCase(Locale.ROOT).endsWith(".mp3") &&
                            !name.contains("/") && !name.startsWith("__") &&
                            !file.optString("source", "").equals("metadata")) {
                        fileName = name;
                        break;
                    }
                    if (fileName == null && format.contains("mp3") && name.toLowerCase(Locale.ROOT).endsWith(".mp3")) fileName = name;
                }
                if (fileName == null) throw new Exception("No MP3 file");
                String stream = "https://archive.org/download/" + Uri.encode(song.identifier) + "/" + Uri.encode(fileName);
                String finalStream = stream;
                runOnUiThread(() -> {
                    song.streamUrl = finalStream;
                    statusLabel.setText("Se încarcă melodia...");
                    startPlayer(finalStream);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    statusLabel.setText("Nu am găsit un fișier MP3 redabil pentru această înregistrare.");
                    Toast.makeText(this, "Încearcă alt rezultat.", Toast.LENGTH_SHORT).show();
                });
            } finally {
                if (connection != null) connection.disconnect();
            }
        }).start();
    }

    private void startPlayer(Uri uri) {
        try {
            player = new MediaPlayer();
            player.setDataSource(this, uri);
            preparePlayer();
        } catch (Exception e) {
            showPlaybackError();
        }
    }

    private void startPlayer(String url) {
        try {
            player = new MediaPlayer();
            player.setDataSource(url);
            preparePlayer();
        } catch (Exception e) {
            showPlaybackError();
        }
    }

    private void preparePlayer() {
        player.setOnPreparedListener(mp -> {
            seekBar.setMax(mp.getDuration());
            mp.start();
            playButton.setText("Pause");
            statusLabel.setText("Redare în curs.");
        });
        player.setOnCompletionListener(mp -> skip(1));
        player.setOnErrorListener((mp, what, extra) -> {
            showPlaybackError();
            return true;
        });
        player.prepareAsync();
        playButton.setText("...");
    }

    private void showPlaybackError() {
        runOnUiThread(() -> {
            statusLabel.setText("Nu pot reda această melodie. Încearcă alt rezultat.");
            playButton.setText("Play");
        });
    }

    private void skip(int direction) {
        if (visibleSongs.isEmpty()) return;
        int next = currentIndex + direction;
        if (next >= 0 && next < visibleSongs.size()) playSong(visibleSongs.get(next));
    }

    private void openAudioPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("audio/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        startActivityForResult(intent, 100);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != 100 || resultCode != RESULT_OK || data == null) return;
        if (data.getClipData() != null) {
            for (int i = 0; i < data.getClipData().getItemCount(); i++) addSong(data.getClipData().getItemAt(i).getUri());
        } else if (data.getData() != null) addSong(data.getData());
        filterLocal(search.getText().toString());
        statusLabel.setText(localSongs.size() + " melodii salvate pe telefon");
    }

    private void addSong(Uri uri) {
        try { getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); }
        catch (Exception ignored) {}
        String name = "Melodie audio";
        Cursor cursor = getContentResolver().query(uri, null, null, null, null);
        if (cursor != null) {
            try {
                int column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (cursor.moveToFirst() && column >= 0) name = cursor.getString(column);
            } finally { cursor.close(); }
        }
        localSongs.add(new Song(uri, name));
    }

    private void togglePlayback() {
        if (player == null) {
            if (!visibleSongs.isEmpty()) playSong(visibleSongs.get(0));
            else Toast.makeText(this, "Selectează mai întâi o melodie.", Toast.LENGTH_SHORT).show();
        } else if (player.isPlaying()) {
            player.pause();
            playButton.setText("Play");
        } else {
            player.start();
            playButton.setText("Pause");
        }
    }

    private String formatTime(int millis) {
        int seconds = Math.max(0, millis / 1000);
        return String.format(Locale.getDefault(), "%d:%02d", seconds / 60, seconds % 60);
    }

    @Override protected void onDestroy() {
        handler.removeCallbacks(progressUpdater);
        if (player != null) { player.release(); player = null; }
        super.onDestroy();
    }
}
