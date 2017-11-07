#!/bin/bash

rm cjd.war
#rm WEB-INF/lib/pcf-hea*.jar
#cd PCFHealthCheck
#mvn clean package
#cd ..

cp pcf-healthcheck-1.jar WEB-INF/lib/.
jar cvf cjd.war META-INF/ WEB-INF/ docs scripts

cf push -f manifest-healthcheck.yml
