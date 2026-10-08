package com.codetone.impro;

import android.app.Activity;
import android.content.ClipData;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class MainActivity extends Activity {
    private static final int BG = Color.rgb(16, 21, 19), PANEL = Color.rgb(29, 37, 32);
    private static final int GREEN = Color.rgb(194, 238, 140), TEXT = Color.rgb(238, 243, 235);
    private static final int MUTED = Color.rgb(148, 166, 151), INK = Color.rgb(26, 43, 23);
    private static final String DEFAULT = "Cmaj7 | Am7 | Dm7 | G7";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<View> editControls = new ArrayList<>();
    private final List<LinearLayout> barCards = new ArrayList<>();
    private final List<TextView> beatDots = new ArrayList<>();
    private AudioPlayer player;
    private SharedPreferences preferences;
    private MusicTheory.Progression progression;
    private ProgressionEditor editor;
    private TextView currentChord, location, nextChord, status, error, tempoLabel, barSummary, selectionLabel;
    private KeyboardView keyboard;
    private LinearLayout beatRow;
    private GridLayout barGrid;
    private Button playButton, meter3, meter4, addBarButton, duplicateButton, deleteButton, undoButton;
    private ScrollView scroll;
    private ChordPickerDialog picker;
    private BarDrag activeDrag;
    private int dragScrollDirection;
    private boolean dragCommitted;
    private SeekBar tempo;
    private Switch loop, metronome;
    private int bpm = 96, beats = 4, highlightedBar = -1, selectedBar;
    private String shownChord = "";
    private boolean playing;
    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            if (player != null && player.isPlaying()) {
                updatePosition(player.playbackFrames());
                handler.postDelayed(this, 30);
            }
        }
    };
    private final BroadcastReceiver noisyReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { player.stop(); }
    };
    private final Runnable dragScroller = new Runnable() {
        @Override public void run() {
            if (activeDrag == null || dragScrollDirection == 0) return;
            scroll.scrollBy(0, dragScrollDirection * dp(10));
            handler.postDelayed(this, 25);
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        preferences = getSharedPreferences("impro", MODE_PRIVATE);
        bpm = Math.max(40, Math.min(240, preferences.getInt("bpm", 96)));
        beats = preferences.getInt("beats", 4) == 3 ? 3 : 4;
        String loadError = null;
        try { editor = new ProgressionEditor(preferences.getString("progression", DEFAULT)); }
        catch (IllegalArgumentException invalidDraft) {
            editor = new ProgressionEditor(DEFAULT);
            loadError = "저장된 코드 진행을 읽을 수 없어 기본 진행을 열었습니다.";
        }
        player = new AudioPlayer(this, (completed, message) -> {
            setPlaying(false);
            if (completed && progression != null) {
                long last = Math.round(progression.bars.size() * beats * AudioRenderer.SAMPLE_RATE * 60.0 / bpm) - 1;
                updatePosition(last);
                status.setText("재생 완료 · 처음부터 다시 연습해 보세요");
            } else status.setText("준비됨 · 재생 버튼으로 시작하세요");
            if (message != null) showError(message);
        });
        buildScreen();
        progression = editor.progression();
        rebuildBars();
        previewSelectedBar();
        updateEditActions();
        if (loadError != null) showError(loadError);
    }

    private void buildScreen() {
        FrameLayout frame = new FrameLayout(this);
        frame.setBackgroundColor(BG);
        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setOnDragListener((view, event) -> {
            if (event.getAction() == DragEvent.ACTION_DRAG_ENDED) { finishDrag(); return true; }
            if (!acceptDrag(event)) return false;
            if (event.getAction() == DragEvent.ACTION_DRAG_LOCATION) updateDragScroll(view, event.getY());
            return event.getAction() != DragEvent.ACTION_DROP;
        });
        LinearLayout content = column();
        content.setFocusableInTouchMode(true);
        content.setPadding(dp(20), dp(22), dp(20), dp(28));
        scroll.addView(content);
        frame.addView(scroll);
        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
            frame.setOnApplyWindowInsetsListener((view, insets) -> {
                android.graphics.Insets safe = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
                view.setPadding(safe.left, safe.top, safe.right, safe.bottom);
                return insets;
            });
        } else frame.setFitsSystemWindows(true);
        setContentView(frame);

        LinearLayout titleRow = row();
        TextView title = text("impro.", 34, TEXT);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        titleRow.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        TextView badge = text("CHORD PRACTICE", 10, GREEN);
        badge.setLetterSpacing(.13f);
        titleRow.addView(badge);
        content.addView(titleRow);
        TextView tagline = text("터치로 코드를 만들고, 드래그로 순서를 바꾸세요.", 14, MUTED);
        add(content, tagline, 4);

        LinearLayout now = column();
        now.setPadding(dp(20), dp(18), dp(20), dp(18));
        now.setBackground(surface(GREEN, GREEN, 20));
        location = text("", 12, INK);
        location.setTypeface(null, Typeface.BOLD);
        now.addView(location);
        currentChord = text("Cmaj7", 48, INK);
        currentChord.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        currentChord.setSingleLine(true);
        currentChord.setAutoSizeTextTypeUniformWithConfiguration(24, 48, 1, android.util.TypedValue.COMPLEX_UNIT_SP);
        add(now, currentChord, 8);
        beatRow = row();
        add(now, beatRow, 12);
        nextChord = text("", 12, INK);
        add(now, nextChord, 14);
        add(content, now, 22);

        keyboard = new KeyboardView(this);
        LinearLayout.LayoutParams keysLayout = new LinearLayout.LayoutParams(-1, dp(60));
        keysLayout.topMargin = dp(12);
        content.addView(keyboard, keysLayout);
        status = text("준비됨 · 재생 버튼으로 시작하세요", 12, MUTED);
        add(content, status, 10);

        LinearLayout transport = row();
        playButton = button("▶  재생", true);
        playButton.setOnClickListener(view -> togglePlayback());
        transport.addView(playButton, new LinearLayout.LayoutParams(0, dp(54), 2));
        Button reset = button("처음으로", false);
        reset.setOnClickListener(view -> { player.stop(); selectedBar = 0; updatePosition(0); updateEditActions(); status.setText("준비됨 · 재생 버튼으로 시작하세요"); });
        LinearLayout.LayoutParams resetLayout = new LinearLayout.LayoutParams(0, dp(54), 1);
        resetLayout.leftMargin = dp(10);
        transport.addView(reset, resetLayout);
        add(content, transport, 14);

        LinearLayout settings = column();
        settings.setPadding(dp(16), dp(14), dp(16), dp(10));
        settings.setBackground(surface(PANEL, Color.rgb(45, 58, 48), 16));
        LinearLayout tempoRow = row();
        tempoRow.addView(text("템포", 14, TEXT), new LinearLayout.LayoutParams(0, -2, 1));
        Button slower = button("−", false);
        slower.setContentDescription("템포 1 BPM 낮추기");
        slower.setOnClickListener(view -> tempo.setProgress(Math.max(0, tempo.getProgress() - 1)));
        tempoRow.addView(slower, new LinearLayout.LayoutParams(dp(48), dp(48)));
        tempoLabel = text("", 21, GREEN);
        tempoLabel.setGravity(Gravity.CENTER);
        tempoRow.addView(tempoLabel, new LinearLayout.LayoutParams(dp(82), -2));
        Button faster = button("+", false);
        faster.setContentDescription("템포 1 BPM 높이기");
        faster.setOnClickListener(view -> tempo.setProgress(Math.min(200, tempo.getProgress() + 1)));
        tempoRow.addView(faster, new LinearLayout.LayoutParams(dp(48), dp(48)));
        settings.addView(tempoRow);
        tempo = new SeekBar(this);
        tempo.setMax(200);
        tempo.setProgress(bpm - 40);
        tempo.setProgressTintList(ColorStateList.valueOf(GREEN));
        tempo.setThumbTintList(ColorStateList.valueOf(GREEN));
        tempo.setContentDescription("템포, 40에서 240 BPM");
        tempo.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int value, boolean fromUser) {
                bpm = value + 40;
                tempoLabel.setText(bpm + " BPM");
            }
            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) { saveSettings(); }
        });
        tempoLabel.setText(bpm + " BPM");
        settings.addView(tempo, new LinearLayout.LayoutParams(-1, dp(48)));
        LinearLayout meterRow = row();
        meterRow.addView(text("박자", 14, TEXT), new LinearLayout.LayoutParams(0, -2, 1));
        meter3 = button("3/4", false);
        meter4 = button("4/4", false);
        meter3.setOnClickListener(view -> changeMeter(3));
        meter4.setOnClickListener(view -> changeMeter(4));
        meterRow.addView(meter3, new LinearLayout.LayoutParams(dp(64), dp(48)));
        LinearLayout.LayoutParams meterLayout = new LinearLayout.LayoutParams(dp(64), dp(48));
        meterLayout.leftMargin = dp(8);
        meterRow.addView(meter4, meterLayout);
        settings.addView(meterRow);
        loop = option("반복 재생", preferences.getBoolean("loop", true));
        metronome = option("메트로놈", preferences.getBoolean("metronome", true));
        add(settings, loop, 6);
        settings.addView(metronome, new LinearLayout.LayoutParams(-1, dp(48)));
        add(content, settings, 22);
        java.util.Collections.addAll(editControls, slower, faster, tempo, meter3, meter4, loop, metronome);
        changeMeter(beats);

        LinearLayout editorTitle = row();
        editorTitle.addView(text("코드 진행", 18, TEXT), new LinearLayout.LayoutParams(0, -2, 1));
        undoButton = button("↶ 되돌리기", false);
        undoButton.setOnClickListener(view -> { if (!playing && editor.undo()) commitEdit("편집을 되돌렸습니다."); });
        editorTitle.addView(undoButton, new LinearLayout.LayoutParams(dp(104), dp(48)));
        add(content, editorTitle, 24);
        error = text("", 13, Color.rgb(255, 184, 131));
        error.setVisibility(View.GONE);
        add(content, error, 8);
        TextView hint = text("마디 터치 → 코드 선택\n카드를 길게 눌러 원하는 마디 위에 놓으면 순서 이동", 12, MUTED);
        hint.setLineSpacing(dp(3), 1);
        add(content, hint, 6);

        barSummary = text("마디", 13, MUTED);
        add(content, barSummary, 12);
        barGrid = new GridLayout(this);
        barGrid.setColumnCount(4);
        add(content, barGrid, 10);
        selectionLabel = text("", 12, MUTED);
        add(content, selectionLabel, 4);
        LinearLayout actions = row();
        addBarButton = button("+ 마디", true);
        addBarButton.setOnClickListener(view -> openBarPicker(editor.size(), true));
        duplicateButton = button("복제", false);
        duplicateButton.setOnClickListener(view -> performEdit(() -> { editor.duplicateBar(selectedBar); selectedBar++; }, "마디를 복제했습니다."));
        deleteButton = button("삭제", false);
        deleteButton.setOnClickListener(view -> performEdit(() -> editor.removeBar(selectedBar), "마디를 삭제했습니다."));
        Button[] actionButtons = {addBarButton, duplicateButton, deleteButton};
        for (int i = 0; i < actionButtons.length; i++) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(48), 1);
            if (i > 0) lp.leftMargin = dp(8);
            actions.addView(actionButtons[i], lp);
        }
        add(content, actions, 10);
        java.util.Collections.addAll(editControls, addBarButton, duplicateButton, deleteButton, undoButton);

        LinearLayout presets = row();
        String[] names = {"ii–V–I", "팝 4마디", "블루스"};
        String[] values = {"Dm7 | G7 | Cmaj7 | Cmaj7", DEFAULT,
                "C7 | F7 | C7 | C7\nF7 | F7 | C7 | C7\nG7 | F7 | C7 | G7"};
        for (int i = 0; i < names.length; i++) {
            Button preset = button(names[i], false);
            String value = values[i];
            preset.setOnClickListener(view -> performEdit(() -> { editor.replace(value); selectedBar = 0; }, "프리셋을 적용했습니다."));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(48), 1);
            if (i > 0) lp.leftMargin = dp(8);
            presets.addView(preset, lp);
            editControls.add(preset);
        }
        add(content, presets, 14);
        content.removeView(settings);
        add(content, settings, 22);
        TextView credit = text("내장 피아노 + 베이스 · 오프라인 재생\nInspired by Impro-Visor · 독립 구현", 11, MUTED);
        credit.setGravity(Gravity.CENTER);
        credit.setLineSpacing(dp(3), 1);
        add(content, credit, 24);
    }

    private void togglePlayback() {
        if (playing) { player.stop(); return; }
        if (activeDrag != null) return;
        selectedBar = 0;
        updatePosition(0);
        saveSettings();
        try {
            player.start(progression, bpm, beats, loop.isChecked(), metronome.isChecked());
            setPlaying(true);
            status.setText("재생 중 · 1회차");
            handler.post(ticker);
        } catch (IllegalStateException problem) {
            showError(problem.getMessage());
            setPlaying(false);
        }
    }

    private void performEdit(Runnable action, String message) {
        if (playing) return;
        try {
            action.run();
            commitEdit(message);
        } catch (IllegalArgumentException problem) {
            showError(problem.getMessage());
        }
    }

    private void commitEdit(String message) {
        progression = editor.progression();
        selectedBar = Math.max(0, Math.min(selectedBar, editor.size() - 1));
        error.setVisibility(View.GONE);
        rebuildBars();
        previewSelectedBar();
        updateEditActions();
        saveSettings();
        status.setText(message);
    }

    private void openBarPicker(int index, boolean adding) {
        if (playing || activeDrag != null || (adding && editor.size() >= MusicTheory.MAX_BARS)) return;
        if (!adding) { selectedBar = index; previewSelectedBar(); updateEditActions(); }
        long revision = editor.revision();
        List<String> chords = adding ? java.util.Collections.singletonList("C") : editor.bar(index);
        picker = new ChordPickerDialog(this, index, chords, beats, adding, values -> {
            if (playing || editor.revision() != revision) return;
            performEdit(() -> {
                if (adding) editor.insertBar(index, values); else editor.setBar(index, values);
                selectedBar = index;
            }, adding ? "마디를 추가했습니다." : "코드를 저장했습니다.");
        });
        picker.show();
    }

    private void previewSelectedBar() {
        long frame = Math.round(selectedBar * beats * AudioRenderer.SAMPLE_RATE * 60.0 / bpm);
        updatePosition(frame == 0 ? 0 : frame + 1);
    }

    private void updateEditActions() {
        if (addBarButton == null) return;
        enabled(addBarButton, !playing && editor.size() < MusicTheory.MAX_BARS);
        enabled(duplicateButton, !playing && editor.size() < MusicTheory.MAX_BARS);
        enabled(deleteButton, !playing && editor.size() > 1);
        enabled(undoButton, !playing && editor.canUndo());
        selectionLabel.setText((selectedBar + 1) + "마디 선택됨 · 복제 / 삭제 대상");
    }
    private void enabled(View view, boolean enabled) { view.setEnabled(enabled); view.setAlpha(enabled ? 1 : .4f); }

    private void showError(String message) {
        error.setText(message);
        error.setVisibility(View.VISIBLE);
        error.announceForAccessibility(message);
    }

    private void setPlaying(boolean value) {
        playing = value;
        playButton.setText(value ? "■  정지" : "▶  재생");
        for (View control : editControls) { control.setEnabled(!value); control.setAlpha(value ? .5f : 1f); }
        for (View card : barCards) { card.setEnabled(!value); card.setAlpha(value ? .7f : 1); }
        updateEditActions();
        if (value) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            handler.removeCallbacks(ticker);
        }
    }

    private void changeMeter(int value) {
        beats = value;
        if (meter3 == null) return;
        styleButton(meter3, value == 3);
        styleButton(meter4, value == 4);
        beatRow.removeAllViews();
        beatDots.clear();
        for (int i = 0; i < beats; i++) {
            TextView dot = text(Integer.toString(i + 1), 14, INK);
            dot.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(34), dp(34));
            lp.rightMargin = dp(9);
            beatRow.addView(dot, lp);
            beatDots.add(dot);
        }
        if (progression != null) previewSelectedBar();
    }

    private void rebuildBars() {
        barGrid.removeAllViews();
        barCards.clear();
        highlightedBar = -1;
        shownChord = "";
        barSummary.setText(progression.bars.size() + " / 64마디");
        for (int i = 0; i < progression.bars.size(); i++) {
            LinearLayout card = column();
            card.setPadding(dp(8), dp(10), dp(8), dp(10));
            TextView number = text(String.format(Locale.US, "%02d        ⠿", i + 1), 11, MUTED);
            card.addView(number);
            TextView chord = text(progression.bars.get(i).label(), 16, TEXT);
            chord.setTypeface(null, Typeface.BOLD);
            chord.setMaxLines(3);
            chord.setAutoSizeTextTypeUniformWithConfiguration(9, 16, 1, android.util.TypedValue.COMPLEX_UNIT_SP);
            LinearLayout.LayoutParams chordLayout = new LinearLayout.LayoutParams(-1, 0, 1);
            chordLayout.topMargin = dp(5);
            card.addView(chord, chordLayout);
            card.setContentDescription((i + 1) + "마디 " + progression.bars.get(i).label() + ", 터치하여 코드 선택, 길게 눌러 순서 이동");
            final int index = i;
            card.setFocusable(true);
            card.setOnClickListener(view -> openBarPicker(index, false));
            card.setOnLongClickListener(view -> startBarDrag(card, index));
            card.setOnDragListener((view, event) -> onBarDrag(view, event, index));
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams(GridLayout.spec(i / 4), GridLayout.spec(i % 4, 1f));
            lp.width = 0;
            lp.height = dp(96);
            lp.setMargins(0, 0, i % 4 == 3 ? 0 : dp(7), dp(7));
            barGrid.addView(card, lp);
            barCards.add(card);
        }
    }

    private boolean startBarDrag(View card, int index) {
        if (playing || editor.size() < 2) return false;
        activeDrag = new BarDrag(editor, editor.revision(), index, card);
        dragCommitted = false;
        boolean started = card.startDragAndDrop(ClipData.newPlainText("impro-bar", ""), new View.DragShadowBuilder(card), activeDrag, 0);
        if (!started) { finishDrag(); return false; }
        card.setAlpha(.35f);
        status.setText("원하는 마디 위에 놓으세요 · 놓은 위치로 순서 이동");
        return true;
    }

    private boolean acceptDrag(DragEvent event) {
        if (playing || !(event.getLocalState() instanceof BarDrag)) return false;
        BarDrag drag = (BarDrag) event.getLocalState();
        return drag.owner == editor && drag.revision == editor.revision() && drag.from >= 0 && drag.from < editor.size();
    }

    private boolean onBarDrag(View view, DragEvent event, int destination) {
        if (event.getAction() == DragEvent.ACTION_DRAG_ENDED) { finishDrag(); return true; }
        if (!acceptDrag(event)) return false;
        switch (event.getAction()) {
            case DragEvent.ACTION_DRAG_STARTED: return true;
            case DragEvent.ACTION_DRAG_ENTERED:
                view.setBackground(surface(Color.rgb(66, 85, 47), GREEN, 10));
                return true;
            case DragEvent.ACTION_DRAG_LOCATION:
                view.setBackground(surface(Color.rgb(66, 85, 47), GREEN, 10));
                updateDragScroll(view, event.getY());
                return true;
            case DragEvent.ACTION_DRAG_EXITED:
                refreshCardStyles();
                return true;
            case DragEvent.ACTION_DROP:
                BarDrag drag = (BarDrag) event.getLocalState();
                editor.moveBar(drag.from, destination);
                selectedBar = destination;
                dragCommitted = true;
                commitEdit((drag.from + 1) + "마디를 " + (destination + 1) + "번 위치로 이동했습니다.");
                return true;
            default: return false;
        }
    }

    private void updateDragScroll(View view, float y) {
        int[] origin = new int[2], viewport = new int[2];
        view.getLocationOnScreen(origin);
        scroll.getLocationOnScreen(viewport);
        float position = origin[1] + y - viewport[1];
        int direction = position < dp(64) ? -1 : position > scroll.getHeight() - dp(64) ? 1 : 0;
        if (direction == dragScrollDirection) return;
        handler.removeCallbacks(dragScroller);
        dragScrollDirection = direction;
        if (direction != 0) handler.post(dragScroller);
    }

    private void finishDrag() {
        if (activeDrag != null) {
            activeDrag.source.setAlpha(1);
            if (!dragCommitted) status.setText("순서 이동을 취소했습니다.");
        }
        activeDrag = null;
        dragScrollDirection = 0;
        handler.removeCallbacks(dragScroller);
        refreshCardStyles();
    }

    private void refreshCardStyles() {
        for (int i = 0; i < barCards.size(); i++) {
            LinearLayout card = barCards.get(i);
            card.setSelected(i == highlightedBar);
            card.setBackground(surface(i == highlightedBar ? Color.rgb(42, 58, 36) : PANEL, i == highlightedBar ? GREEN : Color.rgb(45, 58, 48), 10));
            ((TextView) card.getChildAt(1)).setTextColor(i == highlightedBar ? GREEN : TEXT);
        }
    }

    static final class BarDrag {
        final ProgressionEditor owner;
        final long revision;
        final int from;
        final View source;
        BarDrag(ProgressionEditor owner, long revision, int from, View source) {
            this.owner = owner; this.revision = revision; this.from = from; this.source = source;
        }
    }

    private void updatePosition(long frames) {
        if (progression == null) return;
        double framesPerBeat = AudioRenderer.SAMPLE_RATE * 60.0 / bpm;
        long total = Math.round(progression.bars.size() * beats * framesPerBeat);
        double beatPosition = (frames % total) / framesPerBeat;
        int bar = Math.min(progression.bars.size() - 1, (int) (beatPosition / beats));
        double inBar = beatPosition - bar * beats;
        int beat = Math.min(beats - 1, (int) inBar);
        MusicTheory.Bar measure = progression.bars.get(bar);
        MusicTheory.Chord chord = measure.atBeat(inBar, beats);
        if (!shownChord.equals(chord.symbol)) {
            shownChord = chord.symbol;
            currentChord.setText(chord.rest ? "쉼" : chord.symbol);
            keyboard.setChord(chord);
        }
        location.setText(String.format(Locale.US, "%02d / %02d 마디   ·   %d/4", bar + 1, progression.bars.size(), beats));
        int chordIndex = Math.min(measure.chords.size() - 1, (int) (inBar * measure.chords.size() / beats));
        String next;
        if (chordIndex + 1 < measure.chords.size()) next = measure.chords.get(chordIndex + 1).symbol;
        else if (bar + 1 < progression.bars.size()) next = progression.bars.get(bar + 1).chords.get(0).symbol;
        else next = loop.isChecked() ? progression.bars.get(0).chords.get(0).symbol + " · 반복" : "끝";
        nextChord.setText("다음  →  " + next);
        for (int i = 0; i < beatDots.size(); i++) {
            TextView dot = beatDots.get(i);
            dot.setTextColor(i == beat ? GREEN : INK);
            dot.setBackground(surface(i == beat ? INK : Color.argb(30, 26, 43, 23), Color.TRANSPARENT, 17));
        }
        if (bar != highlightedBar) {
            highlightedBar = bar;
            if (playing) { selectedBar = bar; updateEditActions(); }
            refreshCardStyles();
        }
        if (playing) status.setText("재생 중 · " + (frames / total + 1) + "회차 · " + (beat + 1) + "/" + beats + "박");
    }

    private void saveSettings() {
        if (editor == null || loop == null) return;
        preferences.edit().putString("progression", editor.serialize()).putInt("bpm", bpm)
                .putInt("beats", beats).putBoolean("loop", loop.isChecked()).putBoolean("metronome", metronome.isChecked()).apply();
    }

    @Override protected void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(noisyReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(noisyReceiver, filter);
    }
    @Override protected void onPause() {
        if (activeDrag != null) { activeDrag.source.cancelDragAndDrop(); finishDrag(); }
        saveSettings();
        player.stop();
        super.onPause();
    }
    @Override protected void onStop() { unregisterReceiver(noisyReceiver); super.onStop(); }
    @Override protected void onDestroy() {
        if (picker != null) picker.dismiss();
        handler.removeCallbacksAndMessages(null); player.stop(); super.onDestroy();
    }

    private Switch option(String label, boolean checked) {
        Switch view = new Switch(this);
        view.setText(label);
        view.setTextColor(TEXT);
        view.setTextSize(14);
        view.setChecked(checked);
        view.setThumbTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}}, new int[]{GREEN, MUTED}));
        view.setTrackTintList(ColorStateList.valueOf(Color.rgb(71, 91, 69)));
        view.setPadding(0, dp(6), 0, dp(6));
        view.setMinHeight(dp(48));
        return view;
    }
    private LinearLayout column() { LinearLayout view = new LinearLayout(this); view.setOrientation(LinearLayout.VERTICAL); return view; }
    private LinearLayout row() { LinearLayout view = new LinearLayout(this); view.setOrientation(LinearLayout.HORIZONTAL); view.setGravity(Gravity.CENTER_VERTICAL); return view; }
    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value); view.setTextSize(size); view.setTextColor(color); view.setIncludeFontPadding(false);
        return view;
    }
    private Button button(String label, boolean primary) {
        Button view = new Button(this);
        view.setText(label); view.setTextSize(14); view.setAllCaps(false); view.setTypeface(null, Typeface.BOLD);
        view.setMinWidth(0); view.setMinimumWidth(0); view.setMinHeight(dp(48)); view.setPadding(dp(8), 0, dp(8), 0);
        styleButton(view, primary);
        return view;
    }
    private void styleButton(Button button, boolean primary) {
        button.setTextColor(primary ? INK : TEXT);
        button.setBackground(surface(primary ? GREEN : PANEL, primary ? GREEN : Color.rgb(61, 77, 64), 12));
    }
    private GradientDrawable surface(int fill, int stroke, int radius) {
        GradientDrawable shape = new GradientDrawable(); shape.setColor(fill); shape.setCornerRadius(dp(radius)); shape.setStroke(dp(1), stroke); return shape;
    }
    private void add(LinearLayout parent, View view, int marginTop) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.topMargin = dp(marginTop); parent.addView(view, lp);
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
