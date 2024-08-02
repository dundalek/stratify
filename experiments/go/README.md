
# Experimental Go dependencies visualization

Uses [goda](https://github.com/loov/goda) to extract dependency graph, which is then converted to DGML.

```
go install github.com/loov/goda@latest
```

#### 1) Extract Go dependencies as Graphviz dot file

From within a Go project:

```
goda graph "./..." > graph.dot
```

Or to also include dependencies:

```
goda graph "./:all" > graph-all.dot
```

#### 2) Convert dot to DGML

```
clojure -Sdeps '{:deps{io.github.dundalek/stratify{:git/sha"98931f29e55bf02fe0c50d445e58e1abbb3d620c":deps/root"experiments/go"}}}' \
        -M -m stratify.go graph.dot > graph.dgml
```
