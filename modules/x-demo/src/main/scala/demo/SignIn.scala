package demo

import java.time.Instant
import java.util.UUID

type EventId   = UUID
type UserId    = UUID
type Timestamp = Instant

type DeviceId   = UUID
type DeviceName = String

final case class UserDevice(
    eventId: DeviceId,
    name: DeviceName
)

sealed trait UserEvent:
  def eventId: EventId
  def userId: UserId
  def timestamp: Timestamp

object UserEvent:
  final case class UserSignedIn(
      eventId: EventId,
      userId: UserId,
      device: UserDevice,
      timestamp: Timestamp
  ) extends UserEvent
