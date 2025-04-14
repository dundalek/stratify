with import <nixpkgs> { };
mkShell {
buildInputs = [
clojure

clojure-lsp

rust-analyzer
cargo
rustc
rustfmt

];
shellHook = ''
'';
}
