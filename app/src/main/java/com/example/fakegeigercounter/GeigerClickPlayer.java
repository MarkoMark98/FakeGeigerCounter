package com.example.fakegeigercounter;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.os.Handler;

import java.util.Random;

public class GeigerClickPlayer {
    private final SoundPool soundPool;
    private final int clickSoundId;
    private final Handler handler = new Handler();
    private boolean isPlaying = false;
    private int currentRadLevel = 0;

    // Intervalli di frequenza (click al secondo)
    private static final float MIN_FREQ = 0.2f;    // 1 click ogni 5 secondi
    private static final float MAX_FREQ = 13f;      // 13 click al secondo
    private static final int MAX_RAD = 1000;        // Valore massimo radiazioni

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
    }

    public void updateRadiationLevel(int radLevel) {
        currentRadLevel = Math.min(radLevel, MAX_RAD);

        if (!isPlaying && radLevel > 0) {
            isPlaying = true;
            scheduleNextClick();
        } else if (radLevel == 0) {
            stop();
        }
    }

    private void scheduleNextClick() {
        if (!isPlaying) return;

        // Applica una curva esponenziale per maggiore realismo
        float normalized = currentRadLevel / (float)MAX_RAD;
        float frequency = MIN_FREQ + (MAX_FREQ - MIN_FREQ) * (float)Math.pow(normalized, 2.5);

        // Aggiungi piccola variazione casuale (±10%)
        float variedFreq = frequency * (0.9f + 0.2f * new Random().nextFloat());

        long delayMillis = (long) (1000 / variedFreq);

        handler.postDelayed(() -> {
            // Modifica leggermente il pitch per maggiore realismo
            float pitch = 1.0f + (new Random().nextFloat() * 0.2f - 0.1f);
            soundPool.play(clickSoundId, 1f, 1f, 1, 0, pitch);

            scheduleNextClick();
        }, delayMillis);
    }

    public void stop() {
        isPlaying = false;
        handler.removeCallbacksAndMessages(null);
    }

    public void release() {
        stop();
        soundPool.release();
    }
}