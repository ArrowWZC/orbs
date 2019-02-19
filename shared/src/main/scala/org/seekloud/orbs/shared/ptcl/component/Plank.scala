package org.seekloud.orbs.shared.ptcl.component

import org.seekloud.orbs.shared.ptcl.config.{OrbsConfig, OrbsConfigImpl}
import org.seekloud.orbs.shared.ptcl.model.Constants.DirectionType
import org.seekloud.orbs.shared.ptcl.model.Point
import org.seekloud.orbs.shared.ptcl.util.QuadTree


/**
  * User: TangYaruo
  * Date: 2019/2/6
  * Time: 17:04
  *
  * 基础版本
  * 待完成功能：发射子弹
  */

case class PlankState(pId: Byte, level: Byte, position: Point, direction: Float, isMove: Byte)

case class Plank(
  config: OrbsConfig,
  override protected var position: Point,
  pId: Byte,
  var level: Byte,
  var direction: Float,
  var isMove: Byte //0:false, 1:true
) extends RectangleObjectOfGame {

  def this(config: OrbsConfig, plankState: PlankState) = {
    this(config, plankState.position, plankState.pId, plankState.level, plankState.direction, plankState.isMove)
  }

  override protected val collisionOffset: Float = config.getPlankCW
  override protected val width: Float = config.getPlankWidthByLevel(level)
  override protected val height: Float = config.getPlankHeight

  def getPlankState: PlankState = {
    PlankState(pId, level, position, direction, isMove)
  }

  //0: left，1：right
  def setMoveDirection(d: Int): Unit = {
    isMove = 1
    direction = if (d == 0) DirectionType.left.toFloat else DirectionType.right
  }

  def stopMoving(): Unit = {
    isMove = 0
  }

  //TODO 板子的运动函数、吃到道具
  def move(boundary: Point, quadTree: QuadTree)(implicit orbsConfig: OrbsConfig): Unit = {
    if (isMove == 1) {
      val oldPosition = this.position
      val moveDistance = orbsConfig.getPlankMoveDistanceByFrame.rotate(direction)
      val horizontalDistance = moveDistance.copy(y = 0)
      val verticalDistance = moveDistance.copy(x = 0)
      List(horizontalDistance, verticalDistance).foreach { d =>
        if (d.x != 0 || d.y != 0) {
          this.position = position + d
          val movedRec = this.getObjectRect()
          if (movedRec.topLeft > Point(0, 0) && movedRec.downRight < boundary) {
            quadTree.updateObject(this)
          }
          if (movedRec.topLeft.x <= 0 || movedRec.downRight.x >= boundary.x) {
            if (movedRec.topLeft.x <= 0) this.position = Point(width / 2, oldPosition.y)
            if (movedRec.downRight.x >= boundary.x) this.position = Point(boundary.x - width / 2, oldPosition.y)
            quadTree.updateObject(this)
          }
        }
      }
    }
  }



}
