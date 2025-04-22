# LSP

This is an experiment of using LSP servers to query for information to build dependency graph for code analysis.
It is based on [version 3.17 of the specification](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/).

The motivation is similar to using SCIP in hope that LSP could provide more detailed information.
The main idea is to query for symbols and for each symbol to lookup references to create the graph.

#### Document symbols + references

Symbols can be fetched using `textDocument/documentSymbol` which returns a list of `DocumentSymbol`s.
It is notable that `DocumentSymbol` has two range properties:
- `range` - which encloses the whole symbol, e.g. including a function body
- `selectionRange` - which is narrower and contained inside range and refers to just the name of a function

Therefore we can determine which references are contained inside a `range` to resolve references on a function level.

One we have symbols for each file, we can use `textDocument/references` to get references.

#### Consideration: Workspace symbols

In addition to document symbols there is also `workspace/symbol` request which returns all symbols in a workspace.
It would be convenient to get all workspace symbols and then get their references.
The limitation is that it returns a list of `WorkspaceSymbol` requests which only returns one location range, which makes it not possible to extract function-level dependency graphs.

Therefore instead of listing workspace symbols we need to list/glob source files from file system, and list document symbols for each file.

#### Consideration: Call hierarchy

TODO

#### Consideration: Semantic tokens

TODO

#### Consideration: Monikers

TODO

## Proxy

There is a [proxy](./bin/proxy) script which proxies messages between a client and a server and logs them into files.

This can be useful to intercept communication and to see examples of messages and parameters sent in practice.

For example configure proxying `rust-analyzer` with Neovim and [lazy-lsp](https://github.com/dundalek/lazy-lsp.nvim):
```lua
configs = {
           rust_analyzer = {
                            cmd = {
                                   "/path/to/bin/proxy", "--output-dir", "/some/dir", "--",
                                   "rust-analyzer"}
                            ,}
           ,}
,
```

## Results
