package com.codetone.impro;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Independently implemented chord symbols and equal-duration leadsheet bars. */
public final class MusicTheory {
    private MusicTheory() {}
    public static final int MAX_BARS = 64;
    private static final Pattern SYMBOL = Pattern.compile("^([A-Ga-g])([#b]?)([^/]*)(?:/([A-Ga-g])([#b]?))?$");
    private static final Map<String, int[]> QUALITIES = new LinkedHashMap<>();
    static {
        add(new int[]{0, 4, 7}, "", "maj", "M");
        add(new int[]{0, 3, 7}, "m", "min", "-");
        add(new int[]{0, 4, 7, 10}, "7");
        add(new int[]{0, 4, 7, 11}, "maj7", "M7", "Δ", "Δ7");
        add(new int[]{0, 3, 7, 10}, "m7", "min7", "-7");
        add(new int[]{0, 3, 7, 11}, "mMaj7", "mM7", "m(maj7)");
        add(new int[]{0, 4, 7, 9}, "6");
        add(new int[]{0, 3, 7, 9}, "m6", "min6", "-6");
        add(new int[]{0, 4, 7, 14}, "add9", "add2");
        add(new int[]{0, 3, 7, 14}, "madd9");
        add(new int[]{0, 4, 7, 9, 14}, "6/9", "69", "6add9");
        add(new int[]{0, 4, 7, 10, 14}, "9");
        add(new int[]{0, 3, 7, 10, 14}, "m9", "min9", "-9");
        add(new int[]{0, 4, 7, 11, 14}, "maj9", "M9", "Δ9");
        add(new int[]{0, 4, 7, 10, 14, 17}, "11");
        add(new int[]{0, 3, 7, 10, 14, 17}, "m11", "-11");
        add(new int[]{0, 4, 7, 10, 14, 21}, "13");
        add(new int[]{0, 3, 7, 10, 14, 21}, "m13", "-13");
        add(new int[]{0, 4, 7, 11, 14, 21}, "maj13", "M13", "Δ13");
        add(new int[]{0, 2, 7}, "sus2");
        add(new int[]{0, 5, 7}, "sus", "sus4");
        add(new int[]{0, 5, 7, 10}, "7sus", "7sus4");
        add(new int[]{0, 5, 7, 10, 14}, "9sus", "9sus4");
        add(new int[]{0, 3, 6}, "dim", "o", "°");
        add(new int[]{0, 3, 6, 9}, "dim7", "o7", "°7");
        add(new int[]{0, 3, 6, 10}, "m7b5", "ø", "ø7", "hdim7");
        add(new int[]{0, 4, 8}, "aug", "+", "#5");
        add(new int[]{0, 4, 8, 10}, "aug7", "+7", "7#5");
        add(new int[]{0, 4, 6, 10}, "7b5");
        add(new int[]{0, 4, 7, 10, 13}, "7b9");
        add(new int[]{0, 4, 7, 10, 15}, "7#9");
        add(new int[]{0, 4, 7, 10, 14, 18}, "7#11", "9#11");
        add(new int[]{0, 4, 7, 11, 18}, "maj7#11", "M7#11");
        add(new int[]{0, 4, 8, 10, 13}, "7#5b9");
        add(new int[]{0, 4, 8, 10, 15}, "7#5#9", "7alt", "alt");
        add(new int[]{0, 4, 7, 10, 13, 21}, "13b9");
        add(new int[]{0, 7}, "5");
    }

    private static void add(int[] intervals, String... names) {
        for (String name : names) QUALITIES.put(name, intervals);
    }

    public static final class Chord {
        public final String symbol;
        public final int root;
        public final int bass;
        public final boolean rest;
        private final int[] intervals;

        private Chord(String symbol, int root, int bass, int[] intervals, boolean rest) {
            this.symbol = symbol;
            this.root = root;
            this.bass = bass;
            this.intervals = intervals.clone();
            this.rest = rest;
        }

        public int[] midiNotes() {
            int[] notes = new int[intervals.length];
            for (int i = 0; i < notes.length; i++) notes[i] = 60 + root + intervals[i];
            return notes;
        }

        /** Pick a compact inversion close to the preceding chord. */
        public int[] voiceNear(int[] previous) {
            if (rest) return new int[0];
            int[] seed = midiNotes();
            Arrays.sort(seed);
            int[] best = seed.clone();
            double bestCost = Double.MAX_VALUE;
            for (int inversion = 0; inversion < seed.length; inversion++) {
                for (int shift = -24; shift <= 12; shift += 12) {
                    int[] candidate = seed.clone();
                    for (int i = 0; i < candidate.length; i++) candidate[i] += shift;
                    Arrays.sort(candidate);
                    if (candidate[0] < 48 || candidate[candidate.length - 1] > 88) continue;
                    double cost = 0;
                    for (int i = 0; i < candidate.length; i++) {
                        double target = previous == null || previous.length == 0 ? 64 + i * 3
                                : previous[Math.min(i, previous.length - 1)];
                        cost += Math.abs(candidate[i] - target);
                    }
                    if (cost < bestCost) { bestCost = cost; best = candidate; }
                }
                seed[0] += 12;
                Arrays.sort(seed);
            }
            return best;
        }
    }

