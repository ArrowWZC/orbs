package org.seekloud.orbs.http

import akka.actor.{ActorSystem, Scheduler}
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.ws.Message
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.Materializer
import akka.stream.scaladsl.Flow
import akka.util.Timeout
import org.seekloud.orbs.common.AppSettings
import akka.actor.typed.scaladsl.AskPattern._
import java.net.URLEncoder

import org.seekloud.orbs.core.UserManager
import org.slf4j.LoggerFactory

import scala.concurrent.{ExecutionContextExecutor, Future}

/**
  * User: TangYaruo
  * Date: 2019/2/15
  * Time: 21:53
  */
object OrbsService {
  private val log = LoggerFactory.getLogger(this.getClass)

}

trait OrbsService extends ServiceUtils
  with BaseService {

  import OrbsService._

  private val normalJoin: Route = path("join") {
    parameter(
      'name.as[String]
    ) { name =>
      val flowFuture: Future[Flow[Message, Message, Any]] = userManager ? (UserManager.GetWebSocketFlow(name, _))
      dealFutureResult(
        flowFuture.map(t => handleWebSocketMessages(t))
      )
    }
  }

  private val loginJoin: Route = path("loginJoin") {
    parameter(
      'playerId.as[String],
      'name.as[String]
    ) { (playerId, name) =>
      val flowFuture: Future[Flow[Message, Message, Any]] = userManager ? (UserManager.GetLoginWebSocketFlow(playerId, name, _))
      dealFutureResult(
        flowFuture.map(t => handleWebSocketMessages(t))
      )
    }
  }

  val orbsRoutes: Route = (pathPrefix("game") & get) {
    pathEndOrSingleSlash {
      getFromResource("html/admin.html")
    } ~ normalJoin ~ loginJoin
  }

}
