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

/**
  * User: TangYaruo
  * Date: 2019/2/15
  * Time: 15:51
  */
class GameHolder4Play(name: String) extends GameHolder(name) {

  private[this] val actionSerialNumGenerator = new AtomicInteger(0)
  private val preExecuteFrameOffset = org.seekloud.orbs.shared.ptcl.model.Constants.preExecuteFrameOffset

  //游戏启动
  def start(name: String): Unit = {
    println(s"start $name; firstCome $firstCome")
    myName = name
    canvas.getCanvas.focus()
    if (firstCome) {
      addActionListenEvent()
      val url = Routes.wsJoinGameUrl(name)
      webSocketClient.setup(url)
      gameLoop()
    }
    else if (webSocketClient.getWsState) {
      println("restart...")
      reStart()
    } else {
      JsFunc.alert("网络连接失败，请重新刷新")
    }
  }

  def reStart(): Unit = {
    webSocketClient.sendMsg(RestartGame)
  }

  def getActionSerialNum: Byte = (actionSerialNumGenerator.getAndIncrement() % 127).toByte

  def setIds(playerId: String, name: String, byteId: Byte): Unit = {
    if (playerId == myId) {
      myByteId = byteId
    } else {
      opId = Some(playerId)
      opName = Some(name)
      opByteId = Some(byteId)
    }

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
        orbsSchemaOpt = Some(OrbsSchemaClientImpl(drawFrame, ctx, msg.config, myId, myName, opId, opName, canvasBoundary, canvasUnit))
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
        gameState = GameState.play
        if(nextFrame == 0) nextFrame = dom.window.requestAnimationFrame(gameRender())
        firstCome = false


      case msg: UserMap =>
        orbsSchemaOpt.foreach { orbsSchema =>
          msg.playerIdMap.foreach(p => orbsSchema.playerIdMap.put(p._1, p._2))
          orbsSchema.needUserMap = false
        }


      case RestartYourInfo =>
        gameState = GameState.play

      case msg: SchemaSyncState =>
        orbsSchemaOpt.foreach(_.receiveOrbsSchemaState(msg.s))
        justSynced = true

//      case msg: PlankMissBall =>


      case msg: PingPackage =>
        receivePingPackage(msg)
      case msg: UserActionEvent =>
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
            orbsSchemaOpt.foreach(thorSchema => thorSchema.playerIdMap.remove(e.byteId))

          case _ =>

        }
        if (orbsSchemaOpt.nonEmpty) {
          orbsSchemaOpt.foreach(_.receiveGameEvent(msg))
        } else {
          dom.window.setTimeout(() => orbsSchemaOpt.foreach(_.receiveGameEvent(msg)), 100)
        }

      case x => dom.window.console.log(s"接收到无效消息$x")


    }
  }

  def addActionListenEvent(): Unit = {
    canvas.getCanvas.focus()
    canvas.getCanvas.onmousedown = { (e: dom.MouseEvent) =>
      orbsSchemaOpt.foreach { orbsSchema =>
        val event = MouseClickLeft(myByteId, orbsSchema.systemFrame + preExecuteFrameOffset, getActionSerialNum)
        webSocketClient.sendMsg(event)
        orbsSchema.preExecuteUserEvent(event)
      }
    }

    canvas.getCanvas.onkeydown = { (e: dom.KeyboardEvent) =>
      orbsSchemaOpt.foreach { orbsSchema =>
        if (e.keyCode == KeyCode.Left) {
          val event = PlankLeftKeyDown(myByteId, orbsSchema.systemFrame + preExecuteFrameOffset, getActionSerialNum)
          webSocketClient.sendMsg(event)
          orbsSchema.preExecuteUserEvent(event)

        } else if (e.keyCode == KeyCode.Right) {
          val event = PlankRightKeyDown(myByteId, orbsSchema.systemFrame + preExecuteFrameOffset, getActionSerialNum)
          webSocketClient.sendMsg(event)
          orbsSchema.preExecuteUserEvent(event)

        } else if (e.keyCode == KeyCode.Space) {
          //TODO 判断重启
        }
      }
    }

    canvas.getCanvas.onkeyup = { (e: dom.KeyboardEvent) =>
      orbsSchemaOpt.foreach { orbsSchema =>
        if (e.keyCode == KeyCode.Left) {
          val event = PlankLeftKeyUp(myByteId, orbsSchema.systemFrame + preExecuteFrameOffset, getActionSerialNum)
          webSocketClient.sendMsg(event)
          orbsSchema.preExecuteUserEvent(event)
        } else if (e.keyCode == KeyCode.Right) {
          val event = PlankRightKeyUp(myByteId, orbsSchema.systemFrame + preExecuteFrameOffset, getActionSerialNum)
          webSocketClient.sendMsg(event)
          orbsSchema.preExecuteUserEvent(event)
        }
      }
    }
  }


}