    public static final class Bar {
        public final List<Chord> chords;
        private Bar(List<Chord> chords) { this.chords = Collections.unmodifiableList(new ArrayList<>(chords)); }
        public String label() {
            StringBuilder result = new StringBuilder();
            for (Chord chord : chords) {
                if (result.length() > 0) result.append("  ");
                result.append(chord.symbol);
            }
            return result.toString();
        }
        public Chord atBeat(double beat, int beatsPerBar) {
            int index = Math.min(chords.size() - 1, (int) (Math.max(0, beat) * chords.size() / beatsPerBar));
            return chords.get(index);
        }
    }

    public static final class Progression {
        public final List<Bar> bars;
        private Progression(List<Bar> bars) { this.bars = Collections.unmodifiableList(new ArrayList<>(bars)); }
        public String normalized() {
            StringBuilder result = new StringBuilder();
            for (Bar bar : bars) {
                if (result.length() > 0) result.append(" | ");
                result.append(bar.label());
            }
            return result.toString();
        }
    }

    public static Chord parseChord(String input) {
        String symbol = input.trim().replace('♯', '#').replace('♭', 'b').replace('−', '-');
        if (symbol.equalsIgnoreCase("NC") || symbol.equalsIgnoreCase("N.C."))
            return new Chord("NC", 0, 0, new int[0], true);
        // 6/9 is a chord quality; a final slash followed by a note is the bass.
        symbol = symbol.replace("6/9", "69");
        Matcher matcher = SYMBOL.matcher(symbol);
        if (!matcher.matches()) throw new IllegalArgumentException("알 수 없는 코드: " + input);
        String quality = matcher.group(3);
        int[] intervals = QUALITIES.get(quality);
        if (intervals == null) throw new IllegalArgumentException("지원하지 않는 코드: " + input + " (예: Cmaj7, Dm7, G7, F#m7b5)");
        int root = pitchClass(matcher.group(1) + matcher.group(2));
        int bass = matcher.group(4) == null ? root : pitchClass(matcher.group(4) + matcher.group(5));
        String display = matcher.group(1).toUpperCase(Locale.ROOT) + matcher.group(2) + quality;
        if (matcher.group(4) != null) display += "/" + matcher.group(4).toUpperCase(Locale.ROOT) + matcher.group(5);
        return new Chord(display, root, bass, intervals, false);
    }

    private static int pitchClass(String note) {
        int pitch;
        switch (Character.toUpperCase(note.charAt(0))) {
            case 'C': pitch = 0; break;
            case 'D': pitch = 2; break;
            case 'E': pitch = 4; break;
            case 'F': pitch = 5; break;
            case 'G': pitch = 7; break;
            case 'A': pitch = 9; break;
            case 'B': pitch = 11; break;
            default: throw new IllegalArgumentException("잘못된 음 이름: " + note);
        }
        if (note.length() > 1) pitch += note.charAt(1) == '#' ? 1 : -1;
        return Math.floorMod(pitch, 12);
    }

    public static Progression parse(String input) {
        if (input == null || input.trim().isEmpty()) throw new IllegalArgumentException("재생할 코드를 입력해 주세요.");
        if (input.length() > 8192) throw new IllegalArgumentException("코드 입력은 8,192자까지 가능합니다.");
        String text = input.trim().replace("\r\n", "\n").replace('\r', '\n');
        boolean explicitBars = text.indexOf('|') >= 0 || text.indexOf('\n') >= 0;
        text = text.replaceAll("\\|[ \\t]*\\n[ \\t]*\\|?", "|")
                .replaceAll("\\n[ \\t]*\\|", "|").replaceAll("\\n+", "|");
        if (text.startsWith("|")) text = text.substring(1).trim();
        if (text.endsWith("|")) text = text.substring(0, text.length() - 1).trim();
        String[] parts = explicitBars ? text.split("\\|", -1) : text.split("[\\s,]+");
        if (parts.length > MAX_BARS) throw new IllegalArgumentException("최대 " + MAX_BARS + "마디까지 입력할 수 있습니다.");
        List<Bar> bars = new ArrayList<>();
        Chord previous = null;
        for (int barIndex = 0; barIndex < parts.length; barIndex++) {
            String part = parts[barIndex].trim();
            if (part.isEmpty()) throw new IllegalArgumentException((barIndex + 1) + "마디가 비어 있습니다. 쉼은 NC로 입력해 주세요.");
            String[] symbols = part.split("[\\s,]+");
            if (symbols.length > 4) throw new IllegalArgumentException((barIndex + 1) + "마디: 한 마디에는 코드 4개까지 넣을 수 있습니다.");
            List<Chord> chords = new ArrayList<>();
            for (String symbol : symbols) {
                try {
                    if (symbol.equals("/")) {
                        if (previous == null) throw new IllegalArgumentException("첫 코드에 /를 쓸 수 없습니다.");
                        chords.add(previous);
                    } else {
                        previous = parseChord(symbol);
                        chords.add(previous);
                    }
                } catch (IllegalArgumentException error) {
                    throw new IllegalArgumentException((barIndex + 1) + "마디: " + error.getMessage());
                }
            }
            bars.add(new Bar(chords));
        }
        return new Progression(bars);
    }
}
