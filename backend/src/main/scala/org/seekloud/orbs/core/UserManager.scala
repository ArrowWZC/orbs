package org.seekloud.orbs.core

import java.util.concurrent.atomic.AtomicLong

import akka.actor.typed.scaladsl.{ActorContext, Behaviors, StashBuffer, TimerScheduler}
import akka.actor.typed.{ActorRef, Behavior}
import akka.http.scaladsl.model.ws.{BinaryMessage, Message, TextMessage}
import akka.stream.{ActorAttributes, Supervision}
import akka.stream.scaladsl.Flow
import akka.util.ByteString
import org.seekloud.orbs.models.dao.UserInfoDao
import org.seekloud.orbs.Boot.executor
import org.seekloud.orbs.shared.ptcl.protocol.OrbsProtocol.{UserInfo, Wrap, WsMsgFront, WsMsgServer}
import org.seekloud.orbs.shared.ptcl.protocol.UserProtocol.{NicknameError, NicknameInvalid, PasswordError, UserExist, SignInRsp, SignUpFail, SignUpRsp}
import org.slf4j.LoggerFactory

import scala.collection.mutable
import scala.concurrent.duration.FiniteDuration
import scala.util.{Failure, Success}
import scala.concurrent.duration._
import scala.language.postfixOps

/**
  * User: TangYaruo
  * Date: 2019/2/5
  * Time: 15:59
  */
object UserManager {

  private val log = LoggerFactory.getLogger(this.getClass)
  private final val InitTime = Some(5.minutes)


  trait Command

  final case class ChildDead[U](name: String, childRef: ActorRef[U]) extends Command

  final case class GetWebSocketFlow(name: String, replyTo: ActorRef[Flow[Message, Message, Any]]) extends Command

  final case class GetLoginWebSocketFlow(playerId: String, name: String, replyTo: ActorRef[Flow[Message, Message, Any]]) extends Command

  final case class GetFlowSuccess(replyTo: ActorRef[UserActor.Command]) extends Command

  final case class User(playerId: String, nickname: String, password: String)

  final case class UserSignUp(nickname: String, password: String, replyTo: ActorRef[SignUpRsp]) extends Command

  final case class UserSignIn(nickname: String, password: String, replyTo: ActorRef[SignInRsp]) extends Command

  private final case class SwitchBehavior(
    name: String,
    behavior: Behavior[Command],
    durationOpt: Option[FiniteDuration] = None,
    timeOut: TimeOut = TimeOut("busy time error")
  ) extends Command

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


  val behavior: Behavior[Command] = create()

  private def create(): Behavior[Command] =
    Behaviors.setup[Command] { ctx =>
      log.info(s"UserManager is starting...")
      implicit val stashBuffer: StashBuffer[Command] = StashBuffer[Command](Int.MaxValue)
      Behaviors.withTimers[Command] {
        implicit timer =>
          val uidGenerator = new AtomicLong(1L)
          UserInfoDao.getAllUser.onComplete {
            case Success(rst) =>
              log.info(s"init success!")
              val user = rst.map(u => User(u.playerId, u.nickname, u.password)).toList
              ctx.self ! SwitchBehavior("idle", idle(uidGenerator, user))
            case Failure(e) =>
              log.error(s"init error: $e")
              ctx.self ! SwitchBehavior("idle", idle(uidGenerator, Nil))
          }
          switchBehavior(ctx, "busy", busy(), InitTime, TimeOut("init time out"))
      }
    }

