[![Download](https://img.shields.io/gradle-plugin-portal/v/com.github.imflog.kafka-schema-registry-gradle-plugin)](https://plugins.gradle.org/plugin/com.github.imflog.kafka-schema-registry-gradle-plugin)
![Github Actions](https://github.com/ImFlog/schema-registry-plugin/workflows/Master/badge.svg)

# Schema-registry-plugin

The aim of this plugin is to adapt
the [Confluent schema registry maven plugin](https://docs.confluent.io/current/schema-registry/docs/maven-plugin.html)
for Gradle builds.

## Usage

This plugin requires Gradle 8.0 or later (kotlin minimum version required by downstream libraries).

The plugin relies on a confluent library that is not available on common repositories, you will have to add it to
your `buildscript`.

<div class="tabbed-code-block">
  <details open>
    <summary>Groovy</summary>

```groovy
buildscript {
    repositories {
        gradlePluginPortal()
        maven {
            url = "https://packages.confluent.io/maven/"
        }
    }
}
plugins {
    id "com.github.imflog.kafka-schema-registry-gradle-plugin" version "X.X.X"
}
```

  </details>
  <details>
    <summary>Kotlin</summary>

```kotlin
buildscript {
    repositories {
        gradlePluginPortal()
        maven("https://packages.confluent.io/maven/")
    }
}
plugins {
    id("com.github.imflog.kafka-schema-registry-gradle-plugin") version "X.X.X"
}
```

  </details>
</div>

Where "X.X.X" is the current version,
see [gradle plugin portal](https://plugins.gradle.org/plugin/com.github.imflog.kafka-schema-registry-gradle-plugin) for
details.

## Tasks

When you install the plugin, four tasks are added under `registry` group:

* downloadSchemasTask
* testSchemasTask
* registerSchemasTask
* configSubjectsTask

What these tasks do and how to configure them is described in the following sections.

### Global configuration

```groovy
schemaRegistry {
    url = 'http://registry-url:8081/'
    quiet = true
    outputDirectory = "/home/kafka/results"
    pretty = true
    clientConfig = [
        "basic.auth.credentials.source": "USER_INFO",
        "basic.auth.user.info": "user:password",
        // ...
    ]
}
```

* `url` is where the script can reach the Schema Registry.
* `quiet` is whether you want to disable "INFO" level logs.
  This can be useful if you test the compatibility of a lot of schema.
  Could be removed if https://github.com/gradle/gradle/issues/1010 is fixed.
* `outputDirectory` is the directory where action result will be stored as files (only register for now).
  This is an optional parameter.
* `pretty` is whether the downloaded Avro or json schemas should be formatted ("pretty-printed") or minified.
  This is an optional parameter.
* `clientConfig` is a map of configuration properties for the Schema Registry client.
  This is an optional parameter. All values from the [Confluent Schema Registry client configuration](https://docs.confluent.io/platform/current/schema-registry/sr-client-configs.html)
  are supported.

### Download schemas

Like the name of the task imply, this task is responsible for retrieving schemas from a schema registry.

A DSL is available to configure the task:

```groovy

// Optional
import com.github.imflog.schema.registry.tasks.download.MetadataExtension

schemaRegistry {
    url = 'http://registry-url:8081/'
    download {
        // Optional
        metadata = new MetadataExtension(true, "path/to/metadata/")

        // extension of the output file depends on the the schema type
        subject('avroSubject', '/absolutPath/src/main/avro')
        subject('protoSubject', 'src/main/proto')
        subject('jsonSubject', 'src/main/json')

        // You can use a regex to download multiple schemas at once
        subjectPattern('avro.*', 'src/main/avro')
    }
}
```

Here is the list of all the signatures for the `subject` extension:

* `subject(inputSubject: String, outputPath: String)`
* `subject(inputSubject: String, outputPath: String, downloadReferences: Boolean)`
* `subject(inputSubject: String, outputPath: String, outputFileName: String)`
* `subject(inputSubject: String, outputPath: String, outputFileName: String, downloadReferences: Boolean)`
* `subject(inputSubject: String, outputPath: String, version: Int)`
* `subject(inputSubject: String, outputPath: String, version: Int, downloadReferences: Boolean)`
* `subject(inputSubject: String, outputPath: String, version: Int, outputFileName: String)`
* `subject(inputSubject: String, outputPath: String, version: Int, outputFileName: String, downloadReferences: Boolean)`
* `subjectPattern(inputPattern: String, outputPath: String)`
* `subjectPattern(inputPattern: String, outputPath: String, downloadReferences: Boolean)`

You can configure the metadata extension in order to download the schemas metadata in json files.
It will be saved in files named like the schema file but suffixed by `-metadata.json` in the outputPath you specify
and defaults to the same output directory as your schemas.
The metadata extension will also download optional `metadata` and `rulesets` if your schema-registry supports this.  

NB:

* If not provided, the outputFileName is equal to the inputSubject.
* It's not possible to specify the outputFileName for subject pattern as it would override the
  file for each downloaded schema.

### Test schemas compatibility

This task test compatibility between local schemas and schemas stored in the Schema Registry.

A DSL is available to specify what to test:

```groovy
schemaRegistry {
    url = 'http://registry-url:8081'
    compatibility {
        subject('avroWithLocalReferences', '/absolutPath/dependent/path.avsc', "AVRO")
                .addLocalReference("localAvroSubject", "/a/local/path.avsc")
        subject('avroWithRemoteReferences', '/absolutPath/dependent/path.avsc', "AVRO")
                .addReference('avroSubject', 'avroSubjectType', 1)
                .addReference('avroSubjectLatestVersion', 'avroSubjectLatestVersionType')
                .addReference('avroSubjectLatestVersionExplicit', 'avroSubjectLatestVersionExplicitType', -1)
        subject('protoWithReferences', 'dependent/path.proto', "PROTOBUF").addReference('protoSubject', 'protoSubjectType', 1)
        subject('jsonWithReferences', 'dependent/path.json', "JSON").addReference('jsonSubject', 'jsonSubjectType', 1)
    }
}
```

You have to list all the (subject, avsc file path) pairs that you want to test.

If you have references with other schemas stored in the registry that are required before the compatibility check,
you can call the `addReference("name", "subject", version)`,
this will add a reference to use from the registry.
A convenience method, `addReference("name", "subject")`,
uses the latest version of the schema in the registry.
You can also specify `-1` explicitly to use the latest version.
The addReference calls can be chained.

If you have local references to add before calling the compatibility in the registry,
you can call the `addLocalReference("name", "/a/path")`,
this will add a reference from a local file and inline it in the schema registry call.
The addLocalReference calls can be chained.

Notes:

* If you want to reuse Subjects with the register task you can define a `Subject` object like so:
  ```groovy
  import com.github.imflog.schema.registry.Subject
  Subject mySubject = Subject("avroSubject", "/path.avsc", "AVRO")
  
  schemaRegistry {
    url = 'http://registry-url:8081'
    register {
      subject(mySubject)
    }
    compatibility {
      subject(mySubject)
    }
  }
  ```

#### Avro

Mixing local and remote references is perfectly fine for Avro without specific configurations.

#### Json

Mixing local and remote references is perfectly fine for JSON.

If you need to add reference to local schema to a JSON schema, make sure that the local reference contains a `$id`
attribute.
This id is the value that need to be put on the `$ref` part.
For more concrete example, take a look at the [json example](examples/json/build.gradle).

#### Protobuf

> :warning: Local references support for Protobuf is in Beta.

Mixing local and remote references is perfectly fine for Protobuf.

The plugin will resolve Protobuf local references recursively, unpacking nested imports. 

Protobuf will always resolve imports relative to the supplied import roots, which in our case is the project's root 
directory, meaning both names and paths of each local reference would normally match. At the moment, it's 
**not possible** to resolve a local reference against a file outside the root directory. 

It has only been tested with `proto3`, though it should work for simple scenarios in `proto2` as well.

Things that are not yet supported:
- Extensions
- RPC definitions

### Register schemas

Once again the name speaks for itself.
This task register schemas from a local path to a Schema Registry.

A DSL is available to specify what to register:

```groovy
schemaRegistry {
    url = 'http://registry-url:8081'
    register {
        subject('avroWithLocalReferences', '/absolutPath/dependent/path.avsc', "AVRO")
                .addLocalReference("localAvroSubject", "/a/local/path.avsc")
        subject('avroWithRemoteReferences', '/absolutPath/dependent/path.avsc', "AVRO")
                .addReference('avroSubject', 'avroSubjectType', 1)
                .addReference('avroSubjectLatestVersion', 'avroSubjectLatestVersionType')
                .addReference('avroSubjectLatestVersionExplicit', 'avroSubjectLatestVersionExplicitType', -1)
        subject('protoWithReferences', 'dependent/path.proto', "PROTOBUF").addReference('protoSubject', 'protoSubjectType', 1)
        subject('jsonWithReferences', 'dependent/path.json', "JSON").addReference('jsonSubject', 'jsonSubjectType', 1)
                .setMetadata('/absolutPath/dependent/metadata.json').setRuleSet('/absolutPath/dependent/ruleset.json')
                .setNormalized(true)
    }
}
```

If you have references to other schemas required before the register,
you can call the `addReference("name", "subject", version)`,
this will add a reference to use from the registry.
A convenience method, `addReference("name", "subject")`,
uses the latest version of the schema in the registry.
You can also specify `-1` explicitly to use the latest version.
The addReference calls can be chained.

If you have local references to add before calling the register,
you can call the `addLocalReference("name", "/a/path")`,
this will add a reference from a local file and inline it in the schema registry call.
The addLocalReference calls can be chained.

You can also provide `setNormalized(true)` to normalize the schema. Here is a link for more information: [Schema Normalization](https://docs.confluent.io/platform/current/schema-registry/fundamentals/serdes-develop/index.html#schema-normalization)

For some schema registries like the one offered by `confluent-cloud`, you can add `metadata` and `rulesets` to a subject.
Here are some links for more information: 
* [Schema Registry Rules](https://docs.confluent.io/platform/7.5/schema-registry/fundamentals/data-contracts.html#rules)
* [Schema Registry Metadata](https://docs.confluent.io/platform/7.5/schema-registry/fundamentals/data-contracts.html#metadata-properties)

Notes:

* A registered.csv file will be created with the following format `subject, path, id`
  if you need information about the registered id.
* If you want to reuse Subjects with the compatibility task you can define a `Subject` object like so:
  ```groovy
  import com.github.imflog.schema.registry.Subject
  Subject mySubject = Subject("avroSubject", "/path.avsc", "AVRO")
  
  schemaRegistry {
    url = 'http://registry-url:8081'
    register {
      subject(mySubject)
    }
    compatibility {
      subject(mySubject)
    }
  }
  ```

#### Avro

Mixing local and remote references is perfectly fine for Avro without specific configurations.

#### Json

Mixing local and remote references is perfectly fine for JSON.

If you need to add reference to local schema to a JSON schema, make sure that the local reference contains a `$id`
attribute.
This id is the value that need to be put on the `$ref` part.
For more concrete example, take a look at the [json example](examples/json/build.gradle).

#### Protobuf

:warning: Local references is not yet supported for PROTOBUF.

### Configure subjects

This task sets the schema compatibility level for registered subjects.

A DSL is available to specify which subjects to configure:

```groovy
schemaRegistry {
    url = 'http://registry-url:8081'
    config {
        subject('mySubject', 'FULL_TRANSITIVE')
        subject('otherSubject', 'FORWARD')
    }
}
```

See the Confluent
[Schema Registry documentation](https://docs.confluent.io/current/schema-registry/avro.html#compatibility-types)
for more information on valid compatibility levels.

You have to list the (subject, compatibility-level)

## Security

According to how your schema registry instance security configuration,
you can configure the plugin to access it securely.

### Basic authentication

Basic authentication is handled like any other client configuration. But here is an example of how to configure it.

```groovy
schemaRegistry {
    url = 'http://registry-url:8081'
    clientConfig = [
        "basic.auth.credentials.source": "USER_INFO",
        "basic.auth.user.info": "user:password"
    ]
}
```

### Encryption (SSL)

If you want to encrypt the traffic in transit (using SSL), use the following client configuration:

```groovy
schemaRegistry {
    url = 'https://registry-url:8081'
    clientConfig = [
      "schema.registry.ssl.truststore.location": "/path/to/registry.truststore.jks",
      "schema.registry.ssl.truststore.password": "truststorePassword",
      "schema.registry.ssl.keystore.location"  : "/path/to/registry.keystore.jks",
      "schema.registry.ssl.keystore.password"  : "keystorePassword"
    ]
}
```

Valid key values are listed here: [org.apache.kafka.common.config.SslConfigs](https://github.com/confluentinc/kafka/blob/master/clients/src/main/java/org/apache/kafka/common/config/SslConfigs.java)
:warning: Make sure to prefix the key with `schema.registry.` else the SSL configuration will not be applied (implementation in the schema registry client).

### Examples

Detailed examples can be found in the [examples' directory](examples).

## Version compatibility

When using the plugin, a default version of the confluent Schema registry is use.
The 5.5.X version of the schema-registry introduced changes that made the older version of the plugin obsolete.

It was easier to introduce all the changes in one shot instead of supporting both version. Here is what it implies for
users:

* plugin versions above 1.X.X support the confluent version > 5.5.X (Avro / Json / Protobuf)
* plugin versions should support anything below 5.4.X

## Developing

### Running tests

In order to customize the Kafka version to run in integration tests,
you can specify the ENV VAR KAFKA_VERSION with the version that you want to test upon.
The library is tested with the following versions:

* 6.2.6
* 7.2.2
* 7.3.1 (by default if no env_var is passed)

PS: If you are running an ARM computer (like apple M1),
you can add the `.arm64` suffix to the version to run ARM container and speed up tests.

```bash
KAFKA_VERSION=7.2.0.arm64 ./gradlew integrationTest
````

### Publishing locally

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
            url "https://packages.confluent.io/maven/"
        }
        maven {
          url = "https://jitpack.io"
        }
    }
    dependencies {
        classpath "com.github.imflog:kafka-schema-registry-gradle-plugin:X.X.X-SNAPSHOT"
    }
}

apply plugin: "com.github.imflog.kafka-schema-registry-gradle-plugin"
```

## Thanks to all the sponsors :pray:

<a href="https://github.com/marktros"><img style="border-radius: 50%;" src="https://github.com/marktros.png" width="60px" alt="Mark Ethan Trostler" /></a>
