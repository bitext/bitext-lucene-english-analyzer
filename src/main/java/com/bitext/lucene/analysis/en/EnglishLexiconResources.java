package com.bitext.lucene.analysis.en;

import java.io.IOException;
import java.io.InputStream;

/** Selects one validated immutable dictionary backend for a configured factory lifecycle. */
final class EnglishLexiconResources {
  private EnglishLexiconResources() {}

  /** Loads a released BFX resource from the application classpath. */
  public static EnglishLexicon fromClasspath(String resource, String expectedResourceId) {
    if (resource == null || resource.isEmpty()) {
      throw new IllegalArgumentException("configured FST resource is missing");
    }
    String path = classpathPath(resource);
    try (InputStream stream = EnglishLexiconResources.class.getResourceAsStream(path)) {
      if (stream == null)
        throw new IllegalArgumentException("configured FST resource not found: " + resource);
      return FstEnglishLexicon.load(stream, expectedResourceId);
    } catch (IOException error) {
      throw new IllegalArgumentException(
          "cannot load configured FST resource: " + resource + ": " + error.getMessage(), error);
    }
  }

  /** Loads a released BFX resource supplied by a host resource loader. */
  public static EnglishLexicon fromStream(InputStream stream, String expectedResourceId)
      throws IOException {
    return FstEnglishLexicon.load(stream, expectedResourceId);
  }

  private static String classpathPath(String resource) {
    String path =
        resource.startsWith("classpath:") ? resource.substring("classpath:".length()) : resource;
    return path.startsWith("/") ? path : "/" + path;
  }
}
