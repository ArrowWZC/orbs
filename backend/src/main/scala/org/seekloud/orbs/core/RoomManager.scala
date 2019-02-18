package org.seekloud.orbs.core

import java.util.concurrent.atomic.AtomicLong

import akka.actor.typed.scaladsl.{ActorContext, Behaviors, StashBuffer, TimerScheduler}
import akka.actor.typed.{ActorRef, Behavior}
import org.seekloud.orbs.common.AppSettings
import org.seekloud.orbs.core.UserActor.JoinRoom
import org.slf4j.LoggerFactory

import scala.collection.mutable

/**
  * User: TangYaruo
  * Date: 2019/2/5
  * Time: 15:59
  */
object RoomManager {

  private val log = LoggerFactory.getLogger(this.getClass)

  trait Command

  private case class ChildDead[U](name: String, childRef: ActorRef[U]) extends Command

  case class LeftRoom(playerId: String, name: String) extends Command

  case class ReStartJoinRoom(playerId: String, name: String, userActor: ActorRef[UserActor.Command]) extends Command

  val behavior: Behavior[Command] = create()

  private def create(): Behavior[Command] =
    Behaviors.setup[Command] { ctx =>
      implicit val stashBuffer: StashBuffer[Command] = StashBuffer[Command](Int.MaxValue)
      Behaviors.withTimers[Command] {
        implicit timer =>
          val roomIdGenerator = new AtomicLong(1L)
          val roomInUse = mutable.HashMap((1l, List.empty[(String, String)]))
          idle(roomIdGenerator, roomInUse)
      }
    }

  private def idle(
    roomIdGenerator: AtomicLong,
    roomInUse: mutable.HashMap[Long, List[(String, String)]] // roomId => List[userId, userName]
  )(
    implicit stashBuffer: StashBuffer[Command],
    timer: TimerScheduler[Command]
  ): Behavior[Command] =
    Behaviors.receive[Command] { (ctx, msg) =>
      msg match {
        case msg: JoinRoom =>

          //为新用户分配房间
          roomInUse.find(p => p._2.length < AppSettings.personLimit).toList.sortBy(_._1).headOption match {
            case Some(r) => //进入人数未满的房间
              roomInUse.put(r._1, (msg.playerId, msg.name) :: r._2)
              getRoomActor(ctx, r._1) ! RoomActor.JoinRoom(r._1, msg.playerId, msg.name, msg.userActor)
            case None => //创建新房间
              var roomId = roomIdGenerator.getAndIncrement()
              while (roomInUse.exists(_._1 == roomId)) roomId = roomIdGenerator.getAndIncrement()
              roomInUse.put(roomId, List((msg.playerId, msg.name)))
              getRoomActor(ctx, roomId) ! RoomActor.JoinRoom(roomId, msg.playerId, msg.name, msg.userActor)

          }

          Behaviors.same

        case msg: ReStartJoinRoom =>
          roomInUse.find(_._2.exists(_._1 == msg.playerId)) match {
            case Some(r) =>
              getRoomActor(ctx, r._1) ! RoomActor.JoinRoom(r._1, msg.playerId, msg.name, msg.userActor)
            case None =>
              log.debug(s"${msg.name} ReStartJoinRoom but not find it.")
          }
          Behaviors.same

        case LeftRoom(playerId, name) =>
          roomInUse.find(_._2.exists(_._1 == playerId)) match {
            case Some(r) =>
              roomInUse.put(r._1, r._2.filterNot(_._1 == playerId))
              getRoomActor(ctx, r._1) ! RoomActor.LeftRoom(playerId, name, roomInUse(r._1))
              if (roomInUse(r._1).isEmpty && r._1 > 1l) roomInUse.remove(r._1)
            case None => log.debug(s"LeftRoom player-$name already gone.")
          }
          Behaviors.same

        case ChildDead(child, childRef) =>
          log.debug(s"roomManager unWatch RoomActor-$child")
          ctx.unwatch(childRef)
          Behaviors.same

        case x =>
          log.warn(s"unKnown msg: $x")
          Behaviors.unhandled
      }
    }

  private def getRoomActor(ctx: ActorContext[Command], roomId: Long) = {
    val childName = s"Room-$roomId"
    ctx.child(childName).getOrElse {
      val actor = ctx.spawn(RoomActor.create(roomId), childName)
      ctx.watchWith(actor, ChildDead(childName, actor))
      actor
    }.upcast[RoomActor.Command]
  }


}
