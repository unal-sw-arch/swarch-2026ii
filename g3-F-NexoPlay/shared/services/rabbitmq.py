import json
import os

import pika


class RabbitMQService:
    EXCHANGE = "video.processing"
    QUEUE = "video.transcode"
    FAILED_EXCHANGE = "video.processing.failed"
    FAILED_QUEUE = "video.transcode.failed"

    def __init__(self):
        self.connection = None
        self.channel = None

    def connect(self) -> None:
        parameters = pika.ConnectionParameters(
            host=os.getenv("RABBITMQ_HOST", "localhost"),
            port=int(os.getenv("RABBITMQ_PORT", "5672")),
            virtual_host=os.getenv("RABBITMQ_VHOST", "/"),
            credentials=pika.PlainCredentials(
                os.getenv("RABBITMQ_USER", "video_worker"),
                os.getenv("RABBITMQ_PASSWORD", "local_video_password"),
            ),
            heartbeat=60,
            blocked_connection_timeout=30,
            socket_timeout=10,
            stack_timeout=20,
        )
        self.connection = pika.BlockingConnection(parameters)
        self.channel = self.connection.channel()
        self.channel.exchange_declare(exchange=self.EXCHANGE, exchange_type="direct", durable=True)
        self.channel.exchange_declare(
            exchange=self.FAILED_EXCHANGE, exchange_type="direct", durable=True
        )
        self.channel.queue_declare(queue=self.FAILED_QUEUE, durable=True)
        self.channel.queue_bind(
            queue=self.FAILED_QUEUE, exchange=self.FAILED_EXCHANGE, routing_key="failed"
        )
        self.channel.queue_declare(
            queue=self.QUEUE, durable=True,
            arguments={"x-dead-letter-exchange": self.FAILED_EXCHANGE,
                       "x-dead-letter-routing-key": "failed"},
        )
        self.channel.queue_bind(queue=self.QUEUE, exchange=self.EXCHANGE, routing_key="transcode")
        self.channel.basic_qos(prefetch_count=1)
        self.channel.confirm_delivery()

    def publish(self, payload: dict, *, failed: bool = False) -> None:
        self.channel.basic_publish(
            exchange=self.FAILED_EXCHANGE if failed else self.EXCHANGE,
            routing_key="failed" if failed else "transcode",
            body=json.dumps(payload).encode("utf-8"),
            properties=pika.BasicProperties(
                content_type="application/json", delivery_mode=2,
                message_id=str(payload["job_id"]),
            ),
            mandatory=True,
        )

    def close(self) -> None:
        if self.connection is not None and self.connection.is_open:
            self.connection.close()
