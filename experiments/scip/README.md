# SCIP

One of the challenges to create a polyglot code analysis system is the effort it takes to integrate individual programming languages.
It would be useful to leverage a universal system, like what [LSP](https://microsoft.github.io/language-server-protocol/) provides to IDEs.

Turns out there is [Language Server Index Format (LSIF)](https://lsif.dev/) which is basically a dump of analysis from Language Servers.
LSIF was [used at Sourcegraph](https://sourcegraph.com/blog/evolution-of-the-precise-code-intel-backend) for their code navigation feature.
Later Soucegraph [announounced](https://sourcegraph.com/blog/announcing-scip) SCIP Code Intelligence Protocol ([SCIP](https://github.com/sourcegraph/scip)) as a successor to LSIF.
This is an investigation whether SCIP can be used to build code analysis features.

## Exploration

SCIP encodes index as Protobuf using [CLI tools](https://github.com/sourcegraph/scip?tab=readme-ov-file#tools-using-scip) to extract code written in several programming languages. In my exploration I am using [Pronto](https://github.com/AppsFlyer/pronto) library for Clojure to parse the SCIP index.

The [data model](https://github.com/sourcegraph/scip/blob/main/scip.proto) of the `Index` consists of `Documents` that contain `Occurrences` of `Symbols`.

Location of an occurrence is defined by `Range`:

> Half-open [start, end) range of this occurrence. Must be exactly three or four elements:  
> - Four elements: `[startLine, startCharacter, endLine, endCharacter]`  
> - Three elements: `[startLine, startCharacter, endCharacter]`. The end line is inferred to have the same value as the start line.  

Let's take following Typescript example:

```ts
export function greet() {
  console.log('Hello, World!');
}

greet();
```

It is represented as:

```clj
{:metadata
 {:version :UnspecifiedProtocolVersion,
  :tool_info
  {:name "scip-typescript", :version "v0.3.14", :arguments []},
  :project_root
  "file:///home/me/projects/nanaban/code/stratify/test/resources/sample-ts-simple",
  :text_document_encoding :UTF8},
 :documents
 [{:relative_path "src/main.ts",
   :occurrences
   [{:range [0 0 0],
     :symbol "scip-typescript npm . . src/`main.ts`/",
     :symbol_roles 1,
     :override_documentation [],
     :syntax_kind :UnspecifiedSyntaxKind,
     :diagnostics []}
    {:range [0 16 21],
     :symbol "scip-typescript npm . . src/`main.ts`/greet().",
     :symbol_roles 1,
     :override_documentation [],
     :syntax_kind :UnspecifiedSyntaxKind,
     :diagnostics []}
    {:range [1 2 9],
     :symbol
     "scip-typescript npm typescript 5.6.3 lib/`lib.dom.d.ts`/console.",
     :symbol_roles 0,
     :override_documentation [],
     :syntax_kind :UnspecifiedSyntaxKind,
     :diagnostics []}
    {:range [1 10 13],
     :symbol
     "scip-typescript npm typescript 5.6.3 lib/`lib.dom.d.ts`/Console#log().",
     :symbol_roles 0,
     :override_documentation [],
     :syntax_kind :UnspecifiedSyntaxKind,
     :diagnostics []}
    {:range [4 0 5],
     :symbol "scip-typescript npm . . src/`main.ts`/greet().",
     :symbol_roles 0,
     :override_documentation [],
     :syntax_kind :UnspecifiedSyntaxKind,
     :diagnostics []}],
   :symbols
   [{:symbol "scip-typescript npm . . src/`main.ts`/",
     :documentation ["```ts\nmodule \"main.ts\"\n```"],
     :relationships []}
    {:symbol "scip-typescript npm . . src/`main.ts`/greet().",
     :documentation ["```ts\nfunction greet(): void\n```"],
     :relationships []}]}],
 :external_symbols []}
```

Notable occurrence is the definition of the `greet()` function at range `[0 16 21]` (`:symbol_roles` `1` means it is a definition).
We see the three-element range, which means it is on a single line and refers to the symbol itself, we have no information about the range of the function body.

Next we can see last two occurrences (`:symbol_roles` `0` is for references):
- The range `[1 10 13]` is a call to `Console#log()` inside the `greet` function.
- The range `[4 0 5]` is a call to `greet` from the module top-level.

There is now way to distinguish whether a reference belongs to a function or not. Therefore we cannot determine dependencies of a function. This is an issue because:
- a link in a code visualization graph would be from a module, so the feature of navigating to function dependencies would not work
- we cannot help untangle code by simulating refactoring a function to a different module since the state of dependencies would remain unchanged

## Conclusion

Unfortunately SCIP does not seem to provide enough information about dependencies and is not well suited for advanced code navigation and visualization.

Alternatives to consider:
- Try the predecessor [LSIF](https://lsif.dev/)  
  It is based on JSON and is less efficient, but perhaps could contain more information necessary to construct the code graph.
- [stack-graphs](https://github.com/github/stack-graphs) (introduction [post](https://github.blog/open-source/introducing-stack-graphs/))  
  Stack Graphs don't rely on existing languages analyzers, but instead builds on top TreeSitter grammars and parsers are build on top a concept of stacked scopes.
