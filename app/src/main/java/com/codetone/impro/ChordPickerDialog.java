package com.codetone.impro;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Touch-only editor. Changes remain a draft until the user taps Save. */
public final class ChordPickerDialog extends Dialog {
    public interface OnSave { void save(List<String> chords); }
    private static final int BG = Color.rgb(20, 27, 23), PANEL = Color.rgb(35, 46, 39);
    private static final int GREEN = Color.rgb(194, 238, 140), TEXT = Color.rgb(238, 243, 235), MUTED = Color.rgb(148, 166, 151);
    private static final String[] COMMON = {"", "m", "7", "maj7", "m7", "6", "m6", "add9", "sus2", "sus4", "9", "m9", "maj9", "dim7", "m7b5", "aug"};
    private static final String[] EXTENDED = {"11", "m11", "13", "m13", "maj13", "mMaj7", "69", "madd9", "7sus4", "9sus4", "dim", "7b5", "7#5", "7b9", "7#9", "7#11", "maj7#11", "7#5b9", "7#5#9", "7alt", "13b9", "5"};
    private static final String[] BASS = {"C", "Db", "D", "Eb", "E", "F", "Gb", "G", "Ab", "A", "Bb", "B"};
    private final List<String> draft;
    private final OnSave onSave;
    private final String title;
    private final boolean adding;
    private final Map<String, Button> rootButtons = new LinkedHashMap<>(), accidentalButtons = new LinkedHashMap<>(), qualityButtons = new LinkedHashMap<>();
    private final List<Button> slots = new ArrayList<>();
    private LinearLayout slotRow;
    private GridLayout extended;
    private TextView preview, division;
    private KeyboardView keyboard;
    private Button addChord, removeChord, bassButton, restButton, extendedButton;
    private int selected;
    private String root = "C", accidental = "", quality = "", bass;
    private boolean rest;
    private final int beats;

    public ChordPickerDialog(Activity activity, int barIndex, List<String> chords, int beats, boolean adding, OnSave onSave) {
        super(activity);
        this.title = String.format(java.util.Locale.US, "%02d마디 · 코드 선택", barIndex + 1);
        this.draft = new ArrayList<>(chords);
        this.onSave = onSave;
        this.beats = beats;
        this.adding = adding;
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout panel = column();
        panel.setPadding(dp(18), dp(20), dp(18), dp(14));
        panel.setBackground(surface(BG, false));
        LinearLayout heading = row();
        TextView name = label(title, 20, TEXT);
        heading.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
        Button cancel = button("취소", false);
        cancel.setOnClickListener(view -> dismiss());
        heading.addView(cancel, new LinearLayout.LayoutParams(dp(62), dp(48)));
        panel.addView(heading);

        ScrollView scroll = new ScrollView(getContext());
        LinearLayout content = column();
        content.setPadding(0, dp(8), 0, dp(12));
        scroll.addView(content);
        panel.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        content.addView(label("마디 안의 코드", 12, MUTED));
        slotRow = row();
        add(content, slotRow, 8);
        LinearLayout slotTools = row();
        addChord = button("+ 코드", false);
        addChord.setOnClickListener(view -> {
            if (draft.size() >= 4) return;
            draft.add(selected + 1, draft.get(selected));
            selected++;
            loadSlot();
        });
        removeChord = button("코드 삭제", false);
        removeChord.setOnClickListener(view -> {
            if (draft.size() == 1) return;
            draft.remove(selected);
            selected = Math.min(selected, draft.size() - 1);
            loadSlot();
        });
        slotTools.addView(addChord, new LinearLayout.LayoutParams(0, dp(48), 1));
        LinearLayout.LayoutParams toolsLp = new LinearLayout.LayoutParams(0, dp(48), 1);
        toolsLp.leftMargin = dp(8);
        slotTools.addView(removeChord, toolsLp);
        add(content, slotTools, 8);
        division = label("", 12, MUTED);
        add(content, division, 8);
        preview = label("", 32, GREEN);
        preview.setTypeface(null, Typeface.BOLD);
        add(content, preview, 18);
        keyboard = new KeyboardView(getContext());
        LinearLayout.LayoutParams keysLp = new LinearLayout.LayoutParams(-1, dp(48));
        keysLp.topMargin = dp(8);
        content.addView(keyboard, keysLp);

        add(content, label("루트음", 13, MUTED), 20);
        GridLayout roots = grid(4);
        int index = 0;
        for (String note : new String[]{"C", "D", "E", "F", "G", "A", "B"}) {
            Button button = button(note, false);
            button.setContentDescription("루트 " + note);
            button.setOnClickListener(view -> { root = note; rest = false; commitSlot(); });
            rootButtons.put(note, button);
            cell(roots, button, index++, 4);
        }
        restButton = button("쉼", false);
        restButton.setOnClickListener(view -> { rest = true; commitSlot(); });
        cell(roots, restButton, index, 4);
        add(content, roots, 8);
        LinearLayout accidentals = row();
        String[] signs = {"b", "", "#"}, signLabels = {"♭", "♮", "♯"};
        for (int i = 0; i < signs.length; i++) {
            String sign = signs[i];
            Button button = button(signLabels[i], false);
            button.setContentDescription(i == 0 ? "플랫" : i == 1 ? "제자리표" : "샤프");
            button.setOnClickListener(view -> { accidental = sign; rest = false; commitSlot(); });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(48), 1);
            if (i > 0) lp.leftMargin = dp(6);
            accidentals.addView(button, lp);
            accidentalButtons.put(sign, button);
        }
        add(content, accidentals, 4);

