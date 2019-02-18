package org.seekloud.orbs.core

import java.util.concurrent.atomic.AtomicLong

import akka.actor.typed.scaladsl.{ActorContext, Behaviors, TimerScheduler}
import akka.actor.typed.{ActorRef, Behavior}
import akka.http.scaladsl.model.ws.{BinaryMessage, Message, TextMessage}
import akka.stream.{ActorAttributes, Supervision}
import akka.stream.scaladsl.Flow
import akka.util.ByteString
import org.seekloud.orbs.shared.ptcl.protocol.OrbsProtocol.{UserInfo, Wrap, WsMsgFront, WsMsgServer}
import org.slf4j.LoggerFactory

/**
  * User: TangYaruo
  * Date: 2019/2/5
  * Time: 15:59
  */
object UserManager {

  private val log = LoggerFactory.getLogger(this.getClass)

  trait Command

  final case class ChildDead[U](name: String, childRef: ActorRef[U]) extends Command

  final case class GetWebSocketFlow(name: String, replyTo: ActorRef[Flow[Message, Message, Any]]) extends Command

  final case class GetFlowSuccess(replyTo: ActorRef[UserActor.Command]) extends Command

  val behavior: Behavior[Command] = create()

  private def create(): Behavior[Command] =
    Behaviors.setup[Command] { ctx =>
      Behaviors.withTimers[Command] {
        implicit timer =>
          val uidGenerator = new AtomicLong(1L)
          idle(uidGenerator)
      }
    }

  private def idle(uidGenerator: AtomicLong)(
    implicit timer: TimerScheduler[Command]
  ): Behavior[Command] = {
    Behaviors.receive[Command] { (ctx, msg) =>
      msg match {

        case msg: GetWebSocketFlow =>
          val playerInfo = UserInfo(uidGenerator.getAndIncrement().toString, msg.name)
          val userActor = getUserActor(ctx, playerInfo.playerId, playerInfo)
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


}
