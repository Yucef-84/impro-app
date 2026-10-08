package com.codetone.impro;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;

/** Owns a cancellable audio thread. Only the worker releases its AudioTrack. */
public final class AudioPlayer {
    public interface Listener { void onStopped(boolean completed, String error); }
    private final AudioManager manager;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Listener listener;
    private final AudioAttributes attributes = new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build();
    private final AudioFocusRequest focus;
    private volatile Session current;

    public AudioPlayer(Context context, Listener listener) {
        manager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        this.listener = listener;
        focus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attributes)
                .setOnAudioFocusChangeListener(change -> {
                    if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
                            || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) stop();
                }, main).build();
    }

    public void start(MusicTheory.Progression progression, int bpm, int beats, boolean loop, boolean click) {
        stop();
        if (manager.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
            throw new IllegalStateException("다른 앱이 오디오를 사용 중입니다. 잠시 후 다시 재생해 주세요.");
        Session session = new Session(new AudioRenderer(progression, bpm, beats, loop, click));
        current = session;
        new Thread(() -> run(session), "impro-audio").start();
    }

    public boolean isPlaying() { return current != null && !current.cancelled; }

    public long playbackFrames() {
        Session session = current;
        if (session == null) return 0;
        synchronized (session.lock) {
            if (session.track == null) return 0;
            // AudioTrack exposes an unsigned 32-bit counter, measured in heard samples.
            return Integer.toUnsignedLong(session.track.getPlaybackHeadPosition());
        }
    }

    public void stop() {
        Session session = current;
        if (session == null) return;
        current = null;
        session.cancelled = true;
        synchronized (session.lock) {
            if (session.track != null) {
                try {
                    session.track.pause();
                    session.track.flush();
                    session.track.stop();
                } catch (IllegalStateException ignored) { /* Worker cleanup may already be underway. */ }
            }
        }
        manager.abandonAudioFocusRequest(focus);
        listener.onStopped(false, null);
    }

    private void run(Session session) {
        String error = null;
        boolean completed = false;
        try {
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO);
            int minimum = AudioTrack.getMinBufferSize(AudioRenderer.SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT);
            if (minimum <= 0) throw new IllegalStateException("이 기기의 오디오 출력을 초기화하지 못했습니다.");
            AudioTrack track = new AudioTrack.Builder().setAudioAttributes(attributes)
                    .setAudioFormat(new AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(AudioRenderer.SAMPLE_RATE).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                    .setBufferSizeInBytes(Math.max(minimum, 8192)).setTransferMode(AudioTrack.MODE_STREAM).build();
            synchronized (session.lock) {
                session.track = track;
                if (session.cancelled) return;
                track.play();
            }
            short[] buffer = new short[1024];
            long submitted = 0;
            while (!session.cancelled) {
                int count = session.renderer.render(buffer, 0, buffer.length);
                if (count == 0) break;
                int offset = 0;
                while (offset < count && !session.cancelled) {
                    int written = track.write(buffer, offset, count - offset, AudioTrack.WRITE_BLOCKING);
                    if (written < 0) throw new IllegalStateException("오디오 재생 오류 (" + written + ")");
                    if (written == 0) {
                        if (!session.cancelled) Thread.sleep(5);
                        continue;
                    }
                    offset += written;
                    submitted += written;
                }
            }
            // Let the final queued samples play before releasing the track.
            while (!session.cancelled && Integer.toUnsignedLong(track.getPlaybackHeadPosition()) < submitted)
                Thread.sleep(10);
            completed = !session.cancelled;
        } catch (Exception problem) {
            if (!session.cancelled) error = problem.getMessage() == null ? "오디오를 재생할 수 없습니다." : problem.getMessage();
        } finally {
            synchronized (session.lock) {
                if (session.track != null) {
                    session.track.release();
                    session.track = null;
                }
            }
            final String finalError = error;
            final boolean finalCompleted = completed;
            main.post(() -> {
                if (current == session) {
                    current = null;
                    manager.abandonAudioFocusRequest(focus);
                    listener.onStopped(finalCompleted, finalError);
                }
            });
        }
    }

    private static final class Session {
        final Object lock = new Object();
        final AudioRenderer renderer;
        volatile boolean cancelled;
        AudioTrack track;
        Session(AudioRenderer renderer) { this.renderer = renderer; }
    }
}
