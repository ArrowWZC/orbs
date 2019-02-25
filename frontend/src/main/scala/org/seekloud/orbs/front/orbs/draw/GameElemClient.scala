package org.seekloud.orbs.front.orbs.draw

import org.seekloud.orbs.front.common.Routes
import org.seekloud.orbs.front.orbs.OrbsSchemaClientImpl
import org.seekloud.orbs.shared.ptcl.component.{Ball, Brick, Plank}
import org.seekloud.orbs.shared.ptcl.model.Point
import org.seekloud.orbs.shared.ptcl.util.middleware.MiddleContext

import scala.util.Random

/**
  * User: TangYaruo
  * Date: 2019/2/18
  * Time: 14:40
  */
trait GameElemClient {
  this: OrbsSchemaClientImpl =>
  val ballImg = drawFrame.createImage(Routes.imgPath("ball.png"))
  val tool = drawFrame.createImage(Routes.imgPath("tool.png"))


  def drawArcRect(ctx: MiddleContext, r: Double, width: Double, height: Double, left: Double, top: Double): Unit = {
    if (width >= 2 * r) {
      ctx.save()
      ctx.beginPath()
      ctx.arc(left + r, top + r, r, 0.5 * math.Pi, 1.5 * math.Pi, counterclockwise = false)
      ctx.lineTo(left + width - r, top)
      ctx.arc(left + width - r, top + r, r, 1.5 * math.Pi, 0.5 * math.Pi, counterclockwise = false)
      ctx.lineTo(left + r, top + height)
      ctx.closePath()
      ctx.fill()
      ctx.restore()
    }
  }

  def drawAPlank(plank: Plank, ctx: MiddleContext, canvasUnit: Float, canvasBounds: Point): Unit = {
    val r = plank.getHeight / 2
    val barLeft = plank.getPlankState.position.x - plank.getWidth / 2
    val barTop = plank.getPlankState.position.y - plank.getHeight / 2
    ctx.setFill("#616161")
    drawArcRect(ctx, r, plank.getWidth, plank.getHeight, barLeft, barTop)
    ctx.setFill("#c7c7c7")
    drawArcRect(ctx, r - 1, plank.getWidth - 2, plank.getHeight - 2, barLeft + 1, barTop + 1)

  }

  def drawPlank(id: String, ctx: MiddleContext, canvasUnit: Float, canvasBounds: Point): Unit = {
    plankMap.get(id).foreach { p =>
      drawAPlank(p, ctx, canvasUnit, canvasBounds)
    }
  }

  def drawABall(ball: Ball, ctx: MiddleContext, canvasUnit: Float, canvasBounds: Point): Unit = {
    val r = config.getBallRadius
    val sx = ball.getBallState.position.x - r
    val sy = ball.getBallState.position.y - r
    val dx = 2 * r
    val dy = 2 * r
    if (0 < sx && sx < canvasBounds.x && 0 < sy && sy < canvasBounds.y) {
      ctx.save()
      ctx.drawImage(ballImg, sx * canvasUnit, sy * canvasUnit, Some(dx * canvasUnit, dy * canvasUnit))
      ctx.restore()
    }
  }

  def drawBall(id: String, ctx: MiddleContext, canvasUnit: Float, canvasBounds: Point): Unit = {
    ballMap.filter(_._1.startsWith(id)).foreach { b =>
      drawABall(b._2, ctx, canvasUnit, canvasBounds)
    }
  }

  def drawABrick(brick: Brick, ctx: MiddleContext, canvasUnit: Float, canvasBounds: Point): Unit = {

    val brickPosition = brick.getBrickState.position
    val brickShape = Point(brick.getWidth, brick.getHeight)
    ctx.save()
//    ctx.setShadowColor("rgba(0,0,0,0.5")
//    ctx.setShadowBlur(1)
    ctx.lineWidth(2.5)
    ctx.setFill(config.getBrickColor(brick.color))
    ctx.setStrokeStyle("rgba(0,0,0,0.5")
    ctx.beginPath()
    ctx.rect(brickPosition.x - brickShape.x / 2, brickPosition.y - brickShape.y / 2, brickShape.x, brickShape.y)
    ctx.closePath()
    ctx.fill()
    ctx.stroke()
    if (brick.isNormal % 2 == 0 && brick.isNormal < 10) {
      ctx.drawImage(tool, brickPosition.x - 6, brickPosition.y - 8, Some(12, 16))
    }
    ctx.restore()

  }


  def drawBricks(id: String, ctx: MiddleContext, canvasUnit: Float, canvasBounds: Point) = {
    brickMap.filter(_._1.startsWith(id)).foreach { r =>
      drawABrick(r._2, ctx, canvasUnit, canvasBounds)
    }
  }


}
