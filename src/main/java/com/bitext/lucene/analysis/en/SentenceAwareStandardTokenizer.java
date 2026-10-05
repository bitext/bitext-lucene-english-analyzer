package com.bitext.lucene.analysis.en;

import java.io.IOException;
import java.io.Reader;
import java.text.BreakIterator;
import java.text.CharacterIterator;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.standard.StandardTokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionLengthAttribute;
import org.apache.lucene.analysis.tokenattributes.TypeAttribute;
import org.apache.lucene.util.AttributeFactory;

/**
 * A continuous {@link StandardTokenizer} annotated with bounded English sentence metadata.
 *
 * <p>The delegate is reset once per field. Its reader exposes only committed prefixes; when a
 * sentence decision cannot fit in the configured bounds, lexical delivery continues unchanged and
 * sentence metadata becomes unavailable for the rest of that field.
 */
final class SentenceAwareStandardTokenizer extends Tokenizer {
  public static final int DEFAULT_INITIAL_CAPACITY = 4_096;
  public static final int DEFAULT_RETAINED_CEILING = 65_536;
  public static final int DEFAULT_RIGHT_GUARD = 256;
  public static final int DEFAULT_RANGE_CEILING = 1_024;

  private final CharTermAttribute term = addAttribute(CharTermAttribute.class);
  private final OffsetAttribute offsets = addAttribute(OffsetAttribute.class);
  private final PositionIncrementAttribute increment =
      addAttribute(PositionIncrementAttribute.class);
  private final PositionLengthAttribute length = addAttribute(PositionLengthAttribute.class);
  private final TypeAttribute type = addAttribute(TypeAttribute.class);
  private final SentenceAttribute sentence = addAttribute(SentenceAttribute.class);
  private final SentenceMetadataStatusAttribute metadata =
      addAttribute(SentenceMetadataStatusAttribute.class);
  private final StandardTokenizer delegate = new StandardTokenizer();
  private final CharTermAttribute delegateTerm = delegate.addAttribute(CharTermAttribute.class);
  private final OffsetAttribute delegateOffsets = delegate.addAttribute(OffsetAttribute.class);
  private final PositionIncrementAttribute delegateIncrement =
      delegate.addAttribute(PositionIncrementAttribute.class);
  private final PositionLengthAttribute delegateLength =
      delegate.addAttribute(PositionLengthAttribute.class);
  private final TypeAttribute delegateType = delegate.addAttribute(TypeAttribute.class);

  private final Config config;
  private BoundedSentenceReader replay;
  private boolean delegateActive;
  private int activeSentence;

  public SentenceAwareStandardTokenizer() {
    this(AttributeFactory.DEFAULT_ATTRIBUTE_FACTORY, Config.defaults());
  }

  public SentenceAwareStandardTokenizer(Config config) {
    this(AttributeFactory.DEFAULT_ATTRIBUTE_FACTORY, config);
  }

  public SentenceAwareStandardTokenizer(AttributeFactory factory, Config config) {
    super(factory);
    this.config = config;
    delegate.setMaxTokenLength(config.maxTokenLength());
  }

  @Override
  public boolean incrementToken() throws IOException {
    if (!delegate.incrementToken()) return false;
    clearAttributes();
    term.setEmpty().append(delegateTerm);
    int start = delegateOffsets.startOffset();
    offsets.setOffset(correctOffset(start), correctOffset(delegateOffsets.endOffset()));
    increment.setPositionIncrement(delegateIncrement.getPositionIncrement());
    length.setPositionLength(delegateLength.getPositionLength());
    type.setType(delegateType.type());
    BoundedSentenceReader.Annotation annotation = replay.annotationFor(start, activeSentence);
    activeSentence = annotation.nextSentence();
    if (annotation.available()) sentence.setSentence(annotation.sentenceId());
    else sentence.clearSentence();
    metadata.setStatus(annotation.status());
    return true;
  }

  @Override
  public void reset() throws IOException {
    closeDelegate();
    super.reset();
    replay = new BoundedSentenceReader(input, config);
    delegate.setReader(replay);
    delegate.setMaxTokenLength(config.maxTokenLength());
    delegate.reset();
    activeSentence = 0;
    delegateActive = true;
  }

  @Override
  public void end() throws IOException {
    super.end();
    if (delegateActive) {
      delegate.end();
      int finalOffset = correctOffset(delegateOffsets.endOffset());
      offsets.setOffset(finalOffset, finalOffset);
      increment.setPositionIncrement(delegateIncrement.getPositionIncrement());
    }
  }

