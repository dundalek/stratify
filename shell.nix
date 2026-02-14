with import <nixpkgs> { };
let
  astgen = callPackage ./nix/astgen.nix { };
  goastgen = callPackage ./nix/goastgen.nix { };
  scip-clang = callPackage ./nix/scip-clang.nix { };
  scip-go = callPackage ./nix/scip-go.nix { };
  scip-python = callPackage ./nix/scip-python.nix { };
  scip-ruby = callPackage ./nix/scip-ruby.nix { };
  scip-typescript = callPackage ./nix/scip-typescript.nix { };
in
mkShell {
buildInputs = [

babashka
clojure

# == For LSP extraction

clojure-lsp

# for clangd
clang-tools

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

scip-clang
scip-go
scip-python
scip-ruby
# scip-typescript

# == for joern extraction

astgen
goastgen

];
shellHook = ''
  export ASTGEN_BIN=$(which astgen)
'';
}
