package com.javalabs.atomic.lab;

import java.time.Duration;

/**
 * LAB ONLY.
 *
 * <p>Bu bekleme production kodu değildir; race condition'ı deterministik şekilde görünür hale getirmek
 * için lab amacıyla kullanılmıştır.
 *
 * <p>Neden gerçekçi? Gerçek sistemde check ile act arasında da zaman geçer: thread'in CPU'dan düşürülmesi
 * (preemption), GC pause, araya eklenmiş bir log/metric/validation çağrısı... Bu pencere genelde
 * mikrosaniyeler sürdüğü için bug "ayda bir, yoğun trafikte" görünür. Burada pencereyi milisaniyelere
 * genişleterek aynı bug'ı her çalıştırmada görünür yapıyoruz. Bug'ı yaratan bekleme değil, kodun
 * kendisidir: bekleme sadece zaten var olan pencereyi büyütür.
 */
final class LabOnlyDelay {

    private LabOnlyDelay() {
    }

    static void widenRaceWindow(Duration window) {
        if (window.isZero()) {
            return;
        }
        try {
            Thread.sleep(window);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