        add(content, label("코드 종류", 13, MUTED), 18);
        GridLayout common = grid(4);
        for (int i = 0; i < COMMON.length; i++) cell(common, qualityButton(COMMON[i]), i, 4);
        add(content, common, 8);
        extendedButton = button("확장 코드 ▾", false);
        extendedButton.setOnClickListener(view -> {
            boolean show = extended.getVisibility() != View.VISIBLE;
            extended.setVisibility(show ? View.VISIBLE : View.GONE);
            extendedButton.setText(show ? "확장 코드 ▴" : "확장 코드 ▾");
        });
        add(content, extendedButton, 6);
        extended = grid(4);
        for (int i = 0; i < EXTENDED.length; i++) cell(extended, qualityButton(EXTENDED[i]), i, 4);
        extended.setVisibility(View.GONE);
        add(content, extended, 6);
        bassButton = button("베이스 · 루트", false);
        bassButton.setOnClickListener(view -> chooseBass());
        add(content, bassButton, 12);

        Button save = button(adding ? "마디 추가" : "저장", true);
        save.setOnClickListener(view -> {
            onSave.save(new ArrayList<>(draft));
            dismiss();
        });
        LinearLayout.LayoutParams saveLp = new LinearLayout.LayoutParams(-1, dp(54));
        saveLp.topMargin = dp(10);
        panel.addView(save, saveLp);
        setContentView(panel);
        loadSlot();
        Window window = getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams lp = window.getAttributes();
            lp.dimAmount = .65f;
            window.setAttributes(lp);
            window.setGravity(Gravity.BOTTOM);
            window.setLayout(-1, Math.round(getContext().getResources().getDisplayMetrics().heightPixels * .88f));
        }
    }

    private Button qualityButton(String kind) {
        String name = kind.isEmpty() ? "메이저" : kind.equals("m") ? "마이너" : kind.equals("69") ? "6/9" : kind;
        Button button = button(name, false);
        button.setContentDescription("코드 종류 " + name);
        button.setOnClickListener(view -> { quality = kind; rest = false; commitSlot(); });
        qualityButtons.put(kind, button);
        return button;
    }
    private void loadSlot() {
        String symbol = draft.get(selected);
        rest = MusicTheory.parseChord(symbol).rest;
        if (!rest) {
            Matcher matcher = Pattern.compile("^([A-G])([#b]?)([^/]*)(?:/([A-G][#b]?))?$").matcher(symbol);
            if (matcher.matches()) {
                root = matcher.group(1); accidental = matcher.group(2); quality = matcher.group(3); bass = matcher.group(4);
            }
            if (!qualityButtons.containsKey(quality)) {
                // Retain uncommon valid aliases from a progression saved by version 1.0.
                cell(extended, qualityButton(quality), extended.getChildCount(), 4);
            }
            boolean isCommon = java.util.Arrays.asList(COMMON).contains(quality);
            extended.setVisibility(isCommon ? View.GONE : View.VISIBLE);
            extendedButton.setText(isCommon ? "확장 코드 ▾" : "확장 코드 ▴");
        } else { root = "C"; accidental = ""; quality = ""; bass = null; }
        refresh();
    }
    private void commitSlot() {
        draft.set(selected, rest ? "NC" : root + accidental + quality + (bass == null ? "" : "/" + bass));
        refresh();
    }
    private void refresh() {
        slotRow.removeAllViews();
        slots.clear();
        for (int i = 0; i < draft.size(); i++) {
            final int slot = i;
            Button button = button(draft.get(i).equals("NC") ? "쉼" : draft.get(i), i == selected);
            button.setContentDescription((i + 1) + "번 코드 " + draft.get(i));
            button.setOnClickListener(view -> { selected = slot; loadSlot(); });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(52), 1);
            if (i > 0) lp.leftMargin = dp(6);
            slotRow.addView(button, lp);
            slots.add(button);
        }
        addChord.setEnabled(draft.size() < 4);
        removeChord.setEnabled(draft.size() > 1);
        addChord.setAlpha(draft.size() < 4 ? 1 : .4f);
        removeChord.setAlpha(draft.size() > 1 ? 1 : .4f);
        String length = String.format(java.util.Locale.US, "%.2f", (double) beats / draft.size()).replaceAll("0+$", "").replaceAll("\\.$", "");
        division.setText("코드 " + draft.size() + "개 · 각각 " + length + "박씩 연주");
        preview.setText(rest ? "쉼 · NC" : draft.get(selected));
        keyboard.setChord(MusicTheory.parseChord(draft.get(selected)));
        for (Map.Entry<String, Button> item : rootButtons.entrySet()) style(item.getValue(), !rest && item.getKey().equals(root));
        for (Map.Entry<String, Button> item : accidentalButtons.entrySet()) style(item.getValue(), !rest && item.getKey().equals(accidental));
        for (Map.Entry<String, Button> item : qualityButtons.entrySet()) style(item.getValue(), !rest && item.getKey().equals(quality));
        style(restButton, rest);
        bassButton.setText("베이스 · " + (bass == null ? "루트" : bass));
        bassButton.setEnabled(!rest);
        bassButton.setAlpha(rest ? .4f : 1f);
    }
    private void chooseBass() {
        String[] names = new String[13];
        names[0] = "루트와 같게";
        System.arraycopy(BASS, 0, names, 1, BASS.length);
        int checked = bass == null ? 0 : MusicTheory.parseChord("C/" + bass).bass + 1;
        new AlertDialog.Builder(getContext()).setTitle("슬래시 베이스")
                .setSingleChoiceItems(names, checked, (dialog, which) -> {
                    bass = which == 0 ? null : BASS[which - 1];
                    commitSlot();
                    dialog.dismiss();
                }).setNegativeButton("취소", null).show();
    }

    private LinearLayout column() { LinearLayout view = new LinearLayout(getContext()); view.setOrientation(LinearLayout.VERTICAL); return view; }
    private LinearLayout row() { LinearLayout view = new LinearLayout(getContext()); view.setGravity(Gravity.CENTER_VERTICAL); return view; }
    private GridLayout grid(int columns) { GridLayout view = new GridLayout(getContext()); view.setColumnCount(columns); return view; }
    private void cell(GridLayout grid, View view, int index, int columns) {
        GridLayout.LayoutParams lp = new GridLayout.LayoutParams(GridLayout.spec(index / columns), GridLayout.spec(index % columns, 1f));
        lp.width = 0; lp.height = dp(48); lp.setMargins(0, 0, index % columns == columns - 1 ? 0 : dp(6), dp(6));
        grid.addView(view, lp);
    }
    private TextView label(String value, int size, int color) { TextView view = new TextView(getContext()); view.setText(value); view.setTextSize(size); view.setTextColor(color); return view; }
    private Button button(String value, boolean active) {
        Button button = new Button(getContext()); button.setText(value); button.setAllCaps(false); button.setTextSize(14);
        button.setTypeface(null, Typeface.BOLD); button.setMinWidth(0); button.setMinimumWidth(0); button.setMinHeight(dp(48));
        button.setPadding(dp(4), 0, dp(4), 0);
        button.setAutoSizeTextTypeUniformWithConfiguration(10, 14, 1, android.util.TypedValue.COMPLEX_UNIT_SP);
        style(button, active); return button;
    }
    private void style(Button button, boolean active) { button.setTextColor(active ? BG : TEXT); button.setBackground(surface(active ? GREEN : PANEL, active)); }
    private GradientDrawable surface(int fill, boolean active) { GradientDrawable view = new GradientDrawable(); view.setColor(fill); view.setCornerRadius(dp(12)); view.setStroke(dp(1), active ? GREEN : Color.rgb(58, 75, 62)); return view; }
    private void add(LinearLayout parent, View view, int margin) { LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.topMargin = dp(margin); parent.addView(view, lp); }
    private int dp(int value) { return Math.round(value * getContext().getResources().getDisplayMetrics().density); }
}
