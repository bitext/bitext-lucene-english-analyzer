package com.bitext.lucene.analysis.en;

import java.util.Objects;
import org.apache.lucene.util.AttributeImpl;
import org.apache.lucene.util.AttributeReflector;

/** Copy-safe implementation for bounded sentence metadata status. */
public final class SentenceMetadataStatusAttributeImpl extends AttributeImpl
    implements SentenceMetadataStatusAttribute {
  private Status status = Status.AVAILABLE;

  @Override
  public Status status() {
    return status;
  }

  @Override
  public void setStatus(Status value) {
    status = Objects.requireNonNull(value, "status");
  }

  @Override
  public void clear() {
    status = Status.AVAILABLE;
  }

  @Override
  public void copyTo(AttributeImpl target) {
    ((SentenceMetadataStatusAttribute) target).setStatus(status);
  }

  @Override
  public void reflectWith(AttributeReflector reflector) {
    reflector.reflect(SentenceMetadataStatusAttribute.class, "status", status);
  }
}
