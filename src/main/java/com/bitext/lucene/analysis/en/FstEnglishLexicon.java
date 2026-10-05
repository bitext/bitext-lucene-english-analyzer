package com.bitext.lucene.analysis.en;

import com.bitext.lucene.analysis.en.model.CandidateAnalysis;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import org.apache.lucene.codecs.CodecUtil;
import org.apache.lucene.store.ByteArrayDataInput;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.fst.BytesRefFSTEnum;
import org.apache.lucene.util.fst.FST;
import org.apache.lucene.util.fst.PositiveIntOutputs;
import org.apache.lucene.util.fst.Util;

/** Immutable runtime lexicon backed by a persisted Lucene BYTE1 FST. */
final class FstEnglishLexicon implements EnglishLexicon {
  static final int MAGIC = 0x42465831; // BFX1
  static final int FORMAT_VERSION = 1;
  private static final int MAX_PAYLOAD = 64 * 1024 * 1024;
  private static final int MAX_ENTRIES = 10_000_000;
  private static final int MAX_STRING = 16 * 1024 * 1024;
  private static final int MIN_BLOCK_BYTES = 4;
  private static final int MIN_CANDIDATE_BYTES = 16;

  private final String resourceId;
  private final String normalizationId;
  private final FST<Long> fst;
  private final List<List<CandidateAnalysis>> blocks; // block ID n is stored at n - 1
  private final int entryCount;
  private final int candidateCount;

  FstEnglishLexicon(
      String resourceId,
      String normalizationId,
      FST<Long> fst,
      List<List<CandidateAnalysis>> blocks,
      int entryCount,
      int candidateCount) {
    this.resourceId = resourceId;
    this.normalizationId = normalizationId;
    this.fst = fst;
    this.blocks = List.copyOf(blocks);
    this.entryCount = entryCount;
    this.candidateCount = candidateCount;
  }

  @Override
  public String resourceId() {
    return resourceId;
  }

  @Override
  public String normalizationId() {
    return normalizationId;
  }

  public int entryCount() {
    return entryCount;
  }

  public int candidateCount() {
    return candidateCount;
  }

  public long fstBytes() {
    return fst.numBytes();
  }

  @Override
  public List<CandidateAnalysis> lookup(String key) {
    Objects.requireNonNull(key, "key");
    try {
      Long block = Util.get(fst, new BytesRef(key));
      if (block == null) return List.of();
      if (block < 1 || block > blocks.size())
        throw new IllegalStateException("invalid FST analysis block");
      return blocks.get(Math.toIntExact(block - 1));
    } catch (IOException exception) {
      throw new IllegalStateException("FST lookup failed", exception);
    }
  }

  /** Enumerates persisted FST keys and identifiers without reconstructing a lookup map. */
  public List<Entry> entries() {
    try {
      List<Entry> result = new ArrayList<>(entryCount);
      BytesRefFSTEnum<Long> iterator = new BytesRefFSTEnum<>(fst);
      BytesRefFSTEnum.InputOutput<Long> next;
      while ((next = iterator.next()) != null) {
        long id = next.output;
        if (id < 1 || id > blocks.size())
          throw new IllegalStateException("invalid FST analysis block");
        result.add(new Entry(next.input.utf8ToString(), id, blocks.get(Math.toIntExact(id - 1))));
      }
      if (result.size() != entryCount) throw new IllegalStateException("FST entry count mismatch");
      return List.copyOf(result);
    } catch (IOException exception) {
      throw new IllegalStateException("FST enumeration failed", exception);
    }
  }

  record Entry(String key, long blockId, List<CandidateAnalysis> candidates) {}

  public static FstEnglishLexicon load(Path path, String expectedResourceId) throws IOException {
    try (InputStream input = Files.newInputStream(path)) {
      return load(input, expectedResourceId);
    }
  }

