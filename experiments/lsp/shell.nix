with import <nixpkgs> { };
mkShell {
buildInputs = [
clojure

clojure-lsp

gopls
go

rust-analyzer
cargo
rustc
rustfmt
];
shellHook = ''
'';
}
