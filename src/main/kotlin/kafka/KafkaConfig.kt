package org.burgas.kafka

import io.confluent.kafka.serializers.KafkaAvroDeserializer
import io.confluent.kafka.serializers.KafkaAvroSerializer
import io.github.flaxoos.ktor.server.plugins.kafka.*
import io.github.flaxoos.ktor.server.plugins.kafka.components.fromRecord
import io.ktor.server.application.*
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.burgas.database.CarFullResponse
import org.burgas.database.IdentityFullResponse
import org.burgas.database.ParkingFullResponse

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

        val carTopic = TopicName.named("car-topic")
        topic(carTopic) {
            partitions = 1
            replicas = 1
            configs {
                messageTimestampType = MessageTimestampType.CreateTime
            }
        }

        val parkingTopic = TopicName.named("parking-topic")
        topic(parkingTopic) {
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
            consumerRecordHandler(carTopic) { record ->
                val carFullResponse = fromRecord<CarFullResponse>(record.value())
                println("${record.topic()} :: $carFullResponse")
            }
            consumerRecordHandler(parkingTopic) { record ->
                val parkingFullResponse = fromRecord<ParkingFullResponse>(record.value())
                println("${record.topic()} :: $parkingFullResponse")
            }
        }

        registerSchemas {
            IdentityFullResponse::class at identityTopic
            CarFullResponse::class at carTopic
            ParkingFullResponse::class at parkingTopic
        }
    }
}