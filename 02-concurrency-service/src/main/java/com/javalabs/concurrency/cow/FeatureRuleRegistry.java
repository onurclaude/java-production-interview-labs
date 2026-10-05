package com.javalabs.concurrency.cow;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Lab 14 — CopyOnWriteArraySet: "Runtime Enabled Feature/Validation Rule Registry".
 *
 * <p>List'ten (Lab 13) farkı YALNIZCA okuma/iterate maliyeti değil — asıl ihtiyaç UNIQUENESS'tir.
 * Aynı rule iki kez "enable" edilmeye çalışıldığında DUPLICATE OLUŞMAMALIDIR (örn. "FRAUD_STRICT_MODE"
 * iki kez eklenirse, bir List'te iki kez kontrol çalışır; bir Set'te tek kalır). CopyOnWriteArraySet
 * bunu backing'inde bir CopyOnWriteArrayList kullanarak sağlar: add() çağrısı önce eleman var mı diye
 * kontrol eder, VARSA hiçbir kopyalama yapmadan false döner.
 *
 * <p>Aynı read-heavy/write-rare karakteristik ve aynı write maliyeti (O(n) kopyalama) burada da geçerlidir.
 */
@Component
public class FeatureRuleRegistry {

    private final CopyOnWriteArraySet<String> rules =
            new CopyOnWriteArraySet<>(Set.of("FRAUD_STRICT_MODE", "PRICING_V2"));

    public List<String> list() {
        return rules.stream().sorted().toList();
    }

    public AddRuleResult add(String name) {
        // CopyOnWriteArraySet.add(): eleman ZATEN varsa false döner ve array'i KOPYALAMAZ.
        // Bu, uniqueness garantisinin hem doğruluk hem de maliyet açısından nasıl çalıştığını gösterir.
        boolean added = rules.add(name);
        String reason = added ? null : "Rule already enabled - duplicate ignored (Set uniqueness guarantee)";
        return new AddRuleResult(name, added, rules.size(), reason);
    }

    public RemoveRuleResult remove(String name) {
        boolean removed = rules.remove(name);
        return new RemoveRuleResult(name, removed, rules.size());
    }

    public void reset() {
        rules.clear();
        rules.addAll(Set.of("FRAUD_STRICT_MODE", "PRICING_V2"));
    }
}
