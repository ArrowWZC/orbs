package org.seekloud.orbs.shared.ptcl.orbs

import org.seekloud.orbs.shared.ptcl.component._
import org.seekloud.orbs.shared.ptcl.config.OrbsConfig
import org.seekloud.orbs.shared.ptcl.model.Constants.{reflector, catchBallBuffer, life}
import org.seekloud.orbs.shared.ptcl.model.{Point, Rectangle}
import org.seekloud.orbs.shared.ptcl.protocol.OrbsProtocol._
import org.seekloud.orbs.shared.ptcl.util.QuadTree

import scala.collection.mutable


/**
  * User: TangYaruo
  * Date: 2019/2/6
  * Time: 14:08
  */

case class OrbsSchemaState(
  f: Int,
  ball: List[BallState],
  plank: List[PlankState],
  brick: List[BrickState]
)

trait OrbsSchema {

  import scala.language.implicitConversions

  def debug(msg: String): Unit

  def info(msg: String): Unit

  implicit val config: OrbsConfig

  val boundary: Point = config.boundary

  var systemFrame: Int = 0 //系统帧数

  var needUserMap: Boolean = false

  var episodeWinner: Option[Byte] = None

  var latestBricksDownFrame: Int = 0

  protected val brickDownInterval: Int = 200

  /*元素*/
  val ballMap = mutable.HashMap[String, Ball]() //playerId & bId -> Ball
  val plankMap = mutable.HashMap[String, Plank]() //playerId -> Plank
  val brickMap = mutable.HashMap[String, Brick]() //playerId & rid-> Brick

  /*id管理*/
  val playerIdMap = mutable.HashMap[Byte, (String, String)]() //映射id -> (playerId, name)

  /*事件*/
  val gameEventMap = mutable.HashMap[Int, List[GameEvent]]() // frame -> List[GameEvent]
  val actionEventMap = mutable.HashMap[Int, List[UserActionEvent]]() // frame -> List[UserActionEvent]


  protected val quadTree: QuadTree = new QuadTree(Rectangle(Point(0, 0), boundary))

  protected def byteId2PlayerId(byteId: Byte): Either[String, String] = {
    if (playerIdMap.contains(byteId)) {
      Right(playerIdMap(byteId)._1)
    } else {
      needUserMap = true
      Left("")
    }
  }

  protected def playerId2ByteId(playerId: String): Either[Byte, Byte] = {
    if (playerIdMap.exists(_._2._1 == playerId)) {
      Right(playerIdMap.filter(_._2._1 == playerId).keySet.head)
    } else {
      needUserMap = true
      Left(-1)
    }
  }

  def getOrbsSchemaState: OrbsSchemaState = {
    OrbsSchemaState(
      systemFrame,
      ballMap.values.map(_.getBallState).toList,
      plankMap.values.map(_.getPlankState).toList,
      brickMap.values.map(_.getBrickState).toList
    )
  }

  protected final def handleUserEnterRoomEvent(e: UserEnterRoom): Unit = {
    plankMap.get(e.playerId).foreach { p =>
      plankMap.remove(e.playerId)
      quadTree.remove(p)
    }
    ballMap.filter(_._1.startsWith(e.playerId)).foreach { b =>
      ballMap.remove(b._1)
      quadTree.remove(b._2)
    }
    playerIdMap.put(e.byteId, (e.playerId, e.name))
    val plank = new Plank(config, e.plankState)
    val ball = new Ball(config, e.ballState)
    plankMap.put(e.playerId, plank)
    ballMap.put(e.playerId + "&" + ball.bId, ball)
    quadTree.insert(plank)
    quadTree.insert(ball)
  }

  protected final def handleUserEnterRoomEvent(l: List[UserEnterRoom]): Unit = {
    l foreach handleUserEnterRoomEvent
  }

  //处理本帧加入的用户
  protected def handleUserEnterRoomNow(): Unit = {
    gameEventMap.get(systemFrame).foreach {
      events =>
        handleUserEnterRoomEvent(events.filter(_.isInstanceOf[UserEnterRoom]).map(_.asInstanceOf[UserEnterRoom]).reverse)
    }
  }

