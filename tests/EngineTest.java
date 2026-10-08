package com.codetone.impro;

import java.util.Arrays;

/** Real engine tests without Android, JUnit, a device, or network dependencies. */
public final class EngineTest {
    private static int checks;
    public static void main(String[] args) {
        chordParsing();
        progressionParsing();
        touchEditing();
        audioTimingAndSignal();
        System.out.println("PASS: " + checks + " engine checks");
    }

    private static void chordParsing() {
        equal(new int[]{60, 64, 67}, MusicTheory.parseChord("C").midiNotes(), "C major");
        equal(new int[]{62, 65, 69, 72}, MusicTheory.parseChord("Dm7").midiNotes(), "D minor seventh");
        equal(new int[]{60, 64, 67, 71}, MusicTheory.parseChord("CM7").midiNotes(), "capital M is major");
        equal(MusicTheory.parseChord("Cmaj7").midiNotes(), MusicTheory.parseChord("CΔ7").midiNotes(), "delta alias");
        equal(new int[]{66, 69, 72, 76}, MusicTheory.parseChord("F#m7b5").midiNotes(), "half diminished");
        equal(new int[]{60, 63, 66, 69}, MusicTheory.parseChord("Cdim7").midiNotes(), "diminished seventh");
        check(MusicTheory.parseChord("C/E").bass == 4, "slash bass");
        check(MusicTheory.parseChord("Bb7/Db").bass == 1, "flat slash bass");
        check(MusicTheory.parseChord("B#").root == 0 && MusicTheory.parseChord("Cb").root == 11, "enharmonic roots");
        check(MusicTheory.parseChord("F♯m7").root == 6, "unicode sharp");
        equal(MusicTheory.parseChord("C69").midiNotes(), MusicTheory.parseChord("C6/9").midiNotes(), "six nine");
        check(MusicTheory.parseChord("C6/9/E").bass == 4, "six nine plus slash bass");
        check(MusicTheory.parseChord("N.C.").rest, "rest");
        for (String chord : new String[]{"C", "Am7", "Dm9", "G13", "Cmaj7", "F#dim7", "Bb7#5#9"}) {
            int[] voicing = MusicTheory.parseChord(chord).voiceNear(new int[]{60, 64, 67, 71});
            for (int note : voicing) check(note >= 48 && note <= 88, "voicing register: " + chord);
            for (int i = 1; i < voicing.length; i++) check(voicing[i] >= voicing[i - 1], "sorted voicing: " + chord);
        }
        rejects(() -> MusicTheory.parseChord("H7"), "unknown root");
        rejects(() -> MusicTheory.parseChord("Cbanana"), "unknown quality");
        rejects(() -> MusicTheory.parseChord("C/E/F"), "extra slash");
        rejects(() -> MusicTheory.parseChord("C/"), "empty bass");
    }

    private static void progressionParsing() {
        MusicTheory.Progression p = MusicTheory.parse("Cmaj7 | Am7 | Dm7 | G7");
        check(p.bars.size() == 4, "four bars");
        check(MusicTheory.parse("C Am Dm G").bars.size() == 4, "space-only shorthand");
        check(MusicTheory.parse("C, Am, Dm, G").bars.size() == 4, "comma shorthand");
        check(MusicTheory.parse("C\nAm\nDm\nG").bars.size() == 4, "newline bars");
        check(MusicTheory.parse("| C | Am |\n| Dm | G |").bars.size() == 4, "wrapped leadsheet lines");
        check(MusicTheory.parse("C |\nAm | G").bars.size() == 3, "bar plus newline");
        MusicTheory.Bar two = MusicTheory.parse("Dm7 G7 | Cmaj7").bars.get(0);
        check(two.chords.size() == 2, "two chords in a bar");
        check(two.atBeat(1.999, 4).symbol.equals("Dm7"), "first half bar");
        check(two.atBeat(2, 4).symbol.equals("G7"), "second half bar");
        check(two.atBeat(1.5, 3).symbol.equals("G7"), "equal split in 3/4");
        p = MusicTheory.parse("Cmaj7 / | / | NC | C/E");
        check(p.bars.get(1).chords.get(0).symbol.equals("Cmaj7"), "repeat across bars");
        check(p.bars.get(2).chords.get(0).rest, "rest bar");
        rejects(() -> MusicTheory.parse(""), "empty progression");
        rejects(() -> MusicTheory.parse("/ | C"), "initial repeat");
        rejects(() -> MusicTheory.parse("C || G"), "empty inner bar");
        rejects(() -> MusicTheory.parse("C D E F G | C"), "too many chords per bar");
        rejects(() -> MusicTheory.parse(String.join(" | ", java.util.Collections.nCopies(65, "C"))), "bar limit");
        check(MusicTheory.parse(String.join(" | ", java.util.Collections.nCopies(64, "C"))).bars.size() == 64, "maximum bars");
    }

