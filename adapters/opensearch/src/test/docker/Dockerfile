ARG OPENSEARCH_VERSION=3.4.0
FROM opensearchproject/opensearch:${OPENSEARCH_VERSION}
COPY adapters/opensearch/build/distributions/bitext-java-english-opensearch-1.0.0.zip /tmp/bitext-java-english-opensearch.zip
RUN bin/opensearch-plugin install --batch file:///tmp/bitext-java-english-opensearch.zip
