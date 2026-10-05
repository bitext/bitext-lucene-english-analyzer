ARG ELASTICSEARCH_VERSION=9.5.4
FROM docker.elastic.co/elasticsearch/elasticsearch:${ELASTICSEARCH_VERSION}
COPY adapters/elasticsearch/build/distributions/bitext-java-english-elasticsearch-1.0.0.zip /tmp/bitext-java-english-elasticsearch.zip
RUN bin/elasticsearch-plugin install --batch file:///tmp/bitext-java-english-elasticsearch.zip
