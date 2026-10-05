---
name: java-production-interview-labs
description: Production-benzeri Java/Spring Boot interview lab'ları geliştirirken kullanılacak çalışma standardı. Gerçek business senaryoları, BAD→OBSERVE→DEBUG→GOOD→VERIFY yaklaşımı, manuel doğrulama, Türkçe teknik yorumlar ve production trade-off analizi zorunludur.
---

# Java Production Interview Labs

## 1. AMAÇ

Bu skill Java/Spring Boot konularını teorik tanımlar veya oyuncak örnekler üzerinden öğretmek için değildir.

Amaç:

- Gerçek production senaryolarına benzeyen çalıştırılabilir örnekler oluşturmak.
- Bir teknolojinin yalnızca nasıl kullanıldığını değil, NEREDE ve NEDEN kullanıldığını göstermek.
- Alternatif çözümleri karşılaştırmak.
- Diğer çözümün neden seçilmediğini açıklamak.
- Yanlış yaklaşımın gerçek davranışını gözlemlemek.
- Kullanıcının IntelliJ debugger, Postman, PostgreSQL/DBeaver ve loglar üzerinden sistemi kendisinin inceleyebilmesini sağlamak.
- Interview sırasında tanım vermek yerine teknik karar ve trade-off açıklayabilecek seviyeye gelmek.

Bu bir tutorial projesi değildir.

Bu bir:

PRODUCTION BEHAVIOR + DEBUGGING + INTERVIEW LAB

projesidir.

---

# 2. EN ÖNEMLİ KURAL

Bir teknolojiyi sadece göstermek amacıyla kullanma.

Önce gerçek problem oluştur.

Sonra çözümü seç.

Her önemli teknik kullanım şu soruya cevap vermelidir:

"Gerçek bir projede bunu neden kullanırdım?"

Cevap verilemiyorsa o teknoloji örneğe zorla eklenmemelidir.

---

# 3. STANDART LAB AKIŞI

Uygun olan her örnek şu sırayı takip etmelidir:

BAD
→ OBSERVE
→ DEBUG
→ GOOD
→ VERIFY

## BAD

Gerçek projede yazılabilecek fakat concurrency, transaction, performance, maintainability veya consistency açısından problemli implementasyon.

## OBSERVE

Problemi gerçekten oluştur.

Sadece README'de "burada race condition olabilir" yazma.

Race condition gerçekten oluşabilmeli.

## DEBUG

Problemin neden oluştuğu gözlemlenebilir olmalı.

Gerekirse:

- breakpoint
- thread name
- SQL log
- transaction log
- application log
- DB state
- artificial delay

kullan.

## GOOD

Production açısından daha doğru çözümü uygula.

## VERIFY

Aynı senaryoyu tekrar çalıştır.

BAD ve GOOD davranışı karşılaştırılabilir olmalı.

---

# 4. UNIT TEST YASAK

BU PROJEDE UNIT TEST YAZMA.

Kesin kurallar:

- src/test altında unit test oluşturma.
- Mockito testi oluşturma.
- Test coverage hedefi oluşturma.
- "Tests passed" sonucunu implementasyon kanıtı olarak kullanma.
- Görevi test sınıfları oluşturarak tamamlanmış kabul etme.
- Kullanıcı özellikle istemedikçe test framework'lerine zaman harcama.

Bu projede öğrenme yöntemi çalışan sistemi gözlemlemektir.

---

# 5. DOĞRULAMA GERÇEK UYGULAMA ÜZERİNDEN YAPILACAK

Senaryoya göre aşağıdakiler kullanılmalıdır:

- Postman
- gerçek HTTP endpoint
- PostgreSQL
- DBeaver
- application log
- IntelliJ debugger
- breakpoint
- concurrent HTTP request
- Hibernate SQL log
- thread bilgileri
- transaction davranışı
- database lock davranışı
- gerekiyorsa Kafka UI
- gerekiyorsa birden fazla application instance

Kullanıcı örneği kendi bilgisayarında çalıştırabilmelidir.

---

# 6. TÜRKÇE KOD YORUMLARI ZORUNLU

Kritik teknik noktalarda Türkçe açıklayıcı yorumlar yaz.

Ancak syntax anlatan gereksiz yorum yazma.

KÖTÜ:

```java
// Sayacı bir artırır.
counter.incrementAndGet();
```

İYİ:

