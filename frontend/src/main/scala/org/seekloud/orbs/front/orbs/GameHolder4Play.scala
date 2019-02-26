package org.seekloud.orbs.front.orbs

import java.util.concurrent.atomic.AtomicInteger

import org.scalajs.dom
import org.scalajs.dom.ext.KeyCode
import org.seekloud.orbs.front.common.Routes
import org.seekloud.orbs.front.utils.{JsFunc, Shortcut}
import org.seekloud.orbs.shared.ptcl.model.Constants.GameState
import org.seekloud.orbs.shared.ptcl.model.Point
import org.seekloud.orbs.shared.ptcl.protocol.OrbsProtocol
import org.seekloud.orbs.shared.ptcl.protocol.OrbsProtocol._

import scala.util.Random

/**
  * User: TangYaruo
  * Date: 2019/2/15
  * Time: 15:51
  */
class GameHolder4Play(name: String, oName: String) extends GameHolder(name, oName) {

  private[this] val actionSerialNumGenerator = new AtomicInteger(0)
  private val preExecuteFrameOffset = org.seekloud.orbs.shared.ptcl.model.Constants.preExecuteFrameOffset

  //游戏启动
  def start(name: String, playerId: Option[String] = None): Unit = {
    println(s"start $name; firstCome $firstCome")
    myName = name
    myCanvas.getCanvas.focus()
    if (firstCome) {
      addActionListenEvent()
      val url = if (playerId.isEmpty) Routes.wsJoinGameUrl(name) else Routes.wsLoginGameUrl(playerId.get, name)
      webSocketClient.setup(url)
      gameLoop()
    }
    else if (webSocketClient.getWsState) {
      //      println("restart...")
      //      reStart()
    } else {
      JsFunc.alert("网络连接失败，请重新刷新")
    }
  }

  def sendInfo(info: String): Unit = {
    val event = ToOpInfo(myByteId, info)
    webSocketClient.sendMsg(event)
  }

  //  def reStart(): Unit = {
  //    webSocketClient.sendMsg(RestartGame())
  //  }

  def getActionSerialNum: Byte = (actionSerialNumGenerator.getAndIncrement() % 127).toByte

  def setIds(playerId: String, name: String, byteId: Byte): Unit = {
    if (playerId == myId) {
      myByteId = byteId
    } else {
      if (opLeft) {
        opLeft = false
        orbsSchemaOpt.foreach(_.opLeft = false)
        gameState = GameState.play
      }
      opId = Some(playerId)
      opName = Some(name)
      if (gameState != GameState.stop) opByteId = Some(byteId) //opByteId用来判断对手是否重启
      orbsSchemaOpt.foreach(_.setOpId(playerId, name))
      if (gameState != GameState.stop) gameState = GameState.play
    }
  }

  def clearOpId() = {
    opId = None
    opName = None
    opByteId = None
    orbsSchemaOpt.foreach(_.clearOpInfo())
  }