  public static FstEnglishLexicon load(InputStream source, String expectedResourceId)
      throws IOException {
    try (DataInputStream input = new DataInputStream(source)) {
      if (input.readInt() != MAGIC) throw new IOException("not a Bitext FST lexicon");
      if (input.readInt() != FORMAT_VERSION)
        throw new IOException("unsupported FST lexicon format");
      int payloadLength = input.readInt();
      if (payloadLength < 0 || payloadLength > MAX_PAYLOAD)
        throw new IOException("invalid FST payload length");
      byte[] payload = input.readNBytes(payloadLength);
      byte[] expected = input.readNBytes(32);
      if (payload.length != payloadLength || expected.length != 32 || input.read() != -1)
        throw new IOException("truncated or trailing FST resource");
      if (!MessageDigest.isEqual(expected, sha256(payload)))
        throw new IOException("FST resource integrity mismatch");
      return readPayload(payload, expectedResourceId);
    }
  }

  // Kept package-private for compiler tests that validate the payload separately.
  static FstEnglishLexicon readPayload(byte[] payload, String expectedResourceId)
      throws IOException {
    try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(payload))) {
      String luceneVersion = readString(input, false);
      org.apache.lucene.util.Version writerVersion;
      try {
        writerVersion = org.apache.lucene.util.Version.parse(luceneVersion);
      } catch (java.text.ParseException invalid) {
        throw new IOException("invalid Lucene writer version: " + luceneVersion, invalid);
      }
      if (writerVersion.major != org.apache.lucene.util.Version.LATEST.major) {
        throw new IOException("incompatible Lucene writer major: " + luceneVersion);
      }
      String resourceId = readString(input, false);
      String normalizationId = readString(input, false);
      if (resourceId.isEmpty() || normalizationId.isEmpty())
        throw new IOException("unsupported FST resource identity or key profile");
      final LexiconKeyProfile profile;
      try {
        profile = LexiconKeyProfile.fromId(normalizationId);
      } catch (IllegalArgumentException invalid) {
        throw new IOException("unsupported FST key profile: " + normalizationId, invalid);
      }
      if (expectedResourceId != null && !expectedResourceId.equals(resourceId))
        throw new IOException("unexpected FST resource identity: " + resourceId);
      int entries = checkedCount(input.readInt(), "entry");
      int candidates = checkedCount(input.readInt(), "candidate");
      if (entries == 0 || candidates == 0) throw new IOException("empty FST resource");
      int metadataLength = checkedLength(input.readInt(), "FST metadata");
      byte[] metadata = input.readNBytes(metadataLength);
      int dataLength = checkedLength(input.readInt(), "FST data");
      byte[] data = input.readNBytes(dataLength);
      if (metadata.length != metadataLength || data.length != dataLength)
        throw new IOException("truncated FST bytes");
      requireByte1(metadata);
      ByteArrayDataInput metadataInput = new ByteArrayDataInput(metadata);
      FST.FSTMetadata<Long> fstMetadata;
      try {
        fstMetadata = FST.readMetadata(metadataInput, PositiveIntOutputs.getSingleton());
      } catch (RuntimeException invalid) {
        throw new IOException("invalid FST metadata", invalid);
      }
      if (metadataInput.getPosition() != metadataLength || fstMetadata.getNumBytes() != dataLength)
        throw new IOException("inconsistent FST sections");
      FST<Long> fst;
      try {
        fst = new FST<>(fstMetadata, new ByteArrayDataInput(data));
      } catch (RuntimeException invalid) {
        throw new IOException("invalid FST data", invalid);
      }
      int remaining = input.available();
      if (entries > remaining / MIN_BLOCK_BYTES || candidates > remaining / MIN_CANDIDATE_BYTES)
        throw new IOException("declared FST table exceeds payload bounds");
      List<List<CandidateAnalysis>> blocks = new ArrayList<>(entries);
      int total = 0;
      for (int id = 1; id <= entries; id++) {
        int count = checkedCount(input.readInt(), "analysis block");
        if (count == 0) throw new IOException("empty analysis block " + id);
        if (count > input.available() / MIN_CANDIDATE_BYTES)
          throw new IOException("analysis block exceeds payload bounds");
        List<CandidateAnalysis> block = new ArrayList<>(count);
        for (int i = 0; i < count; i++) block.add(readCandidate(input));
        Collections.sort(block);
        if (new java.util.HashSet<>(block).size() != block.size())
          throw new IOException("duplicate candidate in analysis block");
        total = Math.addExact(total, count);
        blocks.add(List.copyOf(block));
      }
      if (total != candidates || input.read() != -1)
        throw new IOException("invalid FST analysis table");
      FstEnglishLexicon result =
          new FstEnglishLexicon(resourceId, normalizationId, fst, blocks, entries, candidates);
      validateFstOrdinals(result, entries, profile);
      return result;
    } catch (ArithmeticException exception) {
      throw new IOException("FST count overflow", exception);
    }
  }

  /**
   * Validates the public serialized FST declaration; this format intentionally accepts BYTE1 only.
   */
  private static void requireByte1(byte[] metadata) throws IOException {
    try {
      ByteArrayDataInput input = new ByteArrayDataInput(metadata);
      CodecUtil.checkHeader(input, "FST", FST.VERSION_START, FST.VERSION_CURRENT);
      if (input.readByte() != 0)
        throw new IOException("FST empty-output declaration is unsupported");
      int inputType = input.readByte() & 0xff;
      if (inputType != 0) throw new IOException("FST input type must be BYTE1");
    } catch (IndexOutOfBoundsException malformed) {
      throw new IOException("truncated FST metadata declaration", malformed);
    }
  }

  private static void validateFstOrdinals(
      FstEnglishLexicon result, int entries, LexiconKeyProfile profile) throws IOException {
    try {
      BytesRefFSTEnum<Long> iterator = new BytesRefFSTEnum<>(result.fst);
      BytesRefFSTEnum.InputOutput<Long> next;
      int ordinal = 0;
      while ((next = iterator.next()) != null) {
        String key = strictUtf8(next.input);
        if (!profile.acceptsKey(key)) throw new IOException("invalid " + profile.id() + " FST key");
        ordinal++;
        if (next.output == null
            || next.output != ordinal
            || next.output < 1
            || next.output > entries)
          throw new IOException("invalid or aliased FST analysis block identifier");
      }
      if (ordinal != entries) throw new IOException("FST key count mismatch");
    } catch (RuntimeException exception) {
      throw new IOException("malformed FST resource", exception);
    }
  }

  private static String strictUtf8(BytesRef value) throws IOException {
    try {
      return StandardCharsets.UTF_8
          .newDecoder()
          .onMalformedInput(CodingErrorAction.REPORT)
          .onUnmappableCharacter(CodingErrorAction.REPORT)
          .decode(java.nio.ByteBuffer.wrap(value.bytes, value.offset, value.length))
          .toString();
    } catch (java.nio.charset.CharacterCodingException exception) {
      throw new IOException("invalid UTF-8 FST key", exception);
    }
  }

  static byte[] sha256(byte[] bytes) throws IOException {
    try {
      return MessageDigest.getInstance("SHA-256").digest(bytes);
    } catch (NoSuchAlgorithmException e) {
      throw new IOException(e);
    }
  }

  static int checkedCount(int value, String name) throws IOException {
    if (value < 0 || value > MAX_ENTRIES) throw new IOException("invalid " + name + " count");
    return value;
  }

  static int checkedLength(int value, String name) throws IOException {
    if (value < 0 || value > MAX_PAYLOAD) throw new IOException("invalid " + name + " length");
    return value;
  }

  static String readString(DataInputStream input, boolean nullable) throws IOException {
    boolean present = input.readBoolean();
    if (!present) {
      if (nullable) return null;
      throw new IOException("required string absent");
    }
    int length = checkedLength(input.readInt(), "string");
    if (length > MAX_STRING) throw new IOException("oversized string");
    byte[] bytes = input.readNBytes(length);
    if (bytes.length != length) throw new IOException("truncated string");
    return StandardCharsets.UTF_8
        .newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(java.nio.ByteBuffer.wrap(bytes))
        .toString();
  }

  static CandidateAnalysis readCandidate(DataInputStream input) throws IOException {
    try {
      return new CandidateAnalysis(
          readString(input, false),
          readString(input, true),
          readString(input, true),
          readMap(input),
          readMap(input));
    } catch (IllegalArgumentException invalid) {
      throw new IOException("invalid candidate record", invalid);
    }
  }

  static Map<String, String> readMap(DataInputStream input) throws IOException {
    int count = checkedCount(input.readInt(), "map");
    Map<String, String> map = new TreeMap<>();
    for (int i = 0; i < count; i++) {
      String k = readString(input, false), v = readString(input, false);
      if (map.put(k, v) != null) throw new IOException("duplicate map key");
    }
    return map;
  }
}
