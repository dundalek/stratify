with import <nixpkgs> { };
mkShell {
buildInputs = [
  babashka
  clojure

  # for scip extractors
  go
  nodejs
  ruby
  cargo
  rust-analyzer
];
}
