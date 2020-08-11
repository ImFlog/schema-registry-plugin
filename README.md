[![CircleCI](https://circleci.com/gh/ImFlog/schema-registry-plugin/tree/master.svg?style=svg)](https://circleci.com/gh/ImFlog/schema-registry-plugin/tree/master)
![Github Actions](https://github.com/ImFlog/schema-registry-plugin/workflows/Master/badge.svg)

# Schema-registry-plugin
The aim of this plugin is to adapt the [Confluent schema registry maven plugin](https://docs.confluent.io/current/schema-registry/docs/maven-plugin.html) for Gradle builds.

## Installing
See [gradle plugins portal](https://plugins.gradle.org/plugin/com.github.imflog.kafka-schema-registry-gradle-plugin)
for instructions about how to add the plugin to your build configuration.

Also, do not forget to add the confluent repository in your buildscript:
```groovy
buildscript {
    repositories {
        maven {
            url "https://plugins.gradle.org/m2/"
        }
        maven {
            url "http://packages.confluent.io/maven/"
        }
  }
    dependencies {
        classpath "com.github.imflog:kafka-schema-registry-gradle-plugin:X.X.X"
    }
}
```
## Tasks
When you install the plugin, four tasks are added under `registry` group:
* downloadSchemasTask
* testSchemasTask
* registerSchemasTask
* configSubjectsTask

What these tasks do and how to configure them is described in the following sections.
### Download schemas
Like the name of the task imply, this task is responsible for retrieving schemas from a schema registry.

A DSL is available to configure the task:
```groovy
schemaRegistry {
    url = 'http://localhost:8081/'
    credentials {
        username = 'basicauthentication-username'
        password = 'basicauthentication-password'
    } //optional
    
    download {
        // extension of the output file depends on the the schema type
        subject('avroSubject', 'src/main/avro')
        subject('protoSubject', 'src/main/proto')
        subject('jsonSubject', 'src/main/json')
    }
}
```
You have to put the url where the script can reach the Schema Registry.

You need to specify the pairs (subjectName, outputDir) for all the schemas you want to download. 

### Test schemas compatibility
This task test compatibility between local schemas and schemas stored in the Schema Registry.

A DSL is available to specify what to test:
```groovy
import io.confluent.kafka.schemaregistry.client.rest.entities.SchemaReference

schemaRegistry {
    url = 'http://localhost:8081'
    credentials {
        username = 'basicauthentication-username'
        password = 'basicauthentication-password'
    } //optional
    compatibility {
        subject('avroWithDependencies', 'dependent/path.avsc', "AVRO", [
            new SchemaReference('avroSubject', 'avroSubjectType', 1)
        ])
        subject('protoWithDependencies', 'dependent/path.proto', "PROTOBUF", [
            new SchemaReference('protoSubject', 'protoSubjectType', 1)
        ])
        subject('jsonWithDependencies', 'dependent/path.json', "JSON", [
            new SchemaReference('jsonSubject', 'jsonSubjectType', 1)
        ])
    }
}
```
You have to put the url where the script can reach the Schema Registry.

You have to list all the (subject, avsc file path) pairs that you want to test. 

If you have dependencies with other schemas required before the compatibility check,
you can add a third parameter with the list of schemaReferences stored in the registry, they will be fetched dynamically.

### Register schemas
Once again the name speaks for itself.
This task register schemas from a local path to a Schema Registry.

A DSL is available to specify what to register:
```groovy
import io.confluent.kafka.schemaregistry.client.rest.entities.SchemaReference

schemaRegistry {
    url = 'http://localhost:8081'
    credentials {
        username = 'basicauthentication-username'
        password = 'basicauthentication-password'
    } //optional
    register {
        subject('avroWithDependencies', 'dependent/path.avsc', "AVRO", [
            new SchemaReference('avroSubject', 'avroSubjectType', 1)
        ])
        subject('protoWithDependencies', 'dependent/path.proto', "PROTOBUF", [
            new SchemaReference('protoSubject', 'protoSubjectType', 1)
        ])
        subject('jsonWithDependencies', 'dependent/path.json', "JSON", [
            new SchemaReference('jsonSubject', 'jsonSubjectType', 1)
        ])
    }
}
```
You have to put the url where the script can reach the Schema Registry.

You have to list all the (subject, avsc file path) pairs that you want to send.

you can add a third parameter with the list of schemaReferences stored in the registry, they will be fetched dynamically.

### Configure subjects

This task sets the schema compatibility level for registered subjects.

A DSL is available to specify which subjects to configure:
```groovy
schemaRegistry {
    url = 'http://localhost:8081'
    credentials {
        username = 'basicauthentication-username'
        password = 'basicauthentication-password'
    } //optional
    config {
        subject('mySubject', 'FULL_TRANSITIVE')
        subject('otherSubject', 'FORWARD')
    }
}
```

See the Confluent
[Schema Registry documentation](https://docs.confluent.io/current/schema-registry/avro.html#compatibility-types)
for more information on valid compatibility levels.

You have to put the URL where the script can reach the Schema Registry.

You have to list the (subject, compatibility-level) 

### Examples
See the [examples](examples) directory to see the plugin in action !

## Developing
In order to build the plugin locally, you can run the following commands:
```bash
./gradlew build # To compile and test the code
./gradlew publishToMavenLocal # To push the plugin to your mavenLocal
```

Once the plugin is pushed into your mavenLocal, you can use it by 
adding the `mavenLocal` to the buildscript repositories like so:
```groovy
buildscript {
    repositories {
        // The new repository to import, you may not want this in your final gradle configuration.
        mavenLocal()
        maven {
            url "http://packages.confluent.io/maven/"
        }
  }
    dependencies {
        classpath "com.github.imflog:kafka-schema-registry-gradle-plugin:X.X.X-SNAPSHOT"
    }
}
```