  private def idle(uidGenerator: AtomicLong, userMap: List[User])(
    implicit stashBuffer: StashBuffer[Command],
    timer: TimerScheduler[Command]
  ): Behavior[Command] = {
    Behaviors.receive[Command] { (ctx, msg) =>
      msg match {
        case msg: UserSignUp =>
          userMap.find(_.nickname == msg.nickname) match {
            case Some(_) => //用户名已存在
              msg.replyTo ! NicknameInvalid
              ctx.self ! SwitchBehavior("idle", idle(uidGenerator, userMap))
            case None => //用户名有效
              val playerId = uidGenerator.getAndIncrement().toString
              val userInfo = org.seekloud.orbs.models.dao.UserInfo(playerId, msg.nickname, msg.password)
              UserInfoDao.addUser(userInfo).onComplete {
                case Success(_) =>
                  val newUserMap = User(playerId, msg.nickname, msg.password) :: userMap
                  msg.replyTo ! SignUpRsp(Some(playerId))
                  ctx.self ! SwitchBehavior("idle", idle(uidGenerator, newUserMap))
                case Failure(e) =>
                  log.debug(s"user-${msg.nickname} sign up error: $e")
                  msg.replyTo ! SignUpFail
                  ctx.self ! SwitchBehavior("idle", idle(uidGenerator, userMap))

              }
          }
          switchBehavior(ctx, "busy", busy())

        case msg: UserSignIn =>
          userMap.find(_.nickname == msg.nickname) match {
            case Some(user) =>
              if (msg.password == user.password) {
                if (getUserActorOpt(ctx, user.playerId).nonEmpty) {
                  msg.replyTo ! UserExist
                } else {
                  msg.replyTo ! SignInRsp(Some(user.playerId))

                }
              } else {
                msg.replyTo ! PasswordError
              }
            case None =>
              msg.replyTo ! NicknameError
          }
          Behaviors.same

        case msg: GetWebSocketFlow =>
          val playerInfo = UserInfo(uidGenerator.getAndIncrement().toString, msg.name)
          val userActor = getUserActor(ctx, playerInfo.playerId, playerInfo)
          msg.replyTo ! getWebSocketFlow(userActor, ctx)
          Behaviors.same

        case msg: GetLoginWebSocketFlow =>
          val playerInfo = UserInfo(msg.playerId, msg.name)
          val userActor = getUserActor(ctx, msg.playerId, playerInfo)
          msg.replyTo ! getWebSocketFlow(userActor, ctx)
          Behaviors.same

        case msg: GetFlowSuccess =>
          msg.replyTo ! UserActor.StartGame
          Behaviors.same

        case ChildDead(name, childRef) =>
          log.info(s"unwatch UserActor-$name.")
          ctx.unwatch(childRef)
          Behaviors.same
        case x =>
          log.warn(s"unknown msg: $x")
          Behaviors.unhandled
      }
    }
  }

  private def busy()
    (
      implicit stashBuffer: StashBuffer[Command],
      timer: TimerScheduler[Command]
    ): Behavior[Command] =
    Behaviors.receive[Command] { (ctx, msg) =>
      msg match {
        case SwitchBehavior(name, b, durationOpt, timeOut) =>
          switchBehavior(ctx, name, b, durationOpt, timeOut)

        case TimeOut(m) =>
          log.debug(s"${ctx.self.path} is time out when busy, msg=$m")
          switchBehavior(ctx, "idle", idle(new AtomicLong(1L), Nil))

        case x =>
          stashBuffer.stash(x)
          Behavior.same

      }
    }

  private def getWebSocketFlow(userActor: ActorRef[UserActor.Command], ctx: ActorContext[Command]): Flow[Message, Message, Any] = {
    import scala.language.implicitConversions
    import org.seekloud.byteobject.ByteObject._
    import org.seekloud.byteobject.MiddleBufferInJvm
    implicit def parseJsonString2WsMsgFront(s: String): Option[WsMsgFront] = {
      import io.circe.generic.auto._
      import io.circe.parser._
      try {
        val wsMsg = decode[WsMsgFront](s).right.get
        Some(wsMsg)
      } catch {
        case e: Exception =>
          log.warn(s"parse front msg failed when json parse,s=$s")
          None
      }
    }

    Flow[Message]
      .collect {
        case TextMessage.Strict(m) =>
          UserActor.WsMessage(m)
        case BinaryMessage.Strict(m) =>
          val buffer = new MiddleBufferInJvm(m.asByteBuffer)
          bytesDecode[WsMsgFront](buffer) match {
            case Right(req) =>
              UserActor.WsMessage(Some(req))
            case Left(e) =>
              log.error(s"decode binaryMessage failed,error:${e.message}")
              UserActor.WsMessage(None)
          }

      }.via(UserActor.flow(userActor, ctx.self))
      .map {
        case wrap: Wrap =>
          BinaryMessage.Strict(ByteString(wrap.ws))
        case x =>
          log.debug(s"akka stream receive unknown msg=$x")
          TextMessage.apply("")

      }.withAttributes(ActorAttributes.supervisionStrategy(decider))
  }

  private val decider: Supervision.Decider = {
    e: Throwable =>
      e.printStackTrace()
      log.error(s"WS stream failed with $e")
      Supervision.Resume
  }

  private def getUserActor(ctx: ActorContext[Command], id: String, userInfo: UserInfo) = {
    val childName = s"UserActor-$id"
    ctx.child(childName).getOrElse {
      val userActor = ctx.spawn(UserActor.create(id, userInfo), childName)
      ctx.watchWith(userActor, ChildDead(childName, userActor))
      userActor
    }.upcast[UserActor.Command]
  }

  private def getUserActorOpt(ctx: ActorContext[Command], id: String): Option[ActorRef[UserActor.Command]] = {
    val childName = s"UserActor-$id"
    ctx.child(childName).map(_.upcast[UserActor.Command])
  }


}
