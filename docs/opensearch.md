# OpenSearch 3.4+

Build the plugin with JDK 21:

```bash
./gradlew build
```

Install `build/distributions/bitext-java-english-opensearch-1.0.0.zip` with the
normal plugin tool, then restart OpenSearch:

```bash
bin/opensearch-plugin install file:///absolute/path/to/bitext-java-english-opensearch-1.0.0.zip
```

The released analyzer uses its bundled resources by default:

```json
{
  "settings": {
    "analysis": {
      "analyzer": {
        "bitext_english": {
          "type": "bitext_java_english"
        }
      }
    }
  }
}
```

The same plugin ZIP is tested with OpenSearch 3.4.0 and 3.9.0. For
`Children. States.`, `_analyze` emits `children`, `child`, `states`, and
`state`.

## Optional output mode

`output_mode` is the only optional analyzer setting. `EXPAND` is the default:
it preserves useful surface forms while adding normalized lemma forms. Use
`REPLACE` when only normalized/lemma output is wanted where expansion would
otherwise retain the original surface term:

```json
{
  "type": "bitext_java_english",
  "output_mode": "REPLACE"
}
```