  protected final def handleUserLeftRoomEvent(e: UserLeftRoom): Unit = {
    plankMap.filter(_._1 == e.playerId).foreach{ p =>
      plankMap.remove(p._1)
      quadTree.remove(p._2)
    }
    ballMap.filter(_._1.startsWith(e.playerId)).foreach{b =>
      ballMap.remove(b._1)
      quadTree.remove(b._2)
    }
    brickMap.filter(_._1.startsWith(e.playerId)).foreach { r =>
      brickMap.remove(r._1)
      quadTree.remove(r._2)
    }


  }

  protected final def handleUserLeftRoomEvent(l: List[UserLeftRoom]): Unit = {
    l foreach handleUserLeftRoomEvent
  }

  //处理本帧离开的用户
  protected final def handleUserLeftRoomNow(): Unit = {
    gameEventMap.get(systemFrame).foreach { events =>
      handleUserLeftRoomEvent(events.filter(_.isInstanceOf[UserLeftRoom]).map(_.asInstanceOf[UserLeftRoom]).reverse)
    }
  }

  protected final def handleGenerateBrickEvent(e: GenerateBrick): Unit = {
//    println(s"Generate bricks, frame: $systemFrame, brickSize: ${e.brick.size}, curBrickSize: ${brickMap.size}")
    if (e.isRestart.contains(1)) {
      brickMap.filter(_._2.pId == e.playerId).foreach { r =>
        brickMap.remove(r._1)
        quadTree.remove(r._2)
      }
    }
    e.brick.foreach { b =>
      val brick = new Brick(config, b)
      val playerId = byteId2PlayerId(e.playerId)
      playerId match {
        case Right(id) =>
          brickMap.put(id + "&" + brick.rId, brick)
          quadTree.insert(brick)
        case Left(_) =>
          debug(s"handle GenerateBrick [${b.rId}] error since byteId [${e.playerId}] doesn't exist!!!")
      }
    }
  }

  protected final def handleGenerateBrickEvent(l: List[GenerateBrick]): Unit = {
    l foreach handleGenerateBrickEvent
  }

  //处理本帧生成砖块事件
  protected final def handleGenerateBrickNow(): Unit = {
    gameEventMap.get(systemFrame).foreach { events =>
      handleGenerateBrickEvent(events.filter(_.isInstanceOf[GenerateBrick]).map(_.asInstanceOf[GenerateBrick]).reverse)
    }
  }

  protected final def handleBrickBeAttackedEvent(e: BrickBeAttacked): Unit = {
    val playerId = byteId2PlayerId(e.playerId)
    playerId match {
      case Right(pId) =>
        val brick = brickMap.filter(b => b._1.split("&").head == pId && b._2.rId == e.rId).values.headOption
        brickMap.remove(pId + "&" + e.rId)
        brick.foreach(quadTree.remove)
      case Left(_) =>
        debug(s"brick [${e.playerId}] not exist in playerIdMap.")

    }

  }

  protected final def handleBrickBeAttackedEvent(l: List[BrickBeAttacked]): Unit = {
    l foreach handleBrickBeAttackedEvent
  }


  protected final def handleBrickBeAttackedNow(): Unit = {
    gameEventMap.get(systemFrame).foreach { events =>
      handleBrickBeAttackedEvent(events.filter(_.isInstanceOf[BrickBeAttacked]).map(_.asInstanceOf[BrickBeAttacked]).reverse)
    }
  }

  protected final def handlePlankMissBallEvent(e: PlankMissBall): Unit = {
    val playerId = byteId2PlayerId(e.playerId)

    playerId match {
      case Right(pId) =>
        val ball = ballMap.filter(b => b._1.split("&").head == pId && b._2.bId == e.bId).values.headOption
        ballMap.remove(pId + "&" + e.bId)
        ball.foreach(quadTree.remove)
        val newBall = new Ball(config, e.newBall)
        ballMap.put(pId + "&" + newBall.bId, newBall)
        quadTree.insert(newBall)
        plankMap.get(pId).foreach(p => p.ballAvailable = (p.ballAvailable - 1).toByte)
      case Left(_) =>
        debug(s"ball [${e.playerId}] not exist in playerIdMap.")

    }

  }