  @Override
  public void close() throws IOException {
    try {
      closeDelegate();
    } finally {
      replay = null;
      super.close();
    }
  }

  private void closeDelegate() throws IOException {
    if (delegateActive) {
      delegate.close();
      delegateActive = false;
    }
  }

  int retainedCharsForTest() {
    return replay == null ? 0 : replay.retainedCharsForTest();
  }

  int queuedRangesForTest() {
    return replay == null ? 0 : replay.queuedRangesForTest();
  }

  BoundedSentenceReader.Accounting accountingForTest() {
    return replay == null ? BoundedSentenceReader.Accounting.empty() : replay.accountingForTest();
  }

  long sourceReadCountForTest() {
    return replay == null ? 0 : replay.sourceReadCountForTest();
  }

  long boundaryScanCountForTest() {
    return replay == null ? 0 : replay.boundaryScanCountForTest();
  }

  /** Independent bounded reader settings; all values are UTF-16 code-unit counts except ranges. */
  record Config(
      int maxTokenLength,
      int initialCapacity,
      int retainedCeiling,
      int rightGuard,
      int rangeCeiling) {
    public Config {
      if (maxTokenLength < 1
          || initialCapacity < 1
          || retainedCeiling < initialCapacity
          || rightGuard < 0
          || rightGuard >= retainedCeiling
          || rangeCeiling < 1) {
        throw new IllegalArgumentException("invalid sentence-aware tokenizer bounds");
      }
    }

    public static Config defaults() {
      return new Config(
          255,
          DEFAULT_INITIAL_CAPACITY,
          DEFAULT_RETAINED_CEILING,
          DEFAULT_RIGHT_GUARD,
          DEFAULT_RANGE_CEILING);
    }
  }

  /** Reader which never reports internal sentence-window exhaustion as EOF. */
  static final class BoundedSentenceReader extends Reader {
    private final Reader source;
    private final Config config;
    private final BreakIterator iterator = BreakIterator.getSentenceInstance(Locale.ENGLISH);
    private final StringBuilder unresolved = new StringBuilder();
    private final StringBuilder ready = new StringBuilder();
    private final Deque<Range> ranges = new ArrayDeque<>();
    private final char[] sourceBuffer;
    private int readyCursor;
    private int absoluteDelivered;
    private int nextSentence;
    private boolean realEof;
    private SentenceMetadataStatusAttribute.Status fallback;
    private boolean closed;
    private int observationHorizon;
    private long sourceReadCount;
    private long boundaryScanCount;
    private int maxUnresolvedChars;
    private int maxUnreadReadyChars;
    private int maxIteratorSnapshotChars;
    private int maxQueuedRanges;

    BoundedSentenceReader(Reader source, Config config) {
      this.source = source;
      this.config = config;
      sourceBuffer = new char[Math.min(config.initialCapacity(), 8192)];
      observationHorizon = config.initialCapacity();
    }

    @Override
    public int read(char[] destination, int offset, int count) throws IOException {
      if (closed) throw new IOException("reader is closed");
      if (count == 0) return 0;
      while (readyCursor == ready.length()) {
        ready.setLength(0);
        readyCursor = 0;
        if (!produce()) return -1;
      }
      int copied = Math.min(count, ready.length() - readyCursor);
      ready.getChars(readyCursor, readyCursor + copied, destination, offset);
      readyCursor += copied;
      absoluteDelivered = Math.addExact(absoluteDelivered, copied);
      return copied;
    }

    private boolean produce() throws IOException {
      if (fallback != null) return produceFallback();
      while (true) {
        int boundary = committedBoundary();
        if (boundary > 0) {
          if (ranges.size() >= config.rangeCeiling()) {
            beginFallback(SentenceMetadataStatusAttribute.Status.RANGE_LIMIT);
            return produceFallback();
          }
          ready.append(unresolved, 0, boundary);
          unresolved.delete(0, boundary);
          ranges.addLast(
              new Range(Math.addExact(absoluteDelivered, ready.length()), nextSentence++));
          return true;
        }
        if (realEof) {
          if (unresolved.isEmpty()) return false;
          if (ranges.size() >= config.rangeCeiling()) {
            beginFallback(SentenceMetadataStatusAttribute.Status.RANGE_LIMIT);
            return produceFallback();
          }
          int end = unresolved.length();
          ready.append(unresolved);
          unresolved.setLength(0);
          ranges.addLast(
              new Range(Math.addExact(absoluteDelivered, ready.length()), nextSentence++));
          return true;
        }
        if (unresolved.length() >= config.retainedCeiling()) {
          // The ceiling itself may be the complete field.  Ask for precisely one more code unit
          // before declaring the unresolved field too large; it is replayed if it exists.
          probeEndOfInputAtCeiling();
          if (realEof) continue;
          beginFallback(SentenceMetadataStatusAttribute.Status.TEXT_LIMIT);
          return produceFallback();
        }
        fillObservationHorizon();
      }
    }