```java
// AtomicInteger yalnızca aynı JVM içindeki thread'ler arasında güvence sağlar.
// Uygulama birden fazla pod olarak çalışıyorsa bu sayaç global limit değildir.
counter.incrementAndGet();
```

Yorumlar özellikle şunları açıklamalıdır:

- Neden bu çözüm seçildi?
- Alternatif neden seçilmedi?
- Production riski nedir?
- Multi-thread durumda ne olur?
- Multi-instance/pod ortamında ne olur?
- Hangi durumda bu çözüm kullanılmamalıdır?
- Bu kodun kritik trade-off'u nedir?

Her satıra yorum yazma.

Sadece öğrenme açısından değerli noktalara yorum ekle.

---

# 7. TEKNİK KARAR STANDARDI

Her önemli teknik kullanım için mümkün olduğunca şu sorular cevaplanmalıdır:

1. Gerçek business problemi nedir?
2. Neden bu çözümü kullanıyoruz?
3. Alternatif çözüm nedir?
4. Alternatifi neden burada kullanmadık?
5. Bu çözüm hangi durumda kötüleşir?
6. Concurrency altında ne olur?
7. Birden fazla pod olduğunda ne olur?
8. DB yavaşladığında ne olur?
9. External service yavaşladığında ne olur?
10. Timeout olduğunda sistem hangi durumda kalır?
11. Production'da problemi nasıl gözlemleriz?
12. Interview'da bu karar nasıl savunulur?

"Best practice olduğu için" yeterli açıklama değildir.

---

# 8. GEREKSİZ TEKNOLOJİ KULLANMA

Şunları yapma:

- Her yerde Stream kullanma.
- Her DTO'yu record yapma.
- Her ilişkiye CascadeType.ALL verme.
- Her concurrency problemine distributed lock koyma.
- Her metoda @Transactional ekleme.
- Her problemi Design Pattern ile çözmeye çalışma.
- Sırf modern Java göstermek için okunabilirliği bozma.
- Gereksiz abstraction oluşturma.
- Overengineering yapma.

Basit çözüm yeterliyse basit çözüm tercih edilmelidir.

---

# 9. VARSAYILAN TEKNOLOJİLER

Varsayılan stack:

- Java 21
- Spring Boot
- Maven
- PostgreSQL
- Spring Data JPA
- Hibernate
- Postman
- DBeaver
- IntelliJ IDEA

Yeni dependency ancak gerçek teknik gerekçesi varsa eklenmelidir.

# DOCKER KULLANMA

Bu projedeki lab/microservice'lerde Docker ve Docker Compose KULLANMA.

Kesin kurallar:

- Dockerfile oluşturma.
- docker-compose.yml / compose.yml oluşturma.
- Uygulamayı Docker container içinde çalıştırma.
- PostgreSQL, Kafka, Redis veya başka altyapıları Docker ile ayağa kaldırma.
- "Kolay kurulum" gerekçesiyle Docker ekleme.
- Docker tabanlı development environment oluşturma.
- Testcontainers kullanma.

Tüm Spring Boot mikroservisleri kullanıcının IntelliJ IDEA üzerinden doğrudan çalıştırabileceği şekilde tasarlanmalıdır.

Tercih edilen çalışma şekli:

IntelliJ
|
v
Spring Boot Application
|
v
localhost:<service-port>

Bir lab ileride PostgreSQL, Kafka, Redis gibi bir altyapıya ihtiyaç duyarsa bu altyapının nasıl sağlanacağı ilgili fazda ayrıca kararlaştırılacaktır.

Kullanıcı açıkça istemeden Docker ekleme.

README dosyalarında da Docker'ı varsayılan çalıştırma yöntemi olarak gösterme.

Ana çalıştırma yöntemi:

- IntelliJ IDEA üzerinden main class
  ve/veya
- Maven `spring-boot:run`

olmalıdır.

# PORT VE APPLICATION CONFIGURATION KURALI

Bu proje kullanıcının bilgisayarındaki diğer projelerle aynı anda çalıştırılacaktır.

Bu nedenle port seçimi serbest DEĞİLDİR.

## Zorunlu port

Bu skill kapsamında oluşturulan yeni Spring Boot mikroservislerinde varsayılan port:

8086

olmalıdır.

---

# 10. KONU 1 — ATOMIC PRIMITIVES

Amaç AtomicInteger'ın ne olduğunu öğretmek değildir.

Gerçek production senaryosu kullanılmalıdır.

Önerilen ana senaryo:

Payment/external provider concurrency kontrolü.