  protected final def handlePlankMissBallEvent(l: List[PlankMissBall]): Unit = {
    l foreach handlePlankMissBallEvent
  }

  protected final def handlePlankMissBallNow(): Unit = {
    gameEventMap.get(systemFrame).foreach { events =>
      handlePlankMissBallEvent(events.filter(_.isInstanceOf[PlankMissBall]).map(_.asInstanceOf[PlankMissBall]).reverse)
    }
  }

  /*后台重写*/
  protected def plankMissBallCallBack(ball: Ball)(plank: Plank): Unit = {}


  protected def attackPlankCallBack(ball: Ball)(plank: Plank): Unit = {
    if (systemFrame - ball.lastCatchFrame > catchBallBuffer) {
      ball.reflect(reflector.horizontal)
      ball.setCatchFrame(systemFrame)
    }
  }

  /*后台重写*/
  protected def attackBrickCallBack(ball: Ball)(brick: Brick): Unit = {
    ball.reflect(reflector.horizontal)
  }


  protected final def handleBallMoveNow(): Unit = {
    ballMap.foreach { ball =>
      playerId2ByteId(ball._1.split("&").head) match {
        case Right(ballByteId) =>
          if (plankMap.exists(_._1 == ball._1.split("&").head)) {
            if (ball._2.isMissed == 1) {
              val plank = plankMap.get(ball._1.split("&").head)
              plank.foreach(plankMissBallCallBack(ball._2))
            }
            val objects = quadTree.retrieveFilter(ball._2)
            objects.filter(_.isInstanceOf[Plank]).map(_.asInstanceOf[Plank]).filter(_.pId == ballByteId)
              .foreach { p =>
                ball._2.checkAttackObject(p, attackPlankCallBack(ball._2))
              }
            objects.filter(_.isInstanceOf[Brick]).map(_.asInstanceOf[Brick])
              .foreach { brick =>
                brickMap.filter(_._2 == brick).foreach { b =>
                  if (b._1.split("&").head == ball._1.split("&").head) {
                    ball._2.checkAttackObject(brick, attackBrickCallBack(ball._2))
                  }
                }
              }
            ball._2.move(boundary, quadTree, plankMap(ball._1.split("&").head))(config)
          } else {
            debug(s"player [${ball._1}] has no plank, remove ball.")
            ballMap.remove(ball._1)
            quadTree.remove(ball._2)
          }
        case Left(_) => debug(s"ball-${ball._1} move error. missing in playerIdMap")
      }
    }
  }

  protected final def handlePlankMoveNow(): Unit = {
    plankMap.values.foreach(p => p.move(boundary, quadTree))
  }

  /*后台重写，以后台判定为主*/
  protected def handleEpisodeEndNow(): Unit = {}

  protected final def handlePlayerWinEvent(e: PlayerWin): Unit = {
    //除去玩家的所有砖块，保留板子和球
//    println(s"handle ${e.playerId} win!!!")
    episodeWinner = Some(e.playerId)
    episodeEndInit()
  }

  protected final def handlePlayerWinEvent(l: List[PlayerWin]): Unit = {
    l foreach handlePlayerWinEvent
  }

  protected final def handlePlayerWinEventNow(): Unit = {
    gameEventMap.get(systemFrame).foreach { events =>
      handlePlayerWinEvent(events.filter(_.isInstanceOf[PlayerWin]).map(_.asInstanceOf[PlayerWin]).reverse)
    }
  }

  protected def handleBricksDownEventNow(): Unit = {}

  protected def restartCallBack(playerId: Byte): Unit = {}

  protected def episodeEndInit(): Unit = {
    brickMap.foreach { brick =>
      brickMap.remove(brick._1)
      quadTree.remove(brick._2)
    }
  }

