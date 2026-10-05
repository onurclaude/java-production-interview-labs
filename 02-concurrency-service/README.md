# Java Concurrency & Thread Management Production Lab

Business context: **Order Processing Service**. Bu servis Java concurrency API'lerini syntax olarak
öğretmez. Her lab şu soruyu cevaplar: *"Gerçek bir Spring Boot backend'de bu aracı hangi problem için,
neden kullanırım — ve neyin alternatifi DEĞİLDİR?"*

Port: **8082** · Java 21 · Spring Boot · Maven · PostgreSQL/Kafka/Redis **YOK** · Unit test **YOK**.

---

## Architecture

```
                              HTTP
                               |
                      Order Processing Lab
                               |
      +------------+-----------+-----------+------------+
      |            |           |           |            |
   Thread       Executor   Coordination   Locks      ABA / COW
      |            |           |           |            |
 Platform      Fixed Pool    Latch      RWLock     AtomicStampedReference
 Virtual       Bounded +    Barrier    Stamped     CopyOnWriteArrayList
 CPU-bound     Rejection    Phaser     Lock         CopyOnWriteArraySet
      |            |
      +----- CustomerCheckSimulator (generic blocking downstream call)
      |
 Virtual Downstream (BAD/GOOD)
      |
 FraudProviderSimulator (maxConcurrency=10, hard limit)
      |
 Semaphore Bulkhead (GOOD) ----^
```

`CustomerCheckSimulator`, Lab 1/2/3/4'te BİLİNÇLİ olarak aynı simulator'dır — dört thread modelini aynı
iş yükü üzerinde adil şekilde karşılaştırmak için. `FraudProviderSimulator` ise gerçek bir concurrency
limiti UYGULAR (aşılırsa hata fırlatır) ve sadece Lab 5/6'da kullanılır.

---

## Çalıştırma

IntelliJ IDEA'dan `ConcurrencyServiceApplication` main class'ını çalıştırın, veya:

```bash
cd 02-concurrency-service
./mvnw spring-boot:run
```

Uygulama `http://localhost:8082` üzerinde ayağa kalkar. Postman koleksiyonu: `postman/concurrency-lab.postman_collection.json`.
Concurrent HTTP yük için: `scripts/run-concurrent-requests.sh` / `.ps1`.

### Global stats / reset

```
GET  /api/labs/concurrency/stats   — lab'lar arası aggregate gözlem (God Object DEĞİL, sadece okuma)
POST /api/labs/concurrency/reset   — LAB ONLY, sayaçları/cache'leri temiz state'e döndürür
```

---

## Güvenlik Sınırları

Her request body'sindeki sayılar `application.yml -> concurrency-lab.safety` altındaki üst sınırlarla
kontrol edilir (örn. `taskCount <= 500` platform thread için, `<= 5000` virtual thread için, CPU-bound'da
`<= 32` task ve `<= 5_000_000` work unit). Amaç: yanlış/kötü niyetli bir input'un (örn. `taskCount=500000`
raw platform thread) makineyi kilitlemesini önlemek. Sınır aşılırsa `400 INVALID_INPUT` döner — exception
yutulmaz, sebep response'ta görünür.

---

## Lab 1 — Platform Thread

**Problem nedir?** Her order check'i (customer/pricing/shipping çağrısı) blocking I/O içerir. Az sayıda,
nadiren çalışan blocking task için her birine kendi OS thread'ini açmak GAYET DOĞALDIR.

**Neden bu primitive?** `new Thread(...)` en basit, en açık modeldir — havuzlama/queue/rejection
karmaşıklığı yoktur. Az sayıda bağımsız task için fazladan abstraction gereksizdir.

**Nasıl reproduce ederim?** `POST /api/labs/threads/platform` body: `{"taskCount":50,"delayMs":300}`.

**Ne gözlemlemeliyim?** `maxObservedConcurrency` ~= `taskCount` (hiçbir sınırlama yok, hepsi paralel
koşar). `taskCount`'u 500'e (güvenlik sınırı) çıkarıp elapsedMs'in nasıl davrandığını izleyin.

**Production'da neye dikkat etmeliyim?** taskCount binlere çıktığında (örn. aniden gelen 5000 concurrent
request) her biri kendi OS thread'ini açarsa: thread başına ~1MB stack, OS scheduler'a kayıt, context-switch
maliyeti — sistem "thread oluşturamıyorum" hatasıyla çökebilir. Bu lab "Platform Thread kötüdür" demez;
**sınırsız sayıda** platform thread kötüdür.

---

## Lab 2 — FixedThreadPool

**Problem nedir?** Platform Thread lab'ındaki sınırsızlığı önlemek için worker sayısını sabitlemek isteriz.

**Neden bu primitive?** `Executors.newFixedThreadPool(poolSize)` — task'lar sabit sayıda worker'a GİRER,
fazlası bekler. Raw thread oluşturmaktan daha kontrollüdür: eşzamanlı OS thread sayısı `poolSize` ile sınırlıdır.

**Nasıl reproduce ederim?** `POST /api/labs/executor/fixed` body: `{"taskCount":100,"poolSize":10,"delayMs":500}`.

**Ne gözlemlemeliyim?** `maxObservedConcurrency` **asla** `poolSize`'ı aşmaz (test: 30 task/poolSize=5 ->
`maxObservedConcurrency=5`, `elapsedMs`~6 dalga). 100 task/10 worker'da ~10 task aynı anda, diğerleri sırada.

**Production'da neye dikkat etmeliyim?** `Executors.newFixedThreadPool()` içeride **unbounded**
`LinkedBlockingQueue` kullanır. `poolSize`'dan fazla task gelirse SESSİZCE queue'da birikir — reddedilmez.
Sürekli arrival-rate > service-rate olursa queue sınırsız büyür (memory leak, artan latency). **"FixedThreadPool
kullandım, production-safe oldum" YANLIŞTIR.** Bkz. Lab 3.

---

## Lab 3 — Bounded Executor & Rejection

**Problem nedir?** Lab 2'nin unbounded queue riski: yük sürekli kapasiteyi aşarsa sistem "yavaş yavaş
ölür" (latency artar, memory büyür) ama hiçbir sinyal vermez.

