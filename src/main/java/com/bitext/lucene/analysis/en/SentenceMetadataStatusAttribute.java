package com.bitext.lucene.analysis.en;

import org.apache.lucene.util.Attribute;

/** Records whether sentence metadata on the current token is complete or boundedly unavailable. */
public interface SentenceMetadataStatusAttribute extends Attribute {
  enum Status {
    AVAILABLE,
    TEXT_LIMIT,
    RANGE_LIMIT
  }

  Status status();

  void setStatus(Status status);
}
