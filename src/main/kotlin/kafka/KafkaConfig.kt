package org.burgas.kafka

import io.confluent.kafka.serializers.KafkaAvroDeserializer
import io.confluent.kafka.serializers.KafkaAvroSerializer
import io.github.flaxoos.ktor.server.plugins.kafka.*
import io.github.flaxoos.ktor.server.plugins.kafka.components.fromRecord
import io.ktor.server.application.*
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.burgas.database.IdentityFullResponse

fun Application.configureKafka() {

    install(Kafka) {

        schemaRegistryUrl = "http://localhost:8081"

        val identityTopic = TopicName.named("identity-topic")
        topic(identityTopic) {
            partitions = 1
            replicas = 1
            configs {
                messageTimestampType = MessageTimestampType.CreateTime
            }
        }

        common {
            bootstrapServers = listOf("localhost:9092")
            retries = 1
            clientId = "my-client-id"
        }

        admin { }

        producer {
            clientId = "my-client-id"
            keySerializerClass = StringSerializer::class.java
            valueSerializerClass = KafkaAvroSerializer::class.java
        }

        consumer {
            groupId = "my-group-id"
            clientId = "my-client-id-override"
            keyDeserializerClass = StringDeserializer::class.java
            valueDeserializerClass = KafkaAvroDeserializer::class.java
        }

        consumerConfig {
            consumerRecordHandler(identityTopic) { record ->
                val identityFullResponse = fromRecord<IdentityFullResponse>(record.value())
                println("${record.topic()} :: $identityFullResponse")
            }
        }

        registerSchemas {
            IdentityFullResponse::class at identityTopic
        }
    }
}