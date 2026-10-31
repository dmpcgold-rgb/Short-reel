package at.dmpc.clipqueue;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.net.Uri;

import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;

import java.util.ArrayList;

/** Fullscreen swipe-style offline playlist player. */
public class PlayerActivity extends Activity {
    public static final String EXTRA_INDEX = "start_index";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private ExoPlayer player;
    private ArrayList<Clip> clips;
    private TextView title, counter;
    private Button pauseButton, repeatButton;
    private SeekBar seekBar;
    private boolean scrubbing;
    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            if (player != null) {
                long duration = player.getDuration();
                if (!scrubbing && duration > 0 && duration != androidx.media3.common.C.TIME_UNSET) {
                    seekBar.setProgress((int) Math.min(1000, (player.getCurrentPosition() * 1000L) / duration));
                }
                pauseButton.setText(player.isPlaying() ? "❚❚ Pause" : "▶ Weiter");
                handler.postDelayed(this, 400);
            }
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().setNavigationBarColor(Color.BLACK);
        clips = ClipStore.load(this);
        if (clips.isEmpty()) { Toast.makeText(this, "Playlist ist leer", Toast.LENGTH_SHORT).show(); finish(); return; }
        player = new ExoPlayer.Builder(this).build();
        ArrayList<MediaItem> playlist = new ArrayList<>();
        for (Clip clip : clips) {
            playlist.add(new MediaItem.Builder().setUri(Uri.parse(clip.uri)).setMediaId(clip.id).build());
        }
        int start = Math.max(0, Math.min(getIntent().getIntExtra(EXTRA_INDEX, 0), clips.size() - 1));
        player.setMediaItems(playlist, start, 0);
        player.setRepeatMode(Player.REPEAT_MODE_OFF);
        player.addListener(new Player.Listener() {
            @Override public void onMediaItemTransition(MediaItem item, int reason) { updateLabels(); }
            @Override public void onIsPlayingChanged(boolean playing) { if (pauseButton != null) pauseButton.setText(playing ? "❚❚ Pause" : "▶ Weiter"); }
            @Override public void onPlayerError(PlaybackException error) {
                Toast.makeText(PlayerActivity.this, "Video nicht verfügbar – über ›Nächstes‹ überspringen", Toast.LENGTH_LONG).show();
            }
        });

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        setContentView(root);
        PlayerView surface = new PlayerView(this);
        surface.setUseController(false);
        surface.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_ZOOM);
        surface.setPlayer(player);
        root.addView(surface, new FrameLayout.LayoutParams(-1, -1));

        View gesture = new View(this);
        final float[] downY = {0f};
        final float[] downX = {0f};
        gesture.setOnTouchListener((view, e) -> {
            if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
                downY[0] = e.getY(); downX[0] = e.getX();
                return true;
            }
            if (e.getActionMasked() == MotionEvent.ACTION_UP) {
                float dy = e.getY() - downY[0];
                float dx = e.getX() - downX[0];
                if (Math.abs(dy) > dp(65) && Math.abs(dy) > Math.abs(dx) * 1.2f) {
                    if (dy < 0) next(); else previous();
                } else if (Math.abs(dy) < dp(12) && Math.abs(dx) < dp(12)) {
                    togglePause();
                    view.performClick();
                }
                return true;
            }
            return true;
        });
        root.addView(gesture, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setPadding(dp(12), dp(18), dp(12), dp(12));
        top.setBackground(gradient(0xE0000000, 0x00000000, true));
        Button back = control("‹ Zurück");
        back.setOnClickListener(v -> finish());
        top.addView(back);
        LinearLayout heading = new LinearLayout(this);
        heading.setOrientation(LinearLayout.VERTICAL);
        heading.setPadding(dp(12), 0, 0, 0);
        title = new TextView(this);
        title.setTextColor(Color.WHITE);
        title.setTextSize(15);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        heading.addView(title);
        counter = new TextView(this);
        counter.setTextSize(12);
        counter.setTextColor(0xFF70E7C6);
        heading.addView(counter);
        top.addView(heading, new LinearLayout.LayoutParams(0, -2, 1));
        FrameLayout.LayoutParams tp = new FrameLayout.LayoutParams(-1, -2, Gravity.TOP);
        root.addView(top, tp);

        LinearLayout bottom = new LinearLayout(this);
        bottom.setPadding(dp(14), dp(30), dp(14), dp(30));
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setBackground(gradient(0x00000000, 0xE0000000, false));
        TextView tip = new TextView(this);
        tip.setText("↑ Nächstes Video       ↓ Vorheriges Video");
        tip.setTextColor(0xFFCDD6E1);
        tip.setTextSize(12);
        tip.setGravity(Gravity.CENTER);
        bottom.addView(tip);
        seekBar = new SeekBar(this);
        seekBar.setMax(1000);
        seekBar.setProgressTintList(android.content.res.ColorStateList.valueOf(0xFF70E7C6));
        seekBar.setThumbTintList(android.content.res.ColorStateList.valueOf(0xFF70E7C6));
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onStartTrackingTouch(SeekBar b) { scrubbing = true; }
            @Override public void onStopTrackingTouch(SeekBar b) {
                long duration = player.getDuration();
                if (duration > 0) player.seekTo((duration * b.getProgress()) / 1000L);
                scrubbing = false;
            }
            @Override public void onProgressChanged(SeekBar b, int p, boolean user) {}
        });
        bottom.addView(seekBar, new LinearLayout.LayoutParams(-1, dp(38)));
        LinearLayout controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER);
        Button prev = control("❮ Zurück");
        pauseButton = control("❚❚ Pause");
        Button next = control("Weiter ❯");
        prev.setOnClickListener(v -> previous());
        pauseButton.setOnClickListener(v -> togglePause());
        next.setOnClickListener(v -> next());
        for (Button b : new Button[]{prev, pauseButton, next}) {
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(0, dp(52), 1);
            bp.setMargins(dp(3), 0, dp(3), 0);
            controls.addView(b, bp);
        }
        bottom.addView(controls);
        repeatButton = control("↻ Wiederholen: Aus");
        LinearLayout.LayoutParams repeatParams = new LinearLayout.LayoutParams(-1, dp(42));
        repeatParams.topMargin = dp(14);
        bottom.addView(repeatButton, repeatParams);
        repeatButton.setOnClickListener(v -> {
            boolean all = player.getRepeatMode() != Player.REPEAT_MODE_ALL;
            player.setRepeatMode(all ? Player.REPEAT_MODE_ALL : Player.REPEAT_MODE_OFF);
            repeatButton.setText(all ? "↻ Playlist wiederholen: An" : "↻ Wiederholen: Aus");
        });
        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        root.addView(bottom, bp);

        updateLabels();
        player.prepare();
        player.play();
        handler.post(ticker);
    }

    private void updateLabels() {
        int i = player.getCurrentMediaItemIndex();
        if (title != null && i >= 0 && i < clips.size()) {
            title.setText(clips.get(i).title);
            counter.setText((i + 1) + " / " + clips.size() + " · Automatische Wiedergabe");
            if (seekBar != null) seekBar.setProgress(0);
        }
    }

    private void next() {
        if (player == null) return;
        if (player.hasNextMediaItem()) player.seekToNextMediaItem();
        else if (player.getRepeatMode() == Player.REPEAT_MODE_ALL) player.seekTo(0, 0);
        else Toast.makeText(this, "Ende der Playlist", Toast.LENGTH_SHORT).show();
        player.play();
    }

    private void previous() {
        if (player == null) return;
        if (player.hasPreviousMediaItem()) player.seekToPreviousMediaItem();
        else player.seekTo(0);
        player.play();
    }

    private void togglePause() {
        if (player == null) return;
        if (player.isPlaying()) player.pause(); else player.play();
    }

    private Button control(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(13);
        button.setAllCaps(false);
        button.setTextColor(Color.WHITE);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xBB263246);
        bg.setCornerRadius(dp(13));
        button.setBackground(bg);
        return button;
    }

    private GradientDrawable gradient(int a, int b, boolean top) {
        return new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{a, b});
    }

    private int dp(int dp) { return Math.round(dp * getResources().getDisplayMetrics().density); }

    @Override protected void onPause() {
        if (player != null) player.pause();
        super.onPause();
    }
    @Override protected void onDestroy() {
        handler.removeCallbacks(ticker);
        if (player != null) { player.release(); player = null; }
        super.onDestroy();
    }
}
