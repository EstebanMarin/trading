package trading.events

import trading.commands.ForecastCommand
import trading.domain.*

import io.circe.Codec

enum ForecastEvent derives Codec.AsObject:
  def id: EventId
  def cid: CorrelationId
  def forecastId: ForecastId
  def createdAt: Timestamp

  case Published(
      id: EventId,
      cid: CorrelationId,
      authorId: AuthorId,
      forecastId: ForecastId,
      symbol: Symbol,
      createdAt: Timestamp
  )

  case NotPublished(
      id: EventId,
      cid: CorrelationId,
      authorId: AuthorId,
      forecastId: ForecastId,
      reason: Reason,
      createdAt: Timestamp
  )

  case Voted(
      id: EventId,
      cid: CorrelationId,
      forecastId: ForecastId,
      result: VoteResult,
      createdAt: Timestamp
  )

  case NotVoted(
      id: EventId,
      cid: CorrelationId,
      forecastId: ForecastId,
      reason: Reason,
      createdAt: Timestamp
  )
