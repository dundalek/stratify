{ lib
, stdenv
, fetchurl
}:

stdenv.mkDerivation rec {
  pname = "scip-clang";
  version = "0.3.1";

  src = fetchurl {
    url = "https://github.com/sourcegraph/scip-clang/releases/download/v${version}/scip-clang-x86_64-${
      if stdenv.isDarwin then "darwin" else "linux"
    }";
    sha256 = if stdenv.isDarwin
              then "sha256-0000000000000000000000000000000000000000000=" # macOS
              else "sha256-F5s2uH2KcfI93Ad5o5FDDh8xLjkcRSYNtVKHOEkHDbA="; # Linux
  };

  dontUnpack = true;

  installPhase = ''
    mkdir -p $out/bin
    cp $src $out/bin/scip-clang
    chmod +x $out/bin/scip-clang
  '';

  meta = with lib; {
    description = "A SCIP indexer for C/C++/Objective-C";
    homepage = "https://github.com/sourcegraph/scip-clang";
    platforms = platforms.unix;
  };
}
