with import <nixpkgs> { };
mkShell {
buildInputs = [
clojure

clojure-lsp

gopls
go

lua-language-server

rust-analyzer
cargo
rustc
rustfmt

typescript-language-server
typescript
];
shellHook = ''
'';
}
