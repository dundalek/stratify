
### Unknown error

```
Unknown error
Please report an issue with details at https://github.com/dundalek/stratify/issues

Caused by:
Execution error (Error) at io.github.dundalek.stratify.error-catalog-test/fn$fn (error_catalog_test.clj:XXX).
Sample message

Full report at:
...
```

### :io.github.dundalek.stratify.internal/no-source-namespaces

```
Error:
There are no defined namespaces in analysis.
Did you pass correct source paths?

Code:
:io.github.dundalek.stratify.internal/no-source-namespaces

Caused by:
Execution error (ExceptionInfo) at io.github.dundalek.stratify.internal/analysis->graph (internal.clj:XXX).
There are no defined namespaces in analysis.
Did you pass correct source paths?

Full report at:
...
```

### :io.github.dundalek.stratify.dgml/failed-to-write

```
Error:
Failed to write output file.

Code:
:io.github.dundalek.stratify.dgml/failed-to-write

Caused by:
Execution error (FileNotFoundException) at java.io.FileOutputStream/open0 (FileOutputStream.java:-2).
/NON_EXISTING/output.dgml (No such file or directory)

Full report at:
...
```

## codecharta


### :io.github.dundalek.stratify.codecharta/ccsh-not-found

```
Error:
Failed to run `ccsh`.
Please make sure to have CodeCharta CLI installed.
Suggestion: `npm i -g codecharta-analysis`.

Code:
:io.github.dundalek.stratify.codecharta/ccsh-not-found

Caused by:
Execution error (IOException) at java.lang.ProcessImpl/forkAndExec (ProcessImpl.java:-2).
error=2, No such file or directory

Full report at:
...
```

### :io.github.dundalek.stratify.codecharta/ccsh-failed-to-run

```
Error:
Failed to run `ccsh`.

Code:
:io.github.dundalek.stratify.codecharta/ccsh-failed-to-run

Caused by:
Execution error (IOException) at java.lang.ProcessImpl/forkAndExec (ProcessImpl.java:-2).
error=13, Permission denied

Full report at:
...
```

## codecov


### :io.github.dundalek.stratify.codecov/failed-to-parse

```
Error:
Failed to parse Codecov file.

Code:
:io.github.dundalek.stratify.codecov/failed-to-parse

Caused by:
Execution error (FileNotFoundException) at java.io.FileInputStream/open0 (FileInputStream.java:-2).
test/resources/pulumi/NON_EXISTING.json (No such file or directory)

Full report at:
...
```

### :io.github.dundalek.stratify.codecov/failed-to-parse

```
Error:
Failed to parse Codecov file.

Code:
:io.github.dundalek.stratify.codecov/failed-to-parse

Caused by:
Execution error (JsonEOFException) at com.fasterxml.jackson.core.base.ParserMinimalBase/_reportInvalidEOF (ParserMinimalBase.java:XXX).
Unexpected end-of-input: expected close marker for Object (start marker at [Source: REDACTED (`StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION` disabled); line: 1, column: 1])
 at [Source: REDACTED (`StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION` disabled); line: 2, column: 1]

Full report at:
...
```

### :io.github.dundalek.stratify.codecov/invalid-input

```
Error:
Failed to load Pulumi resources.

{"coverage" ["missing required key"]}

Code:
:io.github.dundalek.stratify.codecov/invalid-input

Caused by:
Execution error (ExceptionInfo) at malli.core/-exception (core.cljc:XXX).
:malli.core/coercion

Full report at:
...
```

### :io.github.dundalek.stratify.codecov/coverage-range-out-of-bounds

```
Error:
Coverage line range is out of bounds. Please make sure the coverage file is up-to-date with the source code.

Code:
:io.github.dundalek.stratify.codecov/coverage-range-out-of-bounds

Caused by:
Execution error (ExceptionInfo) at io.github.dundalek.stratify.codecov/make-line-coverage-raw-lookup$fn (codecov.clj:XXX).
Coverage line range is out of bounds. Please make sure the coverage file is up-to-date with the source code.

Full report at:
...
```

