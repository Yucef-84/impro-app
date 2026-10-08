package com.codetone.impro;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

/** Validated, atomic bar editing with an undo history. Playback uses the same snapshot. */
public final class ProgressionEditor {
    private List<List<String>> bars;
    private MusicTheory.Progression progression;
    private final Deque<List<List<String>>> history = new ArrayDeque<>();
    private long revision;

    public ProgressionEditor(String saved) {
        progression = MusicTheory.parse(saved);
        bars = symbols(progression);
    }
    public int size() { return bars.size(); }
    public long revision() { return revision; }
    public boolean canUndo() { return !history.isEmpty(); }
    public MusicTheory.Progression progression() { return progression; }
    public String serialize() { return progression.normalized(); }
    public List<String> bar(int index) { return Collections.unmodifiableList(new ArrayList<>(bars.get(index))); }

    public void setBar(int index, List<String> chords) {
        List<List<String>> candidate = copy(bars);
        candidate.set(index, validateBar(chords));
        apply(candidate);
    }
    public void insertBar(int index, List<String> chords) {
        if (bars.size() >= MusicTheory.MAX_BARS) throw new IllegalArgumentException("최대 64마디까지 만들 수 있습니다.");
        List<List<String>> candidate = copy(bars);
        candidate.add(index, validateBar(chords));
        apply(candidate);
    }
    public void duplicateBar(int index) { insertBar(index + 1, bar(index)); }
    public void removeBar(int index) {
        if (bars.size() == 1) throw new IllegalArgumentException("마디는 하나 이상 있어야 합니다.");
        List<List<String>> candidate = copy(bars);
        candidate.remove(index);
        apply(candidate);
    }
    public void moveBar(int from, int to) {
        if (from < 0 || from >= bars.size() || to < 0 || to >= bars.size()) throw new IndexOutOfBoundsException();
        if (from == to) return;
        List<List<String>> candidate = copy(bars);
        candidate.add(to, candidate.remove(from));
        apply(candidate);
    }
    public void replace(String preset) { apply(symbols(MusicTheory.parse(preset))); }
    public boolean undo() {
        if (history.isEmpty()) return false;
        bars = history.pop();
        progression = MusicTheory.parse(join(bars));
        revision++;
        return true;
    }

    private void apply(List<List<String>> candidate) {
        MusicTheory.Progression validated = MusicTheory.parse(join(candidate));
        if (validated.normalized().equals(progression.normalized())) return;
        history.push(copy(bars));
        if (history.size() > 30) history.removeLast();
        progression = validated;
        bars = symbols(validated);
        revision++;
    }
    private static List<String> validateBar(List<String> chords) {
        if (chords == null || chords.isEmpty() || chords.size() > 4) throw new IllegalArgumentException("한 마디에는 코드 1–4개를 넣을 수 있습니다.");
        List<String> result = new ArrayList<>();
        for (String chord : chords) result.add(MusicTheory.parseChord(chord).symbol);
        return result;
    }
    private static List<List<String>> symbols(MusicTheory.Progression progression) {
        List<List<String>> result = new ArrayList<>();
        for (MusicTheory.Bar bar : progression.bars) {
            List<String> chords = new ArrayList<>();
            for (MusicTheory.Chord chord : bar.chords) chords.add(chord.symbol);
            result.add(chords);
        }
        return result;
    }
    private static List<List<String>> copy(List<List<String>> source) {
        List<List<String>> result = new ArrayList<>();
        for (List<String> bar : source) result.add(new ArrayList<>(bar));
        return result;
    }
    private static String join(List<List<String>> source) {
        List<String> result = new ArrayList<>();
        for (List<String> bar : source) result.add(String.join(" ", bar));
        return String.join(" | ", result);
    }
}
