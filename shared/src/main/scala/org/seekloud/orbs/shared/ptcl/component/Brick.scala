package org.seekloud.orbs.shared.ptcl.component

import org.seekloud.orbs.shared.ptcl.config.OrbsConfig
import org.seekloud.orbs.shared.ptcl.model.Point
import org.seekloud.orbs.shared.ptcl.util.QuadTree

/**
  * User: TangYaruo
  * Date: 2019/2/6
  * Time: 17:04
  */

case class BrickState(pId: Byte, rId: Int, isNormal: Byte, position: Point, color: Byte)

case class Brick(
  config: OrbsConfig,
  override protected var position: Point,
  pId: Byte,
  rId: Int,
  isNormal: Byte, //0：普通砖块，1:道具砖块,
  color: Byte
) extends RectangleObjectOfGame {

  def this(config: OrbsConfig, brickState: BrickState) = {
    this(config, brickState.position, brickState.pId, brickState.rId, brickState.isNormal, brickState.color)
  }

  override protected val collisionOffset: Float = config.getBrickCW
  override protected var width: Float = config.getBrickShape.x
  override protected val height: Float = config.getBrickShape.y

  def getBrickState: BrickState = {
    BrickState(pId, rId, isNormal, position, color)
  }

  def brickDown(quadTree: QuadTree): Unit = {
    this.position = Point(this.position.x, this.position.y + height)
    quadTree.updateObject(this)
  }

  def setPosition(newPosition: Point, quadTree: QuadTree): Unit = {
    this.position = Point(newPosition.x, newPosition.y)
    quadTree.updateObject(this)
  }

}
