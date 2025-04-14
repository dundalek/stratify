with import <nixpkgs> { };
mkShell {
buildInputs = [
clojure
clojure-lsp
];
shellHook = ''
'';
}
