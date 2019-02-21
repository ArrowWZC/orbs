package org.seekloud.orbs.shared.ptcl.component

import org.seekloud.orbs.shared.ptcl.config.OrbsConfig
import org.seekloud.orbs.shared.ptcl.model.Constants.{DirectionType, reflector}
import org.seekloud.orbs.shared.ptcl.model.{Point, Rectangle}
import org.seekloud.orbs.shared.ptcl.util.QuadTree

import scala.util.Random

/**
  * User: TangYaruo
  * Date: 2019/2/6
  * Time: 17:03
  *
  * 目前为基础版本
  * 待完成版本：球的类型、速度、数量
  */

case class BallState(pId: Byte, bId: Int, level: Byte, position: Point, direction: Float, isAttack: Byte, isMove: Byte, isMissed: Byte)

case class Ball(
  config: OrbsConfig,
  override protected var position: Point,
  var direction: Float,
  pId: Byte,
  bId: Int,
  var level: Byte,
  var isAttack: Byte, //0: false, 1: true
  var isMove: Byte, //0: false, 1: true
  var isMissed: Byte, //0: false, 1: true
  var lastCatchFrame: Int = 0
) extends CircleObjectOfGame {

  def this(config: OrbsConfig, ballState: BallState) = {
    this(config, ballState.position, ballState.direction, ballState.pId, ballState.bId, ballState.level, ballState.isAttack, ballState.isMove, ballState.isMissed)
  }

  override val radius: Float = config.getBallRadius

  def getBallState: BallState = {
    BallState(pId, bId, level, position, direction, isAttack, isMove, isMissed)
  }

  def startAttack(d: Float): Unit = {
    val random = new Random()
    this.isMove = 1
    this.isAttack = 1
    //    this.direction = (random.nextFloat() * math.Pi * 0.5 - 3 / 4.0 * math.Pi).toFloat
//    this.direction = (-7 / 20.0 * math.Pi).toFloat
    this.direction = d
  }

  //0: left，1：right
  def setMoveDirection(d: Int): Unit = {
    isMove = 1
    direction = if (d == 0) DirectionType.left.toFloat else DirectionType.right
  }

  def stopMoving(): Unit = {
    isMove = 0
  }

  def setCatchFrame(frame: Int): Unit = {
    lastCatchFrame = frame
  }


  def checkAttackObject[T <: ObjectOfGame](o: T, attackCallBack: T => Unit): Unit = {
    if (this.isIntersects(o)) {
      attackCallBack(o)
    }
  }

  /**
    * @param tp 反射面的类型 0：垂直，1：水平
    **/
  def reflect(tp: Int): Unit = {
//    println(s"ball-$bId reflect original: $direction")
    tp match {
      case 0 =>
        if (this.direction > 0) {
          this.direction = (-this.direction + math.Pi).toFloat
        } else {
          this.direction = (-this.direction - math.Pi).toFloat
        }
      case 1 =>
        this.direction = -this.direction
      case _ =>
    }
//    println(s"ball-$bId reflect current:$direction")
  }

  def move(boundary: Point, quadTree: QuadTree, plank: Plank)(implicit orbsConfig: OrbsConfig): Unit = {
    //isAttack的时候速度是球本身的速度，否则按照板子的速度
    if (isMove == 1) {
      if (isAttack == 1) { //球不在板上
//        println(s"ball-$bId direction: ${direction / math.Pi}Pi")
//        println(s"ball-$bId position: $position")
        var isVReflect = false
        var isHReflect = false
        val moveDistance = orbsConfig.getBallMoveDistanceByFrame(level).rotate(direction)
        val horizontalDistance = moveDistance.copy(y = 0)
        val verticalDistance = moveDistance.copy(x = 0)
        List(horizontalDistance, verticalDistance).foreach { d =>
          if (d.x != 0 || d.y != 0) {
            this.position = this.position + d
            val movedRec = Rectangle(this.position - Point(radius, radius), this.position + Point(radius, radius))
            if (movedRec.topLeft > Point(0, 0) && movedRec.downRight < boundary) {
              quadTree.updateObject(this)
            }
            if (d.x != 0) {
              //触碰到左右边界
              if (movedRec.topLeft.x <= 0 || movedRec.downRight.x >= boundary.x) {
                if (!isVReflect) {
                  reflect(reflector.vertical)
                  isVReflect = true
                }
                if (movedRec.topLeft.x <= 0) this.position = Point(radius, this.position.y)
                if (movedRec.downRight.x >= boundary.x) this.position = Point(boundary.x - radius, this.position.y)
                quadTree.updateObject(this)
              }
            }
            if (d.y != 0) {
              //触碰到上边界
              if (movedRec.topLeft.y <= 0) {
                if (!isHReflect) {
                  reflect(reflector.horizontal)
                  isHReflect = true
                }
                this.position = Point(this.position.x, radius)
                quadTree.updateObject(this)

              }
              //从下边界消失
              if (movedRec.topLeft.y >= boundary.y) {
                this.isMissed = 1
              }
            }
          }
        }

      } else { //球在板上
        if (plank.isMove == 1) {
          val moveDistance = orbsConfig.getPlankMoveDistanceByFrame.rotate(direction)
          val horizontalDistance = moveDistance.copy(y = 0)
          val verticalDistance = moveDistance.copy(x = 0)
          List(horizontalDistance, verticalDistance).foreach { d =>
            if (d.x != 0 || d.y != 0) {
              this.position = this.position + d
              val movedRec = Rectangle(this.position - Point(radius, radius), this.position + Point(radius, radius))
              if (movedRec.topLeft > Point(0, 0) && movedRec.downRight < boundary) {
                quadTree.updateObject(this)
              }
              //板子触碰左边界
              if (this.position.x - plank.getWidth * 0.5 <= 0) {
                this.position = Point(plank.getWidth * 0.5.toFloat, this.position.y)
                quadTree.updateObject(this)
              }
              //板子触碰右边界
              if (this.position.x + plank.getWidth * 0.5 >= boundary.x) {
                this.position = Point(boundary.x - plank.getWidth * 0.5.toFloat, this.position.y)
                quadTree.updateObject(this)
              }

            }
          }
        }
      }

    }
  }


}
