package org.seekloud.orbs.front.orbs

import org.seekloud.orbs.front.common.Routes
import org.seekloud.orbs.front.orbs.draw.{BackgroundClient, GameElemClient}
import org.seekloud.orbs.shared.ptcl.config.OrbsConfig
import org.seekloud.orbs.shared.ptcl.model.{Constants, Point}
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

  def emoji(name: String): MiddleImage = drawFrame.createImage(Routes.imgPath(name))

  def replaceListItem(list: List[String], oldItem: String, newItem: List[String]): List[String] = {
    var tmpList = List[String]()
    list.foreach {
      case `oldItem` => newItem.reverse.foreach(i => tmpList = i :: tmpList)
      case x => tmpList = x :: tmpList
    }
    tmpList.reverse
  }

  def drawPlayerBarrage(ctx: MiddleContext, info: String): Unit = {
    var elem = List[String](info)
    var startPosition = 50
    def classify(info: String, key: String): Unit = {
      var newInfo = List[String]()
      val es = info.split(key)
      if (info.forall(_ == key.head)) {
        (1 to info.length).foreach( _ => newInfo = key :: newInfo)
      } else {
        if (es.nonEmpty) {
          es.foreach { e =>
            if (e != es.last) {
              newInfo = key :: e :: newInfo
            } else {
              newInfo = e :: newInfo
            }
          }
          if (info.endsWith(key)) {
            (1 to info.split(es.last).last.length / 6).foreach { _ => newInfo = key :: newInfo}
          }
        } else {
          (1 to info.length / 6) foreach(_ => newInfo = key :: newInfo)
        }
        newInfo = newInfo.filterNot(_.isEmpty)
      }
      elem = replaceListItem(elem, info, newInfo)
    }

    Constants.emoji.keySet.foreach { key =>
      elem.foreach { i =>
        if (i.contains(key) && i != key) {
          classify(i, key)
        }
      }
    }
    ctx.save()
    elem.filterNot(_.isEmpty).foreach { e =>
      if (Constants.emoji.keySet.contains(e)) {
        ctx.drawImage(emoji(Constants.emoji(e)), startPosition, 40, Some(25, 25))
        startPosition += 35
      } else {
        ctx.setFill("rgb(250, 250, 250)")
        ctx.setTextAlign("left")
        ctx.setFont("Helvetica", 20)
        ctx.fillText(e, startPosition, 65)
        startPosition += e.length * 20 + 10
      }
    }
    ctx.restore()
  }

  def drawBarrage(sender: Byte, info: String): Unit = {
    playerIdMap.get(sender).foreach { player =>
      if (player._1 == myId) {
        drawPlayerBarrage(opCtx, info)
      } else {
        drawPlayerBarrage(ctx, info)
      }
    }
  }

  def drawNormalBarrage(info: String): Unit = {
    ctx.save()
    ctx.setFill("rgb(250, 250, 250)")
    ctx.setTextAlign("left")
    ctx.setFont("Helvetica", 20)
    ctx.fillText(info, 50, 65)
    ctx.restore()
  }

  def drawGame(offSetTime: Long, canvasUnit: Float, canvasBounds: Point): Unit = {
    if (!waitSyncData) {
      //      println(s"episode winner: $episodeWinner")
      plankMap.get(myId) match {
        case Some(plank) =>
          if (episodeWinner.isEmpty) {
            if (!opLeft) {
              drawBackground(ctx, canvasUnit, canvasBounds)
              drawPlayerInfo(myId, myName, ctx, canvasUnit, canvasBounds)
              drawPlank(myId, ctx, canvasUnit, canvasBounds)
              drawBall(myId, ctx, canvasUnit, canvasBounds)
              drawBricks(myId, ctx, canvasUnit, canvasBounds)
            } else {
              drawOpGone(ctx)
            }
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
