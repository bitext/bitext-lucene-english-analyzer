# Elasticsearch 9.x

Build the stable plugin with JDK 21:

```bash
./gradlew build
```

Install `build/distributions/bitext-java-english-elasticsearch-1.0.0.zip` with
Elasticsearch's normal plugin tool, then restart the node:

```bash
bin/elasticsearch-plugin install file:///absolute/path/to/bitext-java-english-elasticsearch-1.0.0.zip
```

Create an index with the bundled analyzer. No resource locations or analyzer
pipeline settings are required:

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

For `Children. States.`, `_analyze` emits `children`, `child`, `states`, and
`state`. This release is built and tested with Elasticsearch 9.5.4.

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
