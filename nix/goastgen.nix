{ lib
, stdenv
, fetchurl
, writeShellScriptBin
}:

let
  binary = stdenv.mkDerivation rec {
    pname = "goastgen-bin";
    version = "0.1.0";

    src = fetchurl {
      url = "https://github.com/joernio/goastgen/releases/download/v${version}/goastgen-${
        if stdenv.isDarwin then "macos" else "linux"
      }${
        if stdenv.isAarch64 then "-arm64" else ""
      }";
      sha256 = if stdenv.isDarwin
                then if stdenv.isAarch64
                     then "1n1zbla180nyml4wxfb4hqfrl61bhnqz4ln4hz1y26ki2vn9b533" # macOS ARM64
                     else "06xll7585qdpkzl83lcfn2qx09sm87nq146jxg07l6mmjqjysddf" # macOS AMD64
                else if stdenv.isAarch64
                     then "03j7yr90k10y3ps1wicznwxj334mg4v4zcb2ly2ksq98w49p2iz2" # Linux ARM64
                     else "0mhl9v57b3r1kx8423x8ywwkrdn0micc4qjjm0ghz5lffiky88qa"; # Linux AMD64
    };

    dontUnpack = true;

    installPhase = ''
      mkdir -p $out/bin
      cp $src $out/bin/goastgen-bin
      chmod +x $out/bin/goastgen-bin
    '';
  };
in
# Wrapper that fixes version output for Joern compatibility
# goastgen returns "v0.1.0" but Joern expects "0.1.0"
writeShellScriptBin "goastgen" ''
  if [ "$1" = "-version" ]; then
    ${binary}/bin/goastgen-bin -version | sed 's/^v//'
  else
    ${binary}/bin/goastgen-bin "$@"
  fi
'' // {
  meta = with lib; {
    description = "Go AST generator for Joern";
    homepage = "https://github.com/joernio/goastgen";
    license = licenses.asl20;
    maintainers = [ ];
    mainProgram = "goastgen";
    platforms = platforms.unix;
  };
}
