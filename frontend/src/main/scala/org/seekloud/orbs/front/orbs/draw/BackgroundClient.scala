package org.seekloud.orbs.front.orbs.draw

import org.seekloud.orbs.front.common.Routes
import org.seekloud.orbs.front.orbs.OrbsSchemaClientImpl
import org.seekloud.orbs.shared.ptcl.model.Constants.GameInfo
import org.seekloud.orbs.shared.ptcl.model.Point
import org.seekloud.orbs.shared.ptcl.util.middleware.MiddleContext

/**
  * User: TangYaruo
  * Date: 2019/2/15
  * Time: 15:41
  */
trait BackgroundClient {
  this: OrbsSchemaClientImpl =>

  val backgroundImg = drawFrame.createImage(Routes.imgPath("test.jpg"))

  def drawBackground(ctx: MiddleContext, canvasUnit: Float, canvasBounds: Point): Unit = {
//    ctx.save()
//    ctx.drawImage(backgroundImg, 0, 0, Some(canvasBounds.x * canvasUnit, canvasBounds.y * canvasUnit))
//    ctx.restore()
    ctx.save()
    ctx.setFill("#000000")
    ctx.fillRec(0, 0, canvasSize.x, canvasSize.y)
    ctx.restore()
  }

  def drawInfo(ctx: MiddleContext, info: String): Unit = {
    ctx.setFill("#000000")
    ctx.fillRec(0, 0, canvasSize.x, canvasSize.y)
    ctx.setFill("rgb(250, 250, 250)")
    ctx.setTextAlign("left")
    ctx.setFont("Helvetica", 20)
    ctx.fillText(info, 50, 80)
  }

  def drawGameLoading(ctx: MiddleContext): Unit = {
    println("linking...")
    drawInfo(ctx, GameInfo.linking)
  }

  def drawWaitingOp(ctx: MiddleContext): Unit = {
    drawInfo(ctx, GameInfo.waitOpJoin)
  }

  def drawOpNotIn(ctx: MiddleContext): Unit = {
    drawInfo(ctx, GameInfo.opNotIn)
  }

  def drawPlayerLeave(ctx: MiddleContext): Unit = {
    drawInfo(ctx, GameInfo.opponentLeft)
  }

  def drawPlayerWin(ctx: MiddleContext): Unit = {
    drawInfo(ctx, "YOU WIN!!!")
  }

  def drawPlayerLose(ctx: MiddleContext): Unit = {
    drawInfo(ctx, "YOU LOSE!!!")
  }

  def drawOpponentWin(ctx: MiddleContext): Unit = {
    drawInfo(ctx, "Opponent Win!!!")
  }

  def drawOpponentLose(ctx: MiddleContext): Unit = {
    drawInfo(ctx, "Opponent Lose!!!")
  }

  def drawEqual(ctx: MiddleContext): Unit = {
    drawInfo(ctx, "平局！！！")
  }




}
