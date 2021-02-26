package examples

import org.apache.pulsar.client.api.PulsarClientException
import zio._
import zio.blocking._
import zio.clock._
import zio.console._
import zio.pulsar._
import zio.stream._
//import zio.pulsar.SubscriptionProperties.TopicSubscriptionProperties
//import zio.logging._

object StreamingExample extends App {

  def run(args: List[String]): URIO[ZEnv, ExitCode] =
    app.provideCustomLayer(layer).useNow.exitCode

  val pulsarClient = PulsarClient.live("localhost", 6650)

  // val logger =
  //   Logging.console(
  //     logLevel = LogLevel.Info,
  //     format = LogFormat.ColoredLogFormat()
  //   ) >>> Logging.withRootLoggerName("streaming-example")

  val layer = ((Console.live ++ Clock.live)/* >>> logger */) >+> pulsarClient

  val topic = "my-topic"

  val producer: ZManaged[PulsarClient/* with Logging*/, PulsarClientException, Unit] = 
    for {
      sink   <- Producer.make(topic).map(_.asSink)
      stream = Stream.fromIterable(0 to 100).map(i => s"Message $i".getBytes())
      _      <- stream.run(sink).toManaged_
    } yield ()

  val consumer: ZManaged[PulsarClient/* with Logging*/ with Blocking, Throwable, Unit] =
    for {
      //_ <- log.info("Connect to Pulsar").toManaged_
      client <- PulsarClient.make.toManaged_
      c   <- ConsumerBuilder(client)
               .withSubscription(Subscription("my-subscription", SubscriptionType.Exclusive))
               .withTopic(topic)
               .build
      _ <- c.receiveStream.take(10).foreach { a => 
            //log.info("Received: (id: " + a.getMessageId.toString + ") " + a.getData().map(_.toChar).mkString) *>
              c.acknowledge(a.getMessageId())
            }.toManaged_
      //_ <- log.info("Finished").toManaged_
    } yield ()

  val app =
    for {
      f <- consumer.fork
      _ <- producer
      _ <- f.join.toManaged_
    } yield ()
}