  override protected def wsMessageHandler(data: OrbsProtocol.WsMsgServer): Unit = {
    data match {
      case msg: YourInfo =>
        dom.console.log(s"get YourInfo ${msg.id} ${msg.name}")
        myId = msg.id
        myName = msg.name
        myByteId = msg.byteId
        gameConfig = Some(msg.config)
        if (!msg.playerIdMap.forall(_._1 == msg.byteId)) {
          opId = Some(msg.playerIdMap.filterNot(_._1 == msg.byteId).head._2._1)
          opName = Some(msg.playerIdMap.filterNot(_._1 == msg.byteId).head._2._2)
          opByteId = Some(msg.playerIdMap.filterNot(_._1 == msg.byteId).head._1)
        }
        orbsSchemaOpt = Some(OrbsSchemaClientImpl(drawFrame, myCtx, opCtx, msg.config, myId, myName, opId, opName, opLeft, canvasBoundary, canvasUnit))
        if (timer != 0) {
          dom.window.clearInterval(timer)
          orbsSchemaOpt.foreach { orbsSchema =>
            timer = Shortcut.schedule(gameLoop, orbsSchema.config.frameDuration)
            msg.playerIdMap.foreach(p => orbsSchema.playerIdMap.put(p._1, p._2))
          }
        } else {
          orbsSchemaOpt.foreach { orbsSchema =>
            timer = Shortcut.schedule(gameLoop, orbsSchema.config.frameDuration)
            msg.playerIdMap.foreach(p => orbsSchema.playerIdMap.put(p._1, p._2))
          }
        }
        if (opId.isEmpty) gameState = GameState.wait4Opponent else gameState = GameState.play
        //        gameState = GameState.play
        if (nextFrame == 0) nextFrame = dom.window.requestAnimationFrame(gameRender())
        firstCome = false


      case msg: UserMap =>
        orbsSchemaOpt.foreach { orbsSchema =>
          msg.playerIdMap.foreach {
            p =>
              orbsSchema.playerIdMap.put(p._1, p._2)
              if (p._1 != myByteId) {
                opByteId = Some(p._1)
                opId = Some(p._2._1)
                opName = Some(p._2._2)
              }
          }
          orbsSchema.needUserMap = false
        }


      case RestartYourInfo =>
        gameState = GameState.play

      case msg: SchemaSyncState =>
        orbsSchemaOpt.foreach(_.receiveOrbsSchemaState(msg.s))
        justSynced = true

      //      case msg: PlankMissBall =>

      //      case msg: BrickDown =>
      //        orbsSchemaOpt.foreach(_.handleBrickDownEvent(msg))


      case msg: PingPackage =>
        receivePingPackage(msg)

      case ReachPlank =>
        Shortcut.playMusic("bump")

      case msg: UserActionEvent =>
        msg match {
          case e: RestartGame =>
            if (e.playerId == myByteId) {
              //              println(s"收到自己的重启消息！")
              opByteId match {
                case Some(_) => gameState = GameState.play
                case None => gameState = GameState.wait4Opponent
              }
            } else { //收到对手RestartGame消息
              //              println(s"收到对手的重启消息")
              opByteId = Some(e.playerId)
              if (gameState == GameState.wait4Opponent) {
                gameState = GameState.play
              } else {
                gameState = GameState.wait4Relive
              }
            }
          case _ =>
        }
        orbsSchemaOpt.foreach(_.receiveUserEvent(msg))
      case msg: GameEvent =>
        msg match {
          case e: UserEnterRoom =>
            if (orbsSchemaOpt.nonEmpty) {
              orbsSchemaOpt.foreach(_.playerIdMap.put(e.byteId, (e.playerId, e.name)))
              setIds(e.playerId, e.name, e.byteId)
            } else {
              dom.window.setTimeout(() =>
                orbsSchemaOpt.foreach { orbsSchema =>
                  orbsSchema.playerIdMap.put(e.byteId, (e.playerId, e.name))
                  setIds(e.playerId, e.name, e.byteId)
                }, 100)

            }
          case e: UserLeftRoom =>
            orbsSchemaOpt.foreach {
              orbsSchema =>
                orbsSchema.playerIdMap.remove(e.byteId)
                if (e.playerId != myId) {
                  clearOpId()
                  opLeft = true
                  orbsSchema.opLeft = true
                  orbsSchema.episodeWinner = None
                  gameState = GameState.stop
                }
            }

          case e: PlayerWin =>
            //            println(s"player-${e.playerId} win...")
            //            dom.window.setTimeout(() =>
            //              {
            //                gameState = GameState.stop
            //                opByteId = None
            //              }, 500)
            if (e.playerId == myByteId) {
              Shortcut.playMusic("success")
            } else {
              Shortcut.playMusic("lose")
            }
            gameState = GameState.stop
            //            println(s"win后gameState: $gameState")
            opByteId = None

          case e: BrickBeAttacked =>
            orbsSchemaOpt.foreach { orbsSchema =>
              orbsSchema.brickMap.filter(r => r._1 == myId && r._2.rId == e.rId).foreach {
                brick =>
                  brick._2.isNormal match {
                    case 0 =>
                      normalB = Some("板子开始变长")
                      normalBT = 150
                    case 2 =>
                      normalB = Some("板子开始变短")
                      normalBT = 150
                    case 4 =>
                      normalB = Some("球开始加速")
                      normalBT = 150
                    case 6 =>
                      normalB = Some("球开始减速")
                      normalBT = 150
                  }
              }
            }
            Shortcut.playMusic("breakout")

          case _ =>

        }
        if (orbsSchemaOpt.nonEmpty) {
          orbsSchemaOpt.foreach(_.receiveGameEvent(msg))
        } else {
          dom.window.setTimeout(() => orbsSchemaOpt.foreach(_.receiveGameEvent(msg)), 100)
        }

      case event: ToOpInfo =>
        barrage = Some((event.sender, event.info))
        barrageTime = 150

      case x => dom.window.console.log(s"接收到无效消息$x")


    }
  }