**Neden bu primitive?** `ThreadPoolExecutor` + `ArrayBlockingQueue` (sabit kapasite) + custom
`RejectedExecutionHandler`. Queue dolduğunda task SESSİZCE birikmek yerine HEMEN reddedilir — bu
**backpressure**'dır: "şu an kaldıramıyorum" sinyalini erken ve açıkça vermek, sessizce kuyruğa atıp
sistemi yavaşça batırmaktan çok daha iyidir.

**Nasıl reproduce ederim?** `POST /api/labs/executor/bounded` body: `{"taskCount":40,"delayMs":300}`
(core=5, max=10, queue=10 — `application.yml`'den, request'ten DEĞİL).

**Ne gözlemlemeliyim?** Gerçek test sonucu: `accepted=20, rejected=20, maxObservedActiveWorkers=10,
maxObservedQueueSize=10`. 20 = maxPoolSize(10) + queueCapacity(10); fazlası (20) reddedildi.

**Production'da neye dikkat etmeliyim?** Bounded queue + rejection, capacity planning'i ZORUNLU kılar:
reject edilen request'i ne yapacaksınız (retry? 503 döndür? farklı bir kuyruğa mı atarsınız?) düşünülmeli.
Rejection politikası yoksa (AbortPolicy) exception'ın nereye gittiğini bilmelisiniz — burada custom handler
ile sessizce SAYIYORUZ (throw etmiyoruz), gerçek bir controller'da bu genelde `503 Service Unavailable` +
`Retry-After` olarak client'a yansıtılır.

---

## Lab 4 — Virtual Thread

**Problem nedir?** Lab 1/2/3'teki OS thread sınırlaması: binlerce concurrent blocking I/O task'ı ifade
etmek, binlerce OS thread'i meşgul etmeden mümkün mü?

**Neden bu primitive?** Virtual Thread, blocking çağrı (burada `Thread.sleep`) sırasında **carrier**
(platform) thread'i serbest bırakır. Binlerce task, az sayıda OS thread üzerinde "mount/unmount" edilerek
çalışabilir.

**Nasıl reproduce ederim?** `POST /api/labs/threads/virtual` body: `{"taskCount":500,"delayMs":300}`
(AYNI `CustomerCheckSimulator`, Lab 1 ile karşılaştırılabilir).

**Ne gözlemlemeliyim?** `allTasksObservedVirtual=true` (`Thread.currentThread().isVirtual()==true` her
task'ta doğrulanıyor), `maxObservedConcurrency` ~= `taskCount` (500 task "aynı anda" blocking I/O'da
görünebilir — 500 platform thread açmadan).

**Production'da neye dikkat etmeliyim?** "Virtual Thread her şeyi hızlandırır" YANLIŞTIR. Blocking I/O'da
muazzam fayda sağlar; CPU-bound işte sağlamaz (bkz. "CPU-bound vs I/O-bound"). Ayrıca Virtual Thread
sayısının çokluğu, DOWNSTREAM'in (DB connection pool, external provider) bu kadar çoğu kaldırabileceği
anlamına GELMEZ — bkz. Lab 5.

---

## Lab 5 — Virtual Threads Do Not Protect Downstream (BAD)

**Problem nedir?** External Fraud Provider aynı anda en fazla 10 concurrent request kabul ediyor
(`concurrency-lab.provider.fraud.max-concurrency`). Uygulama 500 Virtual Thread açabiliyor diye provider'ı
kontrolsüz çağırırsa ne olur?

**Neden bu primitive (ya da neden YOK)?** Bu lab BİLİNÇLİ OLARAK hiçbir gate/limit KULLANMAZ — tam olarak
bu eksikliği göstermek için var.

**Nasıl reproduce ederim?** `POST /api/labs/virtual/downstream/bad` body: `{"requestCount":100}`.

**Ne gözlemlemeliyim?** Gerçek test sonucu: `accepted=10, overloadedOrRejected=90,
maxObservedProviderConcurrency=100, providerLimitRespected=false`. Provider'ın gerçek kapasitesi (10) 10
kat aşıldı; 90 çağrı `ProviderOverloadedException` ile reddedildi (HTTP `502 PROVIDER_OVERLOADED`).

**Production'da neye dikkat etmeliyim?** "500 Virtual Thread oluşturabiliyorum" ile "downstream 500
concurrent request kaldırabilir" TAMAMEN FARKLI ŞEYLERDİR. Virtual Thread **task execution modelini**
ölçekler; downstream'in (DB pool, external API) kapasitesini DEĞİŞTİRMEZ. HikariCP pool'u, rate limit'ler,
provider SLA'ları Virtual Thread sayısından bağımsız sabit kalır.

---

## Lab 6 — Semaphore Bulkhead (GOOD)

**Problem nedir?** Lab 5'teki overload'u önlemek: Virtual Thread'in ölçeklenebilirliğinden feragat etmeden
downstream'i korumak.

**Neden bu primitive?** `Semaphore(10)` — provider'a aynı anda en fazla 10 çağrının ulaşmasını GARANTİ eder.
`tryAcquire → provider.check() → finally release` deseni zorunludur: `release()` finally DIŞINDA olsaydı,
provider exception fırlattığında (örn. simulated failure) permit asla geri verilmez ve Semaphore zamanla
"sahte doluluğa" kilitlenirdi (gerçek kapasite varken kabul etmemeye başlardı) — Semaphore'un en klasik bug'ı.

**Nasıl reproduce ederim?** `POST /api/labs/virtual/downstream/good` body:
`{"requestCount":100,"useTimeout":false,"timeoutMs":0}` (veya `useTimeout:true,timeoutMs:500`).

**Ne gözlemlemeliyim?** Gerçek test sonucu (aynı 100 request, Lab 5 ile karşılaştırın): `accepted=100,
overloadedOrRejected=0, maxObservedProviderConcurrency=10, providerLimitRespected=true`. Hiçbir çağrı
provider limitini aşmadı; 100 request de sonunda işlendi (acquire() sınırsız bekledi).
`useTimeout=true` ile deneyin: yüksek `requestCount`'ta `timeoutMs` çok kısaysa bazı çağrılar
`rejectedOrTimedOut` olarak sayılır (acquire() yerine tryAcquire(timeout) kullanıldığında).

