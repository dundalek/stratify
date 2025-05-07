{ pkgs }:
let
  version = "0.3.1";
in
pkgs.stdenv.mkDerivation {
  pname = "scip-clang";
  inherit version;

  src = pkgs.fetchurl {
    url = "https://github.com/sourcegraph/scip-clang/releases/download/v${version}/scip-clang-x86_64-${
      if pkgs.stdenv.isDarwin then "darwin" else "linux"
    }";
    sha256 = if pkgs.stdenv.isDarwin
              then "sha256-0000000000000000000000000000000000000000000=" # macOS
              else "sha256-F5s2uH2KcfI93Ad5o5FDDh8xLjkcRSYNtVKHOEkHDbA="; # Linux
  };

  dontUnpack = true;

  installPhase = ''
    mkdir -p $out/bin
    cp $src $out/bin/scip-clang
    chmod +x $out/bin/scip-clang
  '';

  meta = with pkgs.lib; {
    description = "A SCIP indexer for C/C++/Objective-C";
    homepage = "https://github.com/sourcegraph/scip-clang";
    platforms = platforms.unix;
  };
}
