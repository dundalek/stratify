# Joern

[Joern](https://github.com/joernio/joern) is a tool for code analysis which includes parsers for multiple languages.
It is mostly aimed toward security analysis, but it might be possible to use it for visualization.

The model is based on [Code Property Graphs (CPG)](https://cpg.joern.io/).
It has concept of layers.
It starts with AST layer, other layers add additional information and link to nodes in AST layer.

Extracting CPG using the [export tool](https://docs.joern.io/export/):
```fish
for lang in go py rb ts;
  joern-parse --output tmp/cpg-$lang.bin ../scip/test/resources/sample-$lang;
  joern-export --repr all --format graphml --out test/resources/joern-cpg/out-$lang tmp/cpg-$lang.bin;
end
```
