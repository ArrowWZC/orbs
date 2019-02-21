package org.seekloud.orbs.core

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, StashBuffer, TimerScheduler}
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.Flow
import akka.stream.typed.scaladsl.{ActorSink, ActorSource}
import org.seekloud.byteobject.ByteObject._
import org.seekloud.byteobject.MiddleBufferInJvm
import org.seekloud.orbs.shared.ptcl.config.OrbsConfigImpl
import org.seekloud.orbs.shared.ptcl.protocol.OrbsProtocol._
import org.seekloud.orbs.Boot.roomManager
import org.slf4j.LoggerFactory

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration._
import scala.language.postfixOps

/**
  * User: TangYaruo
  * Date: 2019/2/5
  * Time: 15:59
  */
object UserActor {

  private val log = LoggerFactory.getLogger(this.getClass)
  private final val InitTime = Some(5.minutes)

  trait Command

  /*ws*/
  case object CompleteMsgFront extends Command

  case class FailMsgFront(ex: Throwable) extends Command

  case class WsMessage(msg: Option[WsMsgFront]) extends Command

  case class UserFrontActor(actor: ActorRef[WsMsgBackSource], replyTo: ActorRef[UserManager.Command]) extends Command

  case class LeftRoom[U](actorRef: ActorRef[U]) extends Command

  /*game*/
   object StartGame extends Command

  case class JoinRoom(playerId: String, name: String, userActor: ActorRef[UserActor.Command]) extends Command with RoomManager.Command

  case class JoinRoomSuccess(playerId: String, byteId: Byte, roomActor: ActorRef[RoomActor.Command], config: OrbsConfigImpl, playerIdMap: List[(Byte, (String, String))]) extends Command

  case class DispatchMsg(msg: WsMsgBackSource) extends Command

  case class DispatchMap(map: List[(Byte, (String, String))]) extends Command

  private case class TimeOut(msg: String) extends Command

  private final case object BehaviorChangeKey

  private[this] def switchBehavior(ctx: ActorContext[Command],
    behaviorName: String, behavior: Behavior[Command], durationOpt: Option[FiniteDuration] = None, timeOut: TimeOut = TimeOut("busy time error"))
    (implicit stashBuffer: StashBuffer[Command],
      timer: TimerScheduler[Command]) = {
    timer.cancel(BehaviorChangeKey)
    durationOpt.foreach(timer.startSingleTimer(BehaviorChangeKey, timeOut, _))
    stashBuffer.unstashAll(ctx, behavior)
  }

  private def sink(actor: ActorRef[Command]) = ActorSink.actorRef[Command](
    ref = actor,
    onCompleteMessage = CompleteMsgFront,
    onFailureMessage = FailMsgFront.apply
  )

  def flow(actor: ActorRef[UserActor.Command], replyTo: ActorRef[UserManager.Command]): Flow[WsMessage, WsMsgBackSource, Any] = {
    val in = Flow[WsMessage].to(sink(actor))
    val out =
      ActorSource.actorRef[WsMsgBackSource](
        completionMatcher = {
          case CompleteMsgServer =>
        },
        failureMatcher = {
          case FailMsgServer(e) => e
        },
        bufferSize = 256,
        overflowStrategy = OverflowStrategy.dropHead
      ).mapMaterializedValue(outActor => actor ! UserFrontActor(outActor, replyTo))
    Flow.fromSinkAndSource(in, out)
  }

  def create(playerId: String, userInfo: UserInfo): Behavior[Command] =
    Behaviors.setup[Command] { ctx =>
      log.info(s"UserActor-$playerId is starting...")
      implicit val stashBuffer: StashBuffer[Command] = StashBuffer[Command](Int.MaxValue)
      Behaviors.withTimers[Command] { implicit timer =>
        implicit val sendBuffer: MiddleBufferInJvm = new MiddleBufferInJvm(8192)
        switchBehavior(ctx, "init", init(playerId, userInfo), InitTime, TimeOut("init time out."))
      }
    }

