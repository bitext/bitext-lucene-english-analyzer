#!/usr/bin/env bash
# Public release integration checks. Requires Docker, curl, and jq.
set -euo pipefail

root=$(cd "$(dirname "$0")/../../.." && pwd)
results="$root/build/integration-results"
run_id="bitext-public-$RANDOM-$$"
elasticsearch_version=${ELASTICSEARCH_VERSION:-9.5.4}
opensearch_version=${OPENSEARCH_VERSION:-3.4.0}
solr_version=${SOLR_VERSION:-10.0.0}
mkdir -p "$results"

containers=()
cleanup() {
  for container in "${containers[@]}"; do
    docker rm -f "$container" >/dev/null 2>&1 || true
  done
}
trap cleanup EXIT

wait_http() {
  local url=$1 output=$2
  for ((attempt = 0; attempt < 90; attempt++)); do
    if curl -fsS "$url" > "$output"; then return 0; fi
    sleep 2
  done
  echo "Timed out waiting for $url" >&2
  return 1
}

assert_terms() {
  local response=$1 expected=$2
  test "$(jq -c '[.tokens[].token]' "$response")" = "$expected"
}

wait_elasticsearch_templates() {
  local port=$1
  for ((attempt = 0; attempt < 60; attempt++)); do
    if curl -fsS "http://127.0.0.1:$port/_cluster/pending_tasks" | jq -e '.tasks | length == 0' >/dev/null; then return 0; fi
    sleep 2
  done
  echo "Elasticsearch did not finish startup template validation" >&2
  return 1
}

run_elasticsearch() {
  local container="bitext-public-es-$run_id" port=19254
  docker build -f "$root/adapters/elasticsearch/src/test/docker/Dockerfile" \
    --build-arg "ELASTICSEARCH_VERSION=$elasticsearch_version" \
    -t "bitext-public-es:$run_id" "$root"
  docker run -d --name "$container" --memory=4g --cpus=2 -p "127.0.0.1:$port:9200" \
    -e discovery.type=single-node -e xpack.security.enabled=false \
    -e ES_JAVA_OPTS='-Xms2g -Xmx2g' "bitext-public-es:$run_id" >/dev/null
  containers+=("$container")
  wait_http "http://127.0.0.1:$port/_cluster/health" "$results/elasticsearch-health.json"
  wait_elasticsearch_templates "$port"
  curl -fsS -X PUT "http://127.0.0.1:$port/bitext-public" -H 'Content-Type: application/json' \
    --data-binary @"$root/adapters/elasticsearch/src/test/resources/public-english-index.json" > "$results/elasticsearch-index.json"
  curl -fsS -X POST "http://127.0.0.1:$port/bitext-public/_analyze" -H 'Content-Type: application/json' \
    --data '{"analyzer":"bitext_english","text":"Children. States."}' > "$results/elasticsearch-analyze.json"
  assert_terms "$results/elasticsearch-analyze.json" '["children","child","states","state"]'
  docker rm -f "$container" >/dev/null
}

run_opensearch() {
  local container="bitext-public-os-$run_id" port=19654
  docker build -f "$root/adapters/opensearch/src/test/docker/Dockerfile" \
    --build-arg "OPENSEARCH_VERSION=$opensearch_version" \
    -t "bitext-public-os:$run_id" "$root"
  docker run -d --name "$container" --memory=4g --cpus=2 -p "127.0.0.1:$port:9200" \
    -e discovery.type=single-node -e plugins.security.disabled=true \
    -e DISABLE_INSTALL_DEMO_CONFIG=true -e OPENSEARCH_JAVA_OPTS='-Xms2g -Xmx2g' \
    "bitext-public-os:$run_id" >/dev/null
  containers+=("$container")
  wait_http "http://127.0.0.1:$port/_cluster/health" "$results/opensearch-health.json"
  curl -fsS -X PUT "http://127.0.0.1:$port/bitext-public" -H 'Content-Type: application/json' \
    --data-binary @"$root/adapters/opensearch/src/test/resources/public-english-index.json" > "$results/opensearch-index.json"
  curl -fsS -X POST "http://127.0.0.1:$port/bitext-public/_analyze" -H 'Content-Type: application/json' \
    --data '{"analyzer":"bitext_english","text":"Children. States."}' > "$results/opensearch-analyze.json"
  assert_terms "$results/opensearch-analyze.json" '["children","child","states","state"]'
  docker rm -f "$container" >/dev/null
}

run_solr() {
  local container="bitext-public-solr-$run_id" port=18954
  docker build -f "$root/src/test/docker/solr/Dockerfile" \
    --build-arg "SOLR_VERSION=$solr_version" -t "bitext-public-solr:$run_id" "$root"
  docker run -d --name "$container" --memory=4g --cpus=2 -p "127.0.0.1:$port:8983" \
    -e SOLR_JAVA_MEM='-Xms2g -Xmx2g' "bitext-public-solr:$run_id" solr-foreground --user-managed >/dev/null
  containers+=("$container")
  wait_http "http://127.0.0.1:$port/solr/admin/info/system" "$results/solr-system.json"
  docker exec "$container" solr create -c bitextpublic -n _default >/dev/null
  curl -fsS -X POST "http://127.0.0.1:$port/solr/bitextpublic/schema" -H 'Content-Type: application/json' \
    --data-binary @"$root/src/test/resources/solr/public-field-type.json" > "$results/solr-field-type.json"
  curl -fsS "http://127.0.0.1:$port/solr/bitextpublic/analysis/field?analysis.fieldtype=bitext_java_english&analysis.fieldvalue=Children.%20States.&wt=json" > "$results/solr-analyze.json"
  test "$(jq -c '.analysis.field_types.bitext_java_english.index[-1] | map(.text)' "$results/solr-analyze.json")" = '["children","child","states","state"]'
  curl -fsS -X POST "http://127.0.0.1:$port/solr/bitextpublic/update?commit=true" -H 'Content-Type: application/json' \
    --data '[{"id":"1","text":"Children. States."}]' > "$results/solr-index.json"
  curl -fsS "http://127.0.0.1:$port/solr/bitextpublic/select?q=text%3Achild&wt=json" > "$results/solr-search.json"
  test "$(jq -r '.response.numFound' "$results/solr-search.json")" = 1
  docker rm -f "$container" >/dev/null
}

run_elasticsearch
run_opensearch
run_solr
printf 'Public Elasticsearch, OpenSearch, and Solr integration checks passed.\n'
