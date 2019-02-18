package org.seekloud.orbs.shared.ptcl.model

import scala.collection.mutable

/**
  * User: TangYaruo
  * Date: 2018/11/14
  * Time: 14:34
  */
object Constants {

  object DirectionType {
    final val right:Float = 0
    final val left = math.Pi
  }

  object GameState{
    val firstCome = 1
    val play = 2
    val stop = 3
    val loadingPlay = 4
    val relive = 5
  }

  object Canvas {
    val width: Float = 350
    val height: Float = 500
  }

  val preExecuteFrameOffset = 2 //预执行2帧

  val canvasUnitPerLine = Canvas.width //可视窗口每行显示多少个canvasUnit


}