  protected final def handleUserActionEvent(actions: List[UserActionEvent]): Unit = {
    /**
      * 用户行为事件
      **/
    actions.sortBy(_.serialNum).foreach { actions =>
      val playerIdStr = byteId2PlayerId(actions.playerId)
      playerIdStr match {
        case Right(pId) =>
          plankMap.get(pId) match {
            case Some(plank) =>
              ballMap.map(r => r._1.split("&").head -> r._2).get(pId) match {
                case Some(ball) =>
                  actions match {
                    case _: PlankLeftKeyDown =>
                      plank.setMoveDirection(0)
                      if (ball.isAttack == 0) ball.setMoveDirection(0)
                    case _: PlankLeftKeyUp =>
                      plank.stopMoving()
                      if (ball.isAttack == 0) ball.stopMoving()
                    case _: PlankRightKeyDown =>
                      plank.setMoveDirection(1)
                      if (ball.isAttack == 0) ball.setMoveDirection(1)
                    case _: PlankRightKeyUp =>
                      plank.stopMoving()
                      if (ball.isAttack == 0) ball.stopMoving()
                    case e: MouseClickLeft =>
                      ball.startAttack(e.d)
                    case e: RestartGame =>
                      episodeWinner = None
                      restartCallBack(e.playerId)

                  }
                case None =>
                  debug(s"handle action cannot find ball [$pId]")
                  actions match {
                    case _: PlankLeftKeyDown =>
                      plank.setMoveDirection(0)
                    case _: PlankLeftKeyUp =>
                      plank.stopMoving()
                    case _: PlankRightKeyDown =>
                      plank.setMoveDirection(1)
                    case _: PlankRightKeyUp =>
                      plank.stopMoving()
                    case _: MouseClickLeft =>
                    case e: RestartGame =>
                      episodeWinner = None
                      restartCallBack(e.playerId)

                  }
              }

            case None =>
              debug(s"handle action cannot find plank [$pId]")
          }
        case Left(_) =>
          debug(s"handle action cannot find byteId [${actions.playerId}]")
      }
    }

  }

  protected def clearEventWhenUpdate(): Unit = {}

  //处理本帧用户行为事件
  final protected def handleUserActionEventNow(): Unit = {
    actionEventMap.get(systemFrame).foreach { actionEvents =>
      handleUserActionEvent(actionEvents.reverse)
    }
  }

  protected final def addUserAction(action: UserActionEvent): Unit = {
    actionEventMap.get(action.frame) match {
      case Some(actionEvents) => actionEventMap.put(action.frame, action :: actionEvents)
      case None => actionEventMap.put(action.frame, List(action))
    }
  }

  protected final def addGameEvent(event: GameEvent): Unit = {
    gameEventMap.get(event.frame) match {
      case Some(events) => gameEventMap.put(event.frame, event :: events)
      case None => gameEventMap.put(event.frame, List(event))
    }
  }

  protected def addGameEvents(frame: Int, events: List[GameEvent], actionEvents: List[UserActionEvent]): Unit = {
    gameEventMap.put(frame, events)
    actionEventMap.put(frame, actionEvents)
  }

  def removePreEvent(frame: Int, playerId: Short, serialNum: Byte): Unit = {
    actionEventMap.get(frame).foreach { actions =>
      actionEventMap.put(frame, actions.filterNot(t => t.playerId == playerId && t.serialNum == serialNum))
    }
  }
  def leftGame(playerId: String, name: String): Unit = {
    val byteId = playerId2ByteId(playerId)
    byteId match {
      case Right(sId) =>
        val event = UserLeftRoom(playerId, sId, name, systemFrame)
        addGameEvent(event)
      case Left(_) => // do nothing
    }
  }

  def update(): Unit = {
    handleUserLeftRoomNow()
    handlePlayerWinEventNow()
    handleEpisodeEndNow()
    handleBricksDownEventNow()
    handlePlankMoveNow()
    handleBallMoveNow()
    handleUserActionEventNow()
    handlePlankMissBallNow()
    handleGenerateBrickNow()
    handleBrickBeAttackedNow()
    handleUserEnterRoomNow()

    quadTree.refresh(quadTree)
    clearEventWhenUpdate()
  }


}
