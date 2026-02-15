# Stratify

Stratify is a tool for exploring and improving architecture of software.
Discover bits of Stratified Design that are hiding in your code.
Gain big picture understanding to make better decisions how to grow your system.

Features and sources:

- Code maps - Visualize structure and dependencies of codebases, supports following sources:
  - [Source code](#source-code-extraction) - C/C++, Clojure, Go, Java, JavaScript/TypeScript, Lua, Python, Ruby, Rust, Zig
  - [Graphviz](#graphviz-visualization) - Interactive visualization of outputs produced by other tools  
    (e.g. Go, JavaSript/TypeScript dependencies or others)
  - [Architecture maps](#architecture-maps) - Explore C4 models
  - [Infrastructure maps](#infrastructure-maps) - Infrastructure-as-Code (IaC) using Pulumi or SST
- [Metrics reports](#metrics-reports) -  Calculate code metrics and generate visual reports
- [Architecture checks](#architecture-checks) - Enforce architectural constrains, dependency rules, layer violations

Visualization renderers:
- [DGML](#dgml-renderer) - For visualization it leverages the [code map](https://learn.microsoft.com/en-us/visualstudio/modeling/browse-and-rearrange-code-maps?view=vs-2022) tool from Visual Studio,
which is designed for hierarchical graphs,
and allows to interactively collapse or expand the amount of shown information.
- [3D Code City](#3d-code-city-renderer) - Outputs data for use with [CodeCharta](https://codecharta.com) tool to visualize codebase and metrics in 3D view.
- [3D Code Galaxy](#3d-galaxy-renderer) - Outputs data for use with the [Dep-Tree](https://github.com/gabotechs/dep-tree) visualizer that uses 3D force layout view.

### DGML Renderer

This is an advantage over static graph rendering tools like Graphviz 
which only work for trivial sized graphs,
because for a size of systems encountered in practice it becomes a tangle of lines.
That is overwhelming and does not aid much in understanding the structure.

| | |
| - | - |
| ![stratify-asami](doc/img/stratify-asami.png) | ![stratify-babashka](doc/img/stratify-babashka.png) |
| ![stratify-promesa](doc/img/stratify-promesa.png) | ![stratify-jepsen](doc/img/stratify-jepsen.png) |

The code map tool in Visual Studio uses [DGML](#about-dgml),
which is an XML-based format for representing directed graphs.
Visualizing a codebase is a two step process:
1. First, this tool reads Clojure code and outputs a DGML graph.
2. Then the graph is loaded and visualized using the DGML Editor in Visual Studio.

### 3D Code City Renderer

Additionally, Clojure code can be extracted to [CodeCharta](https://codecharta.com/) format to visualize it as 3D Code City. In this view code metrics can be mapped to visualization to uncover hotspots or areas that need attention.
[See details](#3d-code-city).

![Logseq codebase visualized using CodeCharta](doc/img/codecharta-logseq-cropped.avif)

### 3D Galaxy Renderer

There is also support for the [Dep-Tree](https://github.com/gabotechs/dep-tree) visualizer which uses 3D force layout.
The way the nodes are spread into clusters can be used to judge modularity of a codebase.
[See instructions](#3d-code-galaxy).

![Example visualization of InstandDB](doc/img/dep-tree-instantdb.avif)

### Demos and Talks

Watch the [demo video](https://www.youtube.com/watch?v=8LMrIpxxpDw) which shows several use cases:

- Big picture understanding - Explore a codebase top-down to gain insights about the system structure.
- Local understanding - Navigate and traverse the call graph to learn details about implementation.
- Refactoring simulation - Improve structure of a codebase by previewing results of refactoring.

[![Stratify Demo](doc/img/demo-thumbnail.png)](https://www.youtube.com/watch?v=8LMrIpxxpDw)

Watch the [London Clojurians talk](https://www.youtube.com/watch?v=olTNZeKpc2M) that goes into depth and also discusses software architecture in general:

[![Stratify Clojurians talk](doc/img/london-clj-talk-thumbnail.avif)](https://www.youtube.com/watch?v=olTNZeKpc2M)

## Usage

First extract DGML graph from source code.

#### Use without installing

```
clojure -Sdeps '{:deps{io.github.dundalek/stratify{:git/tag"v0.4.0":git/sha"48726c2"}}}' \
        -M -m stratify.main
```

#### Install by adding alias

`~/.clojure/deps.edn` to `:aliases` section

```clojure
{:aliases
 {:stratify
  {:extra-deps {io.github.dundalek/stratify {:git/tag "v0.4.0" :git/sha "48726c2"}}
   :main-opts ["-m" "stratify.main"]
```

Then run:
```
clojure -M:stratify path/to/src -o graph.dgml
```

#### Troubleshooting

Stratify needs Clojure 1.12.
In case you get an error `Could not locate clojure/repl/deps__init.class`:
- Make sure to update Clojure CLI tools to latest version (`clojure --version` should print `Clojure CLI version 1.12.0.1479` or later).
- Alternatively add `org.clojure/clojure {:mvn/version "1.12.0"}`  to `:deps` map explicitly.

<details>
<summary>Details</summary>

Full error message:

```
Execution error (FileNotFoundException) at stratify.main/eval138$loading (main.clj:1).
Could not locate clojure/repl/deps__init.class, clojure/repl/deps.clj or clojure/repl/deps.cljc on classpath.
```

`~/.clojure/deps.edn` `:aliases` section with added Clojure 1.12:

```clojure
{:aliases
 {:stratify
  {:extra-deps {io.github.dundalek/stratify {:git/tag "v0.4.0" :git/sha "48726c2"}
                org.clojure/clojure {:mvn/version "1.12.0"}}
   :main-opts ["-m" "stratify.main"]
```

</details>

#### Options

```
Usage: stratify <options> <src-paths>

Options:
      --metrics-delta                       Calculate and serve metrics delta report
      --include-dependencies                Include links to library dependencies
      --insert-namespace-node <label>       Group vars mixed among namespaces under a node with a given label
      --flat-namespaces                     Render flat namespaces instead of a nested hierarchy
      --coverage-file         <file>        Include line coverage metric from given Codecov file
  -o, --out                   <file>   -    Output file, default "-" standard output
  -f, --from                  <format> clj  Source format, choices:
                Language extractors: "clj", "go-lsp", "go-scip", "lua-lsp", "python-scip", "ruby-scip", "rust-lsp", "ts-lsp", "ts-scip", "zig-lsp"
                Other formats: "dgml", "dot", "overarch", "pulumi", "scip"
  -h, --help                                Print this help message and exit
      --metrics                             Calculate and serve namespace metrics report
  -t, --to                    <format> dgml Target format, choices: "codecharta", "dep-tree", "dgml"
```

### Source Code extraction

| Language | Namespace Dependencies | Function Dependencies | Test Coverage |
|----------|----------|------------|----------|
| C / C++ |  | joern, lsp (clangd) |  |
| Clojure |  | clj-kondo | codecov (cloverage) |
| Go | scip | joern, lsp (gopls) | |
| Java |  | joern | |
| JavaScript / TypeScript | scip | joern, lsp (typescript-language-server) | |
| Lua | treesitter | lsp (lua-language-server) | |
| Python | scip | joern | |
| Ruby | scip | | |
| Rust | | lsp (rust-analyzer) | |
| Zig |  | lsp (zls) | |

Granularity of extractions:

- Namespace dependencies - Coarser-grained dependencies only between modules/files
- Function dependencies - Finer-grained including dependencies between functions/methods within modules/files

##### LSP-based extractors

[LSP](https://langserver.org/) language servers are used to analyze code.
The corresponding language server must be installed and available on the PATH.

For example to extract TypeScript dependencies using typescript-language-server (installed with `npm install -g typescript-language-server`):

```bash
clojure -M:stratify -f ts-lsp -o graph.dgml src
```

##### SCIP-based extractors

This approach leverages [SCIP](https://github.com/sourcegraph/scip) index files.

For selected languages SCIP indexers can be invoked automatically.
For example to extract Python dependencies (`scip-python` must be installed and available on PATH):

```bash
clojure -M:stratify -f python-scip -o graph.dgml src
```

For additional languages generate the index first, the use `-f scip` to extract the index file:
```bash
scip-python index # creates index.scip
clojure -M:stratify -f scip -o graph.dgml index.scip
```

### Using Visual Studio DGML Editor

Once you extracted the graph use [Visual Studio](https://visualstudio.microsoft.com/) to visualize it.

A downside is that Visual Studio is Windows-only, but it can be run in a Virtual Machine (VM) and there are VM images provided for developers.
It is [sufficient](https://learn.microsoft.com/en-us/visualstudio/modeling/analyze-and-model-your-architecture?view=vs-2022#VersionSupport) to use the free Community edition.

- Run Visual Studio in VM (optional)
  - [VM images](https://developer.microsoft.com/en-us/windows/downloads/virtual-machines/) for developers in various formats, e.g. for VirtualBox
  - Visual Studio 2022 Community edition is pre-installed
- Enable DGML Editor
  - menu Tools -> Get Tools and Features (opens Visual Studio Installer) -> Individual Components
    - check DGML Editor
- Install [DgmlPowerTools 2022](https://marketplace.visualstudio.com/items?itemName=ChrisLovett.DgmlPowerTools2022) extension ([source](https://github.com/clovett/DgmlPowerTools), optional)
  - provides extra features like neighborhood and butterfly exploration modes
  - menu Extensions -> Manage Extensions

### 3D Code City

1) Install dependencies


Install [CodeCharta CLI](https://codecharta.com/docs/overview/getting-started#installation) with  `npm i -g codecharta-analysis` to get `ccsh` command.

Install [tokei](https://github.com/XAMPPRocky/tokei?tab=readme-ov-file#installation) (optional to get line and comment counts).

2) Extract with CLI

Run the extraction command, it creates `output-prefix.cc.json.gz` file.
````
clojure -M:stratify -t codecharta -o output-prefix src
````

Additionally use `--coverage-file codecov.json` option to include [code coverage](#code-coverage) metrics.


3) Visualize  

Open [CodeCharta Web Studio](https://codecharta.com/visualization/app/index.html)
and click the open button to load the `.cc.json.gz` file.

Suggested metrics:

- Area - representing size like `loc`, `rloc`
- Color - representing quality like `graph_betweenness_centrality`, `line_coverage`
- Height - representing magnitude like `number_of_commits`, `number_of_authors`  
  (If a source has bad quality metric, the problem is magnified by its height.)

### 3D Code Galaxy

1) Run the extraction command:

````
clojure -M:stratify -t dep-tree -o output.json src
````

2) Open the [Dep-Tree visualizer](https://dep-tree.pages.dev/) and load the file to visualize.

### Code coverage

Stratify can load test coverage using [Codecov](https://docs.codecov.com/docs/codecov-custom-coverage-format) format.

For Clojure use [Kaocha](https://github.com/lambdaisland/kaocha) test runner with
[kaocha-cloverage](https://github.com/lambdaisland/kaocha-cloverage) plugin to collect coverage info.

Enable the `codecov?` option in `tests.edn` to output the codecov file:

```clojure
#kaocha/v1
{:plugins [kaocha.plugin/cloverage]
 :cloverage/opts
 {:codecov? true}}
```

Then use the `--coverage-file` option, nodes in the graph will be colored according to their coverage value.
```
clojure -M:stratify --coverage-file target/coverage/codecov.json -o graph.dgml src
```

![Example graph with code coverage visualization](doc/img/code-coverage-dgml-stratify.avif)

Visualizing code coverage on a directed graph can help to discover a problem when lower layers are poorly tested. 
As a rule of thumb strive for "greener" lower layers, since poor quality on lower layers compounds impact on upper layers.

### Graphviz visualization

To convert Graphviz `.dot` format to DGML pass the `-f dot` option:

```
clojure -M:stratify graph.dot -f dot -o graph.dgml
```

By default nested hierarchy is created based on segments using `/` as separator.  
Pass the `--flat-namespaces` option for flat nodes without nesting.

##### Extract Go dependencies as Graphviz dot file

Use [goda](https://github.com/loov/goda) to extract dependency graph,
which is then converted to DGML.

```
go install github.com/loov/goda@latest
```

From within a Go project:

```
goda graph "./..." > graph.dot
```

Or to also include dependencies:

```
goda graph "./:all" > graph-all.dot
```

Compare the legibility of a typical graphviz dependency graph to the same graph displayed as hierarchical on the right.
Note that groups can be further collapsed using the interactive viewer.

![Graphviz vs. layered graph of Lazygit codebase extracted using Goda](doc/img/lazygit-graphviz-vs-layered.avif)

##### JavaScript/TypeScript dependencies visualization

Use [Dependency cruiser](https://github.com/sverweij/dependency-cruiser) to extract JS/TS dependencies as Graphviz dot file:

```
bunx depcruise src --include-only "^src" --output-type dot > graph.dot
```

To also include dependencies:

```
bunx depcruise src --output-type dot > graph.dot
```

### Architecture maps

View [C4](https://c4model.com) architecture models expressed in [Overarch](https://github.com/soulspace-org/overarch) format.

When a model becomes large it can end up overwhelming.
Overarch lets you choose upfront which parts of your model to render as static diagrams.
However, it can be useful to see the entire model at once,
explore it interactively, and drill down to areas of interest as needed.

To convert an architecture model to DGML use the `-f overarch` option and pass the model directory:

```
clojure -M:stratify models/banking -f overarch -o banking.dgml
```

Here is a rendering of the example [banking model](https://github.com/soulspace-org/overarch/blob/0551900472757ca1cf5973f5e598da534d49367e/models/banking/model.edn):

![Overarch Banking model](doc/img/overarch-banking.png)

### Infrastructure maps

[Pulumi](https://www.pulumi.com/) includes builtin graph visualization `pulumi stack graph`.
It suffers the same illegibility problem like other solutions based on [Graphviz](#graphviz-visualization).
Stratify can be used an alternative to visualize infrastructure stacks with collapsible levels of detail.


Export stack state to JSON:
```
pulumi stack export --json > state.json
```

You can also export preview, useful for visualizing the stack first without deploying:
```
pulumi preview --show-sames --json > state-preview.json
```

Then transform the stack state to DGML graph:

```
clojure -M:stratify -f pulumi -o graph.dgml state.json
```

Since [SST](https://sst.dev/) internally uses Pulumi, it is possible to also visualize SST stacks.
Run `sst diagnostic --stage your-stage` to generate `report.zip` which includes `state.json` that can be visualized.

Example visualization of the AWS-based [Voting App](https://github.com/pulumi/examples/blob/master/aws-ts-pern-voting-app/index.ts) example:

![Voting App example infrastructure visualization](doc/img/pulumi-aws-ts-pern-voting-app.avif)

### Metrics reports

Use the  `--metrics` option to calculate code metrics for given source paths and generate a report.
[Clerk](https://github.com/nextjournal/clerk) is used to start a local web server which renders a notebook as a web page.
Metrics and charts can be adapted by customizing the [notebook.clj](resources/io/github/dundalek/stratify/notebook.clj).

```
clojure -M:stratify src --metrics
```

Use the `-o` / `--out` to generate the report as a HTML file.
It can be useful to run periodically on CI and upload the HTML report to a static hosting server.

```
clojure -M:stratify src --metrics -o report.html
```

| | |
| - | - |
| ![Metrics](doc/img/metrics-table.png) | ![Charts](doc/img/metrics-chart-degrees.png) |

#### Metrics delta

There is a way to compare metrics between two versions to see how a system changed over time.

1) Extract sources to DGML at different checkouts:

```
git checkout commit-a
clojure -M:stratify -o a.dgml src

git checkout commit-b
clojure -M:stratify -o b.dgml src
```


2) Then open the report with:

```
clojure -M:stratify --metrics-delta a.dgml b.dgml
```

| | |
| - | - |
| ![System metrics diff](doc/img/metrics-delta-top.avif) | ![Element metrics diff](doc/img/metrics-delta-bottom.avif) |

### Architecture checks

The goal is to be able to define rules for code like architectural constraints, dependency rules, and layer violations.
It is inspired by [ArchUnit](https://www.archunit.org) with a difference of using graph queries ([Datalog](https://www.learndatalogtoday.org)) aiming to be mostly programming language agnostic and only needing thin adapters.

Currently, the feature is not ready yet.
There is a work-in-progress namespace [queries.clj](src/io/github/dundalek/stratify/queries.clj) used for experiments in the REPL to demonstrate the approach.
The result from code analysis is loaded into in-memory [DataScript](https://github.com/tonsky/datascript) database and queries to check code rules run against it.
Future work will be to try to express various rules, identify common patterns, and create more concise helpers.

## About DGML

[DGML](https://en.wikipedia.org/wiki/DGML) stands for Directed Graph Markup Language 

- Watch [Overview Video](https://www.youtube.com/watch?v=wIjCdOrZj-I) of the features and how to use the editor showcasing [examples](https://github.com/clovett/dgml).
- For more details see [Reference](https://learn.microsoft.com/en-us/visualstudio/modeling/directed-graph-markup-language-dgml-reference?view=vs-2022) and [XSD Schema](https://schemas.microsoft.com/vs/2009/dgml/).

Available renderers:

- DGML editor in Visual Studio 2022, Windows-only (recommended)
- [DGMLViewer](https://marketplace.visualstudio.com/items?itemName=coderAllan.vscode-dgmlviewer) plugin for Visual Studio Code, cross-platform
  - only viewer, no editing
  - does not seem to work very well, many examples cannot be opened