Örneğin provider aynı anda maksimum N işlem kabul ediyor.

Göster:

- normal int ile race condition
- AtomicInteger
- get() + incrementAndGet() tuzağı
- her operasyonun ayrı atomic olmasının bütün business operation'ı atomic yapmadığı
- compareAndSet / CAS
- contention
- exception durumunda counter leak
- finally gerekliliği
- AtomicLong gibi yapıların metrics/counter kullanımındaki doğal yeri

Özellikle multi-instance problemi göster.

Örneğin:

```
Pod A → AtomicInteger max 20
Pod B → AtomicInteger max 20
Pod C → AtomicInteger max 20
```

Business'ın global limiti 20 ise local AtomicInteger bunu çözmez.

---

# 11. KONU 2 — JAVA CONCURRENCY & THREAD MANAGEMENT

Gerçek backend senaryolarında ele alınacak:

- Thread
- Platform Thread
- Virtual Thread
- ExecutorService
- FixedThreadPool
- Semaphore
- CountDownLatch
- CyclicBarrier
- Phaser
- ReadWriteLock
- StampedLock
- AtomicStampedReference
- CopyOnWriteArrayList
- CopyOnWriteArraySet

Her yapı için gerçek kullanım senaryosu oluştur.

Örneğin:

Semaphore:
External provider'a maksimum N concurrent request.

ReadWriteLock:
Çok sık okunan, nadiren güncellenen local pricing/config cache.

CopyOnWriteArrayList:
Çok sık okunan fakat nadiren değişen listener/rule registry.

CountDownLatch:
Bir order işlemine devam etmeden önce birkaç bağımsız işlemin tamamlanmasını bekleme.

AtomicStampedReference:
ABA/version problemi.

ExecutorService tarafında özellikle:

- CPU-bound vs I/O-bound
- pool sizing
- bounded vs unbounded queue
- rejection policy
- backpressure
- shutdown
- task failure

incelenmelidir.

---

# 12. VIRTUAL THREADS

Platform Thread ve Virtual Thread gerçek I/O workload üzerinde karşılaştırılmalıdır.

Örneğin:

```java
Executors.newFixedThreadPool(...)
```

ile:

```java
Executors.newVirtualThreadPerTaskExecutor()
```

karşılaştır.

Ancak şu yanlış sonucu çıkarma:

"Virtual Thread varsa concurrency limiti gerekmez."

Özellikle göster:

10.000 Virtual Thread
≠
10.000 DB connection

10.000 Virtual Thread
≠
External provider'a 10.000 request göndermek doğru

Aşağıdaki downstream limitleri dikkate alınmalıdır:

- HikariCP connection pool
- external provider capacity
- rate limit
- semaphore/bulkhead
- DB capacity

Ayrıca:

- CPU-bound workload
- ThreadLocal
- locking
- pinning davranışı
- blocking I/O

gibi production dikkat noktalarını güncel Java davranışına göre açıkla.

---

# 13. KONU 3 — DESIGN PATTERNS

Design Pattern isimlerini ezberletme.

Pattern gerçek problemi çözmelidir.

Ele alınacak temel pattern'ler:

- Strategy
- Factory / Resolver
- Chain of Responsibility
- Template Method
- Adapter
- Facade
- Builder
- Observer
- Decorator
- Singleton

Özellikle derinleştir:

- Strategy + Factory/Resolver
- Chain of Responsibility
- Adapter
- Singleton + concurrency

Örnek domain:

checkout / payment / order.

Strategy örneği:

Card
Wallet
Bank Transfer

gibi farklı payment davranışları.

Factory/Resolver doğru Strategy'yi seçebilir.

Spring dependency injection kullanılıyorsa gereksiz klasik "new" tabanlı factory yazma.

Chain of Responsibility örneği:

Order Validation
→ Customer Check
→ Fraud Check
→ Limit Check
→ Stock Check

Adapter örneği:

Farklı external payment provider API'lerini ortak internal interface arkasında izole et.

Singleton bölümünde özellikle Spring singleton bean içinde request-specific mutable state tutmanın concurrency problemini canlı göster.

Her pattern için:

BAD
→ problem
→ pattern
→ neden bu pattern
→ alternatif
→ hangi durumda overengineering

akışını kullan.

---

# 14. KONU 4 — CLEAN CODE + MODERN JAVA

Bu bölümün BİRİNCİ önceliği CLEAN CODE'dur.

Modern Java ikinci önceliktir.

## CLEAN CODE

