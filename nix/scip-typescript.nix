{ lib
, stdenv
, fetchFromGitHub
, fetchYarnDeps
, fixup_yarn_lock
, nodejs
, yarn
, makeWrapper
}:

stdenv.mkDerivation rec {
  pname = "scip-typescript";
  version = "0.4.0";

  src = fetchFromGitHub {
    owner = "sourcegraph";
    repo = "scip-typescript";
    rev = "v${version}";
    hash = "sha256-VKzNiazF+TtlvmXNCIEJbhsuNfnL2c8FslnVFvydXs8=";
  };

  offlineCache = fetchYarnDeps {
    yarnLock = "${src}/yarn.lock";
    hash = "sha256-vTIm/oo3+OP2ZUnafar4Pm6trlXLn/W+u1w/347hj/4=";
  };

  nativeBuildInputs = [ nodejs yarn fixup_yarn_lock makeWrapper ];

  configurePhase = ''
    runHook preConfigure

    export HOME=$TMPDIR

    # Fixup yarn.lock to work with offline cache
    ${fixup_yarn_lock}/bin/fixup_yarn_lock yarn.lock

    # Configure yarn offline mirror
    yarn config --offline set yarn-offline-mirror $offlineCache

    # Install dependencies from offline cache
    yarn install --offline --frozen-lockfile --ignore-scripts --no-progress --non-interactive
    patchShebangs node_modules

    runHook postConfigure
  '';

  buildPhase = ''
    runHook preBuild
    yarn --offline build
    runHook postBuild
  '';

  installPhase = ''
    runHook preInstall

    mkdir -p $out/lib/scip-typescript $out/bin

    cp -r dist node_modules package.json $out/lib/scip-typescript/

    makeWrapper ${nodejs}/bin/node $out/bin/scip-typescript \
      --add-flags "$out/lib/scip-typescript/dist/src/main.js"

    runHook postInstall
  '';

  meta = with lib; {
    description = "SCIP indexer for TypeScript and JavaScript";
    homepage = "https://github.com/sourcegraph/scip-typescript";
    license = licenses.asl20;
    maintainers = [ ];
    mainProgram = "scip-typescript";
    platforms = platforms.all;
  };
}
