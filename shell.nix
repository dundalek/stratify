with import <nixpkgs> { };
let
  scip-typescript = callPackage ./nix/scip-typescript.nix { };
in
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

zig
zls

# == for scip extraction

python3Packages.pip
scip-typescript

];
shellHook = ''
'';
}
