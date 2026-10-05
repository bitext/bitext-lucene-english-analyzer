ARG SOLR_VERSION=10.0.0
FROM solr:${SOLR_VERSION}

USER root
# Solr 10's global library directory makes this ordinary Lucene analysis JAR
# visible to cores without redistributing any Solr or Lucene server binaries.
COPY build/libs/bitext-lucene-english-analyzer-1.0.0.jar /opt/solr/lib/
USER solr