**Production'da neye dikkat etmeliyim?** **Virtual Thread ile Semaphore birbirinin ALTERNATİFİ DEĞİLDİR.**
Virtual Thread *task execution modelini* ölçekler; Semaphore *downstream concurrency kapasitesini* korur.
İkisi birlikte kullanılır: Virtual Thread çok task'ı ucuza ifade eder, Semaphore bunların downstream'e aynı
anda ulaşan kısmını sınırlar. `acquire()` sınırsız bekler (yük arttığında bekleyen sayısı sessizce büyür);
`tryAcquire(timeout)` production endpoint'lerinde genelde daha güvenlidir (request thread'i süresiz asılı
kalmaz).

### Semaphore'un multi-pod sınırı

`01-atomic-service`'teki JVM-local dersi burada devam eder: `Semaphore(10)` SADECE BU JVM için 10 request
sınırlar. 3 pod çalışıyorsa (Pod A=10, Pod B=10, Pod C=10) provider'a toplamda **30** concurrent request
gidebilir. Global provider limiti 10 ise, local Semaphore TEK BAŞINA yeterli DEĞİLDİR — gerçek bir
distributed rate limiter (örn. Redis tabanlı) gerekir. **Bu lab bunu IMPLEMENT ETMEZ**, sadece sınırı
doğru anlamanız için burada belirtilir.

---

## Lab 7 — CountDownLatch

**Problem nedir?** Bir order'ı işlemeye devam etmeden önce 3 BAĞIMSIZ kontrolün (Customer, Fraud, Pricing)
hepsinin tamamlanmasını beklememiz gerekiyor.

**Neden bu primitive?** `CountDownLatch(3)` — "N bağımsız olayın tamamlanmasını bekleyen 1 taraf" modelidir.
Her worker `try { ... } finally { latch.countDown(); }` ile kendi tamamlanmasını bildirir.

**Nasıl reproduce ederim?** `POST /api/labs/coordination/latch` body:
`{"customerCheckDelayMs":100,"fraudCheckDelayMs":150,"pricingCheckDelayMs":80,"simulateCrashWithoutFinally":false,"awaitTimeoutMs":5000}`.

**Ne gözlemlemeliyim?** Gerçek test sonucu: `completedInTime=true, remainingCount=0, customerCheckDone=true,
fraudCheckDone=true, pricingCheckDone=true`. `simulateCrashWithoutFinally=true` ile tekrar deneyin: FraudCheck
worker'ı `countDown()`'ı finally DIŞINDA çağırır ve exception fırlatıp asla çağırmaz. Gerçek test sonucu:
`completedInTime=false, remainingCount=1, fraudCheckDone=false` — latch 1500ms timeout'ta kalıcı olarak
takılı kaldı.

**Production'da neye dikkat etmeliyim?** `countDown()` **MUTLAKA finally içinde** olmalıdır; aksi halde bir
worker'daki beklenmeyen exception, latch'i KALICI OLARAK takılı bırakır. `CountDownLatch` **ONE-SHOT**'tur:
count sıfıra indikten sonra RESET EDİLEMEZ — yeniden kullanmak için yeni bir latch gerekir (bkz. Lab 8,
CyclicBarrier reusable'dır). Production'da `await()` HER ZAMAN timeout'lu olmalı; sonsuz `await()` bir
worker crash ettiğinde request thread'ini sonsuza kadar asılı bırakır.

---

## Lab 8 — CyclicBarrier

**Problem nedir?** Bir batch'in 3 SABİT worker'ı "phase 1"i (chunk validation) bitirir ama HİÇBİRİ
"phase 2"ye (processing) diğerleri hazır olmadan geçmemelidir — peer-to-peer senkronizasyon.

**Neden bu primitive?** `CyclicBarrier(3, barrierAction)` — "birbirini bekleyen N eşit taraf" modelidir.
CountDownLatch'ten farkı: latch'te 1 taraf N olayı bekler; barrier'da N taraf BİRBİRİNİ bekler.

**Nasıl reproduce ederim?** `POST /api/labs/coordination/barrier` body:
`{"workerDelaysMs":[100,300,200],"rounds":2}`.

**Ne gözlemlemeliyim?** Gerçek test sonucu (2 round): her round'da `verified=true` — yani O ROUNDDAKİ HİÇBİR
worker'ın `phase2StartMs`'i, en yavaş worker'ın `phase1EndMs`'inden önce değil. `rounds=2`'de **AYNI
CyclicBarrier instance'ı** ikinci round için tekrar kullanıldı (round 1: phase2Start=[315,315,315], round 2:
phase2Start=[640,640,640] — barrier reset oldu ve tekrar çalıştı).

**Production'da neye dikkat etmeliyim?** `CyclicBarrier` **REUSABLE/CYCLIC**'tir: tüm parti barrier'a
ulaştığında otomatik resetlenir, aynı instance tekrar tekrar kullanılabilir (CountDownLatch bunu YAPAMAZ).
`await(timeout)` kullanın — bir worker hiç gelmezse (hata/crash) diğerleri `BrokenBarrierException`/
`TimeoutException` ile gözlemlenebilir şekilde patlar, sonsuza kadar beklemezler.

---

## Lab 9 — Phaser

**Problem nedir?** "Order Import Batch": validate → enrich → finalize fazlarından geçen bir toplu işlem.
Worker sayısı SABİT DEĞİL: bir worker fazlar arasında dinamik olarak katılabilir/ayrılabilir.

**Neden bu primitive?** `Phaser` — parti (participant) sayısı ÇALIŞMA SIRASINDA değişebilir.
`register()`/`arriveAndDeregister()` ile dinamik katılım/ayrılım desteklenir; `CyclicBarrier`'ın
yapamadığı tam olarak budur.

**Nasıl reproduce ederim?** `POST /api/labs/coordination/phaser` body:
`{"validateDelayMs":80,"enrichDelayMs":60,"finalizeDelayMs":50}`.