## graphviz


### :io.github.dundalek.stratify.graphviz/failed-to-parse

```
Error:
Failed to parse Graphviz file.

Code:
:io.github.dundalek.stratify.graphviz/failed-to-parse

Caused by:
Execution error (ParseError) at clj-antlr.common/parse-error (common.clj:XXX).
missing '}' at '<EOF>'

Full report at:
...
```

### :io.github.dundalek.stratify.graphviz/failed-to-parse

```
Error:
Failed to parse Graphviz file.

Code:
:io.github.dundalek.stratify.graphviz/failed-to-parse

Caused by:
Execution error (FileNotFoundException) at java.io.FileInputStream/open0 (FileInputStream.java:-2).
test/resources/graphviz/NON_EXISTING (No such file or directory)

Full report at:
...
```

### :io.github.dundalek.stratify.graphviz/empty-graph

```
Error:
Input graph has no nodes or edges.

Code:
:io.github.dundalek.stratify.graphviz/empty-graph

Caused by:
Execution error (ExceptionInfo) at io.github.dundalek.stratify.graphviz/graphviz->loom (graphviz.clj:XXX).
Input graph has no nodes or edges.

Full report at:
...
```

### :io.github.dundalek.stratify.dgml/failed-to-write

```
Error:
Failed to write output file.

Code:
:io.github.dundalek.stratify.dgml/failed-to-write

Caused by:
Execution error (FileNotFoundException) at java.io.FileOutputStream/open0 (FileOutputStream.java:-2).
/tmp/__NON_EXISTING_DIRECTORY_42__/output.dgml (No such file or directory)

Full report at:
...
```

## overarch


### :io.github.dundalek.stratify.overarch/invalid-input

```
Error:
Failed to load Overarch model.

Code:
:io.github.dundalek.stratify.overarch/invalid-input

Caused by:
Execution error at org.soulspace.overarch.adapter.repository.file-model-repository/read-model-file (file_model_repository.clj:XXX).
Invalid token: //

Full report at:
...
```

### :io.github.dundalek.stratify.dgml/failed-to-write

```
Error:
Failed to write output file.

Code:
:io.github.dundalek.stratify.dgml/failed-to-write

Caused by:
Execution error (FileNotFoundException) at java.io.FileOutputStream/open0 (FileOutputStream.java:-2).
/NON_EXISTING/output.dgml (No such file or directory)

Full report at:
...
```

## pulumi


### :io.github.dundalek.stratify.pulumi/failed-to-parse

```
Error:
Failed to parse Pulumi file.

Code:
:io.github.dundalek.stratify.pulumi/failed-to-parse

Caused by:
Execution error (JsonEOFException) at com.fasterxml.jackson.core.base.ParserMinimalBase/_reportInvalidEOF (ParserMinimalBase.java:XXX).
Unexpected end-of-input: expected close marker for Object (start marker at [Source: REDACTED (`StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION` disabled); line: 1, column: 1])
 at [Source: REDACTED (`StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION` disabled); line: 2, column: 1]

Full report at:
...
```

### :io.github.dundalek.stratify.pulumi/invalid-input

```
Error:
Failed to load Pulumi resources.

{:deployment ["missing required key" "missing required key"], :steps ["missing required key"]}

Code:
:io.github.dundalek.stratify.pulumi/invalid-input

Caused by:
Execution error (ExceptionInfo) at malli.core/-exception (core.cljc:XXX).
:malli.core/coercion

Full report at:
...
```

### :io.github.dundalek.stratify.dgml/failed-to-write

```
Error:
Failed to write output file.

Code:
:io.github.dundalek.stratify.dgml/failed-to-write

Caused by:
Execution error (FileNotFoundException) at java.io.FileOutputStream/open0 (FileOutputStream.java:-2).
/NON_EXISTING/output.dgml (No such file or directory)

Full report at:
...
```