Önce çalışan fakat kötü kod oluştur veya mevcut kötü kodu kullan.

Örnek problemler:

- uzun method
- nested if
- magic string
- magic number
- kötü isimlendirme
- duplicate code
- gereksiz comment
- null handling
- primitive obsession
- çok fazla method parametresi
- controller/service/repository sorumluluklarının karışması
- entity'nin doğrudan API response olması
- gereksiz abstraction
- overengineering

Daha sonra davranışı değiştirmeden refactor et.

Clean Code'u:

"method maksimum X satır olmalıdır"

gibi mekanik kurallara indirgeme.

---

# 15. MODERN JAVA

Modern Java özelliğini yalnızca gerçek avantaj sağlıyorsa kullan.

Özellikle:

- record
- pattern matching for instanceof
- switch expression
- pattern matching switch
- sealed class/interface
- Stream API
- Stream.toList()
- Optional
- var
- List.of
- Set.of
- Map.of
- text blocks, gerçek ihtiyaç varsa

Mümkün olduğunda şu karşılaştırmayı yap:

ESKİ YAKLAŞIM
→ MODERN JAVA
→ NEDEN DAHA İYİ
→ NE ZAMAN KULLANMAMALIYIZ

---

# 16. RECORD

Record özellikle:

- request
- response
- command
- event payload
- projection
- value taşıyan immutable-benzeri yapılar

için değerlendirilebilir.

Ancak:

record == deep immutable

değildir.

İçindeki mutable collection hâlâ değiştirilebilir olabilir.

Gerekiyorsa defensive copy kullan:

```java
List.copyOf(...)
```

JPA entity'lerini sırf modern Java kullanmak için record yapma.

---

# 17. STREAM

Stream gerçek okunabilirlik sağladığı yerde kullanılmalıdır.

Örneğin:

filter
→ map
→ reduce

gibi doğal transformation pipeline'ları.

Ancak:

- aşırı uzun stream chain
- side effect
- stream içinde kontrolsüz DB çağrısı
- stream içinde external API çağrısı
- okunabilirliği azaltan functional kod
- gereksiz parallelStream()

kullanımından kaçın.

Stream kullanmak N+1 problemini çözmez.

parallelStream() otomatik performans çözümü değildir.

---

# 18. OPTIONAL

Optional özellikle "değer bulunamayabilir" dönüş değerlerinde anlamlıdır.

Örneğin:

```java
repository.findById(...)
    .orElseThrow(...)
```

Ancak Optional'ı gereksiz şekilde:

- entity field
- method parameter
- her nullable değer

için kullanma.

---

# 19. VAR

var yalnızca okunabilirliği koruyorsa kullanılmalıdır.

Tipin ne olduğu açık değilse veya business anlamını gizliyorsa kullanma.

Modern syntax kullanmak clean code'dan daha önemli değildir.

---

# 20. KONU 5 — SPRING TRANSACTION + JPA/HIBERNATE + DATABASE CONSISTENCY

Bu bölüm çok önemlidir.

Amaç:

"@Transactional nedir?"

anlatmak değildir.

Amaç:

"Hangi durumda hangi transaction/JPA yaklaşımını neden seçerim?"

sorusunu cevaplamaktır.

---

# 21. TRANSACTION PROPAGATION

Gerçek business transaction üzerinden göster.

Özellikle:

- REQUIRED
- REQUIRES_NEW

Diğer propagation tiplerini yalnız gerçek kullanım gerekçesi varsa detaylandır.

Örnek:

Order/Payment transaction başarısız oluyor.

Ancak audit kaydının kalması gerekiyor.

Önce REQUIRED ile davranışı göster.

Sonra REQUIRES_NEW ile karşılaştır.

Özellikle incele:

- yeni transaction
- yeni connection ihtiyacı
- outer transaction
- connection pool exhaustion
- gereksiz REQUIRES_NEW kullanımı

Self-invocation problemini mutlaka göster.

Örneğin:

```java
this.saveInNewTransaction()
```

çağrısında Spring proxy davranışını canlı olarak incele.

---

# 22. ISOLATION LEVEL

Isolation seviyelerini tablo ezberi şeklinde anlatma.

Gerçek concurrent transaction oluştur.

Örneğin:

Campaign limit
Inventory
Balance
Quota

gibi business constraint kullan.

Karşılaştır:

- READ_COMMITTED
- REPEATABLE_READ
- SERIALIZABLE gerektiğinde

PostgreSQL'in gerçek davranışını esas al.

