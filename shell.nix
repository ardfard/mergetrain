{ sources ? import ./nix/sources.nix }:
let 
  pkgs = import sources.nixpkgs {};
in 
with pkgs;
mkShell {
  buildInputs = [
    redis
  ];
}
