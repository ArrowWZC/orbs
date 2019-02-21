package org.seekloud.orbs.shared.ptcl.model

import scala.collection.mutable

/**
  * User: TangYaruo
  * Date: 2018/11/14
  * Time: 14:34
  */
object Constants {

  object DirectionType {
    final val right: Float = 0
    final val left = math.Pi
  }

  object GameState {
    val firstCome = 1
    val wait4Opponent = 2
    val loadingPlay = 3
    val play = 4
    val stop = 5
    val relive = 6
  }

  object Canvas {
    val width: Float = 350
    val height: Float = 500
  }

  object reflector {
    val vertical = 0
    val horizontal = 1
  }

  object GameInfo {
    val linking = "请稍等，正在连接服务器..."
    val waitOpJoin = "等待对手加入..."
    val opNotIn = "暂时无人加入。。。"
    val opponentLeft = "您的对手已离开！"
  }

  //TODO 普及
  object Boo {
    val isFalse: Byte = 0
    val isTrue: Byte = 1
  }

  val life: Byte = 3 //玩家生命值

  val preExecuteFrameOffset = 2 //预执行2帧

  val canvasUnitPerLine = Canvas.width //可视窗口每行显示多少个canvasUnit

  val catchBallBuffer = 2


}
