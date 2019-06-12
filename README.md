[![CircleCI](https://circleci.com/gh/ImFlog/schema-registry-plugin/tree/master.svg?style=svg)](https://circleci.com/gh/ImFlog/schema-registry-plugin/tree/master)

# Schema-registry-plugin
The aim of this plugin is to adapt the [Confluent schema registry maven plugin](https://docs.confluent.io/current/schema-registry/docs/maven-plugin.html) for Gradle builds.

See [gradle plugins portal](https://plugins.gradle.org/plugin/com.github.imflog.kafka-schema-registry-gradle-plugin)
for instructions about how to add the plugin to your build configuration.

When you do so, four tasks are added under registry group:
* downloadSchemasTask
* testSchemasTask
* registerSchemasTask
* configSubjectsTask

What these tasks do and how to configure them is described in the following sections.
## Download schemas
Like the name of the task imply, this task is responsible of retrieving schemas from a schema registry.

A DSL is available to configure the task:
```groovy
schemaRegistry {
    url = 'http://localhost:8081/'
    credentials {
        username = 'basicauthentication-username'
        password = 'basicauthentication-password'
    } //optional
    
    download {
        subject('topic1-key', 'src/main/avro')
        subject('topic1-value', 'src/main/avro/values')
    }
}
```
You have to put the url where the script can reach the Schema Registry.

You need to specify the pairs (subjectName, outputDir) for all the
schemas you want to download. 

## Test schemas compatibility
This task test compatibility between local schemas and schemas stored in the Schema Registry.

A DSL is available to specify what to test:
```groovy
schemaRegistry {
    url = 'http://localhost:8081'
    credentials {
        username = 'basicauthentication-username'
        password = 'basicauthentication-password'
    } //optional
    compatibility {
        subject('mySubject', 'file/path.avsc')
        subject('otherSubject', 'other/path.avsc')
        subject('subjectWithDependencies', 'dependent/path.avsc', ['firstDependency/path.avsc', 'secondDependency/path.avsc'])
    }
}
```
You have to put the url where the script can reach the Schema Registry.

You have to list all the (subject, avsc file path) pairs that you want to test. 

If you have dependencies with other schemas required before the compatibility check,
you can add a third parameter with the needed paths.

The order of the file paths in the list is significant.
Basically you need to follow the logical order of the types used.
If an `User` need an `Address` record which itself needs a `Street` record
you will need to define the dependencies like this:
```groovy
compatibility{
    subject('userSubject', 'path/user.avsc', ['path/address.avsc', 'path/street.avsc'])
}
```

## Register schemas
Once again the name speaks for itself.
This task register schemas from a local path to a Schema Registry.

A DSL is available to specify what to register:
```groovy
schemaRegistry {
    url = 'http://localhost:8081'
    credentials {
        username = 'basicauthentication-username'
        password = 'basicauthentication-password'
    } //optional
    register {
        subject('mySubject', 'file/path.avsc')
        subject('otherSubject', 'other/path.avsc')
        subject('subjectWithDependencies', 'dependent/path.avsc', ['firstDependency/path.avsc', 'secondDependency/path.avsc'])
    }
}
```
You have to put the url where the script can reach the Schema Registry.

You have to list all the (subject, avsc file path) pairs that you want to send.

If you have dependencies with other schemas required before the register phase,
you can add a third parameter with the needed paths.

The order of the file paths in the list is significant.
Basically you need to follow the logical order of the types used.
If an `User` need an `Address` record which itself needs a `Street` record
you will need to define the dependencies like this:
```groovy
register{
    subject('userSubject', 'path/user.avsc', ['path/address.avsc', 'path/street.avsc'])
}
```

## Configure subjects

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

You have to put the URL where the script can reach the Schema Registry.

You have to list the (subject, compatibility-level) 

See the Confluent [Schema Registry documentation](https://docs.confluent.io/current/schema-registry/avro.html#compatibility-types) for more information on valid compatibility levels.