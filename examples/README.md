# Examples
This directory contains examples about how to use the schema-registry plugin.

If you want to run the samples locally you need docker and docker-compose.
Gradle will take care of starting the docker compose containers automatically.

A composite task `run` will (in this order):
1. call the register task
2. call the configure task
3. call the download task
4. call the compatibility task

You can use `./gradlew run` to run all the examples 
or prefix the task with `:project_name:` like this `./gradlew :basic:run` to run what you want.

Each subproject will define the extension configuration according to the use case.

## Basic
This is the simplest example:
* it register [schemas/foo.avsc](schemas/foo.avsc)
* set the compatibility to `FULL_TRANSITIVE`
* download the previously registered schema in [schemas/downloaded](schemas/downloaded)
* test the compatibility with the [schemas/foo_v2.avsc](schemas/foo_v2.avsc) 

## Override confluent version
The goal is to show how it is possible to override the confluent version.
The run task should work but you can run `./gradlew :override-confluent-version:buildEnvironment` to see the resolved confluent version.
