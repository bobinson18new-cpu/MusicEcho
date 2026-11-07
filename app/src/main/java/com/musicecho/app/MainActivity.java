package com.musicecho.app;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Locale;

public class MainActivity extends Activity {
    private static class Song {
        Uri uri;
        String title;
        Song(Uri uri, String title) { this.uri = uri; this.title = title; }
        @Override public String toString() { return title; }
    }

    private final ArrayList<Song> songs = new ArrayList<>();
    private final Handler handler = new Handler();
    private MediaPlayer player;
    private ArrayAdapter<Song> adapter;
    private TextView playingLabel, timeLabel;
    private SeekBar seekBar;
    private Button playButton;
    private int currentIndex = -1;
    private boolean seeking = false;

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

    private void buildScreen() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(22), dp(20), dp(16));
        root.setBackgroundColor(Color.rgb(12, 14, 19));
        setContentView(root);

        TextView brand = text("MusicEcho", 30, Color.WHITE);
        brand.setTypeface(null, 1);
        root.addView(brand);

        TextView subtitle = text("Muzica ta, în ritmul tău.", 14, Color.rgb(170, 175, 185));
        LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(-1, -2);
        subParams.bottomMargin = dp(18);
        root.addView(subtitle, subParams);

        Button addButton = new Button(this);
        addButton.setText("+  Adaugă melodii");
        addButton.setAllCaps(false);
        addButton.setOnClickListener(v -> openAudioPicker());
        root.addView(addButton, new LinearLayout.LayoutParams(-1, dp(52)));

        EditText search = new EditText(this);
        search.setSingleLine(true);
        search.setHint("Caută în biblioteca ta");
        search.setHintTextColor(Color.GRAY);
        search.setTextColor(Color.WHITE);
        search.setCompoundDrawablePadding(dp(8));
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(-1, dp(52));
        searchParams.topMargin = dp(12);
        searchParams.bottomMargin = dp(8);
        root.addView(search, searchParams);

        TextView libraryTitle = text("Biblioteca ta", 20, Color.WHITE);
        libraryTitle.setTypeface(null, 1);
        root.addView(libraryTitle);

        ListView list = new ListView(this);
        list.setDividerHeight(dp(1));
        list.setDivider(new android.graphics.drawable.ColorDrawable(Color.rgb(45, 48, 56)));
        list.setBackgroundColor(Color.TRANSPARENT);
        adapter = new ArrayAdapter<Song>(this, android.R.layout.simple_list_item_1, songs) {
            @Override public View getView(int position, View convertView, android.view.ViewGroup parent) {
                TextView row = (TextView) super.getView(position, convertView, parent);
                row.setTextColor(Color.WHITE);
                row.setTextSize(16);
                row.setPadding(dp(12), dp(12), dp(12), dp(12));
                row.setBackgroundColor(Color.TRANSPARENT);
                return row;
            }
        };
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) -> playSong(position));
        root.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));

        View separator = new View(this);
        separator.setBackgroundColor(Color.rgb(50, 54, 64));
        LinearLayout.LayoutParams sepParams = new LinearLayout.LayoutParams(-1, dp(1));
        sepParams.topMargin = dp(10);
        root.addView(separator, sepParams);

        playingLabel = text("Nicio melodie selectată", 15, Color.WHITE);
        playingLabel.setTypeface(null, 1);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(-1, -2);
        labelParams.topMargin = dp(12);
        root.addView(playingLabel, labelParams);

        seekBar = new SeekBar(this);
        root.addView(seekBar, new LinearLayout.LayoutParams(-1, dp(34)));
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
        Button previous = new Button(this);
        previous.setText("|<");
        previous.setOnClickListener(v -> playSong(Math.max(0, currentIndex - 1)));
        playButton = new Button(this);
        playButton.setText("Play");
        playButton.setOnClickListener(v -> togglePlayback());
        Button next = new Button(this);
        next.setText(">|");
        next.setOnClickListener(v -> playSong(Math.min(songs.size() - 1, currentIndex + 1)));
        controls.addView(previous);
        controls.addView(playButton);
        controls.addView(next);
        root.addView(controls);

        timeLabel = text("0:00 / 0:00", 12, Color.rgb(170, 175, 185));
        timeLabel.setGravity(Gravity.CENTER);
        root.addView(timeLabel);
        
        search.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { adapter.getFilter().filter(s); }
            @Override public void afterTextChanged(android.text.Editable e) {}
        });
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
        } else if (data.getData() != null) {
            addSong(data.getData());
        }
        adapter.notifyDataSetChanged();
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
        songs.add(new Song(uri, name));
    }

    private void playSong(int index) {
        if (index < 0 || index >= songs.size()) return;
        if (player != null) { player.release(); player = null; }
        currentIndex = index;
        Song song = songs.get(index);
        playingLabel.setText(song.title);
        try {
            player = new MediaPlayer();
            player.setDataSource(this, song.uri);
            player.setOnPreparedListener(mp -> {
                seekBar.setMax(mp.getDuration());
                mp.start();
                playButton.setText("Pause");
            });
            player.setOnCompletionListener(mp -> {
                if (currentIndex + 1 < songs.size()) playSong(currentIndex + 1);
                else playButton.setText("Play");
            });
            player.setOnErrorListener((mp, what, extra) -> {
                Toast.makeText(this, "Nu pot reda acest fișier audio.", Toast.LENGTH_SHORT).show();
                playButton.setText("Play");
                return true;
            });
            player.prepareAsync();
            playButton.setText("...");
        } catch (IOException e) {
            Toast.makeText(this, "Fișierul nu poate fi deschis.", Toast.LENGTH_SHORT).show();
        }
    }

    private void togglePlayback() {
        if (player == null) {
            if (!songs.isEmpty()) playSong(0);
            else Toast.makeText(this, "Adaugă mai întâi melodii.", Toast.LENGTH_SHORT).show();
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