    private int committedBoundary() {
      if (unresolved.isEmpty()) return -1;
      boundaryScanCount++;
      AccessTracker accesses = new AccessTracker();
      maxIteratorSnapshotChars = Math.max(maxIteratorSnapshotChars, unresolved.length());
      iterator.setText(new ObservingCharacterIterator(unresolved.toString(), accesses));
      // BreakIterator may validate endpoints while accepting the iterator.  Only dependency reads
      // performed by the forward boundary operation decide whether the right edge is safe.
      accesses.reset();
      int boundary = iterator.first();
      boundary = iterator.next();
      if (boundary == BreakIterator.DONE) return -1;
      if (realEof) return boundary;
      // A result in, or an iterator decision that reads into, the guarded suffix is provisional.
      int guardedStart = unresolved.length() - config.rightGuard();
      return boundary < guardedStart && !accesses.dependsOnUnavailableOrGuardedSuffix(guardedStart)
          ? boundary
          : -1;
    }

    /** Fills a geometric observation horizon despite a short-reading upstream Reader. */
    private void fillObservationHorizon() throws IOException {
      int target = Math.min(config.retainedCeiling(), observationHorizon);
      while (!realEof && unresolved.length() < target) {
        int asked = Math.min(sourceBuffer.length, target - unresolved.length());
        int read = source.read(sourceBuffer, 0, asked);
        sourceReadCount++;
        if (read < 0) {
          realEof = true;
          break;
        }
        if (read == 0) {
          int one = source.read();
          sourceReadCount++;
          if (one < 0) realEof = true;
          else unresolved.append((char) one);
        } else {
          unresolved.append(sourceBuffer, 0, read);
        }
        sampleRetention();
      }
      if (!realEof && observationHorizon < config.retainedCeiling()) {
        observationHorizon =
            (int) Math.min(config.retainedCeiling(), (long) observationHorizon * 2L);
      }
    }

    private void probeEndOfInputAtCeiling() throws IOException {
      int one = source.read();
      sourceReadCount++;
      if (one < 0) {
        realEof = true;
      } else {
        // Authorized single-code-unit lookahead. It belongs to the lexical stream and must
        // therefore be retained until the fallback reader replays it.
        unresolved.append((char) one);
        sampleRetention();
      }
    }

    private void beginFallback(SentenceMetadataStatusAttribute.Status status) {
      fallback = status;
      if (readyCursor < ready.length()) {
        String tail = ready.substring(readyCursor);
        ready.setLength(0);
        ready.append(tail);
        readyCursor = 0;
      }
      ready.append(unresolved);
      unresolved.setLength(0);
      ranges.clear();
      sampleRetention();
    }

    private boolean produceFallback() throws IOException {
      if (readyCursor < ready.length()) return true;
      ready.setLength(0);
      readyCursor = 0;
      int read = source.read(sourceBuffer, 0, sourceBuffer.length);
      sourceReadCount++;
      if (read < 0) return false;
      if (read == 0) {
        int one = source.read();
        sourceReadCount++;
        if (one < 0) return false;
        ready.append((char) one);
      } else ready.append(sourceBuffer, 0, read);
      sampleRetention();
      return true;
    }

    Annotation annotationFor(int tokenStart, int currentSentence) {
      if (fallback != null && ranges.isEmpty())
        return new Annotation(false, -1, currentSentence, fallback);
      while (ranges.size() > 1 && tokenStart >= ranges.peekFirst().endOffset())
        ranges.removeFirst();
      Range current = ranges.peekFirst();
      if (current == null)
        return new Annotation(
            false,
            -1,
            currentSentence,
            fallback == null ? SentenceMetadataStatusAttribute.Status.TEXT_LIMIT : fallback);
      return new Annotation(
          true,
          current.sentenceId(),
          current.sentenceId(),
          SentenceMetadataStatusAttribute.Status.AVAILABLE);
    }

