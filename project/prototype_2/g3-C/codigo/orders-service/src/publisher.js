// Sends email events to the message broker (RabbitMQ). The Notifications Service reads them.
const amqp = require("amqplib");

const RABBITMQ_URL = process.env.RABBITMQ_URL || "amqp://localhost";
const QUEUE = "notifications";
let channel = null;

async function connect() {
  try {
    const connection = await amqp.connect(RABBITMQ_URL);
    channel = await connection.createChannel();
    await channel.assertQueue(QUEUE, { durable: true });
    connection.on("close", () => {
      channel = null;
      setTimeout(connect, 5000);
    });
    console.log("Connected to RabbitMQ");
  } catch (error) {
    console.log("RabbitMQ not available, trying again in 5 seconds");
    setTimeout(connect, 5000);
  }
}

// The order is already saved: if the broker is down we only lose the email, not the order
function publish(event) {
  if (!channel) {
    console.log("Event not sent (no broker):", event.type);
    return;
  }
  channel.sendToQueue(QUEUE, Buffer.from(JSON.stringify(event)), { persistent: true });
}

module.exports = { connect, publish };