  def addActionListenEvent(): Unit = {
    myCanvas.getCanvas.focus()
    myCanvas.getCanvas.onmousedown = { (e: dom.MouseEvent) =>
      orbsSchemaOpt.foreach { orbsSchema =>
        val random = new Random()
        val initDirection = (random.nextFloat() * math.Pi * 0.5 - 3 / 4.0 * math.Pi).toFloat
        if (gameState == GameState.play) {
          val event = MouseClickLeft(myByteId, initDirection, orbsSchema.systemFrame + preExecuteFrameOffset, getActionSerialNum)
          webSocketClient.sendMsg(event)
          orbsSchema.preExecuteUserEvent(event)
        }
      }
    }

    myCanvas.getCanvas.onkeydown = { (e: dom.KeyboardEvent) =>
      orbsSchemaOpt.foreach { orbsSchema =>
        if (e.keyCode == KeyCode.Left) {
          if (gameState == GameState.play) {
            val event = PlankLeftKeyDown(myByteId, orbsSchema.systemFrame + preExecuteFrameOffset, getActionSerialNum)
            webSocketClient.sendMsg(event)
            orbsSchema.preExecuteUserEvent(event)
          }
        } else if (e.keyCode == KeyCode.Right) {
          if (gameState == GameState.play) {
            val event = PlankRightKeyDown(myByteId, orbsSchema.systemFrame + preExecuteFrameOffset, getActionSerialNum)
            webSocketClient.sendMsg(event)
            orbsSchema.preExecuteUserEvent(event)
          }

        } else if (e.keyCode == KeyCode.Space) {
          //TODO 判断重启
          e.preventDefault()
          if (orbsSchema.episodeWinner.nonEmpty && (gameState == GameState.stop || gameState == GameState.wait4Relive)) {
            val event = RestartGame(myByteId, orbsSchema.systemFrame + preExecuteFrameOffset, getActionSerialNum)
            webSocketClient.sendMsg(event)
            orbsSchema.preExecuteUserEvent(event)
          }
        }
      }
    }

    myCanvas.getCanvas.onkeyup = { (e: dom.KeyboardEvent) =>
      orbsSchemaOpt.foreach { orbsSchema =>
        if (e.keyCode == KeyCode.Left) {
          if (gameState == GameState.play) {
            val event = PlankLeftKeyUp(myByteId, orbsSchema.systemFrame + preExecuteFrameOffset, getActionSerialNum)
            webSocketClient.sendMsg(event)
            orbsSchema.preExecuteUserEvent(event)
          }
        } else if (e.keyCode == KeyCode.Right) {
          if (gameState == GameState.play) {
            val event = PlankRightKeyUp(myByteId, orbsSchema.systemFrame + preExecuteFrameOffset, getActionSerialNum)
            webSocketClient.sendMsg(event)
            orbsSchema.preExecuteUserEvent(event)
          }
        }
      }
    }
  }


}