Isolation yükseltmenin her zaman daha iyi olmadığını göster.

İncele:

- concurrency
- throughput
- blocking
- retry
- serialization failure
- lock davranışı

---

# 23. OPTIMISTIC LOCK

Gerçek entity conflict'i oluştur.

@Version kullan.

İki concurrent request aynı entity'yi güncellesin.

Gözlemle:

- version
- conflict
- OptimisticLockException
- API davranışı

Sonra şu kararları tartış:

- retry yapmalı mıyız?
- kullanıcıya 409 dönmeli miyiz?
- low contention'da neden mantıklı?
- high contention'da ne olur?
- retry storm oluşabilir mi?

---

# 24. PESSIMISTIC LOCK

Gerçek DB row lock kullan.

Örneğin:

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
```

Göster:

TX-A row lock alıyor.

TX-B aynı row için bekliyor.

İncele:

- blocking
- timeout
- deadlock
- transaction süresi
- connection kullanımı

Özellikle:

DB lock tutulurken external HTTP çağrısı yapmanın riskini göster.

Optimistic ve pessimistic mümkünse aynı business problemi üzerinde karşılaştırılmalıdır.

---

# 25. N+1 PROBLEMİ

N+1 gerçek Hibernate SQL logları üzerinden gösterilmelidir.

Örnek domain:

Order
→ OrderItem
→ Product
→ Customer

Önce problemli query oluştur.

Örneğin:

1 query Orders
+
N query OrderItems

gerçekten loglarda görünmelidir.

Sonra çözümleri karşılaştır:

- JOIN FETCH
- EntityGraph
- DTO Projection
- uygun query tasarımı

Özellikle anlat:

EAGER == N+1 çözümü değildir.

LAZY/EAGER tek başına query strategy değildir.

Pagination + collection fetch join problemini de göster.

---

# 26. ENTITY RELATIONSHIPS

Gerçek domain üzerinden:

Customer
→ Order
→ OrderItem
→ Product

incele:

- OneToMany
- ManyToOne
- owning side
- mappedBy
- unidirectional
- bidirectional

Önemli prensip:

Database'te foreign key olması Java entity modelinde iki yönlü navigation olmak zorunda olduğu anlamına gelmez.

Gereksiz bidirectional relationship oluşturma.

---

# 27. CASCADE

Cascade'i annotation ezberi olarak anlatma.

Entity lifecycle üzerinden anlat.

Özellikle:

- PERSIST
- MERGE
- REMOVE
- ALL

Order → OrderItem

ile:

OrderItem → Product

ilişkisini karşılaştır.

OrderItem Order aggregate'ının parçası olabilir.

Product ise bağımsız lifecycle'a sahip olabilir.

Yanlış CascadeType.ALL kullanımının gerçek tehlikesini göster.

---

# 28. ORPHAN REMOVAL

Gerçek parent-child lifecycle üzerinden göster.

Örneğin:

```java
order.getItems().remove(item)
```

sonrasında DB davranışını gözlemle.

Temel karar sorusu:

"Child entity parent olmadan business olarak anlamlı mı?"

orphanRemoval kullanımını bu soruya göre değerlendir.

---

# 29. PERSISTENCE CONTEXT

Ayrıca gerçek senaryolarla şunları göster:

- Persistence Context
- Dirty Checking
- flush
- commit
- transient
- managed
- detached
- repository.save() davranışı
- LazyInitializationException
- entity equals/hashCode problemleri
- bulk update'in persistence context'i bypass etmesi

Özellikle Dirty Checking canlı gösterilmeli.

Örneğin:

```java
@Transactional
public void rename(Long id) {
    Product product = repository.findById(id).orElseThrow();

    product.changeName("Yeni isim");

    // Burada repository.save(product) çağrısı olmadan UPDATE oluşmasının
    // nedeni Persistence Context ve Dirty Checking davranışıdır.
}
```

SQL logundan UPDATE gözlemlenmelidir.

---

# 30. README STANDARDI

Her lab README'sinde en az şu bölümler bulunmalıdır:

## Problem

Gerçek business problemi.

## BAD Implementation

İlk yaklaşım neden riskli?

## Nasıl Reproduce Edilir?

Açık şekilde:

- uygulama nasıl çalıştırılır
- endpoint
- HTTP method
- request body
- kaç request
- concurrent ise nasıl gönderilecek
- DB başlangıç datası

## Ne Gözlemlemeliyim?

Örneğin:

- SQL
- log
- exception
- thread
- lock
- DB kaydı
- response

## GOOD Implementation

Çözüm.

## Neden?

Teknik kararın gerekçesi.

## Alternatifler

Başka hangi çözümler vardı?

## Neden Alternatifi Seçmedik?

Trade-off.

## Production Dikkat Noktaları

Gerçek sistemde ne değişir?

## Interview Notes

Bu senaryodan çıkabilecek önemli interview soruları.

README'yi teorik ders kitabına dönüştürme.

---

# 31. DEBUG EDİLEBİLİRLİK

Kod kullanıcının IntelliJ üzerinden rahatlıkla debug edebileceği şekilde yazılmalıdır.

Kritik concurrency/transaction noktaları mümkün olduğunca ayrı methodlarda olmalıdır.

Gerekirse kontrollü demo endpoint'lerinde artificial delay kullanılabilir.

Örneğin:

```java
Thread.sleep(...)
```

sadece race condition/lock davranışını deterministik şekilde görünür hale getirmek için kullanılabilir.

Production koduymuş gibi gizleme.

Yorumla bunun DEMO amacı taşıdığını belirt.

---

# 32. MULTI-INSTANCE DÜŞÜNCESİ

In-memory çözüm kullanıldığında mutlaka şu soru sorulmalıdır:

"Bu uygulamadan 3 Kubernetes pod çalışırsa ne olur?"

Özellikle:

- AtomicInteger
- synchronized
- Lock
- Semaphore
- local cache
- in-memory registry

için bu değerlendirme yapılmalıdır.

JVM-local concurrency ile distributed concurrency birbirine karıştırılmamalıdır.

---

# 33. PERFORMANCE DÜŞÜNCESİ

Bir çözüm doğru çalışıyor diye production için otomatik olarak iyi kabul edilmemelidir.

Düşün:

- contention
- throughput
- latency
- DB connection
- thread usage
- lock duration
- queue growth
- retry storm
- memory
- downstream capacity

Özellikle concurrency ve transaction lab'larında performans trade-off'u açıklanmalıdır.

---

# 34. CLAUDE / AGENT ÇALIŞMA ŞEKLİ

Bir konu için implementasyon görevi verildiğinde:

1. Önce mevcut repository'yi incele.
2. Mevcut yapıyı gereksiz yere bozma.
3. İstenen konu dışındaki alanları değiştirme.
4. Unit test yazma.
5. Gerçek uygulamayı ayağa kaldır.
6. Endpoint'leri gerçek HTTP ile doğrula.
7. DB/log/thread davranışını kontrol et.
8. BAD senaryonun gerçekten bozulduğunu doğrula.
9. GOOD senaryonun gerçekten problemi çözdüğünü doğrula.
10. Kritik noktalara Türkçe açıklayıcı yorum ekle.
11. README reproduce adımlarını güncelle.
12. Değiştirilen dosyaları raporla.
13. Kullanıcının manuel olarak nasıl deneyebileceğini açıkla.
14. Sonraki konuya kendiliğinden geçme.

---

# 35. FAZ HANDOFF KURALI

Bir konu/faz tamamlandığında otomatik olarak sonraki faza geçme.

Önce sonucu raporla.

Raporda:

- ne yapıldı
- hangi BAD davranış gözlemlendi
- GOOD çözüm ne oldu
- nasıl doğrulandı
- hangi trade-off'lar var
- eksik kalan bir şey var mı

belirt.

Kullanıcı sonucu değerlendirdikten sonra sonraki adım belirlenecektir.

---

# 36. BAŞARI KRİTERİ

Bir lab ancak kullanıcı aşağıdaki soruları cevaplayabilecek hale geldiyse başarılıdır:

"Bu problemi gerçek projede nerede görürüm?"

"Neden bu çözümü seçtim?"

"Diğer çözümü neden seçmedim?"

"Bu çözüm hangi durumda kötü?"

"Concurrency altında ne olur?"

"Birden fazla pod olduğunda ne olur?"

"DB yavaşlarsa ne olur?"

"External service yavaşlarsa ne olur?"

"Production'da bunun bozulduğunu nasıl anlarım?"

"Debugger'da nereden bakarım?"

"Interview'da bunun trade-off'unu nasıl anlatırım?"

Sadece:

- compile olması
- Spring Boot'un başlaması
- endpoint'in 200 dönmesi

başarı kriteri değildir.

Amaç kod yazmış olmak değil, production davranışını anlayabilmektir.
