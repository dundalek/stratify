#!/usr/bin/env bash

shopt -s globstar

javac -d target src/**/*.java
java -cp target com.acme.Main
