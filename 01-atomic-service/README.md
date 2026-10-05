# Atomic Primitives Production Lab

`AtomicInteger`, `AtomicLong` ve CAS'ın gerçek bir backend servisinde **nerede işe yaradığını, neyi çözdüğünü ve neyi çözmediğini** çalışan bir Spring Boot servisi üzerinde gözlemlemek için hazırlanmış lab.

Akış her lab'da aynı: **BAD → OBSERVE → DEBUG → GOOD → VERIFY**.

| | |
|---|---|
| Stack | Java 21, Spring Boot 3.5, Maven (wrapper dahil) |
| Port | `8081` |
| Dependency | Sadece `spring-boot-starter-web`. DB, Redis, Kafka yok. |
| Test | Unit test yok (bilinçli). Doğrulama gerçek HTTP + log + debugger ile yapılır. |

---

## Business Problem

Payment Service, ödemeleri dışarıdaki bir **payment provider**'a iletiyor. Provider'ın sözleşmesi:

> Aynı anda en fazla **20 aktif request** kabul edilir.

Bu limiti aşarsak gerçek hayatta provider 429/503 döner, yavaşlar ya da hesabımızı throttle eder. Yani business kuralı:

```
ACTIVE PROVIDER REQUEST COUNT <= 20
```

Provider'ın "şu an kaç request işliyorum" bilgisine erişimimiz yok; limiti **kendi tarafımızda** uygulamak zorundayız. Bu da klasik bir concurrency problemidir: Tomcat her HTTP request'i ayrı thread'de çalıştırır, Spring bean'leri singleton'dır, yani sayaç onlarca thread tarafından **paylaşılan mutable state**'tir.

