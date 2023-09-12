#!/bin/bash
mvn clean install;
cp -v ./target/*.jar ~/mosaic-22.1/bundle/target/scenarios/RioVerde/application/;