# Design Patterns Production Lab — Checkout / Payment Processing Service

Port: **8086** · Java 21 · Spring Boot · Maven · DB/Kafka/Redis **YOK** · Unit test **YOK**.

## Bu proje ne öğretiyor?

Bu servis bir "Design Pattern örnekleri koleksiyonu" DEĞİLDİR. Amaç, bir backend developer'ın gerçek bir
checkout/payment servisinde şu sorulara KOD ÜZERİNDEN cevap verebilmesini sağlamaktır:

- Burada neden Strategy kullandık? Kullanmasaydık ne olurdu?
- Doğru implementation'ı KİM seçiyor — Strategy kendi kendini mi seçiyor?
- Chain of Responsibility ne zaman basit method çağrılarından daha iyidir?
- Adapter neden external provider entegrasyonunda kritiktir?
- Template Method ile Strategy arasındaki fark NE, ne zaman hangisi?
- Facade sadece "methodları tek yerde toplamak" mıdır?
- Observer neden doğrudan method çağırmaktan farklıdır — ve neden Kafka DEĞİLDİR?
- Decorator neden inheritance yerine tercih edilebilir, AOP'den farkı ne?
- Spring singleton bean neden (ve NE ZAMAN) concurrency problemi yaratır?

Her lab önce GERÇEK bir business problem gösterir, sonra (uygunsa) BAD/naive bir implementasyon, sonra
problemin TAM OLARAK nerede ortaya çıktığını, sonra pattern'in bunu nasıl çözdüğünü. **Pattern'i
göstermek için sahte problem üretilmedi** — her lab, aynı Checkout/Payment domain'inin gerçek bir
parçasıdır.

---

## Çalıştırma

```bash
cd 03-pattern-service
./mvnw spring-boot:run        # Windows: mvnw.cmd spring-boot:run
```

IntelliJ: `PatternServiceApplication` → Run. Uygulama `http://localhost:8086` üzerinde ayağa kalkar.
Postman koleksiyonu: `postman/pattern-lab.postman_collection.json`. Singleton race demosu için:
`scripts/run-singleton-race.sh` / `.ps1`.

---

## Architecture

```
                         HTTP
                          |
                 CheckoutController'lar
                          |
      +--------+----------+----------+---------+---------+--------+---------+
      |        |          |          |         |         |        |        |
  Strategy   Chain      Adapter   Template   Facade   Builder  Observer Decorator
      |        |          |          |         |         |        |        |
  Resolver  RuleChain  PaymentProvider  Abstract  CheckoutFacade (DTO)  Event   wraps
      |      (@Order)   (Adapter)    Processor      |                Publisher PaymentProvider
  PaymentStrategy          |            |      ValidationStep            |
  (CreditCard/Wallet/   BankA/BankB   BankA/BankB  PricingStep      3 bağımsız
   BankTransfer)        Adapter      Processor    PaymentStep       Listener
                            |                      Notif/AuditStep
                      BankA/BankB
                      Client (external SDK simülasyonu)

                 Singleton (bağımsız lab): @Service field'da request-specific
                 mutable state tutmanın concurrency riski
```

---

## Lab 1 — Strategy + Factory/Resolver

### Business Problem

Checkout, `CREDIT_CARD`, `WALLET`, `BANK_TRANSFER` ödemelerini destekliyor. Her yöntemin kendi
validasyonu, kendi provider çağrısı, kendi business kuralı var.

### BAD Yaklaşım

`POST /api/labs/strategy/bad` — `BadPaymentService.pay()` if/else ile payment type'a göre dallanır.

### BAD Kod Neden Büyür?

**"if kötü olduğu için" DEĞİL.** 3 case için if/else gayet okunabilir. Problem şu AN ortaya çıkar:
`BadPaymentService`, her payment type'ın TÜM detayını (hangi validasyon, hangi provider, hangi hata
mesajı) bilmeye başlar. Bugün 3 case, yarın `APPLE_PAY` gelir → bu class değişir. Sonra `GOOGLE_PAY` →
YİNE değişir. Sonra `INSTALLMENT_CARD` → YİNE değişir. Bu class'ı değiştiren herkes TÜM payment type'ları
etkileme riskiyle çalışır.

**Dürüst not:** 3 tane çok basit, HİÇ değişmeyecek case için Strategy + Resolver katmanı eklemek
OVERENGINEERING olabilir. Strategy, davranışlar BÜYÜDÜKÇE ve BAĞIMSIZ DEĞİŞTİKÇE değer kazanır.

### Good Tasarım

