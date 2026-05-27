package com.anime.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {
    
    public static final String MESSAGE_EXCHANGE = "message.exchange";
    public static final String MESSAGE_QUEUE = "message.queue";
    public static final String MESSAGE_ROUTING_KEY = "message.routing.key";
    
    @Bean
    public DirectExchange messageExchange() {
        return new DirectExchange(MESSAGE_EXCHANGE);
    }
    
    @Bean
    public Queue messageQueue() {
        return QueueBuilder.durable(MESSAGE_QUEUE).build();
    }
    
    @Bean
    public Binding messageBinding(Queue messageQueue, DirectExchange messageExchange) {
        return BindingBuilder.bind(messageQueue).to(messageExchange).with(MESSAGE_ROUTING_KEY);
    }
    
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
    
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(jsonMessageConverter());
        return rabbitTemplate;
    }
}