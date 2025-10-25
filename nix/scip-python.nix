{ lib
, stdenv
, fetchurl
, nodejs
, makeWrapper
, python3
}:

stdenv.mkDerivation rec {
  pname = "scip-python";
  version = "0.6.6";

  src = fetchurl {
    url = "https://registry.npmjs.org/@sourcegraph/scip-python/-/scip-python-${version}.tgz";
    sha256 = "sha256-vo4KHsGAQjxg6fLCZywgj0mjWraQrA+PElnkIj29bkI=";
  };

  nativeBuildInputs = [ makeWrapper ];

  buildInputs = [ nodejs python3 ];

  installPhase = ''
    runHook preInstall

    mkdir -p $out/lib/scip-python $out/bin
    cp -r . $out/lib/scip-python/

    makeWrapper ${nodejs}/bin/node $out/bin/scip-python \
      --add-flags "$out/lib/scip-python/index.js" \
      --prefix PATH : ${lib.makeBinPath [ python3 ]}

    runHook postInstall
  '';

  meta = with lib; {
    description = "SCIP indexer for Python";
    homepage = "https://github.com/sourcegraph/scip-python";
    license = licenses.mit;
    maintainers = [ ];
    mainProgram = "scip-python";
    platforms = platforms.all;
  };
}
