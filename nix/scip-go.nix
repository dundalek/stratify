{ lib
, stdenv
, fetchurl
}:

stdenv.mkDerivation rec {
  pname = "scip-go";
  version = "0.1.26";

  src = fetchurl {
    url = "https://github.com/sourcegraph/scip-go/releases/download/v${version}/scip-go_${version}_${
      if stdenv.isDarwin then "darwin" else "linux"
    }_${
      if stdenv.isAarch64 then "arm64" else "amd64"
    }.tar.gz";
    sha256 = if stdenv.isDarwin
              then if stdenv.isAarch64
                   then "sha256-G4el4LKvTkG8HMSSIOfTqEqDFGiuaUSpV059TBJwkJw=" # macOS ARM64
                   else "sha256-do2ASNU38eKiZzWzf6BIEpbGoQEDkrJ1DIi3Nxa1Kc8=" # macOS AMD64
              else if stdenv.isAarch64
                   then "sha256-vI5au5WVIZEtYBgd6JIuUVimCaLp2H5u0reAHBHA76s=" # Linux ARM64
                   else "sha256-ZiV7bbdOE8LnVsmruo59NOYuuR0Wzb4IegsMFwyJw30="; # Linux AMD64
  };

  sourceRoot = ".";

  installPhase = ''
    mkdir -p $out/bin
    cp scip-go $out/bin/scip-go
    chmod +x $out/bin/scip-go
  '';

  meta = with lib; {
    description = "A SCIP indexer for Go";
    homepage = "https://github.com/sourcegraph/scip-go";
    license = licenses.asl20;
    maintainers = [ ];
    mainProgram = "scip-go";
    platforms = platforms.unix;
  };
}