`PaymentStrategy` interface'i (`pay(command)`, `supports()`), 3 implementation
(`CreditCardPaymentStrategy`, `WalletPaymentStrategy`, `BankTransferPaymentStrategy` — her biri
`@Component`), ve `PaymentStrategyResolver` (Spring'in enjekte ettiği `List<PaymentStrategy>`'yi
`PaymentType -> PaymentStrategy` Map'ine çevirir).

### Pattern Nasıl Çalışıyor? — Request Gelince Adım Adım

```
1) Spring context başlar: CreditCardPaymentStrategy, WalletPaymentStrategy, BankTransferPaymentStrategy
   bean'leri oluşturulur (her biri kendi dependency'lerini — ProviderCallSimulator, config — alır).
2) PaymentStrategyResolver constructor'ında Spring, List<PaymentStrategy> parametresine BU 3 bean'i enjekte eder.
3) Resolver, listeyi supports() değerine göre bir Map'e indexler (CREDIT_CARD->.., WALLET->.., ...).
4) POST /api/labs/strategy/good {"paymentType":"CREDIT_CARD"} gelir.
5) GoodPaymentService.pay() -> resolver.resolve(CREDIT_CARD) -> Map lookup -> CreditCardPaymentStrategy.
6) GoodPaymentService, bulunan strategy'ye pay(command) der; CreditCardPaymentStrategy'nin kart
   validasyonunu NASIL yaptığını bilmez/bilmesine gerek yok.
```

**Hiçbir yerde `new CreditCardPaymentStrategy()` YOKTUR.** Manuel `new` ile oluşturursanız Spring'in
dependency injection'ını ve bean lifecycle'ını (constructor'daki provider client, config gibi
dependency'ler) BYPASS edersiniz — her dependency'yi elle geçirmeniz gerekir.

### Debugger'da Neye Bakacağım?

1. `PaymentStrategyResolver` constructor'ı → `strategies` parametresi (kaç bean enjekte edildi?)
2. `PaymentStrategyResolver.resolve()` → hangi `PaymentType` için hangi strategy döndü?
3. İlgili `*PaymentStrategy.pay()` → provider-specific validasyon/çağrı

### Neden Bu Pattern? / Alternatif Neydi?

Alternatif: manuel `switch` + `new` (BAD — Spring DI'ı bypass eder) veya tek class'ta if/else (BAD —
responsibility birikir). Strategy + Resolver, SEÇİM ve DAVRANIŞ'ı ayırır.

### Ne Zaman Kullanmamalıyım?

Payment type sayısı sabit (2-3) ve ASLA değişmeyecekse, if/else'den vazgeçmeyin.

### Production'da Nelere Dikkat Etmeliyim?

Yeni bir `PaymentType` eklenip karşılığında strategy bean'i YAZILMAZSA, `resolve()` runtime'da
`IllegalArgumentException` fırlatır — bu derleme zamanında yakalanmaz, deployment sonrası ilk gerçek
request'te patlar.

### Interview'da Nasıl Anlatırım?

"PaymentService'in büyümesini, her payment type'ın kendi class'ında izole ederek önledik; resolver
Spring-managed strategy'leri otomatik topluyor, yeni bir type eklemek mevcut kodu değiştirmiyor."

---

## Lab 2 — Chain of Responsibility

### Business Problem

Checkout tamamlanmadan önce 4 BAĞIMSIZ kontrol sırayla geçmeli: `CustomerStatusCheck`, `FraudCheck`,
`PaymentLimitCheck`, `StockCheck`. Biri başarısız olursa KALANLAR ÇALIŞMAMALI (fail-fast).

### BAD Yaklaşım / BAD Kod Neden Büyür?

`POST /api/labs/chain/bad` — `BadCheckoutValidationService.validate()`, 4 kontrolü sıralı if/return ile
yönetir. Sorun method sayısı değil: orchestrator, HER rule'un sırasını, mantığını VE fail-fast
davranışını (`markRemainingSkipped` 3 farklı yerde tekrarlanıyor) TEK BAŞINA biliyor. Yeni bir rule
(örn. "VIP müşteri limit muafiyeti") eklemek bu method'un gövdesini değiştirmeyi gerektirir.

### Good Tasarım

`CheckoutRule` interface'i (`check(context)`, `ruleName()`). 4 implementation, her biri `@Order(1..4)`
ile kendi sırasını bildirir: `CustomerStatusRule`, `FraudRule`, `PaymentLimitRule`, `StockRule`.
`CheckoutRuleChain`, Spring'in `@Order`'a göre SIRALI enjekte ettiği `List<CheckoutRule>`'u sırayla
çalıştırır, ilk `FAILED`'de durur.

### Request Gelince Adım Adım

```
POST /api/labs/chain/good {"simulateFraud": true, ...}
1) CheckoutRuleChain.run() -> rules[0]=CustomerStatusRule.check() -> PASSED
2) rules[1]=FraudRule.check() -> FAILED ("Fraud signal detected")
3) stopped=true
4) rules[2]=PaymentLimitRule -> hiç check() ÇAĞRILMAZ, sadece SKIPPED olarak işaretlenir
5) rules[3]=StockRule -> aynı şekilde SKIPPED
```

Gerçek test sonucu: `allPassed=false`, `executedRules=[PASSED, FAILED, SKIPPED, SKIPPED]` — fail-fast
gözlemlenebilir.

### Debugger'da Neye Bakacağım?

`CheckoutRuleChain.run()` içindeki for loop → her iterasyonda `rule.check(context)` sonucu ve
`stopped` flag'inin ne zaman `true` olduğu.

### Ne Zaman Kullanmamalıyım?

3 tane SABİT, hiç değişmeyecek validation için düz method çağrıları daha okunabilir olabilir — Chain,
rule sayısı arttığında, rule'lar bağımsız değiştiğinde, ordering önemli olduğunda, yeni rule eklemek SIK
olduğunda değer kazanır.

### Production'da Dikkat

Chain of Responsibility, **Spring Security'nin `SecurityFilterChain`'i** veya Servlet `FilterChain`'i ile
AYNI kavramsal fikri paylaşır: bağımsız parçalar sırayla çalışır, biri "dur" diyebilir.

---

## Lab 3 — Adapter

### Business Problem

Bizim iç modelimiz `PaymentProvider.charge(command)`. Ama Bank A ve Bank B'nin (simüle) SDK'ları
TAMAMEN FARKLI: `BankAPaymentClient.makePayment(merchantOrder, total, currency)` vs
`BankBPaymentClient.authorize(BankBPayload)` (tutar minor unit/kuruş, tek payload objesi).

### BAD Yaklaşım

`POST /api/labs/adapter/bad` — `AdapterBadPaymentService`, `BankAPaymentClient`'ı DOĞRUDAN çağırır ve
`BankAResponse.statusCode().equals("OK")` ile KENDİSİ yorumlar. Bu service artık "Bank A'nın status
code'u string 'OK'dır" bilgisini TAŞIR. Bank B eklenince bu class YENİ bir if/else ile BankB'nin şeklini
de öğrenmek zorunda kalacaktı.

### Good Tasarım

`PaymentProvider` interface'i (adapter package — Decorator lab'ında da reuse edilir).
`BankAPaymentAdapter` ve `BankBPaymentAdapter`, kendi external client'larını sarar; `BankAResponse`,
`BankBPayload`, `BankBResult` SADECE kendi adapter'larının İÇİNDE yaşar.

### Pattern Nasıl Çalışıyor?

`AdapterGoodPaymentService`, Strategy lab'ındaki resolver mantığının AYNISINI kullanır: Spring'in
enjekte ettiği `List<PaymentProvider>`'ı `providerName() -> provider` Map'ine çevirir. Bu service'in
import listesine bakın: `BankAResponse`, `BankBPayload`, `BankBResult` YOKTUR — sadece `PaymentProvider`.

### Debugger'da Neye Bakacağım?

1. `AdapterGoodPaymentService.charge()` → hangi adapter resolve edildi?
2. `BankAPaymentAdapter.charge()` / `BankBPaymentAdapter.charge()` → provider-specific mapping

### Anti-Corruption Layer İlişkisi

Adapter'ın amacı "sadece başka bir interface implement etmek" DEĞİLDİR. Asıl amaç: **dış sistemin
modelinin bizim domain/business kodumuza SIZMASINI engellemektir** — bu, Domain-Driven Design'daki
**Anti-Corruption Layer** kavramının somut bir uygulamasıdır. Yarın Bank A SDK'sı değişirse, değişiklik
mümkün olduğunca SADECE `BankAPaymentAdapter`'da kalır; `AdapterGoodPaymentService` hiç etkilenmez,
çünkü `BankAResponse` diye bir class'ın var olduğunu bile bilmez.

### Adapter vs Strategy

Karıştırılmamalı: **Strategy** business davranış ALTERNATİFLERİdir (Card/Wallet/Transfer — hepsi BİZİM
tasarladığımız, birbirine eşdeğer seçenekler). **Adapter** UYUMSUZ bir DIŞ interface'i bizim
interface'imize ÇEVİRİR (Bank A SDK'sı bizim seçimimiz değil, dışarıdan verilmiş bir gerçektir).

---

## Lab 4 — Template Method

### Business Problem

Bank A ve Bank B için ödeme akışının İSKELETİ (validate → prepare request → call provider → map
response → audit) AYNIDIR; değişen SADECE her adımın provider'a özgü İÇİDİR.

### BAD Yaklaşım / Neden Büyür?

`POST /api/labs/template/bad/{provider}` — `BankAProcessorBad` ve `BankBProcessorBad`, AYNI 5 adımlık
orkestrasyonu KARAKTER KARAKTER COPY-PASTE eder (validate bloğu ve audit log satırı birebir aynı). Ortak
akışta bir değişiklik gerekirse (örn. audit formatı), HER processor'da TEK TEK düzeltilmesi gerekir.

### Good Tasarım

`AbstractPaymentProcessor<ProviderRequest, ProviderResponse>` — `process()` method'u `final`'dır (alt
class'lar SIRAYI değiştiremez). 3 değişken adım (`prepareRequest`, `callProvider`, `mapResponse`) alt
class'larda (`BankAPaymentProcessor`, `BankBPaymentProcessor`) tanımlanır.

### Request Gelince Adım Adım

```
POST /api/labs/template/good/BANK_A
1) AbstractPaymentProcessor.process() [final, tek yerde tanımlı]
2)   validate(command)                     [ortak adım]
3)   prepareRequest(command)               [BankAPaymentProcessor'a özgü -> BankAProviderRequest]
4)   callProvider(providerRequest)         [BankAPaymentProcessor'a özgü -> BankAPaymentClient.makePayment]
5)   mapResponse(command, providerResponse) [BankAPaymentProcessor'a özgü -> statusCode=="OK" -> PaymentResult]
6)   afterPayment(result)                  [ortak adım -> log]
```

### Template Method vs Strategy

**Bu README'de özellikle ayrı bölüm:**

- **Template Method:** "Algoritmanın ANA İSKELETİ aynı, bazı adımları değişiyor." INHERITANCE
  tabanlıdır — `BankAPaymentProcessor` bir `AbstractPaymentProcessor`'DIR (is-a), compile-time'da bağlıdır.
- **Strategy:** "Bir davranışın TAMAMINI runtime'da değiştirebilmek istiyorum." COMPOSITION tabanlıdır —
  `PaymentStrategy` implementation'ları birbirinden tamamen bağımsızdır, hiçbir ortak base class'a
  bağlı değildir, runtime'da inject edilir.

**Composition genelde inheritance'tan daha ESNEKTİR**: Template Method'u her yerde kullanmak zorunda
değilsiniz — "algoritmanın iskeleti GERÇEKTEN sabit ve paylaşılan" değilse, Strategy çoğu zaman daha
az kısıtlayıcıdır (yeni bir base class hiyerarşisine bağlanmazsınız).

### Ne Zaman Kullanmamalıyım?

İskelet sabit DEĞİLSE (her provider'ın akışı gerçekten farklıysa) Template Method'u zorlamak, alt
class'ları base class'ın varsaymadığı şeyler için `@Override` ile "boşlar doldurmaya" zorlar — bu bir
code smell'dir.

---

## Lab 5 — Facade

### Business Problem

Checkout tamamlamak için 5 alt sistem sırayla çalışmalı: validation, pricing, payment, notification,
audit.

### BAD Yaklaşım

`POST /api/labs/facade/bad` — `FacadeLabController.bad()`, bu 5 adımı KENDİSİ sırayla çağırır. Controller
artık checkout'un İÇ MİMARİSİNİ (hangi alt sistem var, hangi sırada, hangisinin çıktısı diğerine girdi
olur) bilmek zorunda.

### Good Tasarım

`CheckoutFacade.checkout(request)` — controller SADECE bunu çağırır. Facade, 5 `*Step` collaborator'ını
(`CheckoutValidationStep`, `PricingStep`, `PaymentExecutionStep`, `NotificationStep`, `AuditStep`)
sırayla ÇAĞIRIR (orchestration) — BUSINESS LOGIC'İN KENDİSİNİ içine ALMAZ.

### Facade Neyi Değiştiriyor?

Facade'ın amacı "method sayısını azaltmak" DEĞİLDİR. Amacı, çağıran tarafın (controller) alt sistemlerin
VARLIĞINI bilmesine gerek KALMAMASIDIR.

### God Class Riski — Çok Önemli

`CheckoutFacade`'ın 5 `*Step` class'ına DELEGE ettiğine dikkat edin. Eğer bu Facade büyüyüp "pricing
hesaplamasını da ben yapayım, validasyon kuralını da ben yazayım" derse (yani business logic'i KENDİ
İÇİNE çekerse), 3000 satırlık "her şeyi yapan" bir God Service'e dönüşür ve pattern'in amacını kaybeder.

### Facade vs Service

"Facade = Service class'ın havalı adı" YANLIŞTIR. Facade, BİRDEN FAZLA alt subsystem'e DAHA BASİT bir
giriş noktası sunar. Her `@Service` bir Facade DEĞİLDİR — tek bir sorumluluğu olan servisler
(`PricingStep` gibi) Facade değildir, Facade'ın ORKESTRE ETTİĞİ parçalardır.

---

## Lab 6 — Builder

### Business Problem

`ProviderPaymentRequest` 9 alan taşır (4 zorunlu, 5 opsiyonel: description, callbackUrl, installment,
merchantReference, metadata).

### Neden Builder?

Klasik constructor ile: `new ProviderPaymentRequest(orderId, customerId, amount, "TRY", null, null, 0,
merchantRef, null)` — bu "telescoping constructor" problemidir: hangi `null`'ın hangi alana ait olduğunu
ezbere bilmek gerekir, iki `String` parametresinin yerini şaşırmak COMPILE-TIME'da yakalanmaz.

`ProviderPaymentRequest.builder().orderId(x).customerId(y).amount(z).installment(3).build()` — her alan
KENDİ ADIYLA set edilir, zorunlu alanlar `build()` içinde kontrol edilir (fail-fast), sonuç nesne
IMMUTABLE'dır.

### Ne Zaman Gereksiz?

2-3 alanlı basit bir DTO için Builder FAZLA KOD'dur. O durumda:

- Tüm alanlar ZORUNLUYSA → Java `record` (`record Foo(String a, int b)`) çok daha az kodla aynı
  okunabilirliği sağlar.
- Bazı alanlar opsiyonel ama az sayıdaysa → static factory method (`Foo.of(a, b)` + overload'lar).

Builder'ın kazancı: ÇOK SAYIDA opsiyonel alan + aynı tipten birden fazla parametre + immutable nesne
isteği BİRLİKTE var olduğunda ortaya çıkar.

---

## Lab 7 — Observer

### Business Problem

Payment tamamlandığında 3 bağımsız şey olmalı: email bildirimi, audit, analytics.

### BAD Yaklaşım

`PaymentService` doğrudan `emailService.send(); auditService.write(); analyticsService.track();` çağırır
— bu service artık ödemeyle ilgisi olmayan 3 subsystem'i BİLMEK zorunda.

### Good Tasarım

`ObserverPaymentService.completePayment()`, işini bitirince `PaymentCompletedEvent` yayınlar
(`ApplicationEventPublisher.publishEvent()`). `PaymentNotificationListener`, `PaymentAuditListener`,
`PaymentAnalyticsListener` (`@EventListener`) bağımsız olarak dinler.

### ÇOK ÖNEMLİ — Bu Bir Kafka Değil

- `ApplicationEventPublisher`, AYNI JVM/process İÇİNDE çalışan IN-PROCESS bir mekanizmadır.
- Varsayılan (ve bu lab'da BİLİNÇLİ OLARAK kullanılan) davranış **SENKRONDUR**: `publishEvent()`,
  TÜM listener'lar çalışıp bitene kadar BLOKE olur. Async yapmak isterseniz `@EventListener` metodunu
  `@Async` ile işaretlemeniz gerekir — ama bu durumda hata yönetimi (bir listener patlarsa ne olur?) ayrı
  bir tasarım kararı gerektirir; bu lab senkron bırakıldı ki pattern'in akışı net görülsün.
- Uygulama event publish edildikten HEMEN SONRA çökerse ve bir listener henüz çalışmadıysa, o işin
  GARANTİSİ YOKTUR — disk'e yazılmış bir mesaj kuyruğu yok, retry/replay yok. **Kafka** durable,
  distributed bir messaging altyapısıdır; `@EventListener` kullanmak Kafka kullanmakla AYNI DEĞİLDİR.

### Trade-off

**Avantaj:** `ObserverPaymentService`, side effect consumer'larının HİÇBİRİNİ bilmek zorunda değil; yeni
bir `LoyaltyPointsListener` eklemek bu servisi değiştirmez. **Dezavantaj:** control flow artık DOLAYLIDIR
— "bu event'i kim dinliyor?" takibi (özellikle büyük bir kod tabanında) zorlaşabilir; hata yönetimi
(bir listener exception fırlatırsa diğerleri çalışır mı?) önemli hale gelir.

---

## Lab 8 — Decorator

### Business Problem

`PaymentProvider` çağrısının etrafına logging ve metrics eklemek istiyoruz.

### BAD Yaklaşım

Her provider implementation'ının (BankA, BankB, ileride BankC) İÇİNE aynı logging/metrics kodunu
copy-paste etmek.

### Good Tasarım

`LoggingPaymentProviderDecorator` ve `MetricsPaymentProviderDecorator`, YİNE `PaymentProvider` implement
eder, GERÇEK provider'ı (`delegate`) sarar ve çağrıyı ona delege eder. Zincir `DecoratorLabService`
constructor'ında ELLE kurulur: `new LoggingDecorator(new MetricsDecorator(bankAAdapter))`.

### Decorator vs AOP

Spring dünyasında GENERIC, execution-time logging (her method'un etrafına aynı davranışı eklemek) için
**AOP** (`@Aspect`) genelde daha uygundur — tek bir yerde tanımlarsınız, tüm ilgili method'lara otomatik
uygulanır. **Decorator** ise, belirli bir INTERFACE/CONTRACT seviyesinde (burada `PaymentProvider`),
EXPLICIT, elle kontrol edilen bir composition istediğinizde güçlüdür — hangi decorator'ın hangi sırada
sarıldığı KOD OLARAK görünür ve her örnek için farklı olabilir (örn. test ortamında sadece Logging,
production'da Logging+Metrics).

### Decorator vs Proxy

Yapısal olarak benzerler (ikisi de aynı interface'i implement edip bir delegate'e yönlendirir). Kavramsal
fark: **Decorator** davranış EKLEMEYE odaklanır (logging, metrics gibi ek sorumluluk); **Proxy** erişimi/
kontrolü/aracılığı yönetmeye odaklanır (örn. lazy loading, erişim kontrolü, uzak nesneye vekillik). Spring
AOP'nin kendisi de arkada PROXY mekanizması (JDK dynamic proxy / CGLIB) kullanır — `@Transactional`,
`@Cacheable` gibi annotation'lar, bean'inizin etrafına Spring'in oluşturduğu bir proxy sararak çalışır.
Bu lab'da ayrı bir Proxy lab'ı YOKTUR, sadece kavramsal bağlantı not edilmiştir.

---

## Lab 9 — Singleton / Spring Singleton Bean

**Bu lab, klasik "Singleton design pattern" (private constructor + static instance) İLE Spring'in
singleton BEAN SCOPE'unu KARIŞTIRMAMAK için var.** Spring `@Service`/`@Component` bean'leri varsayılan
olarak singleton scope'tadır — context'te bu class'tan TEK bir instance vardır. Ama bu, **thread-safe**
demek DEĞİLDİR.

### BAD — Reproduce Edilen Davranış

`POST /api/labs/singleton/bad` — `BadPaymentContextService`, request'e özgü veriyi (`orderId`, `amount`)
BEAN'İN FIELD'INDA tutar. `scripts/run-singleton-race.sh bad` ile GERÇEK, DETERMİNİSTİK sonuç:

```
Request-A gönderilir: {"orderId":"ORDER-A","holdMs":2000}   -> field'a yazar, 2000ms BEKLER
    (300ms sonra, A henüz field'ı OKUMADAN)
Request-B gönderilir: {"orderId":"ORDER-B","holdMs":0}       -> AYNI bean'in field'ını YAZAR, hemen okur

Gerçek sonuç:
  Request-A response: {"requestOrderId":"ORDER-A","observedOrderId":"ORDER-B","corrupted":true}
  Request-B response: {"requestOrderId":"ORDER-B","observedOrderId":"ORDER-B","corrupted":false}
```

Request-A kendi gönderdiği "ORDER-A"yı değil, Request-B'nin yazdığı "ORDER-B"yi görüyor. **Bu, "teorik
olarak race condition var" değildir — gerçekten, her çalıştırmada reprodüklenen bir sonuçtur** (holdMs
yeterince büyük tutulduğu için, aynı `01-atomic-service`'teki `race-window` tekniğiyle aynı mantık).

### Adım Adım Ne Oluyor?

```
t=0ms    Spring context başladı, BadPaymentContextService'den TEK instance oluşturuldu.
t=0ms    Request-A, Thread-1 ile geldi. Bean.currentOrderId = "ORDER-A".
t=0ms    Thread-1, holdMs=2000 boyunca BEKLER (field'ı henüz OKUMADI).
t=300ms  Request-B, Thread-2 ile AYNI bean'e geldi. Bean.currentOrderId = "ORDER-B".
t=300ms  Thread-2 hemen okur -> kendi yazdığı "ORDER-B"yi görür -> corrupted=false (kendisi için doğru).
t=2000ms Thread-1 (A) uyanır, Bean.currentOrderId'yi OKUR -> "ORDER-B" görür (!!) -> corrupted=true.
```

### GOOD

`POST /api/labs/singleton/good` — `GoodPaymentContextService`, `orderId`/`amount`'ı SADECE method
parametresi/local variable olarak taşır. AYNI script, AYNI timing ile çalıştırıldığında:
`run-singleton-race.sh good` → **her iki request de kendi verisini görür, `corrupted` HER ZAMAN false.**
Java'da her method çağrısının local variable'ları kendi stack frame'ine aittir; iki thread aynı method'u
aynı anda çağırsa bile birbirinin local variable'ını GÖREMEZ — bu YAPISAL bir garanti, "şansa bağlı
çalışmıyor" değil.

### Yanlış Sonuç Çıkarmayın

❌ **YANLIŞ SONUÇ:** "Spring singleton bean kullanma."
✅ **DOĞRU SONUÇ:** "Singleton bean'in İÇİNDE request-specific MUTABLE STATE tutarken concurrency'yi
düşün." Stateless `@Service` çok normal ve TERCİH EDİLEN bir kullanımdır — bu lab'daki her diğer servis
(`PaymentStrategyResolver`, `CheckoutFacade`, vb.) de singleton'dır ve HİÇBİRİNDE bu problem yoktur,
çünkü hiçbiri request-specific veriyi field'da tutmaz.

### Alternatifler (Ve Neden Default Önermiyoruz)

Spring'de `@RequestScope`/`@SessionScope`/prototype scope da vardır — her request için ayrı bir bean
instance'ı oluşturulmasını sağlarlar. Ama "mutable field sorunum var, hemen `@RequestScope` yapayım"
DEFAULT çözüm DEĞİLDİR: bu, asıl problemi (gereksiz field) GİZLER, ÇÖZMEZ, ve proxy/scope yönetimi ek
karmaşıklık ekler. Doğru ilk tepki: **field'ı kaldırın, veriyi method parametresi/local variable olarak
taşıyın.** Çoğu service stateless singleton olmalı.

---

## Pattern Seçim Tablosu

| Problem | Düşünülebilecek Pattern |
|---|---|
| Bir davranışın farklı implementation'ları var | **Strategy** |
| Doğru implementation'ı seçmem gerekiyor | **Factory / Resolver** |
| Arka arkaya bağımsız kurallar çalışıyor, fail-fast gerekiyor | **Chain of Responsibility** |
| External API bizim modele uymuyor | **Adapter** |
| Algoritmanın iskeleti aynı, bazı adımları farklı | **Template Method** |
| Alt sistemleri tek giriş noktasının arkasına almak istiyorum | **Facade** |
| Karmaşık, çok opsiyonel alanlı object construction var | **Builder** |
| Bir olaydan birden fazla bağımsız component haberdar olmalı | **Observer** |
| Mevcut davranışın çevresine ek davranış eklemek istiyorum | **Decorator** |
| Spring singleton bean içinde shared mutable state var | Pattern çözmeden önce **STATELESS DESIGN** düşünün |

## Strategy vs Factory/Resolver

Aynı şey DEĞİLDİR. **Strategy** = "ödeme NASIL yapılacak?" (davranışın kendisi).
**Factory/Resolver** = "HANGİ Strategy kullanılacak?" (seçim). `PaymentStrategyResolver` seçimi YAPAR,
`CreditCardPaymentStrategy` davranışı GERÇEKLEŞTİRİR.

## Facade vs Service

Bkz. Lab 5 "Facade vs Service" bölümü.

## Observer vs Kafka

Bkz. Lab 7 "Çok Önemli — Bu Bir Kafka Değil" bölümü.

## Decorator vs AOP / Decorator vs Proxy

Bkz. Lab 8 bölümleri.

---

## Overengineering Bölümü — Pattern Kullanmamak Ne Zaman Daha İyi?

**Design Pattern kullanmak AMAÇ değildir.** Problem yoksa pattern eklemek kodu daha iyi yapmaz — daha
KARMAŞIK yapar. 2 tane basit if yerine `interface + abstract factory + registry + strategy + context +
resolver` oluşturmak, çoğu zaman gereksiz bir dolambaç yaratır: artık aynı davranışı anlamak için 6 ayrı
dosyaya bakmanız gerekir.

Bu lab'daki HER pattern için "ne zaman kullanmamalıyım" notunu okuyun (her Lab bölümünde var). Ortak ilke:
**pattern, tekrar eden veya BÜYÜYEN bir tasarım problemini çözüyorsa değerlidir.** Problem büyümüyorsa,
en basit çözüm (düz method çağrısı, if/else, doğrudan constructor) genellikle en iyisidir.

---

## Debugging Guide

| Pattern | Breakpoint | Ne Gözlemlenmeli |
|---|---|---|
| Strategy | `PaymentStrategyResolver.resolve()` | Hangi `PaymentType` için hangi strategy bean'i döndü |
| Strategy | `CreditCardPaymentStrategy.pay()` | Card-specific validasyon/provider çağrısı |
| Chain | `CheckoutRuleChain.run()` for loop | `stopped` flag ne zaman `true` oluyor, `SKIPPED` nereden başlıyor |
| Adapter | `AdapterGoodPaymentService.charge()` | Hangi `PaymentProvider` resolve edildi |
| Adapter | `BankAPaymentAdapter.charge()` | `BankAResponse` -> `PaymentResult` mapping |
| Template Method | `AbstractPaymentProcessor.process()` | Sabit 5 adımın sırası (final method) |
| Template Method | `BankAPaymentProcessor.prepareRequest()` | Provider-specific adım |
| Facade | `CheckoutFacade.checkout()` | 5 step'in çağrı sırası |
| Observer | `ObserverPaymentService.completePayment()` → `publisher.publishEvent()` | Senkron çağrı: satırdan sonra 3 listener'ın ÇALIŞMIŞ olması |
| Observer | her `*Listener.onPaymentCompleted()` | Event objesinin içeriği |
| Decorator | `LoggingPaymentProviderDecorator.charge()` | `delegate.charge()` öncesi/sonrası log |
| Singleton BAD | `BadPaymentContextService.process()` — field'a yazma satırı VE okuma satırı | İki ayrı request thread'inin AYNI field'a dokunması |
| Singleton GOOD | `GoodPaymentContextService.process()` | `orderId` parametresinin stack frame'e özel olduğu |

---

## Interview Questions

1. **Strategy Pattern nedir, gerçek projede nerede kullandın?** Bir davranışın (ödeme yapma) birden
   fazla alternatif implementasyonunu, çağıran kodu değiştirmeden değiştirebilmeyi sağlar. Bu projede
   CREDIT_CARD/WALLET/BANK_TRANSFER ödemeleri için kullanıldı.
2. **Strategy ile Factory arasındaki fark nedir?** Strategy davranışın KENDİSİdir; Factory/Resolver o
   davranışı SEÇEN mekanizmadır.
3. **Strategy yerine switch ne zaman kabul edilebilir?** Case sayısı az ve sabitse, davranışlar
   bağımsız büyümüyorsa.
4. **Spring'de Strategy implementation'larını nasıl resolve edersin?** `List<PaymentStrategy>`
   enjekte edip `supports()`'a göre bir Map'e indexleyerek (bkz. `PaymentStrategyResolver`).
5. **Neden strategy'leri `new` ile oluşturmuyoruz?** Spring bean lifecycle'ını (DI, dependency'ler)
   bypass eder; strategy'ler kendi dependency'lerini (provider client, config) Spring'den almalı.
6. **Chain of Responsibility ne zaman kullanılır?** Bağımsız, sıralı, fail-fast çalışması gereken
   kurallar varsa ve bu kurallar büyüyüp değişiyorsa.
7. **Chain ile basit validation service arasındaki trade-off nedir?** Chain esneklik/genişletilebilirlik
   katar ama dolaylılık ekler; 3-4 sabit kural için fazla abstraction olabilir.
8. **Adapter Pattern neden external integration'larda önemlidir?** Dış sistemin modelinin bizim
   business kodumuza sızmasını (coupling) engeller — provider değişince etki alanı sınırlı kalır.
9. **Adapter ile Strategy arasındaki fark nedir?** Strategy bizim tasarladığımız davranış
   alternatifleridir; Adapter bize dışarıdan verilmiş, uyumsuz bir interface'i normalize eder.
10. **Anti-Corruption Layer ile Adapter ilişkisi nedir?** Adapter, ACL kavramının somut, method
    seviyesindeki uygulamasıdır.
11. **Template Method ile Strategy arasındaki fark nedir?** Template Method inheritance tabanlıdır,
    algoritmanın SABİT iskeletini paylaşır; Strategy composition tabanlıdır, davranışın TAMAMINI
    değiştirir.
12. **Composition neden çoğu zaman inheritance'a tercih edilir?** Compile-time bağımlılık yaratmaz,
    runtime'da değiştirilebilir, base class'ın varsaymadığı kısıtlamalara zorlamaz.
13. **Facade Pattern ne çözer?** Çağıran tarafın alt sistemlerin karmaşıklığını/sırasını bilme
    zorunluluğunu kaldırır.
14. **Facade nasıl God Service'e dönüşebilir?** Orkestrasyon yapmak yerine business logic'in kendisini
    içine çekmeye başlarsa.
15. **Builder ne zaman gereksizdir?** Az sayıda (2-3), çoğu zorunlu alan varsa — record/factory method
    yeterlidir.
16. **Observer Pattern'in dezavantajları nelerdir?** Control flow dolaylılaşır, "kim dinliyor"
    takibi zorlaşır, hata yönetimi (bir listener patlarsa?) ayrı bir tasarım kararı gerektirir.
17. **Spring ApplicationEvent ile Kafka arasındaki fark nedir?** ApplicationEvent in-process ve
    (varsayılan) senkrondur, durability/replay garantisi yoktur; Kafka distributed, durable messaging'dir.
18. **Decorator ile AOP arasındaki fark nedir?** AOP generic, cross-cutting davranışı (örn. tüm
    method'lara logging) merkezi olarak uygular; Decorator belirli bir contract seviyesinde explicit,
    elle kontrol edilen composition sağlar.
19. **Decorator ile Proxy arasındaki fark nedir?** Yapısal olarak benzerler; Decorator davranış
    EKLEMEYE, Proxy erişim/kontrol YÖNETİMİNE odaklanır.
20. **Spring singleton bean thread-safe midir?** Hayır, otomatik olarak değildir — singleton SADECE
    "tek instance" demektir, concurrency garantisi vermez.
21. **Stateless singleton service neden genellikle güvenlidir?** Paylaşılan mutable state yoksa,
    paylaşılan bir şey üzerinde race condition da oluşamaz.
22. **Singleton bean field'ında request-specific state tutarsan ne olur?** Concurrent request'ler
    aynı field'ı ezer; bu lab'da `BadPaymentContextService` ile GERÇEKTEN reprodüklendi.
23. **`@RequestScope` kullanmak bu problemin her zaman doğru çözümü müdür?** Hayır — önce field'ın
    neden var olduğunu sorgulayın; çoğu durumda veriyi method parametresi yapmak yeterlidir.
24. **Design Pattern kullanmak ne zaman overengineering olur?** Problem büyümüyorsa/tekrar
    etmiyorsa ve basit kod zaten yeterince okunabilirse.
25. **Bir pattern seçerken hangi problemi çözdüğünü nasıl belirlersin?** "Bu pattern olmasa kod nasıl
    görünürdü, hangi değişiklik hangi dosyaları etkilerdi?" sorusunu sorarak (bkz. her lab'ın "BAD Kod
    Neden Büyür?" bölümü).

---

## Bilinen Limitler

- `ProviderCallSimulator` gerçek bir network call yapmaz; sadece configured bir gecikme simüle eder.
- Observer lab'ı senkron bırakıldı (bilinçli karar) — async/hata-yönetimi senaryoları kapsam dışıdır.
- Singleton BAD/GOOD demosu HTTP üzerinden iki ayrı bağlantı gerektirir (script bunu otomatik yapar);
  tek bir curl çağrısıyla race'i göstermek mümkün değildir.