  private def init(playerId: String, userInfo: UserInfo)(
    implicit stashBuffer: StashBuffer[Command],
    sendBuffer: MiddleBufferInJvm,
    timer: TimerScheduler[Command]
  ): Behavior[Command] =
    Behaviors.receive[Command] { (ctx, msg) =>
      msg match {

        case msg: UserFrontActor =>
          ctx.watchWith(msg.actor, LeftRoom(msg.actor))
          msg.replyTo ! UserManager.GetFlowSuccess(ctx.self)
          switchBehavior(ctx, "idle", idle(playerId, userInfo, msg.actor))

        case LeftRoom(actor) =>
          ctx.unwatch(actor)
          Behaviors.stopped

        case TimeOut(m) =>
          log.debug(s"time out in init, msg: $msg")
          Behaviors.stopped

        case x =>
          log.warn(s"unknown msg in init: $x")
          Behaviors.unhandled

      }
    }

  private def idle(playerId: String, userInfo: UserInfo, frontActor: ActorRef[WsMsgBackSource])(
    implicit stashBuffer: StashBuffer[Command],
    timer: TimerScheduler[Command],
    sendBuffer: MiddleBufferInJvm
  ): Behavior[Command] =
    Behaviors.receive[Command] { (ctx, msg) =>
      msg match {
        case StartGame =>
          roomManager ! JoinRoom(playerId, userInfo.name, ctx.self)
          Behaviors.same

        case msg: JoinRoomSuccess =>
          val wsMsg = YourInfo(msg.config, msg.playerId, userInfo.name, msg.byteId, msg.playerIdMap).asInstanceOf[WsMsgServer].fillMiddleBuffer(sendBuffer).result()
          frontActor ! Wrap(wsMsg)
          switchBehavior(ctx, "play", play(msg.playerId, userInfo, frontActor, msg.roomActor))

        case WsMessage(reqOpt) =>
          // TODO 此处接收消息场景未定
          Behaviors.same

        case LeftRoom(actor) =>
          ctx.unwatch(actor)
          switchBehavior(ctx, "init", init(playerId, userInfo), InitTime, TimeOut("init"))

        case x =>
          log.warn(s"unknown msg in idle: $x")
          Behaviors.unhandled
      }
    }

  private def play(
    playerId: String,
    userInfo: UserInfo,
    frontActor: ActorRef[WsMsgBackSource],
    roomActor: ActorRef[RoomActor.Command]
  )(
    implicit stashBuffer: StashBuffer[Command],
    timer: TimerScheduler[Command],
    sendBuffer: MiddleBufferInJvm
  ): Behavior[Command] =
    Behaviors.receive[Command] { (ctx, msg) =>
      msg match {
        case WsMessage(m) =>
          m match {
            case Some(event: UserActionEvent) =>
              roomActor ! RoomActor.WsMessage(playerId, event)

//            case Some(RestartGame) =>
//              roomManager ! RoomManager.ReStartJoinRoom(userInfo.playerId, userInfo.name, ctx.self)

            case Some(UserMapReq) =>
              roomActor ! RoomActor.UserMap(ctx.self)

            case Some(event: PingPackage) =>
              frontActor ! Wrap(event.asInstanceOf[WsMsgServer].fillMiddleBuffer(sendBuffer).result())

            case _ =>
          }
          Behaviors.same

        case DispatchMsg(m) =>
          frontActor ! m
          Behaviors.same


        case DispatchMap(map) =>
          val msg = Wrap(UserMap(map).asInstanceOf[WsMsgServer].fillMiddleBuffer(sendBuffer).result())
          frontActor ! msg
          Behaviors.same

        case _: JoinRoomSuccess =>
          val ws = RestartYourInfo.asInstanceOf[WsMsgServer].fillMiddleBuffer(sendBuffer).result()
          frontActor ! Wrap(ws)
          Behaviors.same

        case LeftRoom(actor) =>
          ctx.unwatch(actor)
          roomManager ! RoomManager.LeftRoom(playerId, userInfo.name)
          Behaviors.stopped

        case x =>
          log.warn(s"unknown msg in play: $x")
          Behaviors.unhandled
      }
    }


}
