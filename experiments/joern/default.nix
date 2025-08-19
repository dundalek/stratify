{ pkgs ? import <nixpkgs> {} }:

let
  version = "4.0.407";
  src = pkgs.fetchurl {
    url = "https://github.com/joernio/joern/releases/download/v${version}/joern-cli.zip";
    sha256 = "0w5sckr5jhhc30d01d06bpjy3alq2b5lch5b5fhzplp8miyw7yz1";
  };
in
pkgs.stdenv.mkDerivation {
  pname = "joern-cli";
  inherit version;
  inherit src;

  nativeBuildInputs = with pkgs; [
    unzip
    makeWrapper
  ];

  buildInputs = with pkgs; [
    jdk17
  ];

  unpackPhase = ''
    unzip $src
  '';

  installPhase = ''
    mkdir -p $out/bin $out/share/joern

    # Copy the joern installation, preserving the .installation_root marker
    cp -r joern-cli/* $out/share/joern/
    cp joern-cli/.installation_root $out/share/joern/

    # Create wrapper scripts for joern-parse
    makeWrapper $out/share/joern/joern-parse $out/bin/joern-parse \
      --set JAVA_HOME ${pkgs.jdk17} \
      --prefix PATH : ${pkgs.jdk17}/bin

    # Create wrapper scripts for joern-export  
    makeWrapper $out/share/joern/joern-export $out/bin/joern-export \
      --set JAVA_HOME ${pkgs.jdk17} \
      --prefix PATH : ${pkgs.jdk17}/bin
  '';

  meta = with pkgs.lib; {
    description = "Joern CLI tools for code analysis";
    homepage = "https://github.com/joernio/joern";
    license = licenses.asl20;
    platforms = platforms.all;
  };
}