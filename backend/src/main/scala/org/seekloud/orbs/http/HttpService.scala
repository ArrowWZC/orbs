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

import scala.concurrent.{ExecutionContextExecutor, Future}


/**
  * User: TangYaruo
  * Date: 2019/2/5
  * Time: 16:29
  */
trait HttpService extends ResourceService
                     with ServiceUtils
                     with OrbsService
                     with UserService {

  import akka.actor.typed.scaladsl.AskPattern._
  import org.seekloud.utils.CirceSupport._
  import io.circe.generic.auto._

  implicit val system: ActorSystem

  implicit val executor: ExecutionContextExecutor

  implicit val materializer: Materializer

  implicit val timeout: Timeout

  implicit val scheduler: Scheduler

  import akka.actor.typed.scaladsl.adapter._

  lazy val routes: Route = pathPrefix(AppSettings.rootPath) {
    resourceRoutes ~ orbsRoutes ~ userRoutes
  }


}
