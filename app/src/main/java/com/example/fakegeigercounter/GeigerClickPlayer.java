package com.example.fakegeigercounter;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.os.Handler;
import android.util.Log;

import java.util.Random;

public class GeigerClickPlayer {
    private static final String TAG = "GeigerClickPlayer";

    private final SoundPool soundPool;
    private final int clickSoundId;
    private final Handler handler = new Handler();
    private final Random random = new Random();

    // Parametri di configurazione
    private static final float MIN_FREQ = 0.2f;    // 1 click ogni 5 secondi
    private static final float MAX_FREQ = 13f;     // 13 click al secondo
    private static final int MAX_RAD = 1000;       // Valore massimo radiazioni
    private static final float PITCH_VARIATION = 0.2f; // ±10% variazione pitch
    private static final float VOLUME_VARIATION = 0.15f; // ±15% variazione volume

    private int currentRadLevel = 0;
    private boolean isEnabled = false;
    private boolean isPlaying = false;

    public GeigerClickPlayer(Context context) {
        AudioAttributes attributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();

        soundPool = new SoundPool.Builder()
                .setMaxStreams(5)
                .setAudioAttributes(attributes)
                .build();

        clickSoundId = soundPool.load(context, R.raw.geiger_click, 1);

        soundPool.setOnLoadCompleteListener((soundPool, sampleId, status) -> {
            if (status != 0) {
                Log.e(TAG, "Failed to load sound with status: " + status);
            }
        });
    }

    /**
     * Abilita o disabilita il player
     */
    public void setEnabled(boolean enabled) {
        this.isEnabled = enabled;
        if (!enabled) {
            stopPlayback();
        } else if (currentRadLevel > 0) {
            startPlayback();
        }
    }

    /**
     * Aggiorna il livello di radiazione e regola la riproduzione
     */
    public void updateRadiationLevel(int radLevel) {
        currentRadLevel = Math.min(radLevel, MAX_RAD);

        if (!isEnabled) return;

        if (currentRadLevel > 0 && !isPlaying) {
            startPlayback();
        } else if (currentRadLevel == 0 && isPlaying) {
            stopPlayback();
        }
    }

    private void startPlayback() {
        if (isPlaying) return;

        isPlaying = true;
        scheduleNextClick();
        Log.d(TAG, "Playback started");
    }

    private void stopPlayback() {
        if (!isPlaying) return;

        handler.removeCallbacksAndMessages(null);
        isPlaying = false;
        Log.d(TAG, "Playback stopped");
    }

    private void scheduleNextClick() {
        if (!isPlaying || !isEnabled || currentRadLevel <= 0) return;

        // Calcola frequenza con curva esponenziale
        float normalized = currentRadLevel / (float)MAX_RAD;
        float baseFrequency = MIN_FREQ + (MAX_FREQ - MIN_FREQ) * (float)Math.pow(normalized, 2.5);

        // Aggiungi variazione casuale
        float variedFreq = baseFrequency * (0.9f + 0.2f * random.nextFloat());
        long delayMillis = (long) (1000 / variedFreq);

        handler.postDelayed(() -> {
            if (!isPlaying || !isEnabled) return;

            playClickSound();
            scheduleNextClick();
        }, delayMillis);
    }

    private void playClickSound() {
        // Variazioni casuali per maggiore realismo
        float pitch = 1.0f + (random.nextFloat() * 2 * PITCH_VARIATION - PITCH_VARIATION);
        float volume = 1.0f - (random.nextFloat() * VOLUME_VARIATION);

        soundPool.play(clickSoundId, volume, volume, 1, 0, pitch);
    }

    public void stop() {
        setEnabled(false);
    }

    public void release() {
        stop();
        soundPool.release();
        handler.removeCallbacksAndMessages(null);
        Log.d(TAG, "Resources released");
    }
}