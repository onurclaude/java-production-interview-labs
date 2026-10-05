package com.javalabs.atomic.lab;

/**
 * @param reserved          slot gerçekten alındı mı? Sadece true ise release yapılmalıdır.
 * @param observedActive    kararın verildiği andaki sayaç değeri (CAS başarılıysa: artırmadan önceki değer).
 * @param casRetries        slot için kaç kez CAS denemesi başarısız oldu.
 */
record SlotReservation(boolean reserved, int observedActive, int casRetries) {

    static SlotReservation reserved(int observedActive, int casRetries) {
        return new SlotReservation(true, observedActive, casRetries);
    }

    static SlotReservation rejected(int observedActive, int casRetries) {
        return new SlotReservation(false, observedActive, casRetries);
    }
}