    private void sampleRetention() {
      maxUnresolvedChars = Math.max(maxUnresolvedChars, unresolved.length());
      maxUnreadReadyChars =
          Math.max(maxUnreadReadyChars, Math.max(0, ready.length() - readyCursor));
      maxQueuedRanges = Math.max(maxQueuedRanges, ranges.size());
    }

    int retainedCharsForTest() {
      return unresolved.length() + Math.max(0, ready.length() - readyCursor);
    }

    int queuedRangesForTest() {
      return ranges.size();
    }

    Accounting accountingForTest() {
      return new Accounting(
          maxUnresolvedChars,
          maxUnreadReadyChars,
          maxIteratorSnapshotChars,
          maxQueuedRanges,
          sourceBuffer.length);
    }

    long sourceReadCountForTest() {
      return sourceReadCount;
    }

    long boundaryScanCountForTest() {
      return boundaryScanCount;
    }

    SentenceMetadataStatusAttribute.Status fallbackForTest() {
      return fallback;
    }

    record Accounting(
        int maxUnresolvedChars,
        int maxUnreadReadyChars,
        int maxIteratorSnapshotChars,
        int maxQueuedRanges,
        int sourceBufferChars) {
      static Accounting empty() {
        return new Accounting(0, 0, 0, 0, 0);
      }
    }

    @Override
    public void close() throws IOException {
      closed = true;
      unresolved.setLength(0);
      ready.setLength(0);
      ranges.clear();
      iterator.setText("");
      source.close();
    }

    private record Range(int endOffset, int sentenceId) {}

    record Annotation(
        boolean available,
        int sentenceId,
        int nextSentence,
        SentenceMetadataStatusAttribute.Status status) {}

    static final class AccessTracker {
      private int maxContentIndex = -1;
      private boolean artificialRightEdge;
      private boolean unavailableLeft;

      void content(int index) {
        maxContentIndex = Math.max(maxContentIndex, index);
      }

      void artificialRightEdge() {
        artificialRightEdge = true;
      }

      void unavailableLeft() {
        unavailableLeft = true;
      }

      boolean dependsOnUnavailableOrGuardedSuffix(int guardedStart) {
        return artificialRightEdge || unavailableLeft || maxContentIndex >= guardedStart;
      }

      boolean artificialRightEdgeForTest() {
        return artificialRightEdge;
      }

      boolean unavailableLeftForTest() {
        return unavailableLeft;
      }

      int maxContentIndexForTest() {
        return maxContentIndex;
      }

      void reset() {
        maxContentIndex = -1;
        artificialRightEdge = false;
        unavailableLeft = false;
      }
    }

    /**
     * Clone-safe public CharacterIterator wrapper used only for BreakIterator dependency checks.
     */
    static final class ObservingCharacterIterator implements CharacterIterator {
      private final String text;
      private final AccessTracker tracker;
      private int index;

      ObservingCharacterIterator(String text, AccessTracker tracker) {
        this(text, tracker, 0);
      }

      private ObservingCharacterIterator(String text, AccessTracker tracker, int index) {
        this.text = text;
        this.tracker = tracker;
        this.index = index;
      }

      @Override
      public char first() {
        index = 0;
        return current();
      }

      @Override
      public char last() {
        index = text.length();
        if (!text.isEmpty()) index--;
        return current();
      }

      @Override
      public char current() {
        if (index < 0) {
          tracker.unavailableLeft();
          return DONE;
        }
        if (index >= text.length()) {
          tracker.artificialRightEdge();
          return DONE;
        }
        tracker.content(index);
        return text.charAt(index);
      }

      @Override
      public char next() {
        if (index < text.length()) index++;
        return current();
      }

      @Override
      public char previous() {
        if (index == 0) {
          tracker.unavailableLeft();
          return DONE;
        }
        if (index > 0) index--;
        return current();
      }

      @Override
      public char setIndex(int value) {
        if (value < 0 || value > text.length()) throw new IllegalArgumentException("index");
        index = value;
        return current();
      }

      @Override
      public int getBeginIndex() {
        return 0;
      }

      @Override
      public int getEndIndex() {
        return text.length();
      }

      @Override
      public int getIndex() {
        return index;
      }

      @Override
      public Object clone() {
        return new ObservingCharacterIterator(text, tracker, index);
      }
    }
  }
}
