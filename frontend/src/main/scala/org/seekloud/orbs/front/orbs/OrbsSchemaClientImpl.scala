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
  var opLeft: Boolean = false,
  var canvasSize: Point,
  var canvasUnit: Float,
  var preCanvasFood: List[MiddleCanvas] = Nil,
  var preCanvasAdventurer: List[MiddleCanvas] = Nil,
  var preCanvasWeapon: List[MiddleCanvas] = Nil
) extends OrbsSchemaImpl(config, myId, myName, opId, opName)
  with BackgroundClient
  with GameElemClient {

  def drawGame(offSetTime: Long, canvasUnit: Float, canvasBounds: Point): Unit = {
    if (!waitSyncData) {
      //      println(s"episode winner: $episodeWinner")
      plankMap.get(myId) match {
        case Some(plank) =>
          if (episodeWinner.isEmpty) {
            drawBackground(ctx, canvasUnit, canvasBounds)
            drawPlayerInfo(myId, myName, ctx, canvasUnit, canvasBounds)
            drawPlank(myId, ctx, canvasUnit, canvasBounds)
            drawBall(myId, ctx, canvasUnit, canvasBounds)
            drawBricks(myId, ctx, canvasUnit, canvasBounds)
          } else {
            if (episodeWinner.get == plank.pId) { //自己胜利
              drawPlayerWin(ctx)
            } else if (episodeWinner.get == -1) {
              drawEqual(ctx)
            } else { //自己失败
              drawPlayerLose(ctx)
            }
          }

        case None => debug(s"drawGame not find plank: $myId")
      }
      if (opId.nonEmpty) {
        opId.foreach { op =>
          plankMap.get(op) match {
            case Some(plank) =>
              if (episodeWinner.isEmpty) {
                drawBackground(opCtx, canvasUnit, canvasBounds)
                drawPlayerInfo(op, opName.get, opCtx, canvasUnit, canvasBounds)
                drawPlank(op, opCtx, canvasUnit, canvasBounds)
                drawBall(op, opCtx, canvasUnit, canvasBounds)
                drawBricks(op, opCtx, canvasUnit, canvasBounds)
              } else {
                if (episodeWinner.get == plank.pId) { //对手胜利
                  drawOpponentWin(opCtx)
                } else if (episodeWinner.get == -1) {
                  drawEqual(opCtx)
                } else { //对手失败
                  drawOpponentLose(opCtx)
                }
              }
            case None => debug(s"drawGame not find opponent plank: $op")
          }
        }
      } else {
        needUserMap = true
        if (!opLeft) drawWaitingOp(opCtx) else drawPlayerLeave(opCtx)
      }
    } else {
      println(s"waitSyncData is true.")
    }
  }

  def setOpId(id: String, name: String): Unit = {
    opId = Some(id)
    opName = Some(name)
  }

  def clearOpInfo(): Unit = {
    opId = None
    opName = None
  }


  def updateSize(bounds: Point, unit: Float): Unit = {
    canvasSize = bounds
    canvasUnit = unit
  }


}