    private static void touchEditing() {
        ProgressionEditor editor = new ProgressionEditor("Cmaj7 | Am7 | Dm7 | G7");
        editor.moveBar(0, 2);
        check(editor.serialize().equals("Am7 | Dm7 | Cmaj7 | G7"), "drag forward inserts without swapping");
        editor.moveBar(3, 0);
        check(editor.serialize().equals("G7 | Am7 | Dm7 | Cmaj7"), "drag backward preserves intervening order");
        check(editor.undo(), "undo backward drag");
        check(editor.serialize().equals("Am7 | Dm7 | Cmaj7 | G7"), "undo restores bar order");
        check(editor.undo() && !editor.canUndo(), "undo first drag");
        long revision = editor.revision();
        editor.moveBar(1, 1);
        check(editor.revision() == revision && !editor.canUndo(), "dropping on same bar has no history");
        editor.setBar(0, java.util.List.of("Bbmaj7", "G7/Db"));
        check(editor.bar(0).equals(java.util.List.of("Bbmaj7", "G7/Db")), "picker edits multiple chords and slash bass");
        check(editor.progression().bars.get(0).chords.size() == 2, "edited chords reach playback snapshot");
        editor.duplicateBar(0);
        editor.setBar(1, java.util.List.of("NC"));
        check(editor.bar(0).size() == 2 && editor.bar(1).equals(java.util.List.of("NC")), "duplicated bar is independent");
        editor.removeBar(1);
        check(editor.size() == 4, "remove bar");
        editor.insertBar(4, java.util.List.of("F#m7b5"));
        check(editor.size() == 5 && editor.bar(4).get(0).equals("F#m7b5"), "append chosen chord");
        String before = editor.serialize();
        long beforeRevision = editor.revision();
        rejects(() -> editor.setBar(0, java.util.List.of("H7")), "invalid choice is rejected");
        rejects(() -> editor.setBar(0, java.util.List.of()), "empty chord list is rejected");
        rejects(() -> editor.setBar(0, java.util.List.of("C", "D", "E", "F", "G")), "five chord choices are rejected");
        check(editor.serialize().equals(before) && editor.revision() == beforeRevision, "failed edit is atomic");
        ProgressionEditor restored = new ProgressionEditor(editor.serialize());
        check(restored.serialize().equals(editor.serialize()), "touch edits survive persistence");
        ProgressionEditor last = new ProgressionEditor("C");
        rejects(() -> last.removeBar(0), "last bar cannot be deleted");
        check(last.size() == 1, "last bar remains");
        ProgressionEditor maximum = new ProgressionEditor(String.join(" | ", java.util.Collections.nCopies(64, "C")));
        rejects(() -> maximum.insertBar(64, java.util.List.of("G")), "touch editor bar limit");
        rejects(() -> maximum.duplicateBar(0), "duplicate obeys bar limit");
        check(maximum.size() == 64 && !maximum.canUndo(), "failed size limit leaves no history");
        editor.replace("Dm7 G7 | Cmaj7");
        check(editor.size() == 2 && editor.undo(), "preset replacement can be undone");
        check(editor.serialize().equals(before), "undo preset restores whole progression");
    }

    private static void audioTimingAndSignal() {
        MusicTheory.Progression p = MusicTheory.parse("Dm7 G7 | Cmaj7");
        for (int bpm : new int[]{40, 97, 137, 240}) for (int meter : new int[]{3, 4}) {
            AudioRenderer renderer = new AudioRenderer(p, bpm, meter, false, false);
            long expected = Math.round(2 * meter * 44100 * 60.0 / bpm);
            check(renderer.totalFrames == expected, "sample duration " + bpm + " BPM " + meter + "/4");
            short[] block = new short[1024];
            long actual = 0;
            int n, peak = 0;
            double energy = 0;
            while ((n = renderer.render(block, 0, block.length)) != 0) {
                actual += n;
                for (int i = 0; i < n; i++) { peak = Math.max(peak, Math.abs((int) block[i])); energy += (double) block[i] * block[i]; }
            }
            check(actual == expected, "exact final block duration");
            check(peak > 2000 && peak < 32767, "audible, unclipped output");
            check(Math.sqrt(energy / actual) > 300, "nontrivial audio RMS");
        }
        short[] reference = render(new AudioRenderer(p, 240, 4, false, true), 1024);
        short[] otherBlocks = render(new AudioRenderer(p, 240, 4, false, true), 317);
        equal(reference, otherBlocks, "chunk boundaries never change timing or sound");
        AudioRenderer loop = new AudioRenderer(p, 240, 4, true, true);
        short[] doubleCycle = new short[(int) loop.totalFrames * 2];
        check(loop.render(doubleCycle, 0, doubleCycle.length) == doubleCycle.length, "loop continues");
        equal(reference, Arrays.copyOfRange(doubleCycle, reference.length, doubleCycle.length), "repeat has exact samples and no gap");
        MusicTheory.Progression rest = MusicTheory.parse("NC");
        short[] silent = render(new AudioRenderer(rest, 240, 3, false, false), 512);
        for (short sample : silent) if (sample != 0) throw new AssertionError("NC emitted sound");
        check(true, "rest without metronome is silent");
        short[] click = render(new AudioRenderer(rest, 240, 3, false, true), 512);
        check(!Arrays.equals(silent, click), "metronome on a rest bar");
        short[] withRest = render(new AudioRenderer(MusicTheory.parse("C | NC"), 240, 4, false, false), 511);
        for (int i = withRest.length / 2; i < withRest.length; i++) if (withRest[i] != 0) throw new AssertionError("Chord leaked into NC");
        check(true, "rest cuts off previous chord");
        rejects(() -> new AudioRenderer(p, 0, 4, false, false), "invalid tempo");
        rejects(() -> new AudioRenderer(p, 96, 5, false, false), "invalid meter");
    }

    private static short[] render(AudioRenderer renderer, int blockSize) {
        short[] all = new short[(int) renderer.totalFrames];
        int offset = 0;
        while (offset < all.length) {
            int n = renderer.render(all, offset, Math.min(blockSize, all.length - offset));
            if (n == 0) throw new AssertionError("unexpected early end");
            offset += n;
        }
        return all;
    }
    private static void rejects(Runnable action, String message) {
        try { action.run(); } catch (IllegalArgumentException expected) { check(true, message); return; }
        throw new AssertionError(message + " was accepted");
    }
    private static void equal(int[] expected, int[] actual, String message) { check(Arrays.equals(expected, actual), message); }
    private static void equal(short[] expected, short[] actual, String message) { check(Arrays.equals(expected, actual), message); }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }
}
