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
  val lifeImg = drawFrame.createImage(Routes.imgPath("life.png"))
  val brickImg = drawFrame.createImage(Routes.imgPath("brick.png"))

  def drawBackground(ctx: MiddleContext, canvasUnit: Float, canvasBounds: Point): Unit = {
//    ctx.save()
//    ctx.drawImage(backgroundImg, 0, 0, Some(canvasBounds.x * canvasUnit, canvasBounds.y * canvasUnit))
//    ctx.restore()
    ctx.save()
    ctx.setFill("#000000")
    ctx.fillRec(0, 0, canvasSize.x, canvasSize.y)
    ctx.restore()
  }

  //TODO 画名字，生命，剩余砖块数目
  def drawPlayerInfo(id: String, name: String, ctx: MiddleContext, canvasUnit: Float, canvasBounds: Point): Unit = {
    plankMap.get(id).foreach { plank =>
      val life = plank.ballAvailable
      val brickLeft = brickMap.count(_._1.startsWith(id))
      ctx.save()
      ctx.setFill("rgb(250, 250, 250)")
      ctx.setTextAlign("left")
      ctx.setFont("Helvetica", 18)
      ctx.fillText(s"name: $name", 20, 31)
      ctx.drawImage(lifeImg, 220, 20, Some(21, 13))
      ctx.fillText(life.toString, 246, 31)
      ctx.drawImage(brickImg, 275, 17, Some(18, 18))
      ctx.fillText(brickLeft.toString, 298, 31)
      ctx.restore()
    }
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


  def drawWait4Relive(ctx: MiddleContext): Unit = {
    drawInfo(ctx, "press space to restart!")
  }

  def drawOpRestart(ctx: MiddleContext): Unit = {
    drawInfo(ctx, "对方已准备好再来一局~")
  }




}
