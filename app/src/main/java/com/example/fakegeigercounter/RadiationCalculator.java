package com.example.fakegeigercounter;

import java.util.Random;

public class RadiationCalculator {
    private static final int MIN_RSSI = -90;
    private static final int MAX_DIST = MIN_RSSI * MIN_RSSI; // Valore approssimativo per distanza minima
    private static final int MIN_THRESHOLD = 50;
    private static final int MAX_DELAY = 5000; // 5 secondi
    private long lastDetectionTime = 0;
    private int currentRssi = -100;
    private int detectionThreshold = MIN_THRESHOLD;

    public void updateRssi(int rssi) {
        this.currentRssi = rssi;
        this.lastDetectionTime = System.currentTimeMillis();
    }

    public int calculateRadiation() {
        // Se non ci sono rilevazioni recenti, usa il valore minimo
        if ((System.currentTimeMillis() - lastDetectionTime) > MAX_DELAY) {
            currentRssi = MIN_RSSI;
        }

        // Calcola la "distanza" come RSSI al quadrato

        // Mappa i valori come nell'originale Arduino
        int radVal = (int) map(currentRssi, MIN_RSSI, 10,
                0, 1000);

        // Applica i limiti
        radVal = Math.max(0, Math.min(1000, radVal));

        // Aggiorna la soglia di rilevamento
        detectionThreshold = (radVal <= 6) ? MIN_THRESHOLD : 1000;

        return radVal;
    }

    // Funzione di mappatura simile a quella di Arduino
    private float map(float x, float in_min, float in_max, float out_min, float out_max) {
        return (x - in_min) * (out_max - out_min) / (in_max - in_min) + out_min;
    }

    public boolean shouldTriggerPulse() {
        // Simula il comportamento randomico dell'originale
        return new Random().nextInt(distance()) < detectionThreshold;
    }

    private int distance() {
        return currentRssi * currentRssi;
    }
}