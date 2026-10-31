package at.dmpc.clipqueue;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;

/** Streams direct, authorized HTTPS video files only; does not open platform web pages. */
public final class OnlineVideoActivity extends Activity {
    public static final String EXTRA_URL = "at.dmpc.clipqueue.ONLINE_VIDEO_URL";
    private ExoPlayer player;
    private String videoUrl;
    private TextView stateLabel;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        videoUrl = getIntent().getStringExtra(EXTRA_URL);
        if (videoUrl == null || !UrlRules.isDirectVideoCandidate(videoUrl)) {
            Toast.makeText(this, "Nur freigegebene direkte HTTPS-Videodateien", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().setNavigationBarColor(Color.BLACK);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        setContentView(root);

        player = new ExoPlayer.Builder(this).build();
        PlayerView videoView = new PlayerView(this);
        videoView.setUseController(false);
        videoView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
        videoView.setPlayer(player);
        root.addView(videoView, new FrameLayout.LayoutParams(-1, -1));

        FrameLayout touchLayer = new FrameLayout(this);
        GestureDetector gestures = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(android.view.MotionEvent e) { return true; }
            @Override public boolean onSingleTapConfirmed(android.view.MotionEvent e) {
                if (player != null) {
                    if (player.isPlaying()) player.pause(); else player.play();
                }
                return true;
            }
            @Override public void onLongPress(android.view.MotionEvent e) {
                offerDownload();
            }
        });
        touchLayer.setOnTouchListener((view, event) -> {
            gestures.onTouchEvent(event);
            return true;
        });
        touchLayer.setContentDescription("Online-Video. Lange drücken zum Herunterladen.");
        root.addView(touchLayer, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(12), dp(18), dp(12), dp(12));
        top.setBackground(shade(0xDB000000, 0x00000000));
        Button back = control("‹ Zurück");
        back.setOnClickListener(v -> finish());
        top.addView(back, new LinearLayout.LayoutParams(dp(95), dp(45)));
        TextView heading = new TextView(this);
        heading.setText("ONLINE-VIDEO");
        heading.setTextColor(Color.WHITE);
        heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        heading.setTextSize(14);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        heading.setPadding(dp(14), 0, 0, 0);
        top.addView(heading);
        root.addView(top, new FrameLayout.LayoutParams(-1, -2, Gravity.TOP));

        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setPadding(dp(14), dp(22), dp(14), dp(26));
        bottom.setBackground(shade(0x00000000, 0xDB000000));
        stateLabel = new TextView(this);
        stateLabel.setTextColor(0xFFCAD6E4);
        stateLabel.setTextSize(13);
        stateLabel.setText("Lade Videodatei ...");
        stateLabel.setGravity(Gravity.CENTER);
        bottom.addView(stateLabel);
        TextView hint = new TextView(this);
        hint.setText("Lange drücken: Video herunterladen");
        hint.setTextColor(0xFF70E7C6);
        hint.setTextSize(13);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, dp(8), 0, dp(8));
        bottom.addView(hint);

        LinearLayout controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER);
        Button pause = control("❚❚ Pause / ▶ Weiter");
        pause.setOnClickListener(v -> {
            if (player != null) {
                if (player.isPlaying()) player.pause(); else player.play();
            }
        });
        Button download = control("↓ Download");
        download.setOnClickListener(v -> offerDownload());
        controls.addView(pause, new LinearLayout.LayoutParams(0, dp(50), 1));
        controls.addView(download, new LinearLayout.LayoutParams(0, dp(50), 1));
        bottom.addView(controls);
        root.addView(bottom, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));

        player.addListener(new Player.Listener() {
            @Override public void onPlaybackStateChanged(int state) {
                if (stateLabel == null) return;
                if (state == Player.STATE_READY) stateLabel.setText("Online-Wiedergabe bereit");
                else if (state == Player.STATE_BUFFERING) stateLabel.setText("Video wird geladen ...");
                else if (state == Player.STATE_ENDED) stateLabel.setText("Video beendet");
            }
            @Override public void onPlayerError(PlaybackException error) {
                if (stateLabel != null) {
                    stateLabel.setText("Keine abspielbare Videodatei. Prüfe den direkten Link.");
                }
                Toast.makeText(OnlineVideoActivity.this,
                        "Kein direkter, abspielbarer Videostream", Toast.LENGTH_LONG).show();
            }
        });
        player.setMediaItem(MediaItem.fromUri(Uri.parse(videoUrl)));
        player.prepare();
        player.play();
    }

    private void offerDownload() {
        if (videoUrl == null || !UrlRules.isDirectVideoCandidate(videoUrl)) return;
        new AlertDialog.Builder(this)
                .setTitle("Video herunterladen")
                .setMessage("Nur wenn diese Videodatei zum Download freigegeben ist, kannst du eine Kopie unter Filme/ClipQueue speichern. Geschützte ReelShort-Folgen werden nicht umgangen.")
                .setNegativeButton("Abbrechen", null)
                .setPositiveButton("Herunterladen", (dialog, which) -> {
                    Intent intent = new Intent(this, MainActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    intent.putExtra(MainActivity.EXTRA_DOWNLOAD_DIRECT_VIDEO, videoUrl);
                    startActivity(intent);
                    finish();
                }).show();
    }

    private Button control(String label) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setText(label);
        b.setTextColor(Color.WHITE);
        b.setTextSize(13);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xC3263445);
        bg.setCornerRadius(dp(12));
        b.setBackground(bg);
        return b;
    }

    private GradientDrawable shade(int start, int end) {
        return new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[] {start, end});
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    @Override protected void onPause() {
        if (player != null) player.pause();
        super.onPause();
    }

    @Override protected void onDestroy() {
        if (player != null) { player.release(); player = null; }
        super.onDestroy();
    }
}
