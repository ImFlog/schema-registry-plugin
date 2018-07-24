[![CircleCI](https://circleci.com/gh/ImFlog/schema-registry-plugin/tree/master.svg?style=svg)](https://circleci.com/gh/ImFlog/schema-registry-plugin/tree/master)

# Schema-registry-plugin
The aim of this plugin is to adapt the [Confluent schema registry maven plugin](https://docs.confluent.io/current/schema-registry/docs/maven-plugin.html) for Gradle builds.

See [gradle plugins portal](https://plugins.gradle.org/plugin/com.github.imflog.kafka-schema-registry-gradle-plugin)
for instructions about how to add the plugin to your build configuration.

When you do so, three tasks are added under registry group:
* downloadSchemasTask
* testSchemasCompatibilityTask
* registerSchemasTask
What these tasks do and how to configure them is described in the following sections.
## Download schemas
Like the name of the task imply, this task is responsible of retrieving schemas from a schema registry.

A DSL is available to configure the task:
```groovy
schemaRegistry {
    url = 'http://localhost:8081/'
    download {
        output = 'src/main/avro'
        subjects = ['topic1-key', 'topic1-value']
    }
}
```
You have to put the url where the script can reach the Schema Registry.
You need to specify where you want to output the downloaded schemas.
And the subjects are the name of the various subject to retrieve the schemas for. 

## Test schemas compatibility
TODO
## Register schemas
Once again the name speaks for itself.
This task register schemas from a local path to a Schema Registry.

A DSL is available to add specify what to send:
```groovy
schemaRegistry {
    url = 'http://localhost:8081'
    register {
        subject('mySubject', 'file/path')
        subject('otherSubject', 'other/path')
    }
}
```
You have to put the url where the script can reach the Schema Registry.
You have to list all the (subject, avsc file path) pairs that you want to send. 