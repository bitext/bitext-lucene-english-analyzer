package com.bitext.lucene.analysis.en;

/** Declares how a lexical resource's UTF-8 FST keys are represented. */
enum LexiconKeyProfile {
  NORMALIZED_LOWERCASE(EnglishText.NORMALIZED_LOWERCASE_ID),
  EXACT("english-exact-v1");

  private final String id;

  LexiconKeyProfile(String id) {
    this.id = id;
  }

  public String id() {
    return id;
  }

  public static LexiconKeyProfile fromId(String id) {
    for (LexiconKeyProfile profile : values()) if (profile.id.equals(id)) return profile;
    throw new IllegalArgumentException("unsupported FST key profile: " + id);
  }

  public boolean acceptsKey(String key) {
    return EnglishText.wellFormedUnicode(key)
        && !key.isEmpty()
        && (this == EXACT || key.equals(EnglishText.lower(key)));
  }
}