**Ne gözlemlemeliyim?** Gerçek test sonucu (`events` listesi): worker-0/1/2 validate'i bitirir → **MAIN**
phase 0'ın bittiğini `awaitAdvance(0)` ile bekler → worker-3'ü `register()` ile DİNAMİK OLARAK ekler →
worker-0/1/2 enrich'e girer (worker-3 de enrich'e katılır) → worker-0 VE worker-3 enrich sonrası
`arriveAndDeregister()` ile AYRILIR → finalize'ı SADECE worker-1 ve worker-2 tamamlar. Sıra HER ZAMAN
korunur (LAB ONLY `CountDownLatch` ile deterministik hale getirildi — section 40: race şansa bırakılmaz).

**Production'da neye dikkat etmeliyim?** **Dürüst not:** gerçek bir Spring microservice'te Phaser GÜNLÜK
kullanılan bir araç DEĞİLDİR. Parti sayısı sabitse `CyclicBarrier`/`CountDownLatch` zaten yeterlidir ve çok
daha basittir. Phaser'ı sadece gerçekten dinamik parti sayısı olan (nadir) senaryolarda düşünün — aksi
halde gereksiz karmaşıklıktır.

### Karar tablosu — CountDownLatch vs CyclicBarrier vs Phaser

| Soru | CountDownLatch | CyclicBarrier | Phaser |
|---|---|---|---|
| Kim kimi bekler? | 1 taraf, N olayı | N taraf, birbirini | N taraf, birbirini (çok fazlı) |
| Parti sayısı sabit mi? | N sabit (constructor'da) | Sabit | **Dinamik** (register/deregister) |
| Reusable mı? | HAYIR (one-shot) | EVET (otomatik reset) | EVET (fazlar arası) |
| Fazlı iş akışı | Tek faz | Tek faz (sadece reuse) | **Çok fazlı, her fazda farklı parti** |
| Günlük kullanım sıklığı | Sık (orchestration) | Orta (batch sync) | **Nadir** (gerçek dinamik ihtiyaç şart) |

---

## Lab 10 — ReadWriteLock

**Problem nedir?** "Local Pricing Rules Cache": fiyat kuralları ÇOK SIK okunuyor (her order için), config
reload NADİREN gerçekleşiyor.

**Neden bu primitive?** `ReentrantReadWriteLock` — readLock PAYLAŞIMLIDIR (birden fazla reader aynı anda
girebilir), writeLock EXCLUSIVE'dir (reload sırasında TÜM reader/writer'lar bloklanır). `synchronized`
okumaları da serileştirirdi; read-heavy yükte bu gereksiz bir bottleneck olurdu.

**Nasıl reproduce ederim?** `GET /api/labs/locks/read-write/price/P1`, 5 paralel `GET .../price/P2`,
`POST /api/labs/locks/read-write/reload`, `GET /api/labs/locks/read-write/stats`.

**Ne gözlemlemeliyim?** Gerçek test sonucu (5 paralel GET): `maxObservedConcurrentReaders=4` (reader'lar
GERÇEKTEN aynı anda içeride). `reload` sonrası `version` 0→1, tüm fiyatlar deterministik olarak +1.00 arttı.

**Production'da neye dikkat etmeliyim?** Write lock altında GERÇEK external I/O (örn. bir config servisinden
rule çekmek) yapmak TÜM reader'ları o süre boyunca bloklar — write lock altındaki iş minimumda tutulmalı,
I/O lock DIŞINDA hazırlanıp sadece referans değişimi lock altında yapılmalıdır (bu lab'daki `reload()`
içindeki bekleme bunun SİMÜLASYONUDUR, gerçek kod değildir). **ConcurrentHashMap yeterli olabilir miydi?**
Tek key'in get/put'u evet — ama burada ihtiyaç "TÜM price map'ini ATOMİK değiştirmek" (reader'ların yarı
güncellenmiş bir map görmemesi); bu, map referansının write lock altında atomik değişmesiyle sağlanır,
salt thread-safe erişimle değil.

---

## Lab 11 — StampedLock

