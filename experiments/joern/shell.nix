{ pkgs ? import <nixpkgs> {} }:

let
  joern = import ./default.nix { inherit pkgs; };
  rootShell = import ../../shell.nix;
in
pkgs.mkShell {
  buildInputs = rootShell.buildInputs ++ [
    joern
  ];
  
  shellHook = ''
    echo "Joern development environment loaded"
    echo "Available commands: joern-parse, joern-export"
  '';
}