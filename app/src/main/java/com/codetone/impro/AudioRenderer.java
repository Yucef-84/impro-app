package com.codetone.impro;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Offline-capable, sample-clocked piano/bass synthesis. No network or soundfont. */
public final class AudioRenderer {
    public static final int SAMPLE_RATE = 44100;
    private static final int TABLE_SIZE = 4096;
    private static final double[] SINE = new double[TABLE_SIZE];
    static {
        for (int i = 0; i < TABLE_SIZE; i++) SINE[i] = Math.sin(2 * Math.PI * i / TABLE_SIZE);
    }

    public final double framesPerBeat;
    public final long totalFrames;
    public final int beatsPerBar;
    public volatile boolean looping;
    public volatile boolean metronome;
    private long frame;
    private int nextHit;
    private final List<Hit> hits = new ArrayList<>();
    private final List<Voice> voices = new ArrayList<>();

    public AudioRenderer(MusicTheory.Progression progression, int bpm, int beatsPerBar, boolean looping, boolean metronome) {
        if (bpm < 40 || bpm > 240) throw new IllegalArgumentException("템포는 40–240 BPM입니다.");
        if (beatsPerBar != 3 && beatsPerBar != 4) throw new IllegalArgumentException("박자는 3/4 또는 4/4입니다.");
        this.beatsPerBar = beatsPerBar;
        this.looping = looping;
        this.metronome = metronome;
        framesPerBeat = SAMPLE_RATE * 60.0 / bpm;
        totalFrames = Math.round(progression.bars.size() * beatsPerBar * framesPerBeat);
        int[] previous = null;
        for (int barIndex = 0; barIndex < progression.bars.size(); barIndex++) {
            MusicTheory.Bar bar = progression.bars.get(barIndex);
            double startBeat = barIndex * beatsPerBar;
            double chordBeats = (double) beatsPerBar / bar.chords.size();
            for (int chordIndex = 0; chordIndex < bar.chords.size(); chordIndex++) {
                MusicTheory.Chord chord = bar.chords.get(chordIndex);
                if (chord.rest) continue;
                int[] voicing = chord.voiceNear(previous);
                previous = voicing;
                long start = Math.round((startBeat + chordIndex * chordBeats) * framesPerBeat);
                long end = Math.round((startBeat + (chordIndex + 1) * chordBeats) * framesPerBeat);
                hits.add(new Hit(start, end - start, voicing, 0));
                hits.add(new Hit(start, Math.min(end - start, Math.round(framesPerBeat * .80)), new int[]{36 + chord.bass}, 1));
            }
            for (int beat = 0; beat < beatsPerBar; beat++) {
                long start = Math.round((startBeat + beat) * framesPerBeat);
                hits.add(new Hit(start, SAMPLE_RATE / 28, new int[]{beat == 0 ? 91 : 84}, 2));
                if (beat == 2) {
                    MusicTheory.Chord chord = bar.atBeat(beat, beatsPerBar);
                    double chordPosition = beat / chordBeats;
                    if (!chord.rest && Math.abs(chordPosition - Math.rint(chordPosition)) > .001) {
                        // Reinforce beat three only if a chord-change bass is not already there.
                        long chordEnd = Math.round((startBeat + (Math.floor(chordPosition) + 1) * chordBeats) * framesPerBeat);
                        hits.add(new Hit(start, Math.min(chordEnd - start, Math.round(framesPerBeat * .8)), new int[]{36 + chord.bass}, 1));
                    }
                }
            }
        }
        hits.sort(Comparator.comparingLong(hit -> hit.start));
    }

    public int render(short[] buffer, int offset, int count) {
        if (offset < 0 || count < 0 || offset + count > buffer.length) throw new IndexOutOfBoundsException();
        int produced = 0;
        while (produced < count) {
            if (!looping && frame >= totalFrames) break;
            long localFrame = frame % totalFrames;
            if (localFrame == 0) nextHit = 0;
            while (nextHit < hits.size() && hits.get(nextHit).start <= localFrame) {
                Hit hit = hits.get(nextHit++);
                if (hit.type != 2 || metronome) {
                    double volume = hit.type == 0 ? .23 / Math.sqrt(hit.notes.length) : hit.type == 1 ? .24 : .15;
                    for (int note : hit.notes) voices.add(new Voice(note, hit.duration, volume, hit.type));
                }
            }
            double sample = 0;
            for (int i = voices.size() - 1; i >= 0; i--) {
                Voice voice = voices.get(i);
                if (voice.age >= voice.duration) { voices.remove(i); continue; }
                sample += voice.next();
            }
            // Smooth limiting avoids clipping even with extended chords and bass.
            buffer[offset + produced++] = (short) Math.round(32767 * sample / (1 + Math.abs(sample)));
            frame++;
        }
        return produced;
    }

    public long renderedFrames() { return frame; }

    private static final class Hit {
        final long start, duration;
        final int[] notes;
        final int type;
        Hit(long start, long duration, int[] notes, int type) {
            this.start = start; this.duration = duration; this.notes = notes; this.type = type;
        }
    }

    private static final class Voice {
        final long duration;
        final double step, volume, decay;
        final int type;
        long age;
        double phase, level = 1;
        Voice(int midi, long duration, double volume, int type) {
            this.duration = Math.max(1, duration);
            this.volume = volume;
            this.type = type;
            step = 440 * Math.pow(2, (midi - 69) / 12.0) * TABLE_SIZE / SAMPLE_RATE;
            decay = Math.exp(-(type == 2 ? 80 : type == 1 ? 3.2 : 1.25) / SAMPLE_RATE);
        }
        double next() {
            int index = (int) phase;
            double wave = SINE[index];
            if (type != 2) wave += (type == 1 ? .22 : .35) * SINE[(index * 2) & (TABLE_SIZE - 1)]
                    + .12 * SINE[(index * 3) & (TABLE_SIZE - 1)] + .035 * SINE[(index * 4) & (TABLE_SIZE - 1)];
            double attack = Math.min(1, age / (SAMPLE_RATE * (type == 2 ? .001 : .008)));
            double release = Math.min(1, (duration - age) / (SAMPLE_RATE * .025));
            double result = wave * volume * level * attack * release;
            phase += step;
            if (phase >= TABLE_SIZE) phase -= TABLE_SIZE;
            level *= decay;
            age++;
            return result;
        }
    }
}