**Problem nedir?** Aynı read-heavy pricing senaryosu, ama ReadWriteLock'un readLock'u YİNE DE bir lock
ALIR (reader sayacı artırma/azaltma, bazı JVM'lerde contention). Çok yüksek okuma oranında bundan daha
ucuz bir yol var mı?

**Neden bu primitive?** `StampedLock.tryOptimisticRead()` — LOCK ALMAZ, sadece o anki stamp'i okur.
`validate(stamp)` ile "okuma sırasında araya write girdi mi" kontrol edilir; girmediyse veri GEÇERLİDİR
(lock'suz, çok ucuz); girdiyse `readLock()` ile GÜVENLİ fallback yapılır.

**Nasıl reproduce ederim?** `GET /api/labs/locks/stamped/price/P1` (normal, genelde optimistic başarı),
`POST /api/labs/locks/stamped/demo/fallback/P1` (DETERMİNİSTİK fallback demosu), `GET .../stamped/stats`.

**Ne gözlemlemeliyim?** Gerçek test sonucu — normal okuma: `optimisticReadSucceeded=true`. Deterministik
demo: `optimisticStillValidAfterWindow=false`, `fallbackReadValue` dolu (optimistic read ile validate()
arasına LAB ONLY bir pencere açılıp arada GERÇEK bir reload tetiklenir — race şansa bırakılmaz, section 40).
`stats`: `optimisticReadAttempts=2, optimisticReadSuccess=1, optimisticReadFallback=1`.

**Production'da neye dikkat etmeliyim?** StampedLock HER ZAMAN ReadWriteLock yerine kullanılmamalı:
(1) **REENTRANT DEĞİLDİR** — aynı thread aynı lock'u ikinci kez almaya çalışırsa DEADLOCK olur
(ReentrantReadWriteLock bunu tolere eder); (2) optimistic read sonrası `validate()` ÖNCESİNDE okunan veri
kullanılamaz/yayılamaz — API'si kolayca yanlış kullanılabilir; (3) write SIK oluyorsa sürekli fallback'e
düşülür, bu durumda sade bir ReadWriteLock (hatta ConcurrentHashMap) daha basit ve en az o kadar hızlı
olabilir. Optimistic read'in anlamlı olduğu yer: okuma ÇOK sık, yazma ÇOK nadir.

---

## Lab 12 — ABA & AtomicStampedReference

**Problem nedir?** Runtime provider routing state'i (`ProviderRoutingState`) bir thread tarafından
okunuyor; bu arada başka bir thread A→B→A şeklinde DEĞİŞTİRİP GERİ DÖNDÜRÜYOR. "before" ve "after" aynı
(reference-equal) görünüyor — ama arada GERÇEKTEN değişti.

**Neden bu primitive?** `AtomicReference` sadece REFERANS eşitliğine bakar; A→B→A'da son değer ilk değere
(aynı static instance) geri döndüğü için "hiç değişmedi" sonucuna varır (BAD). `AtomicStampedReference`
her değişiklikte artan bir STAMP (versiyon) taşır; A→B→A iki stamp artışı yapar, bu yüzden
"value aynı AMA stamp farklı" tespiti ile aradaki değişiklik YAKALANIR (GOOD).

**Nasıl reproduce ederim?** `POST /api/labs/aba/bad`, `POST /api/labs/aba/stamped` (her ikisi de
deterministik: flip sırası `CountDownLatch` ile garanti edilir, şansa bırakılmaz).

**Ne gözlemlemeliyim?** Gerçek test sonucu — BAD: `before=Provider-A, after=Provider-A,
referenceUnchanged=true, bugDemonstrated=true`. GOOD: `before=Provider-A, after=Provider-A,
stampBefore=0, stampAfter=2, referenceUnchanged=true, reallyUnchanged=false, bugPrevented=true`.

**Production'da neye dikkat etmeliyim?** ABA, "değişmedi" varsayımına dayanan HER optimizasyonda
(cache reuse, stale-check, bazı circuit-breaker implementasyonları) sessizce yanlış sonuç üretebilir.
AtomicStampedReference'ı sadece value-equality'nin gerçekten yetersiz olduğu yerlerde kullanın — her
`AtomicReference` kullanımını stamp'li yapmak gereksiz karmaşıklıktır.

---

## Lab 13 — CopyOnWriteArrayList

**Problem nedir?** "Order Processing Listener Registry": her order işlendiğinde TÜM listener'lar
(inventory, notification...) iterate edilerek çağrılır (ÇOK SIK); yeni bir listener eklemek/kaldırmak
NADİR bir admin operasyonudur.

**Neden bu primitive?** `CopyOnWriteArrayList` — okuma/iterate ASLA lock gerektirmez ve ASLA
`ConcurrentModificationException` fırlatmaz; her write backing array'in TAMAMINI kopyalar.

**Nasıl reproduce ederim?** `GET /api/labs/cow/listeners`,
`POST /api/labs/cow/listeners/demo/concurrent-iteration` body: `{"nameToAddDuringIteration":"AuditListener"}`.

**Ne gözlemlemeliyim?** Gerçek test sonucu: `iteratedSnapshot=["InventoryListener","NotificationListener"]`
(yeni eklenen "AuditListener" İTERASYONDA GÖRÜNMEDİ — snapshot semantics), `concurrentModificationExceptionThrown=false`,
`currentFullListAfterIteration` 3 eleman içeriyor (ekleme GERÇEKTEN oldu, sadece devam eden iterasyon
onu görmedi).

**Production'da neye dikkat etmeliyim?** **Write-heavy sistemde KÖTÜ olabilir.** Her `add()`/`remove()`
O(n) bir array kopyalama maliyeti taşır. Liste büyükse (binlerce eleman) ve mutation sıksa (saniyede
yüzlerce kez), bu ciddi CPU/GC baskısı yaratır. Large-collection + frequent-writes senaryosunda
CopyOnWriteArrayList YANLIŞ seçimdir.

---

## Lab 14 — CopyOnWriteArraySet

**Problem nedir?** "Runtime Enabled Feature/Validation Rule Registry": aynı read-heavy/write-rare
karakteristik, ama bu kez asıl ihtiyaç **UNIQUENESS**'tir — aynı rule iki kez enable edilmeye çalışılırsa
duplicate OLUŞMAMALIDIR.

**Neden bu primitive?** `CopyOnWriteArraySet` (içeride `CopyOnWriteArrayList` kullanır) — `add()` önce
eleman var mı diye kontrol eder, VARSA kopyalama YAPMADAN `false` döner.

**Nasıl reproduce ederim?** `POST /api/labs/cow/feature-rules/FRAUD_STRICT_MODE` (zaten seed'de var).

**Ne gözlemlemeliyim?** Gerçek test sonucu: `{"added":false,"size":2,"reason":"Rule already enabled -
duplicate ignored (Set uniqueness guarantee)"}`. Yeni bir rule (`NEW_RULE_X`) eklenince `added:true,size:3`.

**Production'da neye dikkat etmeliyim?** Aynı write-maliyeti (O(n) kopyalama) ve aynı
read-heavy/write-rare sınırlamaları Lab 13 ile aynıdır — tek fark List'in izin verdiği duplicate'leri
Set'in engellemesidir. Liste sırası ÖNEMLİYSE List, sırası önemsiz ama benzersizlik ÖNEMLİYSE Set kullanın.

---

## CPU-bound vs I/O-bound

`POST /api/labs/threads/cpu-bound` body: `{"taskCount":32,"workUnits":5000000,"mode":"PLATFORM_POOL"}`
ve `mode":"VIRTUAL"` ile aynı workload'u çalıştırıp karşılaştırın.

Gerçek test sonucu (32 core'luk makinede, 32 task): `PLATFORM_POOL elapsedMs=52`, `VIRTUAL elapsedMs=30` —
aynı büyüklük mertebesinde, **ikisi de `availableProcessors()` tarafından sınırlanıyor**. Bu lab'da HİÇBİR
blocking I/O/sleep yoktur; her task saf CPU hesabı yapar.

- **I/O-bound** (Lab 1-4): Virtual Thread'ler ÇOK faydalı olabilir — blocking I/O sırasında carrier
  thread'i serbest bırakırlar, binlerce task az OS thread ile ifade edilebilir.
- **CPU-bound**: temel sınır `availableProcessors()`'tır. Virtual Thread bu sınırı DEĞİŞTİRMEZ; serbest
  bırakılacak bir "blocking an" yoktur, thread sürekli CPU'da çalışır. Daha çok thread açmak (virtual veya
  platform, core sayısından fazla) sadece context-switch maliyeti ekler.

---

## Platform Thread vs Virtual Thread

| | Platform Thread | Virtual Thread |
|---|---|---|
| OS thread ilişkisi | 1:1 (her thread bir OS thread) | N:M (çok virtual, az carrier/OS thread) |
| Blocking I/O maliyeti | OS thread meşgul kalır | Carrier thread serbest bırakılır |
| Stack boyutu | ~1MB (sabit, OS tarafından) | Küçük, JVM heap'inde, büyüyebilir |
| Binlerce concurrent blocking task | Pahalı/sınırlı | Doğal kullanım alanı |
| CPU-bound iş | Fark yok (core sayısı sınır) | Fark yok (core sayısı sınır) |
| ThreadLocal | Pool'da lifecycle/cleanup riski | Teknik olarak mümkün, çok yüksek task sayısında per-thread state maliyetine dikkat |
| Downstream kapasitesi (DB/provider) | İlgisiz, ayrıca yönetilmeli | İlgisiz, ayrıca yönetilmeli (bkz. Lab 5/6) |

### ThreadLocal notu

Platform thread pool dünyasında ThreadLocal'ın klasik riski: pool thread'leri YENİDEN KULLANILDIĞI için,
bir request'in bıraktığı ThreadLocal değeri bir sonraki request'e SIZABİLİR (temizlenmezse). Virtual
Thread'ler genelde TEK KULLANIMLIKTIR (task biter, thread "biter") — bu sızıntı riski daha azdır. Ancak
çok yüksek sayıda Virtual Thread'de her birinin kendi ThreadLocal state'i taşıması toplam memory/GC
maliyetine eklenir; "Virtual Thread ucuz olduğu için ThreadLocal de bedavadır" sonucu YANLIŞTIR.

### Virtual Thread Pinning (Java 21 baseline)

Java 21'de bir Virtual Thread, `synchronized` blok/method içindeyken blocking bir işlem yaparsa carrier
thread'e **pinned** kalır (unmount OLMAZ) — bu durumda Virtual Thread'in asıl kazancı (carrier'ı serbest
bırakma) o blok için kaybolur ve carrier pool'u tükenirse throughput düşebilir (deadlock değildir, ama
darboğaz olabilir). JDK, `jdk.tracePinnedThreads` JVM flag'i ile pinning noktalarını loglayabilir. Bu
davranış JEP 444 (Java 21, Virtual Threads final) kapsamında belgelenmiştir ve sonraki JDK sürümlerinde
(JEP ile `synchronized` pinning'in kaldırılması planlanmıştır) değişebilir — bu lab Java 21 baseline
kullanır ve sırf bunu göstermek için native/synchronized bir demo EKLEMEZ. Pratik öneri: Virtual Thread
içinde blocking I/O'nun `synchronized` blok yerine `java.util.concurrent.locks.ReentrantLock` ile
korunması pinning riskini azaltır.

### Spring Boot + Virtual Thread

Spring Boot 3.2+, `spring.threads.virtual.enabled=true` ile TÜM uygulamanın (örn. Tomcat request handling)
thread modelini global olarak Virtual Thread'e çevirebilir. **Bu lab bunu KULLANMAZ** — Platform/Fixed/
Bounded/Virtual farkını endpoint bazında AÇIKÇA karşılaştırabilmek için her lab kendi executor'ını
bilinçli ve görünür şekilde yönetir. Gerçek bir Spring Boot uygulamasında global virtual thread
configuration tercih edilebilir; bu lab'da karşılaştırma yapabilmek için bilinçli olarak explicit
executor kullanıyoruz.

---

## Executor Queue & Backpressure

```
newFixedThreadPool(N)                    ThreadPoolExecutor(core, max, ArrayBlockingQueue(cap), handler)
        |                                                |
   N worker, UNBOUNDED queue                    core..max worker, BOUNDED queue (cap)
        |                                                |
   task'lar hep KABUL EDİLİR                     queue dolunca YENİ task REDDEDİLİR
   (sessizce birikir, memory/latency büyür)       (backpressure: "şu an kaldıramıyorum" sinyali hemen verilir)
```

**Backpressure**: sistemin kapasitesi dolduğunda bunu HEMEN ve AÇIKÇA sinyallemesi (reject/503), sessizce
kuyruğa alıp yavaşça batmasından (unbounded queue) daha güvenlidir. Lab 2 ile Lab 3'ü aynı yük altında
karşılaştırın: Lab 2 her task'ı kabul eder (queue büyür), Lab 3 kapasiteyi aşan kısmı hemen reddeder.

---

## JVM-local vs Distributed Concurrency

Bu lab'daki TÜM coordination primitive'leri (`Semaphore`, `AtomicStampedReference`, `CopyOnWrite*`,
`ReadWriteLock`, `StampedLock`, `CountDownLatch`, `CyclicBarrier`, `Phaser`) **tek bir JVM'in bellek
alanında** yaşar.

```
Pod A: Semaphore(10) -> provider'a en fazla 10
Pod B: Semaphore(10) -> provider'a en fazla 10
Pod C: Semaphore(10) -> provider'a en fazla 10
                                    TOPLAM: provider'a en fazla 30
```

Provider'ın GERÇEK (global) limiti 10 ise, 3 pod'un her biri kendi içinde doğru davranır ama toplamda
limiti 3 kat aşabilirsiniz. Aynı ders `AtomicStampedReference` için de geçerlidir: routing state'i 3 pod'un
3 ayrı JVM'inde 3 ayrı obje olarak yaşar; bir pod'daki ABA tespiti diğer pod'ları bilmez.

**Bu lab distributed bir çözüm IMPLEMENT ETMEZ** (Redis tabanlı distributed semaphore/lock eklenmemiştir).
Gerçek bir multi-pod production sisteminde global limit için paylaşılan bir koordinasyon noktası (Redis,
ZooKeeper, veritabanı tabanlı bir lock/sayaç vb.) gerekir.

---

## Debugging Guide (IntelliJ)

| Konu | Breakpoint | Neye bakın |
|---|---|---|
| Virtual Thread | `VirtualThreadLab.runTask(...)` | `Thread.currentThread().isVirtual()`, `snap.name()` |
| Platform Thread | `PlatformThreadLab.runTask(...)` | `activeCount.get()`, thread adı `platform-task-N` |
| Bounded rejection | `BoundedThreadPoolLab` içindeki `rejectionHandler` lambda'sı | `exec.getActiveCount()`, `queue.size()` |
| Semaphore GOOD | `SemaphoreBulkheadLab.callProviderThroughGate(...)`, `acquirePermit()` sonrası | `providerGate.availablePermits()` |
| CountDownLatch | `CountDownLatchLab.safeWorker(...)` / `unsafeWorker(...)` | `latch.getCount()` |
| CyclicBarrier | `CyclicBarrierLab.runWorker(...)`, `barrier.await(...)` sonrası | `phase1End[workerId]`, `phase2Start[workerId]` |
| Phaser dinamik katılım | `PhaserLab.run(...)`, `phaser.register()` çağrısından hemen sonra | `phaser.getUnarrivedParties()`, `phaser.getPhase()` |
| ReadWriteLock concurrent reader | `PricingRulesReadWriteLockCache.read(...)`, `currentReaders.incrementAndGet()` sonrası | `currentReaders.get()` |
| StampedLock optimistic validate | `PricingRulesStampedLockCache.read(...)`, `lock.validate(stamp)` çağrısı | `stamp`, `lock.validate(stamp)` dönüş değeri |
| StampedLock fallback | `PricingRulesStampedLockCache.demoForcedFallback(...)` | `stillValid` değişkeni, `fallbackValue` |
| ABA BAD | `AbaBadRoutingService.demonstrate(...)`, `state.get()` (after okunduğu an) | `before == after` (reference identity) |
| ABA GOOD (stamp) | `AbaStampedRoutingService.demonstrate(...)`, `ref.get(stampHolder)` (after) | `stampBefore` vs `stampAfter` |
| COW snapshot semantics | `OrderProcessingListenerRegistry.demoConcurrentIteration(...)`, `iterator.next()` döngüsü | `iteratedSnapshot` büyüklüğü vs `listeners.size()` |

---

## Production Trade-offs

**AtomicInteger/CAS ne çözer?** Tek bir sayının "kontrol et + değiştir"ini atomik yapar (bkz.
`01-atomic-service`). Birden fazla bağımsız kaynağı (örn. "10 downstream çağrısı" gibi bir KAYNAK HAVUZU)
yönetmez — bunun için Semaphore daha doğal bir araçtır.

**Semaphore ne çözer?** Bir KAYNAĞA (downstream API, connection havuzu) aynı anda erişebilecek
çağıran sayısını sınırlar (bulkhead). Thread'leri yönetmez, sadece "izin" (permit) sayar.

**ExecutorService ne çözer?** Task'ların HANGİ thread modelinde ve HANGİ eşzamanlılıkla çalışacağını
yönetir (pool sizing, queue, rejection, lifecycle). Downstream kapasitesini KORUMAZ (bu Semaphore'un işi).

**CountDownLatch ne çözer?** "N bağımsız olayın HEPSİNİN bitmesini bekleyen 1 taraf" senkronizasyonu.
Tekrar kullanılamaz.

**CyclicBarrier ne çözer?** "N eşit tarafın BİRBİRİNİ beklediği" fazlı senkronizasyon. Tekrar
kullanılabilir, parti sayısı sabittir.

**Phaser ne çözer?** CyclicBarrier'ın yaptığını, parti sayısı ÇALIŞMA SIRASINDA değişebildiğinde yapar.
Günlük bir araç değildir; sadece gerçekten dinamik parti sayısı olduğunda haklıdır.

**ReadWriteLock ne çözer?** Read-heavy/write-rare bir veri yapısında READER'LARI birbirine paralel
yaparken WRITER'a exclusive erişim garanti eder.

**StampedLock ne çözer?** Aynı read-heavy senaryoda, write ÇOK NADİRSE, okumaları LOCK ALMADAN
(optimistic) yapmayı mümkün kılar — ReadWriteLock'tan daha ucuz ama daha dikkatli kullanılması gereken
bir API ile.

**CopyOnWrite collection ne çözer?** Read-heavy/write-rare bir KOLEKSİYONDA iterate ederken
ConcurrentModificationException riskini ve lock'suz okuma ihtiyacını çözer. Write-heavy'de KÖTÜDÜR.

**Virtual Thread ne çözer?** Blocking I/O içeren ÇOK SAYIDA task'ı, az OS thread kaynağıyla ifade etmeyi
mümkün kılar. CPU-bound paralellik veya downstream kapasite sorununu ÇÖZMEZ.

**Hiçbiri birbirinin alternatifi değildir:** Virtual Thread (task modeli) + Semaphore (downstream
koruması) + ExecutorService (lifecycle/pool yönetimi) genelde BİRLİKTE, farklı problemleri çözmek için
kullanılır.

### Production alternatifleri (implement edilmedi, sadece not)

- Semaphore → gerçek projede **Resilience4j Bulkhead** (metrikler, circuit-breaker entegrasyonu ile) düşünülebilir.
- Request-scope `ExecutorService` → **framework-managed executor** (Spring'in `TaskExecutor` bean'leri) düşünülebilir.
- Local pricing cache (RWLock/StampedLock) → **Caffeine** (TTL, eviction, istatistik) veya basit senaryoda **ConcurrentHashMap** düşünülebilir.
- `CountDownLatch`/`CyclicBarrier`/`Phaser` orchestration → bazı senaryolarda **CompletableFuture** (`allOf`/`anyOf`) daha doğal olabilir (bu lab'da BİLİNÇLİ olarak primitive'lerin kendisi gözlemlendiği için CompletableFuture arkasında gizlenmedi).
- Local Semaphore/AtomicStampedReference → **distributed concurrency** için local Java primitive YETERSİZDİR; Redis tabanlı bir rate limiter/lock gerekir (implement edilmedi).

---

## Interview Questions

1. **Platform Thread ile Virtual Thread arasındaki temel fark nedir?** Platform Thread 1:1 bir OS thread'e
   bağlıdır; Virtual Thread çok sayıda görevi az sayıda OS (carrier) thread üzerinde mount/unmount ederek
   çalıştırır, özellikle blocking I/O sırasında carrier'ı serbest bırakır.
2. **Virtual Thread neden CPU-bound işi otomatik hızlandırmaz?** Çünkü serbest bırakılacak bir "blocking
   an" yoktur; thread sürekli CPU kullanır. Gerçek paralellik sınırı hep `availableProcessors()`'tır.
3. **10.000 Virtual Thread neden 10.000 DB connection anlamına gelmez?** Virtual Thread task execution
   modelidir; DB connection pool (HikariCP) tamamen ayrı, sabit boyutlu bir kaynaktır. 10.000 thread, pool
   boyutundan fazla connection isterse fazlası pool'da BEKLER (ya da timeout olur).
4. **ExecutorService neden raw Thread oluşturmaktan tercih edilebilir?** Eşzamanlı thread sayısını, task
   kuyruklamasını, rejection politikasını ve lifecycle'ı (shutdown) merkezi ve kontrollü hale getirir.
5. **newFixedThreadPool'ın production riski nedir?** İçeride unbounded `LinkedBlockingQueue` kullanır;
   arrival-rate service-rate'i sürekli aşarsa queue sınırsız büyür (memory/latency riski), hiçbir rejection
   sinyali vermez.
6. **Bounded queue neden önemlidir?** Kapasiteyi aşan yükü SESSİZCE biriktirmek yerine erken ve açıkça
   reddetmeyi (backpressure) mümkün kılar; sistemin "ne zaman doluyum" sınırını somutlaştırır.
7. **Backpressure nedir?** Bir sistemin kapasitesi dolduğunda bunu çağırana HEMEN bildirmesi (reject/503)
   — sessizce kuyruğa alıp gecikmeyle/OOM ile patlamak yerine.
8. **Semaphore ile thread pool aynı problemi mi çözer?** Hayır. Thread pool, TASK'LARIN hangi thread
   modelinde çalışacağını yönetir; Semaphore, bir KAYNAĞA (genelde downstream) erişimi sınırlar — ikisi
   farklı katmanlardadır ve birlikte kullanılabilir.
9. **Semaphore neden distributed concurrency limit değildir?** JVM-local bellekte yaşar; her pod kendi
   Semaphore'una sahiptir, toplam izin sayısı pod sayısıyla çarpılır.
10. **CountDownLatch ile CyclicBarrier farkı nedir?** Latch: 1 taraf N olayı bekler, one-shot. Barrier:
    N eşit taraf birbirini bekler, reusable/cyclic.
11. **Phaser ne zaman CyclicBarrier'dan daha uygun olabilir?** Katılımcı (parti) sayısının ÇALIŞMA
    SIRASINDA değiştiği (dinamik register/deregister gerektiren) senaryolarda.
12. **ReadWriteLock ne zaman synchronized'dan avantajlı olabilir?** Okuma SIK, yazma NADİR olduğunda —
    synchronized tüm erişimi serileştirirken ReadWriteLock okumaları paralelleştirir.
13. **StampedLock optimistic read nasıl çalışır?** `tryOptimisticRead()` lock almadan bir stamp döner;
    veri okunduktan sonra `validate(stamp)` ile "arada write oldu mu" kontrol edilir; olmadıysa veri
    geçerlidir, olduysa `readLock()` ile fallback yapılır.
14. **StampedLock'ın dezavantajları nelerdir?** Reentrant değildir (aynı thread ikinci kez alırsa
    deadlock), optimistic read sonrası validate öncesi veri kullanılamaz (API'si kolay yanlış kullanılır),
    yüksek write oranında sürekli fallback'e düşer.
15. **ABA problemi nedir?** Bir değerin A→B→A şeklinde değişip başlangıç değerine geri dönmesi; sadece
    referans/value karşılaştıran bir kod bu ara değişikliği KAÇIRIR ve "hiç değişmedi" sanır.
16. **AtomicStampedReference ABA'yı nasıl tespit eder?** Her değişiklikte artan bir stamp (versiyon)
    taşıyarak; value aynı olsa da stamp farklıysa aradaki değişiklik anlaşılır.
17. **CopyOnWriteArrayList hangi workload için uygundur?** Okuma/iterate ÇOK SIK, yazma (add/remove)
    ÇOK NADİR olduğunda.
18. **Neden write-heavy sistemde kötü olabilir?** Her write backing array'in TAMAMINI kopyalar (O(n));
    sık write + büyük koleksiyon = ciddi CPU/GC maliyeti.
19. **InterruptedException neden yutulmamalıdır?** Interrupt sinyali JVM'in/çağıranın "bu thread'i iptal
    etmeye çalışıyorum" bildirimidir; yutulursa thread interrupt edildiğini unutur ve örn. bir
    shutdown/cancel isteğini görmezden gelebilir — `Thread.currentThread().interrupt()` ile restore
    edilmelidir.
20. **Lock/permit release neden finally içinde yapılmalıdır?** Kritik bölüm (veya provider çağrısı)
    exception fırlatırsa bile lock/permit'in geri verilmesini garanti eder; aksi halde kalıcı deadlock
    (lock) veya kalıcı kapasite kaybı (Semaphore) oluşur.

---

## Bilinen Limitler

- CPU-bound lab'ındaki Platform/Virtual karşılaştırması, çalıştırıldığı makinenin çekirdek sayısına
  bağlıdır; `availableProcessors()`'a yakın veya daha az `taskCount` ile anlamlı bir karşılaştırma elde
  edilir (çok fazla task, iki modda da kuyruklamaya girer ve fark görünmeyebilir).
- `BoundedThreadPoolLab`, `CountDownLatchLab`, `CyclicBarrierLab`, `PhaserLab` kalıcı/kümülatif bir sayaç
  tutmaz (her çağrı kendi sonucunu tam olarak döner); bu yüzden global `/api/labs/concurrency/stats`
  endpoint'inde görünmezler — bu bilinçli bir tasarım kararıdır (section 20: "God Object oluşturma").
- `demoForcedFallback` ve ABA demo endpoint'leri LAB ONLY deterministik koordinasyon (CountDownLatch/
  join) kullanır; bu, StampedLock/AtomicStampedReference'ın KENDİSİNİN çözümü değildir, sadece lab'ı
  tekrar üretilebilir kılan bir zamanlama garantisidir (section 40).
