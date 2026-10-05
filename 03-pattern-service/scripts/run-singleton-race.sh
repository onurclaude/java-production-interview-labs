#!/usr/bin/env bash
# Lab 9 (Singleton) BAD/GOOD race demo. Bu bir unit test DEĞİLDİR.
#
# Request-A uzun bir holdMs (2000ms) ile gönderilir ve "field'a yazdım ama henüz okumadım" penceresinde
# BEKLER. Bu pencere açıkken Request-B kısa bir holdMs (0ms) ile AYNI endpoint'e gider ve (BAD
# senaryoda) AYNI singleton bean'in field'larını EZER. Request-A sonunda kendi yazdığı "ORDER-A"yı değil,
# Request-B'nin yazdığı "ORDER-B"yi gözlemler.
#
# Kullanım: ./run-singleton-race.sh <bad|good> [baseUrl]
#   ./run-singleton-race.sh bad   http://localhost:8086   -> corrupted=true BEKLENİR
#   ./run-singleton-race.sh good  http://localhost:8086   -> corrupted=false HER ZAMAN

set -euo pipefail

ENDPOINT="${1:-bad}"
BASE="${2:-http://localhost:8086}"
URL="$BASE/api/labs/singleton/$ENDPOINT"

echo "==> [$ENDPOINT] Request-A (ORDER-A, holdMs=2000) gonderiliyor (arka planda)..."
RESP_A_FILE=$(mktemp)
curl -s -X POST "$URL" -H "Content-Type: application/json" \
  -d '{"orderId":"ORDER-A","amount":100.00,"holdMs":2000}' > "$RESP_A_FILE" &
A_PID=$!

sleep 0.3
echo "==> [$ENDPOINT] Request-B (ORDER-B, holdMs=0) gonderiliyor..."
RESP_B=$(curl -s -X POST "$URL" -H "Content-Type: application/json" \
  -d '{"orderId":"ORDER-B","amount":200.00,"holdMs":0}')

wait "$A_PID"
RESP_A=$(cat "$RESP_A_FILE")
rm -f "$RESP_A_FILE"

echo ""
echo "==> Request-A response (beklenen requestOrderId=ORDER-A):"
echo "$RESP_A"
echo ""
echo "==> Request-B response (beklenen requestOrderId=ORDER-B):"
echo "$RESP_B"
echo ""
if echo "$RESP_A" | grep -q '"corrupted":true'; then
  echo "==> SONUC: Request-A'nin gozlemledigi state BOZULDU (corrupted=true) -> race condition reproduklendi."
else
  echo "==> SONUC: corrupted=false."
fi