Provider gerçek değil: uygulamanın içindeki `PaymentProviderSimulator` her çağrıda 500–1000 ms bekler (configurable) ve **gözlem amaçlı** olarak "aynı anda kaç request gördüm" bilgisini tutar. Simulator limit uygulamaz; sadece ölçer. Limit mekanizması (lab'lar) ile ölçüm mekanizması (simulator) bilinçli olarak ayrıdır.

---

## Architecture

```
          HTTP Request (Postman / load script)
                       |
              AtomicLabController           -> HTTP status mapping (200 / 503 / 502)
                       |
   +---------+---------+---------+-------------+
   |         |         |         |             |
PlainInt  CheckThen   CasPayment CounterLeak   AtomicLabAdminService
PaymentLab ActLab      Lab        PaymentLab    (stats / reset)
   |         |         |   \      /   |
   |         |         |  CasSlotLimiter (AtomicInteger + compareAndSet)
   +---------+---------+-------------+
                       |
             PaymentProviderClient          -> LabMetrics (AtomicLong sayaçlar)
                       |
         +-------------+--------------+
         | LOCAL                      | REMOTE (multi-instance lab)
 PaymentProviderSimulator      HTTP -> başka instance'taki
   (aynı JVM)                    /api/simulated-provider/charge
```

| Paket | Sorumluluk |
|---|---|
| `controller` | HTTP ↔ lab eşlemesi, status code'lar. İş mantığı yok. |
| `lab` | Her lab'ın limit mantığı (BAD ve GOOD implementasyonlar). |
| `provider` | Provider simulator + client (LOCAL/REMOTE). |
| `metrics` | JVM-local `AtomicLong` sayaçları. |
| `dto` | Response modelleri. |
| `config` | `atomic-lab.*` ayarları. |

### Konfigürasyon (`application.yml`)

| Property | Varsayılan | Anlamı |
|---|---|---|
| `atomic-lab.provider-concurrency-limit` | `20` | Business limiti (ve simulator'ın kapasite referansı) |
| `atomic-lab.provider.min-delay` / `max-delay` | `500ms` / `1000ms` | Provider çağrısının simüle süresi |
| `atomic-lab.race-window` | `50ms` | **LAB ONLY.** Lab 1-2'de check ile act arasındaki yapay bekleme |
| `atomic-lab.cas-race-window` | `5ms` | **LAB ONLY.** CAS döngüsünde `get()` ile `compareAndSet()` arasındaki yapay bekleme |
| `atomic-lab.provider.mode` | `LOCAL` | `REMOTE` = provider başka instance'ta (multi-instance lab'ı) |

Komut satırından override: `java -jar target/atomic-service.jar --atomic-lab.provider-concurrency-limit=5`

> **Yapay bekleme hakkında dürüst not:** `race-window` bug'ı *yaratmaz*, var olan pencereyi genişletir. Gerçek sistemde check ile act arasında preemption, GC pause, araya giren log/metric çağrısı gibi nedenlerle mikrosaniyeler geçer. Pencereyi `0ms` yapıp aynı yükü gönderdiğimizde de limit ihlal edildi (aşağıdaki ölçümlere bakın); fark, ihlalin miktarının ve görülüp görülmeyeceğinin şansa kalmasıdır. Production bug'ları tam da bu yüzden "ayda bir, yoğun trafikte" görünür.

---

## Çalıştırma

```bash
cd 01-atomic-service
./mvnw clean package          # Windows: mvnw.cmd clean package
java -jar target/atomic-service.jar
```

IntelliJ: `AtomicServiceApplication` → Run/Debug. Port `8081`.

> 8081 başka bir uygulama tarafından kullanılıyorsa: `java -jar target/atomic-service.jar --server.port=8181` (IntelliJ'de *Program arguments*). Load script'te URL'yi de buna göre değiştirin.

### Postman

`postman/atomic-lab.postman_collection.json` dosyasını Postman'e **Import** edin. `baseUrl` değişkeni `http://localhost:8081`.

Her lab için önerilen sıra: **Reset → lab endpoint'i → Stats**.

Tek request'ler Postman'den okunaklı şekilde incelenebilir. Ancak race condition için request'lerin **gerçekten aynı anda** sunucuya varması gerekir. Postman Collection Runner request'leri sıralı gönderdiğinden concurrent yük için aşağıdaki script'i kullanın, sonucu Postman'den `Stats` ile inceleyin.

### Concurrent load script

Ek araç gerektirmez (Apache Bench/k6 yok).

```bash
# Git Bash / Linux / macOS  (curl >= 7.68)
./scripts/run-concurrent-requests.sh <url[,url2,...]> [requestCount=50] [concurrency=50]

# Windows PowerShell
.\scripts\run-concurrent-requests.ps1 -Url <url[,url2,...]> -Requests 50 -Concurrency 50
# Execution policy engeli varsa:
powershell -ExecutionPolicy Bypass -File .\scripts\run-concurrent-requests.ps1 -Url ...
```

Script ne yapar?
1. Verilen URL'e `requestCount` adet **POST** gönderir; aynı anda en fazla `concurrency` tanesi uçuşta olur. Birden fazla URL verilirse request'ler round-robin dağıtılır (multi-instance lab'ı için).
2. Bash versiyonu tek bir `curl --parallel` process'i kullanır. 50 ayrı curl process'i başlatmak (özellikle Windows'ta) request'leri zamana yayar ve race'i zayıflatır. PowerShell versiyonu .NET `HttpClient` ile aynı anda async request başlatır (host başına 2 bağlantı olan .NET Framework limiti yükseltilir).
3. HTTP status dağılımını basar: `200` = provider'a gitti, `503` = limit dolu, `502` = provider hatası.
4. Sonunda her instance'ın `GET /api/labs/atomic/stats` çıktısını basar.

---

## Endpoint'ler

| Method | Path | Ne yapar |
|---|---|---|
| POST | `/api/labs/atomic/plain-int` | Lab 1 - BAD: `int` sayaç |
| POST | `/api/labs/atomic/check-then-act` | Lab 2 - BAD: `AtomicInteger.get()` + `incrementAndGet()` |
| POST | `/api/labs/atomic/cas` | Lab 3 - GOOD: `compareAndSet` slot rezervasyonu |
| POST | `/api/labs/atomic/leak/bad` | Lab 4 - BAD: release `finally`'de değil |
| POST | `/api/labs/atomic/leak/good` | Lab 4 - GOOD: release `finally`'de |
| GET | `/api/labs/atomic/stats` | Lab sayaçları, metrics, provider gözlemi |
| POST | `/api/labs/atomic/reset` | **LAB ONLY.** Tüm lab state'ini sıfırlar |
| POST/GET | `/api/simulated-provider/charge`, `/stats`, `/reset` | Multi-instance lab'ındaki ortak "external provider" |

Bütün ödeme endpoint'leri opsiyonel `?fail=true` alır: provider o çağrıda **deterministik** olarak hata döner (random failure yok).

### Response'lar

**200 ACCEPTED** (provider'a gitti, başarılı):
```json
{
  "implementation": "CAS",
  "outcome": "ACCEPTED",
  "paymentId": "PAY-12",
  "limit": 20,
  "activeRequestsSeenAtDecision": 2,
  "providerInFlightAtCall": 3,
  "casRetries": 2,
  "thread": "http-nio-8081-exec-12",
  "durationMs": 822,
  "observationNote": "Değerler bu thread'in karar anındaki snapshot'ıdır; ..."
}
```

**503 REJECTED** (limit dolu, `Retry-After: 1` header'ı ile): aynı gövde, `outcome: "REJECTED"`.
503 seçimi: bu client'ın hatası değil, sunucunun downstream kapasitesinin anlık dolu olması (bulkhead full). 429 genelde "bu client çok sık istek atıyor" (rate limit) anlamı taşır.

**502 PROVIDER_FAILED** (provider hata döndü): hata arkadaki sistemden geliyor, 500 dönüp bırakmıyoruz.

> **Observation semantics:** `activeRequestsSeenAtDecision` ve `providerInFlightAtCall` bu thread'in **kendi karar anında** gördüğü değerlerdir. Response client'a ulaştığında başka thread'ler bu değerleri çoktan değiştirmiştir. "Sistem şu an böyle" diye değil, "bu request karar verirken bunu gördü" diye okuyun. Sistem geneli için `/stats`.

### Stats

```json
{
  "instance": "atomic-service@8081",
  "limit": 20,
  "providerMode": "LOCAL",
  "activeRequests": { "plainInt": 0, "checkThenAct": 0, "cas": 0, "leakBad": 0, "leakGood": 0 },
  "metrics": {
    "totalRequests": 50, "acceptedRequests": 20, "rejectedRequests": 30,
    "successfulPayments": 20, "failedPayments": 0, "casRetryCount": 779
  },
  "provider": {
    "scope": "LOCAL_JVM", "capacity": 20, "currentInFlight": 0,
    "observedMaxConcurrency": 20, "totalCalls": 20,
    "capacityViolationCalls": 0, "capacityRespected": true
  }
}
```

- `activeRequests.*`: uygulamanın "şu an kaç slot dolu **sanıyorum**" bilgisi (her lab'ın kendi sayacı).
- `provider.observedMaxConcurrency`: provider'ın **gerçekte** aynı anda gördüğü maksimum request. Lab'ın asıl doğrulama metriği budur.
- `provider.capacityViolationCalls`: provider'a, içeride zaten ≥20 request varken giren çağrı sayısı.
- `metrics` sayaçları tüm lab'lar için ortaktır. Lab'lar arasında mutlaka **Reset** çağırın.

---

## Lab 1 — Plain int Race Condition

**Sınıf:** `lab/PlainIntPaymentLab.pay()`

### BAD kodun mantığı

```java
private int activeRequests;              // singleton bean -> tüm request thread'leri paylaşır

int seen = activeRequests;               // CHECK
if (seen >= limit) return rejected;
// (LAB ONLY: race-window bekleme)
++activeRequests;                        // ACT
try { provider.charge(); }
finally { activeRequests--; }
```

### Nasıl çağrılır?

```bash
curl -X POST http://localhost:8081/api/labs/atomic/reset
./scripts/run-concurrent-requests.sh http://localhost:8081/api/labs/atomic/plain-int 50 50
```
Postman: önce `Reset`, yük script'i çalışırken/sonra `Stats`.

### Ne gözlemlenir? (gerçek ölçüm)

```
50 x 200 ACCEPTED          <- hiçbiri reddedilmedi
provider.observedMaxConcurrency: 50   (limit 20)
provider.capacityViolationCalls: 30
activeRequests.plainInt: -3           <- yük bitti, sayaç 0'a dönmedi!
```

Log:
```
INFO  [http-nio-8081-exec-20] PlainIntPaymentLab       : CHECK passed PAY-41: read activeRequests=0 (< 20)
INFO  [http-nio-8081-exec-43] PlainIntPaymentLab       : CHECK passed PAY-40: read activeRequests=0 (< 20)
WARN  [http-nio-8081-exec-3 ] PlainIntPaymentLab       : LIMIT VIOLATED PAY-19: check saw 0, after ++ value is 21 > limit=20
WARN  [http-nio-8081-exec-37] PaymentProviderSimulator : PROVIDER CAPACITY EXCEEDED: inFlight=32 > capacity=20 (new observed max)
```
`++` sonrası değerin 50'ye değil 21-23'e çıkması da lost update'in kanıtı: 50 artışın bir kısmı birbirini ezdi. Aynı yük bir başka çalıştırmada sayacı **-13**'te bıraktı.

### Neden yanlış?

Üç ayrı problem birden var:

1. **Check-then-act:** kontrol ile artırma ayrı adımlar. 50 thread'in hepsi `0` okudu, hepsi kontrolü geçti.
2. **Lost update:** `activeRequests++` = oku → +1 → yaz. İki thread 7 okuyup ikisi de 8 yazarsa bir artış kaybolur. `finally`'deki `--` için de aynısı. Sonuç: sayaç yük sonrası **-3** / **-2** / **+1** gibi değerlerde kaldı. Negatif sayaç sonraki yükte limitin sessizce 23'e çıkması demektir.
3. **Visibility:** alan `volatile` değil; bir thread'in yazdığını diğerinin ne zaman göreceği Java Memory Model'e göre garanti değil.

`volatile` eklemek sadece 3'ü çözer; 1 ve 2 devam eder.

---

## Lab 2 — AtomicInteger Check-Then-Act

**Sınıf:** `lab/CheckThenActPaymentLab.pay()`

### "int yerine AtomicInteger kullandım, çözüldü" yanılgısı

```java
int seen = activeRequests.get();                  // atomic
if (seen >= limit) return rejected;
// (LAB ONLY: race-window bekleme)
int after = activeRequests.incrementAndGet();     // atomic
try { provider.charge(); }
finally { activeRequests.decrementAndGet(); }
```

`get()` atomic, `incrementAndGet()` atomic. Ama business operation **"limit dolu değilse slot al"** ve bu iki ayrı atomic çağrıdan oluşuyor. **Atomic primitive kullanmak bütün business operation'ı otomatik olarak atomic hale getirmez.**

```
Thread A: get() -> 19        kontrol geçti
Thread B: get() -> 19        kontrol geçti
Thread A: incrementAndGet() -> 20
Thread B: incrementAndGet() -> 21   <- limit aşıldı; incrementAndGet() kontrolü tekrar yapmaz
```

### Nasıl reproduce edilir?

```bash
curl -X POST http://localhost:8081/api/labs/atomic/reset
./scripts/run-concurrent-requests.sh http://localhost:8081/api/labs/atomic/check-then-act 50 50
```

### Ne gözlemlenir? (gerçek ölçüm)

```
50 x 200 ACCEPTED
provider.observedMaxConcurrency: 50
activeRequests.checkThenAct: 0          <- sayaç DOĞRU, 0'a döndü
```
```
WARN [http-nio-8081-exec-35] CheckThenActPaymentLab : LIMIT VIOLATED PAY-9: get() saw 0, incrementAndGet() returned 44 > limit=20
```

**Lab 1 ile kritik fark:** burada lost update yok, sayaç her zaman doğru sayıyor ve yük bitince tam 0'a dönüyor. Buna rağmen limit ihlal ediliyor. **"Sayaç doğru" ≠ "invariant korunuyor".** Atomiklik, korumak istediğiniz invariant'ın (`active <= limit`) tüm okuma+yazma adımlarını kapsamalı.

---

## Lab 3 — CAS

**Sınıflar:** `lab/CasPaymentLab.pay()`, `lab/CasSlotLimiter.tryReserve()` / `release()`

### compareAndSet çözümü

```java
while (true) {
    int current = activeRequests.get();
    if (current >= limit) return rejected;
    // (LAB ONLY: cas-race-window bekleme)
    if (activeRequests.compareAndSet(current, current + 1)) return reserved;
    casRetryCount.incrementAndGet();          // başkası araya girdi -> güncel değerle tekrar karar ver
}
// ...
try { provider.charge(); } finally { release(); }
```

`compareAndSet(current, current + 1)` tek bölünmez adımda şunu yapar: *"değer hâlâ benim kontrol ettiğim `current` ise artır, değilse hiçbir şey yapma ve `false` dön."* Kontrol eski bir değere dayanıyorsa yazma **asla** gerçekleşmez. Lab 2'deki "kontrol geçti ama arada değer değişti" durumu imkânsız hale gelir. Pencere ne kadar büyük olursa olsun (yapay bekleme dahil) doğruluk bozulmaz.

Sırf bu lab için yazılmış bir şey değil: `Semaphore`, `ReentrantLock` (AQS), `ConcurrentHashMap`, `AtomicInteger.updateAndGet` içeride hep bu CAS-loop desenini kullanır.

### CAS retry ve contention

CAS başarısız olduğunda bu bir hata değil; "kararını eski veriyle verdin, tekrar oku ve tekrar karar ver" sinyalidir. Döngü güncel değeri okur ve **limiti yeniden kontrol eder**.

Ama lock-free, maliyetsiz demek değil: N thread aynı sayaç için yarışırsa her turda sadece biri kazanır, N-1'i boşa CPU harcayıp tekrar dener. Yüksek contention'da CAS döngüsü **busy-spin**'e dönüşür; CPU yakar, cache line çekirdekler arasında sürekli el değiştirir (cache coherence trafiği). `casRetryCount` bu boşa giden işi görünür kılar.

### Nasıl verify edilir? (gerçek ölçüm)

```bash
curl -X POST http://localhost:8081/api/labs/atomic/reset
./scripts/run-concurrent-requests.sh http://localhost:8081/api/labs/atomic/cas 50 50
```

```
20 x 200 ACCEPTED
30 x 503 REJECTED
provider.observedMaxConcurrency: 20     <- limit asla aşılmadı
provider.capacityViolationCalls: 0
metrics.casRetryCount: 779
activeRequests.cas: 0
```
```
INFO [http-nio-8081-exec-23] CasPaymentLab : SLOT RESERVED PAY-3: CAS(0 -> 1) succeeded after 0 failed attempts
INFO [http-nio-8081-exec-26] CasPaymentLab : SLOT RESERVED PAY-18: CAS(1 -> 2) succeeded after 1 failed attempts
INFO [http-nio-8081-exec-12] CasPaymentLab : SLOT RESERVED PAY-12: CAS(2 -> 3) succeeded after 2 failed attempts
INFO [http-nio-8081-exec-35] CasPaymentLab : REJECT PAY-38: active=20 >= limit=20 (casRetries=20)
```
Desen net: her turda bir thread kazanıyor, diğerleri tekrar deniyor; 20. slot dolduktan sonra kalanlar retry'dan çıkıp limit kontrolünde reddediliyor.

Her CAS failure'ı ayrı ayrı görmek için `application.yml`'de:
```yaml
logging.level.com.javalabs.atomic.lab.CasSlotLimiter: DEBUG
```
```
DEBUG [http-nio-8081-exec-7] CasSlotLimiter : [cas] CAS FAILED: expected 3 but value is now 4 -> retry #1
```

### Yapay bekleme olmadan ne olur? (`race-window=0ms`, `cas-race-window=0ms`, aynı 50'lik yük)

| Lab | Run 1 | Run 2 |
|---|---|---|
| plain-int `observedMax` | 50 (sayaç -2) | 25 (sayaç +1) |
| check-then-act `observedMax` | 24 | 21 |
| cas `observedMax` | 20 (retry 12) | 20 (retry 1) |

BAD implementasyonlar yapay bekleme olmadan da limiti aştı, ama miktar şansa bağlı. CAS her koşulda 20'de kaldı. Retry sayısı bekleme olmadan çok düşük: gerçek pencere nanosaniyeler. Yüksek contention'ın gerçek maliyeti çok çekirdekli, çok yüksek RPS'li sistemlerde ortaya çıkar.

---

## Lab 4 — Counter Leak

**Sınıf:** `lab/CounterLeakPaymentLab.payBad()` / `payGood()`

İki implementasyon da slot'u **aynı doğru CAS mekanizmasıyla** alır; tek fark release'in nerede yapıldığı. Bug'ın kaynağı izole: concurrency değil, **exception path**. BAD ve GOOD ayrı sayaç kullanır.

### BAD — release mutlu yolun sonunda

```java
reserve();
provider.charge(paymentId, fail);   // exception atarsa...
release();                          // ...bu satır hiç çalışmaz -> slot sızar
```

### Nasıl reproduce edilir?

Postman ile (adım adım):
1. `POST /api/labs/atomic/reset`
2. `POST /api/labs/atomic/leak/bad?fail=true` → **502**, sonra `GET /stats` → `activeRequests.leakBad: 1`
3. Toplam 20 kez tekrarlayın (veya script: `./scripts/run-concurrent-requests.sh "http://localhost:8081/api/labs/atomic/leak/bad?fail=true" 19 19`)
4. `GET /stats`:
   ```
   activeRequests.leakBad: 20
   provider.currentInFlight: 0      <- provider'da tek bir aktif request yok!
   ```
5. Sağlıklı bir request: `POST /api/labs/atomic/leak/bad` (fail yok) → **503 REJECTED**, `activeRequestsSeenAtDecision: 20`

```
WARN [http-nio-8081-exec-29] CounterLeakPaymentLab : REJECT PAY-21 (leak-bad): counter says 20 >= limit=20 -> is the provider really busy? (check provider.currentInFlight)
```

Production'daki karşılığı: provider sağlıklı, uygulama sağlıklı (health check yeşil), CPU boşta, ama **hiçbir ödeme geçmiyor**. Restart edince düzelir (sayaç heap'te sıfırlanır), sonra hatalar biriktikçe tekrar bozulur. "Her birkaç günde bir restart gerekiyor" şikayetinin klasik kaynaklarından biri.

### GOOD — reserve try'ın dışında, release finally'de

```java
SlotReservation r = tryReserve();
if (!r.reserved()) return rejected;     // reddedilen request finally'ye hiç girmez
try {
    provider.charge(paymentId, fail);
} finally {
    release();                          // başarı, exception, timeout, interrupt... her yolda
}
```

Aynı adımları `/leak/good` ile tekrarlayın (gerçek ölçüm):
```
20 x 502 PROVIDER_FAILED
activeRequests.leakGood: 0              <- her failure sonrası slot geri verildi
POST /leak/good -> 200 ACCEPTED
40 concurrent /leak/good -> 20 x 200, 20 x 503, leakGood: 0, "release without matching" log'u: 0
```

**İkinci yaygın bug** (kodda yorum olarak gösterildi): reserve'ü `try`'ın **içine** koymak. Reddedilen request de `finally`'ye girer ve hiç almadığı slot'u geri verir; sayaç negatife kayar, limit sessizce genişler. Kural: **sadece gerçekten aldığın kaynağı serbest bırak.** Ek emniyet olarak `CasSlotLimiter.release()` sayacı 0'ın altına indirmez ve eşleşmeyen release'i `ERROR` olarak loglar (gerçek sistemde alarm sebebi).

---

## AtomicLong Metrics

**Sınıf:** `metrics/LabMetrics`

```java
private final AtomicLong totalRequests = new AtomicLong();
public long recordRequest() { return totalRequests.incrementAndGet(); }
```

Neden doğal kullanım?
- Her sayaç **tek başına** anlamlı; sayaçlar arasında "ikisi birlikte tutarlı olmalı" gibi bir invariant yok. Tek bir değişkenin atomic artırılması yeterli. (Lab 2'deki limitte ise "kontrol + artır" birlikte atomic olmak zorundaydı.)
- Kimse bu sayaçlara bakıp karar vermiyor; sadece gözlemleniyor. Okunan değerin bir an sonra eskimesi sorun değil.
- Lock gerekmez, lost update olmaz.

Sınırlar:
- **JVM-local.** 3 pod = 3 ayrı sayaç; pod restart = sıfır. Gerçek observability'de Micrometer counter'ları her pod'dan toplanır (Prometheus vb.) ve aggregation orada yapılır. Bu sınıf onun yerine geçmez.
- `snapshot()` sayaçları tek tek okur; dönen değerler aynı ana ait tutarlı bir fotoğraf değil.
- `recordRequest()`'in döndürdüğü sıra numarası `paymentId` için kullanılıyor: JVM içinde benzersiz, ama 3 pod'da her biri `PAY-1`'den başlar. **Global ID olarak kullanılamaz.**
- Çok yüksek yazma contention'ında `LongAdder` daha iyi ölçeklenir (hücrelere bölünmüş sayaç, okuma sırasında toplanır). Yazma çok, okuma nadirse tercih edilir.

Simulator'daki `observedMaxConcurrency.getAndAccumulate(now, Math::max)` da ayrı bir örnek: "yeni değer büyükse yaz" da bir check-then-act. `get()` + `set()` ile yazılsaydı iki thread birbirinin maksimumunu ezebilir, **ölçümün kendisi** yanlış olurdu.

---

## Multi-instance Problem

`CasPaymentLab` tek JVM'de doğru. Peki 3 pod?

```
Pod A: AtomicInteger -> en fazla 20  ✓
Pod B: AtomicInteger -> en fazla 20  ✓
Pod C: AtomicInteger -> en fazla 20  ✓
                         ─────────────
Provider'a toplamda:    60  ✗   (sözleşme: 20)
```

CAS'ın garantisi **tek bir bellek adresi** üzerindedir. Farklı makinelerdeki JVM'ler aynı adresi paylaşmaz. Her pod kendi içinde kusursuz çalışır ve global limit yine ihlal edilir. Load balancer yükü eşit dağıtsa da bu değişmez; sadece her pod'un limitini `20/3` yapmak da kırılgan bir çözümdür (pod sayısı autoscaling ile değiştiğinde limit de değişmeli, dağılım eşit değilse kapasite boşa gider).

### Kendiniz gözlemleyin

Tek bir instance "external provider" rolünü üstlenir, diğer üçü `remote-provider` profiliyle ona HTTP çağrısı yapar. Böylece **provider'ın toplamda gördüğü** concurrency ölçülebilir. Her biri ayrı terminalde:

```bash
# Ortak "external provider" (8090)
java -jar target/atomic-service.jar --server.port=8090

# Payment Service instance'ları
java -jar target/atomic-service.jar --server.port=8081 --spring.profiles.active=remote-provider   # Instance 1
java -jar target/atomic-service.jar --server.port=8082 --spring.profiles.active=remote-provider   # Instance 2
java -jar target/atomic-service.jar --server.port=8083 --spring.profiles.active=remote-provider   # Instance 3
```
IntelliJ'de: Run configuration → *Modify options → Allow multiple instances*, her biri için *Program arguments*'a yukarıdaki argümanlar. Provider farklı porttaysa: `--atomic-lab.provider.remote-base-url=http://localhost:XXXX`.

90 concurrent request, 3 instance'a round-robin:
```bash
./scripts/run-concurrent-requests.sh http://localhost:8081/api/labs/atomic/cas,http://localhost:8082/api/labs/atomic/cas,http://localhost:8083/api/labs/atomic/cas 90 90
curl http://localhost:8090/api/simulated-provider/stats
```

Gerçek ölçüm:
```
60 x 200 ACCEPTED, 30 x 503 REJECTED
Instance 1: acceptedRequests=20  rejectedRequests=10  casRetryCount=386
Instance 2: acceptedRequests=20  rejectedRequests=10  casRetryCount=390
Instance 3: acceptedRequests=20  rejectedRequests=10  casRetryCount=390
Shared provider: observedMaxConcurrency=60, capacityViolationCalls=40, capacityRespected=false
```

Her instance'ın `/stats` → `provider.scope` alanı `SHARED_REMOTE` gösterir. Tekrar denemeden önce: her instance'ta `/api/labs/atomic/reset` ve provider'da `POST http://localhost:8090/api/simulated-provider/reset`.

**Sonuç: AtomicInteger / CAS = JVM-local concurrency çözümü. Distributed concurrency başka bir problemdir.** Global limit için tüm instance'ların paylaştığı bir koordinasyon noktası gerekir (paylaşılan bir sayaç/token store, tek bir egress gateway/proxy, provider'ın kendi rate-limit cevaplarına uyum). Bu çözümler bu lab'ın kapsamı dışında.

---

## Debugging Guide

IntelliJ'de **Debug** ile başlatın. Concurrency debug'ında kritik ayar: breakpoint'e sağ tık → **Suspend: Thread** (varsayılan *All* tüm thread'leri durdurur, race'i görünmez yapar). Debugger açıkken race penceresi zaten devasa olur; bu sefer yük script'i yerine **2–3 request** yeterli (Postman'den hızlıca 2 kez Send ya da script ile `... 3 3`).

| Lab | Breakpoint | Ne gözlemlenmeli |
|---|---|---|
| 1 | `PlainIntPaymentLab.pay()` → `int seen = activeRequests;` satırının bir altı | İki thread'de `seen` aynı değer. Sonra `++activeRequests` satırında birini ilerletin, diğerinde alanın değerine bakın. |
| 2 | `CheckThenActPaymentLab.pay()` → `if (seen >= limit)` satırı | Thread A `get()=19`, Thread B `get()=19`. Önce A'yı, sonra B'yi `incrementAndGet()` üzerinden geçirin: A → 20, B → 21. Debugger'da `limit=2` ile daha kolay: `--atomic-lab.provider-concurrency-limit=2` |
| 3 | `CasSlotLimiter.tryReserve()` → `if (activeRequests.compareAndSet(...))` satırı | Thread A ve B aynı `current` ile durur. A'yı ilerletin → `true`. B'yi ilerletin → `false`, `retries++` satırına girer, döngü başına döner ve **yeni** değeri okur. |
| 3 | `CasSlotLimiter` (log, breakpoint yok) | `DEBUG` log ile `CAS FAILED: expected X but value is now Y -> retry #n` |
| 4 BAD | `CounterLeakPaymentLab.payBad()` → `badLimiter.release()` satırı, `?fail=true` ile | Breakpoint'e **hiç gelinmez**. Exception provider'dan fırlar, release atlanır. Sonra `/stats` → `leakBad` arttı. |
| 4 GOOD | `CounterLeakPaymentLab.payGood()` → `finally { goodLimiter.release(); }` | `?fail=true` ile bile buraya gelinir. |
| Provider | `PaymentProviderSimulator.charge()` → `recordObservedConcurrency(nowInFlight)` | Conditional breakpoint: `nowInFlight > 20` → limit ihlalini yakalayan thread'i anında durdurur. |

Log'larda her satırda thread adı var (`[http-nio-8081-exec-12]`). Aynı `paymentId`'yi grep'leyerek bir request'in CHECK → ACT → provider yolculuğunu takip edebilirsiniz.

---

## Production Trade-offs

### Atomic primitive ne zaman uygun?
- **Tek bir değişken** üzerindeki invariant'lar: sayaç, maksimum, flag, "bir kere çalış" (`compareAndSet(false, true)`), sequence.
- Kritik bölge çok kısa ve lock tutup başka iş yapmaya gerek yok.
- JVM-local state yeterli: per-pod bulkhead, per-pod metrics, local cache istatistiği.

### Ne zaman uygun değil?
- Invariant **birden fazla değişkeni** kapsıyorsa (ör. `balance` ve `reservedAmount` birlikte tutarlı olmalı). İki ayrı `AtomicLong` bunu sağlayamaz; ya tek bir immutable state'i `AtomicReference` ile CAS'lamak ya da lock gerekir.
- Kritik bölge içinde I/O veya uzun iş varsa (CAS döngüsünde I/O = tekrar tekrar I/O).
- Limit/state **global** olmak zorundaysa (multi-pod).
- Slot boşalmasını **beklemek** gerekiyorsa: CAS sadece "dene, olmazsa reddet" der. Bekleme/kuyruk/timeout lazımsa `Semaphore.tryAcquire(timeout)` gibi daha zengin bir yapı gerekir.

### Semaphore ve synchronized ile karşılaştırma
- **Semaphore:** Bu business problemi gerçek projede Semaphore ile de modellenebilir (`tryAcquire()` / `release()`, timeout'lu bekleme, fairness). Hatta production'da genelde tercih edilen budur, çünkü niyeti doğrudan ifade eder. Ancak burada amacımız CAS davranışını gözlemlemek olduğu için bilinçli olarak CAS kullanıyoruz. Semaphore `02-concurrency-service`'te ele alınacak. (Semaphore da JVM-local'dir; multi-pod problemi aynen geçerli.)
- **synchronized:** `synchronized (lock) { if (active < limit) active++; }` de doğrudur ve okunması kolaydır. Fark: contention'da thread'ler **bloklanır** (park/unpark, context switch); CAS'ta thread hiç bloklanmaz, başarısız olursa tekrar dener. Kısa kritik bölgede düşük-orta contention'da CAS genelde daha hızlıdır. Çok yüksek contention'da CAS boşa spin eder, lock ise thread'leri uyutup CPU'yu korur. Kritik bölge büyüdükçe (birden çok değişken, koşullar) lock'un okunabilirliği ve doğruluğu öne geçer. Not: provider çağrısını `synchronized` içinde yapmak tüm ödemeleri serileştirir. Lock sadece slot alma/verme için tutulmalı.

### CAS ne zaman contention yaratır?
- Çok sayıda thread **aynı** değişkeni **sık** güncellediğinde (hot counter). Her turda bir kazanan, N-1 retry.
- Okuma ile CAS arasındaki iş uzadıkça (pencere büyüdükçe) çakışma olasılığı artar; `cas-race-window` ile bunu deneyebilirsiniz: 5ms → ~780 retry, 0ms → 1–12 retry.
- Azaltma yolları: sayacı parçalamak (`LongAdder` / striping), işi CAS penceresinin dışına almak, backoff, ya da gerçekten bekleme gerekiyorsa lock/Semaphore.

### JVM-local state ne zaman problem?
- **Birden fazla pod:** limit pod sayısıyla çarpılır (bkz. Multi-instance).
- **Restart / deploy:** sayaçlar sıfırlanır. Rolling deploy sırasında eski ve yeni pod'lar birlikte çalışırken toplam kapasite geçici olarak artar.
- **Autoscaling:** pod sayısı değişince efektif global limit de değişir.
- **Leak:** Lab 4'teki gibi sızan state sadece o pod'u etkiler; health check yeşilken o pod sessizce trafik reddeder.

### External provider yavaşlarsa ne olur?
Slot'lar daha uzun tutulur → daha çok request 503 alır. Bu doğru davranış (bulkhead provider'ı korur ve thread'lerimizi tüketmez), ama provider çağrısında **timeout** yoksa provider hiç cevap vermediğinde slot'lar sonsuza kadar dolu kalır: Lab 4'tekiyle aynı semptom. `finally` slot'u ancak çağrı bir şekilde bittiğinde geri verir. Gerçek client'ta read timeout zorunludur (remote modda `PaymentProviderClient` read timeout kullanıyor).

### Production'da bozulduğunu nasıl anlarız?
- `activeRequests` gauge'u sürekli limitte, ama provider latency'si normal ve provider tarafı düşük yük görüyor → leak.
- Rejected (503) oranı artarken provider'a giden RPS düşük → leak veya yanlış limit.
- Provider'dan 429/limit hataları geliyor ama hiçbir pod limitte değil → multi-pod / global limit problemi.
- Yük sonrası sayaç 0'a dönmüyor (negatif/pozitif) → non-atomic güncelleme veya eşleşmeyen release.

---

## Interview Questions

**AtomicInteger kullanmak business operation'ı otomatik olarak atomic yapar mı?**
Hayır. Sadece tek bir metod çağrısı (`get`, `incrementAndGet`, `compareAndSet`) atomic'tir. `if (get() < limit) incrementAndGet()` iki ayrı atomic işlemdir ve arada başka thread değeri değiştirebilir. Invariant'ı koruyan tüm oku-karar ver-yaz adımlarının tek bir atomik işlemde (CAS döngüsü, `updateAndGet`, lock) olması gerekir. Lab 2: sayaç hep doğru, limit yine aşılıyor.

**CAS nedir ve neden retry gerekir?**
Compare-And-Swap: "bellekteki değer beklediğim değerse yenisini yaz, değilse yazma" işlemini tek CPU instruction'ıyla (x86'da `LOCK CMPXCHG`) yapar. Başarısızlık "başka thread araya girdi, kararın eski veriye dayanıyor" demektir. Güncel değeri okuyup **kararı yeniden vermek** (limit kontrolü dahil) için retry gerekir. Retry'da kontrolü atlamak Lab 2'deki hatayı geri getirir.

**ABA problemi nedir? Bu lab'da neden AtomicStampedReference kullanmadık?**
ABA: değer A → B → A değişir; CAS sadece "değer hâlâ A mı" diye baktığı için aradaki değişikliği fark etmez. Değerin **kimliğinin/geçmişinin** önemli olduğu durumlarda sorun olur (ör. lock-free stack'te node'un free edilip tekrar kullanılması, versiyonlu state). Bu lab'da sayaç saf bir **sayı**: değer 5 → 6 → 5 olduysa "şu an 5 slot dolu" bilgisi yine doğru ve CAS'ın kararı (5 < 20 ise 6 yap) yine geçerli. Geçmişin önemi yok, dolayısıyla ABA zararsız. `AtomicStampedReference` (değer + versiyon damgası) gerçekten gerektiği bir senaryoda `02-concurrency-service`'te ele alınacak.

**AtomicInteger ile distributed limit uygulanabilir mi?**
Hayır. AtomicInteger tek JVM'in heap'indeki bir değişkendir; CAS garantisi tek bir bellek adresi içindir. N pod = N bağımsız sayaç, efektif limit N × 20. Lab'da 3 instance, her biri 20'de kalırken provider 60 gördü. Global limit için paylaşılan bir koordinasyon noktası gerekir.

**synchronized ile CAS arasındaki temel trade-off nedir?**
synchronized pesimist: önce kilidi al, sonra işi yap; contention'da thread'ler bloklanır (context switch maliyeti) ama CPU yakmaz, çok değişkenli/karmaşık kritik bölgeleri basitçe korur. CAS optimist: kilitsiz dene, çakışırsa tekrar dene; kısa kritik bölge ve düşük-orta contention'da daha hızlı, deadlock yok, ama yüksek contention'da boşa spin eder ve sadece tek değişkenlik (ya da tek referanslık) state'i doğrudan korur.

**finally neden kritik?**
Alınan kaynak (slot, lock, connection, permit) her çıkış yolunda geri verilmeli: başarı, checked/unchecked exception, timeout, interrupt. Release'i sadece mutlu yola koymak her hatada kaynak sızdırır; sistem hatalar biriktikçe sessizce kapasitesini kaybeder (Lab 4: 20 hata sonrası provider boşken tüm trafik 503). İkinci kural: reserve `try`'ın dışında olmalı ki alınmamış kaynak release edilmesin (yoksa sayaç negatife kayar, limit genişler).

**High contention altında CAS neye dönüşebilir?**
Busy-spin'e: thread'ler sürekli okuyup başarısız CAS yapar, CPU yakar ve cache line çekirdekler arasında sürekli el değiştirir. Throughput artmaz, düşebilir. Teorik olarak lock-free algoritmalar "bir thread mutlaka ilerler" der ama tek tek thread'ler için starvation mümkündür. Çözümler: `LongAdder` gibi striping, backoff, işi CAS penceresinden çıkarmak veya lock/Semaphore'a geçmek.

**AtomicLong hangi tip counter'larda mantıklıdır?**
Bağımsız, tek değişkenli, karar için değil gözlem için okunan sayaçlarda: request/error/rejected sayıları, JVM-local sequence, basit istatistikler. Okuma da sık ise (değeri anlık okuyup kullanıyorsanız) AtomicLong; yazma çok yoğun ve okuma nadirse `LongAdder`. Production'da genelde doğrudan Micrometer `Counter` kullanılır (içeride benzer yapılar) ve pod'lar arası toplama metrics sisteminde yapılır.

**`volatile int` kullansak yeterli olur muydu?**
Hayır. `volatile` visibility ve ordering sağlar, atomicity sağlamaz. `count++` yine oku-artır-yaz üç adımdır ve lost update olur; check-then-act de aynen devam eder.

---

## Reset Endpoint Hakkında

`POST /api/labs/atomic/reset` **sadece lab amaçlıdır, production business endpoint'i değildir.** Production'da in-memory bir concurrency sayacını dışarıdan sıfırlamak, uçuştaki request'lerin muhasebesini bozar: onların `finally` blokları sıfırlanmış sayacı negatife düşürür ve limit sessizce genişler. Bu yüzden endpoint, bu instance'tan çıkmış ve henüz dönmemiş provider çağrısı varken **409 Conflict** döner. Reset'i yük bittikten sonra çağırın.

Provider simulator'ın `inFlight` sayacı reset'te sıfırlanmaz (kendini try/finally ile dengeleyen gerçek bir sayaçtır); sadece `observedMaxConcurrency` ve çağrı sayaçları sıfırlanır.

---

## Bilinen Limitler

- Provider simulator limit aşıldığında reddetmez, sadece ölçer. Gerçek provider 429/503 dönebilir veya yavaşlayabilir.
- `race-window` / `cas-race-window` LAB ONLY yapay beklemelerdir; kodda yorumla işaretlidir.
- Tomcat varsayılan 200 worker thread ile çalışır; 200'den fazla eşzamanlı request'te kuyruklama Tomcat seviyesinde başlar.
- Postman Collection Runner request'leri sıralı gönderir; race için script kullanın.
