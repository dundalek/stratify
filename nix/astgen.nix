{ lib
, stdenv
, fetchurl
}:

stdenv.mkDerivation rec {
  pname = "astgen";
  version = "3.37.0";

  src = fetchurl {
    url = "https://github.com/joernio/astgen/releases/download/v${version}/astgen-${
      if stdenv.isDarwin then "macos" else "linux"
    }${
      if stdenv.isAarch64 then "-arm" else ""
    }";
    sha256 = if stdenv.isDarwin
              then if stdenv.isAarch64
                   then "133v1z56fp90rwfk18gg6dv2lgh7hark2qzbaq0c1r4z7qgiq9zg" # macOS ARM64
                   else "1jynvhsjxa8g192wwz4r4psizliqcrm9qji0qgyfihpranr725fm" # macOS AMD64
              else if stdenv.isAarch64
                   then "029nbn9qhz53rh0n79xwvaj31lymc7jql3g3aj08vd0cb6v3vmzp" # Linux ARM64
                   else "1pklwvc8sp9xm5mg9y7v8z2rkn8hhijpjf6kbmsdl3qyqi3n7w8a"; # Linux AMD64
  };

  dontUnpack = true;

  installPhase = ''
    mkdir -p $out/bin
    cp $src $out/bin/astgen
    chmod +x $out/bin/astgen
  '';

  meta = with lib; {
    description = "JavaScript/TypeScript AST generator for Joern";
    homepage = "https://github.com/joernio/astgen";
    license = licenses.asl20;
    maintainers = [ ];
    mainProgram = "astgen";
    platforms = platforms.unix;
  };
}
