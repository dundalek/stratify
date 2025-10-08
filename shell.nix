with import <nixpkgs> { };
mkShell {
buildInputs = [

babashka
clojure

# == For LSP extraction

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

# == for scip extraction

python3Packages.pip

];
shellHook = ''
'';
}
