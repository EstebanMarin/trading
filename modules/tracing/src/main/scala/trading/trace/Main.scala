package trading.trace

import trading.commands.*
import trading.core.AppTopic
import trading.core.http.Ember
import trading.core.snapshots.SnapshotReader
import trading.events.*
import trading.lib.*
import trading.state.{ DedupState, TradeState }
import trading.trace.log.LogEntryPoint

import cats.effect.*
import dev.profunktor.pulsar.schema.circe.bytes.*
import dev.profunktor.pulsar.{ Pulsar, Subscription }
import dev.profunktor.redis4cats.effect.Log.Stdout.*
import fs2.Stream
import natchez.EntryPoint
import natchez.honeycomb.Honeycomb

object Main extends IOApp.Simple:
  def run: IO[Unit] =
    Stream
      .resource(resources)
      .flatMap { (server, tradingEvents, tradingCommands, authorEvents, forecastEvents, forecastCommands, tracer) =>
        val trading =
          tradingEvents
            .merge[IO, Engine.TradeIn](tradingCommands)
            .evalMapAccumulate(
              List.empty[TradeEvent] -> List.empty[TradeCommand]
            )(Engine.tradingFsm[IO](tracer).run)

        val forecasting =
          authorEvents
            .merge[IO, Engine.ForecastIn](forecastEvents.merge(forecastCommands))
            .evalMapAccumulate(
              (List.empty[AuthorEvent], List.empty[ForecastEvent], List.empty[ForecastCommand])
            )(Engine.forecastFsm[IO](tracer).run)

        Stream(
          Stream.eval(server.useForever),
          trading,
          forecasting
        ).parJoin(3)
      }
      .compile
      .drain

  def mkEntryPoint(
      name: String,
      apiKey: Option[Config.HoneycombApiKey]
  ): Resource[IO, EntryPoint[IO]] =
    apiKey match
      case Some(key) =>
        Honeycomb
          .entryPoint[IO](name) { ep =>
            IO {
              ep.setWriteKey(key.value)
                .setDataset("demo")
                .build
            }
          }
          .evalTap(_ => Logger[IO].info("Setting up Honeycomb as the tracer"))
      case None =>
        Resource.eval {
          Logger[IO].warn("Honeycomb API Key not found, using log tracer").as(LogEntryPoint[IO](name))
        }

  val sub =
    Subscription.Builder
      .withName("tracing")
      .withType(Subscription.Type.Shared)
      .build

  def resources =
    for
      config <- Resource.eval(Config.load[IO])
      pulsar <- Pulsar.make[IO](config.pulsar.url)
      _      <- Resource.eval(IO.println(">>> Initializing tracing service <<<"))
      ep     <- mkEntryPoint("trading-app", config.honeycombApiKey)
      tracer           = Tracer.make[IO](ep)
      tradingEvtTopic  = AppTopic.TradingEvents.make(config.pulsar)
      tradingCmdTopic  = AppTopic.TradingCommands.make(config.pulsar)
      forecastCmdTopic = AppTopic.ForecastCommands.make(config.pulsar)
      forecastEvtTopic = AppTopic.ForecastEvents.make(config.pulsar)
      tradingEvents    <- Consumer.pulsar[IO, TradeEvent](pulsar, tradingEvtTopic, sub).map(_.receive)
      tradingCommands  <- Consumer.pulsar[IO, TradeCommand](pulsar, tradingCmdTopic, sub).map(_.receive)
      authorEvents     <- Consumer.pulsar[IO, AuthorEvent](pulsar, forecastEvtTopic, sub).map(_.receive)
      forecastEvents   <- Consumer.pulsar[IO, ForecastEvent](pulsar, forecastEvtTopic, sub).map(_.receive)
      forecastCommands <- Consumer.pulsar[IO, ForecastCommand](pulsar, forecastCmdTopic, sub).map(_.receive)
      server = Ember.default[IO](config.httpPort)
    yield (server, tradingEvents, tradingCommands, authorEvents, forecastEvents, forecastCommands, tracer)
