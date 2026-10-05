#!/usr/bin/env bash
# Belirtilen endpoint'e gerçek, eşzamanlı HTTP request'leri gönderir ve HTTP status dağılımını özetler.
# Git Bash / Linux / macOS (curl >= 7.68). xargs -P ile paralel çalıştırır.
#
# Kullanım:
#   ./run-concurrent-requests.sh <url> <method GET|POST> <requests> <concurrency>
#
# Örnekler:
#   ./run-concurrent-requests.sh http://localhost:8086/api/labs/locks/read-write/price/P1 GET 30 30
#   ./run-concurrent-requests.sh http://localhost:8086/api/labs/aba/bad POST 10 10

set -euo pipefail

URL="${1:?Usage: $0 <url> <GET|POST> <requests> <concurrency>}"
METHOD="${2:-POST}"
REQUESTS="${3:-30}"
CONCURRENCY="${4:-30}"

echo "==> $REQUESTS $METHOD request, concurrency=$CONCURRENCY -> $URL"

run_one() {
    if [ "$METHOD" = "GET" ]; then
        curl -s -o /dev/null -w "%{http_code}\n" "$URL"
    else
        curl -s -o /dev/null -w "%{http_code}\n" -X POST -H "Content-Type: application/json" -d '{}' "$URL"
    fi
}
export -f run_one
export URL METHOD

seq "$REQUESTS" | xargs -P "$CONCURRENCY" -I {} bash -c 'run_one' > /tmp/concurrency-lab-status-codes.txt

echo ""
echo "==> HTTP status dağılımı"
sort /tmp/concurrency-lab-status-codes.txt | uniq -c
rm -f /tmp/concurrency-lab-status-codes.txt
