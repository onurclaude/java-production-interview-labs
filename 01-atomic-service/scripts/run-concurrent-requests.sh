#!/usr/bin/env bash
#
# Belirtilen endpoint'e gerçek, eşzamanlı HTTP POST request'leri gönderir ve sonuçları özetler.
# Ek araç gerektirmez: sadece curl (>= 7.68, --parallel desteği). Git Bash / Linux / macOS.
#
# Kullanım:
#   ./run-concurrent-requests.sh <url[,url2,...]> [requestCount=50] [concurrency=50]
#
# Örnekler:
#   ./run-concurrent-requests.sh http://localhost:8081/api/labs/atomic/plain-int
#   ./run-concurrent-requests.sh http://localhost:8081/api/labs/atomic/cas 100 50
#   ./run-concurrent-requests.sh "http://localhost:8081/api/labs/atomic/leak/bad?fail=true" 20 20
#   # Multi-instance: request'ler URL'lere round-robin dağıtılır
#   ./run-concurrent-requests.sh http://localhost:8081/api/labs/atomic/cas,http://localhost:8082/api/labs/atomic/cas,http://localhost:8083/api/labs/atomic/cas 90 90
#
# Neden tek curl process'i (--parallel)? 50 ayrı curl process'i başlatmak (özellikle Windows'ta) yüzlerce ms
# sürer ve request'ler zamana yayılır. Tek process içinde paralel transfer, request'lerin gerçekten aynı anda
# sunucuya varmasını sağlar.

set -euo pipefail

URLS="${1:?Kullanım: $0 <url[,url2,...]> [requestCount] [concurrency]}"
REQUESTS="${2:-50}"
CONCURRENCY="${3:-50}"

IFS=',' read -r -a URL_LIST <<< "$URLS"

config_file="$(mktemp)"
codes_file="$(mktemp)"
trap 'rm -f "$config_file" "$codes_file"' EXIT

for ((i = 0; i < REQUESTS; i++)); do
  url="${URL_LIST[$((i % ${#URL_LIST[@]}))]}"
  printf 'url = "%s"\noutput = "/dev/null"\n' "$url" >> "$config_file"
done

echo "==> $REQUESTS POST request, concurrency=$CONCURRENCY"
for url in "${URL_LIST[@]}"; do echo "    $url"; done

start_ms=$(date +%s%3N)
curl --silent --parallel --parallel-immediate --parallel-max "$CONCURRENCY" \
     --request POST --write-out "%{http_code}\n" --config "$config_file" > "$codes_file" || true
end_ms=$(date +%s%3N)

echo
echo "==> HTTP status dağılımı ($((end_ms - start_ms)) ms)"
# Windows curl'ü satır sonuna \r ekler; saymadan önce temizliyoruz.
tr -d '\r' < "$codes_file" | sort | uniq -c | while read -r count code; do
  case "$code" in
    200) label="ACCEPTED (provider'a gitti, başarılı)" ;;
    503) label="REJECTED (limit dolu)" ;;
    502) label="PROVIDER_FAILED" ;;
    000) label="CONNECTION ERROR (uygulama çalışıyor mu?)" ;;
    *)   label="" ;;
  esac
  printf '    %5s x %s %s\n' "$count" "$code" "$label"
done

echo
echo "==> Stats"
for url in "${URL_LIST[@]}"; do
  base="${url%%/api/*}"
  echo "--- $base/api/labs/atomic/stats"
  curl --silent "$base/api/labs/atomic/stats"
  echo
done
