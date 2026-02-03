#!/usr/bin/env bash
cd "$(dirname "$0")"
export LUA_PATH="lua/?.lua;;"
nix-shell -p luajit --run "luajit -e \"print(require('example.foo').x())\""
