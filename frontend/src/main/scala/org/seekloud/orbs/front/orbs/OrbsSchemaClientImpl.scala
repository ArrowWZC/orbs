package org.seekloud.orbs.front.orbs

import org.seekloud.orbs.front.orbs.draw.{BackgroundClient, GameElemClient}
import org.seekloud.orbs.shared.ptcl.config.OrbsConfig
import org.seekloud.orbs.shared.ptcl.model.Point
import org.seekloud.orbs.shared.ptcl.orbs.OrbsSchemaImpl
import org.seekloud.orbs.shared.ptcl.util.middleware._
/**
  * User: TangYaruo
  * Date: 2019/2/15
  * Time: 15:03
  */
case class OrbsSchemaClientImpl(
  drawFrame: MiddleFrame,
  ctx: MiddleContext,
  opCtx: MiddleContext,
  override val config: OrbsConfig,
  myId: String,
  myName: String,
  var opId: Option[String] = None,
  var opName: Option[String] = None,
  var canvasSize:Point,
  var canvasUnit:Float,
  var preCanvasFood: List[MiddleCanvas] = Nil,
  var preCanvasAdventurer: List[MiddleCanvas] = Nil,
  var preCanvasWeapon: List[MiddleCanvas] = Nil
) extends OrbsSchemaImpl(config, myId, myName, opId, opName)
  with BackgroundClient
  with GameElemClient {

  def drawGame(offSetTime: Long, canvasUnit: Float, canvasBounds: Point): Unit = {
    if (!waitSyncData) {
      plankMap.get(myId) match {
        case Some(_) =>
          drawBackground(ctx, canvasUnit, canvasBounds)
          drawPlank(myId, ctx, canvasUnit, canvasBounds)
          drawBall(myId, ctx, canvasUnit, canvasBounds)
          drawBricks(myId, ctx, canvasUnit, canvasBounds)

        case None => debug(s"drawGame not find plank: $myId")
      }
      if (opId.nonEmpty) {
        opId.foreach { op =>
          plankMap.get(op) match {
            case Some(_) =>
              drawBackground(opCtx, canvasUnit, canvasBounds)
              drawPlank(op, opCtx, canvasUnit, canvasBounds)
              drawBall(op, opCtx, canvasUnit, canvasBounds)
              drawBricks(op, opCtx, canvasUnit, canvasBounds)
            case None => debug(s"drawGame not find opponent plank: $myId")
          }
        }
      } else {
        needUserMap = true
        drawWaitingOp(opCtx)
      }
    } else {
      println(s"waitSyncData is true.")
    }
  }

  def setOpId(id: String, name: String): Unit = {
    opId = Some(id)
    opName = Some(name)
  }


  def updateSize(bounds: Point, unit: Float): Unit ={
    canvasSize = bounds
    canvasUnit = unit
  }




}
