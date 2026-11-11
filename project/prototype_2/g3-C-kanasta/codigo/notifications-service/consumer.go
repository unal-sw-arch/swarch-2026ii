package main

import (
	"encoding/json"
	"log"
	"time"

	amqp "github.com/rabbitmq/amqp091-go"
)

const queueName = "notifications"

// Message published by the Accounts Service and the Orders Service
type Event struct {
	Type string    `json:"type"` // ACCOUNT_CREATED, PASSWORD_CHANGED or ORDER_PLACED
	To   string    `json:"to"`
	Name string    `json:"name"`
	Data OrderData `json:"data"`
}

// Keeps listening to the queue. If the broker goes down, it tries again every 5 seconds.
func consumeEvents(url string) {
	for {
		if err := listen(url); err != nil {
			log.Println("Broker not available:", err)
		}
		time.Sleep(5 * time.Second)
	}
}

func listen(url string) error {
	conn, err := amqp.Dial(url)
	if err != nil {
		return err
	}
	defer conn.Close()

	channel, err := conn.Channel()
	if err != nil {
		return err
	}
	if _, err := channel.QueueDeclare(queueName, true, false, false, false, nil); err != nil {
		return err
	}
	messages, err := channel.Consume(queueName, "", false, false, false, false, nil)
	if err != nil {
		return err
	}

	log.Println("Waiting for events in queue", queueName)
	for message := range messages {
		var event Event
		if err := json.Unmarshal(message.Body, &event); err != nil {
			log.Println("Invalid event:", err)
		} else {
			process(event)
		}
		message.Ack(false)
	}
	return nil
}
