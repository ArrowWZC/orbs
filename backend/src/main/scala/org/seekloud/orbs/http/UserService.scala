package org.seekloud.orbs.http

import org.slf4j.LoggerFactory
import akka.actor.typed.ActorRef
import akka.http.scaladsl.server.Directives._
import akka.actor.typed.scaladsl.AskPattern._
import akka.http.scaladsl.server.Route
import akka.stream.scaladsl.Flow
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.stream.{ActorAttributes, Supervision}
import akka.actor.typed.scaladsl.adapter._

import scala.concurrent.Future
import collection.JavaConverters._
import io.circe.generic.auto._
import io.circe.syntax._
import org.seekloud.orbs.core.UserManager
import org.seekloud.orbs.shared.ptcl.protocol.UserProtocol.{SignInReq, SignInRsp, SignUpReq, SignUpRsp}

import scala.concurrent.duration._
import scala.language.postfixOps

/**
  * User: TangYaruo
  * Date: 2019/2/22
  * Time: 15:06
  */
object UserService {
  private val log = LoggerFactory.getLogger(this.getClass)
}

trait UserService extends ServiceUtils with BaseService {

  import io.circe._
  import io.circe.generic.auto._

  import UserService._

  private val signUp = (path("signUp") & post) {
    entity(as[Either[Error, SignUpReq]]) {
      case Right(req) =>
        dealFutureResult {
          val signUpRsp: Future[SignUpRsp] = userManager ? (UserManager.UserSignUp(req.nickname, req.password, _))
          signUpRsp.map {
            rsp =>
              complete(rsp)
          }
        }

      case Left(error) =>
        log.error(s"signUp parse error: $error")
        complete(JsonParseError)
    }
  }

  private val signIn = (path("signIn") & post) {
    entity(as[Either[Error, SignInReq]]) {
      case Right(req) =>
        dealFutureResult {
          val signInReq: Future[SignInRsp] = userManager ? (UserManager.UserSignIn(req.nickname, req.password, _))
          signInReq.map {
            rsp =>
              complete(rsp)
          }
        }
      case Left(error) =>
        log.error(s"signIn parse error: $error")
        complete(JsonParseError)

    }
  }

  val userRoutes: Route = pathPrefix("user") {
    signUp ~ signIn
  }

}
