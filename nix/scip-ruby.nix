{ lib
, stdenv
, fetchurl
}:

stdenv.mkDerivation rec {
  pname = "scip-ruby";
  version = "0.4.6";

  src = fetchurl {
    url = "https://github.com/sourcegraph/scip-ruby/releases/download/scip-ruby-v${version}/scip-ruby-${
      if stdenv.isDarwin then "arm64-darwin" else "x86_64-linux"
    }";
    sha256 = if stdenv.isDarwin
             then "sha256-9ERGG/tNAWe+7uilrpqYZpgTr3eC40XxEqfD7jM23/s=" # macOS ARM64
             else "sha256-rEuJ5ukzTWL6mR9LFmMWVB0ncEtzuTEdp91ZYtKGMpE="; # Linux x86_64
  };

  dontUnpack = true;

  installPhase = ''
    mkdir -p $out/bin
    cp $src $out/bin/scip-ruby
    chmod +x $out/bin/scip-ruby
  '';

  meta = with lib; {
    description = "SCIP indexer for Ruby";
    homepage = "https://github.com/sourcegraph/scip-ruby";
    license = licenses.asl20;
    maintainers = [ ];
    mainProgram = "scip-ruby";
    platforms = [ "x86_64-linux" "aarch64-darwin" ];
  };
}
