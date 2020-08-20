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

## Avro / JSON / Protobuf
Those are full example of how to use the plugin. They all do the same thing but with the different types:
* register the schema `company` and the schema `user` (that use the type `Company`)
* set the compatibility to `FULL_TRANSITIVE`
* download the previously registered schema (in the downloaded folder)
* test the compatibility with the `company_v2` 
You can find the used schemas and the downloaded in [schemas/avro/company.avsc](schemas/avro/company.avsc) for instance.

## Override confluent version
The goal is to show how it is possible to override the confluent version.
The run tasks should work but you can run `./gradlew :override-confluent-version:buildEnvironment` to see the resolved confluent version.
