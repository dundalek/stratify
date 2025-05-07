{
  description = "Development environment with scip-clang";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixpkgs-unstable";
    flake-utils.url = "github:numtide/flake-utils";
  };

  outputs = { self, nixpkgs, flake-utils }:
    flake-utils.lib.eachDefaultSystem (system:
      let
        pkgs = nixpkgs.legacyPackages.${system};

        scip-clang = import ./nix/scip-clang.nix { inherit pkgs; };
      in
      {
        devShells.default = pkgs.mkShell {
          buildInputs = [
            scip-clang
          ];

          shellHook = ''
          '';
        };
      }
    );
}
