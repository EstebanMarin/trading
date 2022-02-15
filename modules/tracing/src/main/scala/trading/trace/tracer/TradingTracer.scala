package trading.trace
package tracer

import trading.commands.*
import trading.domain.Alert
import trading.events.*

import cats.effect.kernel.MonadCancelThrow
import cats.syntax.all.*
import io.circe.syntax.*
import natchez.{ EntryPoint, Kernel }

trait TradingTracer[F[_]]:
  def command(cmd: TradeCommand): F[Kernel]
  def event(kernel: Kernel, evt: TradeEvent): F[Kernel]
  def alert(kernel: Kernel, alt: Alert): F[Unit]

object TradingTracer:
  def make[F[_]: MonadCancelThrow](
      ep: EntryPoint[F]
  ): TradingTracer[F] = new:
    def command(cmd: TradeCommand): F[Kernel] =
      ep.root("trading-root").use { root =>
        root.span(s"trading-command-${cmd.cid.show}").use { sp =>
          sp.put("correlation_id" -> cmd.cid.show) *>
            sp.put("created_at"   -> cmd.createdAt.show) *>
            sp.put("payload" -> cmd.asJson.noSpaces) *>
            sp.kernel
        }
      }

    def event(kernel: Kernel, evt: TradeEvent): F[Kernel] =
      ep.continue(s"trading-command-${evt.cid.show}", kernel).use { sp1 =>
        sp1.span(s"trading-event-${evt.cid.show}").use { sp2 =>
          sp2.put("correlation_id" -> evt.cid.show) *>
            sp2.put("created_at"   -> evt.createdAt.show) *>
            sp2.put("payload" -> evt.asJson.noSpaces) *>
            sp2.kernel
        }
      }

    def alert(kernel: Kernel, alt: Alert): F[Unit] =
      ep.continue(s"trading-event-${alt.cid.show}", kernel).use { sp1 =>
        sp1.span(s"trading-alert-${alt.cid.show}").use { sp2 =>
          sp2.put("correlation_id" -> alt.cid.show) *>
            sp2.put("created_at"   -> alt.createdAt.show) *>
            sp2.put("payload" -> alt.asJson.noSpaces)
        }
      }
