package org.seekloud.orbs.front.orbs.draw

import org.seekloud.orbs.front.common.Routes
import org.seekloud.orbs.front.orbs.OrbsSchemaClientImpl
import org.seekloud.orbs.shared.ptcl.model.Point

/**
  * User: TangYaruo
  * Date: 2019/2/15
  * Time: 15:41
  */
trait BackgroundClient {
  this: OrbsSchemaClientImpl =>

  val backgroundImg = drawFrame.createImage(Routes.imgPath("test.jpg"))

  def drawBackground(canvasUnit: Float, canvasBounds: Point) = {
    ctx.save()
    ctx.drawImage(backgroundImg, 0, 0, Some(canvasBounds.x * canvasUnit, canvasBounds.y * canvasUnit))
    ctx.restore()
  }

  def drawGameLoading(): Unit = {
    println("linking...")
    ctx.setFill("#000000")
    ctx.fillRec(0, 0, canvasSize.x, canvasSize.y)
    ctx.setFill("rgb(250, 250, 250)")
    ctx.setTextAlign("left")
    ctx.setFont("Helvetica", 36)
    ctx.fillText("请稍等，正在连接服务器", 150, 180)
  }


}
