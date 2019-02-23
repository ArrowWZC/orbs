package org.seekloud.orbs.core

import java.util.concurrent.atomic.{AtomicInteger, AtomicLong}

import akka.actor.typed.scaladsl.{Behaviors, TimerScheduler}
import akka.actor.typed.{ActorRef, Behavior}
import org.seekloud.byteobject.MiddleBufferInJvm
import org.seekloud.byteobject.ByteObject._
import org.seekloud.orbs.common.AppSettings
import org.seekloud.orbs.core.game.OrbsSchemaServerImpl
import org.seekloud.orbs.shared.ptcl.protocol.OrbsProtocol._
import org.slf4j.LoggerFactory

import concurrent.duration._
import scala.collection.mutable

/**
  * User: TangYaruo
  * Date: 2019/2/5
  * Time: 16:00
  */
object RoomActor {

  private val log = LoggerFactory.getLogger(this.getClass)

  sealed trait Command

  final case class ChildDead[U](name: String, childRef: ActorRef[U]) extends Command

  case class JoinRoom(roomId: Long, playerId: String, name: String, userActor: ActorRef[UserActor.Command]) extends Command

  case class ReStartJoinRoom(roomId: Long, playerId: String, name: String, userActor: ActorRef[UserActor.Command]) extends Command

  case class LeftRoom(playerId: String, name: String, userList: List[(String, String)]) extends Command

  case class WsMessage(playerId: String, msg: UserActionEvent) extends Command

  case class UserMap(userActor: ActorRef[UserActor.Command]) extends Command

  case object GameLoop extends Command

  private final case object GameLoopKey

  def create(roomId: Long): Behavior[Command] =
    Behaviors.setup[Command] { ctx =>
      log.debug(s"RoomActor-$roomId is starting...")
      Behaviors.withTimers[Command] {
        implicit timer =>
          implicit val sendBuffer: MiddleBufferInJvm = new MiddleBufferInJvm(81920)
          val subscribersMap = mutable.HashMap[String, ActorRef[UserActor.Command]]()
          val orbsSchema = OrbsSchemaServerImpl(AppSettings.orbsGameConfig, log, ctx.self, dispatch(subscribersMap))

          timer.startPeriodicTimer(GameLoopKey, GameLoop, AppSettings.orbsGameConfig.frameDuration.millis)
          idle(roomId, Nil, subscribersMap, orbsSchema, 0L)
      }
    }


  private def idle(
    roomId: Long,
    newPlayer: List[(String, ActorRef[UserActor.Command])],
    subscribersMap: mutable.HashMap[String, ActorRef[UserActor.Command]],
    orbsSchema: OrbsSchemaServerImpl,
    tickCount: Long
  )(
    implicit timer: TimerScheduler[Command],
    sendBuffer: MiddleBufferInJvm
  ): Behavior[Command] =
    Behaviors.receive[Command] { (ctx, msg) =>
      msg match {

        case msg: JoinRoom =>
          val byteId = getByteId(msg.playerId, msg.name, orbsSchema)
          orbsSchema.joinGame(msg.playerId, msg.name, byteId, msg.userActor)
          subscribersMap.put(msg.playerId, msg.userActor)
          idle(msg.roomId, (msg.playerId, msg.userActor) :: newPlayer, subscribersMap, orbsSchema, tickCount)

        case msg: ReStartJoinRoom =>
          val byteId = getByteId(msg.playerId, msg.name, orbsSchema)
          orbsSchema.joinGame(msg.playerId, msg.name, byteId, msg.userActor)
          Behaviors.same

        case LeftRoom(playerId, name, userList) =>
          log.debug(s"$name left room ${System.currentTimeMillis()}")
          orbsSchema.leftGame(playerId, name)
          orbsSchema.episodeWinner = None
          subscribersMap.remove(playerId)

          orbsSchema.playerIdMap.foreach { p =>
            if (p._2._1 == playerId) {
              dispatch(subscribersMap)(UserLeftRoom(playerId, p._1, name, orbsSchema.systemFrame))
              orbsSchema.playerIdMap.remove(p._1)
            }
          }

          if (userList.isEmpty && roomId > 1l) Behaviors.stopped
          else idle(roomId, newPlayer.filter(_._1 == playerId), subscribersMap, orbsSchema, tickCount)

        case UserMap(userActor) =>

          userActor ! UserActor.DispatchMap(orbsSchema.playerIdMap.toList)
          Behaviors.same

        case WsMessage(userId, msg) =>
          orbsSchema.receiveUserAction(msg)
          Behavior.same

        case GameLoop =>
          val snapShot = orbsSchema.getCurSnapshot

          //逻辑帧更新
          orbsSchema.update()

          //同步
          if (tickCount % 2 == 1) {
            val data = orbsSchema.getOrbsSchemaState //TODO 具体同步的数据待改进
            val tail = (tickCount - 1) / 2 % 10
            dispatch(subscribersMap.filter(_._1.endsWith(tail.toString)))(SchemaSyncState(data))
          }

          //为新用户分发全量数据
          newPlayer.foreach {
            player =>
              val orbsSchemaData = orbsSchema.getOrbsSchemaState
              dispatchTo(subscribersMap)(player._1, SchemaSyncState(orbsSchemaData))
          }


          idle(roomId, Nil, subscribersMap, orbsSchema, tickCount + 1)


        case ChildDead(_, childRef) =>
          ctx.unwatch(childRef)
          Behaviors.same

        case x =>
          log.warn(s"unknown msg: $x")
          Behaviors.unhandled
      }
    }

  private def getByteId(playerId: String, name: String, orbsSchema: OrbsSchemaServerImpl): Byte = {
    var id: Byte = 0
    val idGenerator = new AtomicInteger(1)
    while(orbsSchema.playerIdMap.contains(id)) {
      id = idGenerator.getAndIncrement().toByte
    }
    orbsSchema.playerIdMap.put(id, (playerId, name))
    id
  }


  //向所有用户发数据
  def dispatch(subscribers: mutable.HashMap[String, ActorRef[UserActor.Command]])(msg: WsMsgServer)(implicit sendBuffer: MiddleBufferInJvm): Unit = {
    subscribers.values.foreach( _ ! UserActor.DispatchMsg(Wrap(msg.asInstanceOf[WsMsgServer].fillMiddleBuffer(sendBuffer).result())))
  }

  //向特定用户发数据
  def dispatchTo(subscribers: mutable.HashMap[String, ActorRef[UserActor.Command]])(id: String, msg: WsMsgServer)(implicit sendBuffer: MiddleBufferInJvm) = {
    subscribers.get(id).foreach(_ ! UserActor.DispatchMsg(Wrap(msg.asInstanceOf[WsMsgServer].fillMiddleBuffer(sendBuffer).result())))

  }


}
