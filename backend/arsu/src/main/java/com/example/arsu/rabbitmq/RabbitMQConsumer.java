package com.example.arsu.rabbitmq;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

@Service
public class RabbitMQConsumer {

    @RabbitListener(queues = "example-queue")
    public void consume(String message) {
        System.out.println("Consumed message: " + message);
    }
}
