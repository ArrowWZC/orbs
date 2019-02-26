package org.seekloud.orbs.core.game

import java.util.concurrent.atomic.AtomicInteger

import akka.actor.typed.ActorRef
import org.seekloud.orbs.common.AppSettings
import org.slf4j.Logger
import org.seekloud.orbs.core.{RoomActor, UserActor}
import org.seekloud.orbs.shared.ptcl.component._
import org.seekloud.orbs.shared.ptcl.config.OrbsConfig
import org.seekloud.orbs.shared.ptcl.model.{Constants, Point}
import org.seekloud.orbs.shared.ptcl.orbs.OrbsSchema
import org.seekloud.orbs.shared.ptcl.protocol.OrbsProtocol
import org.seekloud.orbs.shared.ptcl.protocol.OrbsProtocol._

import scala.util.Random

/**
  * User: TangYaruo
  * Date: 2019/2/11
  * Time: 20:44
  */
case class OrbsSchemaServerImpl(
  config: OrbsConfig,
  log: Logger,
  roomActorRef: ActorRef[RoomActor.Command],
  dispatch: WsMsgServer => Unit,
) extends OrbsSchema {

  import scala.language.implicitConversions

  init()

  override def debug(msg: String): Unit = log.debug(msg)

  override def info(msg: String): Unit = log.info(msg)

  private val brickIdGenerator = new AtomicInteger(100)

  private val ballIdGenerator = new AtomicInteger(100)

  private var justJoinUser: List[(String, String, Byte, ActorRef[UserActor.Command])] = Nil // playerId, name, byteId, Actor

  private var latestUserJoinFrame: Int = 0

  private var lastEpisodeEndFrame: Int = 0

  def joinGame(playerId: String, name: String, byteId: Byte, userActor: ActorRef[UserActor.Command]): Unit = {
    justJoinUser = (playerId, name, byteId, userActor) :: justJoinUser
  }


  def receiveUserAction(preExecuteUserAction: UserActionEvent): Unit = {
    val f = math.max(preExecuteUserAction.frame, systemFrame)

    val act = preExecuteUserAction match {
      case a: PlankLeftKeyDown => a.copy(frame = f)
      case a: PlankLeftKeyUp => a.copy(frame = f)
      case a: PlankRightKeyDown => a.copy(frame = f)
      case a: PlankRightKeyUp => a.copy(frame = f)
      case a: MouseClickLeft => a.copy(frame = f)
      case a: RestartGame => a.copy(frame = f)
    }

    addUserAction(act)
    dispatch(act)
  }

  def getCurGameSnapshot: OrbsProtocol.OrbsSnapshot = {
    OrbsProtocol.OrbsSnapshot(getOrbsSchemaState)
  }

  def getCurSnapshot: Option[OrbsProtocol.GameSnapshot] = {
    Some(getCurGameSnapshot)
  }

  private def generateBricks(byteId: Byte) = {

    val brickWidth = config.getBrickShape.x
    val brickHeight = config.getBrickShape.y
    val startX = (boundary.x - config.getBrickXMax * brickWidth) / 2.0
    val startY = 5 * brickHeight

    def genPosition(xIndex: Int, yIndex: Int): Point = {
      val x = (startX + (2 * xIndex - 1) * (brickWidth / 2.0)).toFloat
      val y = (startY + (2 * (yIndex + 1) - 1) * (brickHeight / 2.0)).toFloat
      Point(x, y)
    }

    var bricks = List[BrickState]()
    (1 to config.getBrickMax).foreach {
      index =>
        val isNormal = new Random().nextInt(20).toByte
        val xIndex = if (index % config.getBrickXMax != 0) index % config.getBrickXMax else config.getBrickXMax
        val yIndex = if (index % config.getBrickYMax != 0) index / config.getBrickYMax else index / config.getBrickYMax - 1
        val brickPosition = genPosition(xIndex, yIndex)
        //        val random = new Random()
        //        val brickColor = (random.nextInt(5) + 1).toByte
        val brickColor = (yIndex + 1).toByte
        val brickState = BrickState(byteId, brickIdGenerator.getAndIncrement(), isNormal, brickPosition, brickColor)
        //        val brick = new Brick(config, brickState)
        //        brickMap.put(playerId, brick)
        //        quadTree.insert(brick)
        bricks = brickState :: bricks
    }
    bricks
  }

  private def generatePlayerElem(byteId: Byte, playerId: String) = {
    //先生成plank，level默认为1，方向默认为向右，isMove设为false
    //ball 的初始方向为[-pi, 0]之间的随机数，level默认为1
    val random = new Random()
    val plankPosition = Point((boundary.x / 2.0).toFloat, boundary.y - 50)
    val plankState = PlankState(byteId, 2, plankPosition, 0, 0, Constants.life)
    val plank = new Plank(config, plankState)
    plankMap.put(playerId, plank)
    quadTree.insert(plank)

    val ballPosition = Point(plankPosition.x, plankPosition.y - config.getBallRadius - (config.getPlankHeight / 2.0).toFloat)
    val ballDirection = (random.nextFloat() * math.Pi * 0.5 - 3 / 4.0 * math.Pi).toFloat
    val ballState = BallState(byteId, ballIdGenerator.getAndIncrement(), 2, ballPosition, ballDirection, 0, 0, 0)
    val ball = new Ball(config, ballState)
    ballMap.put(playerId + "&" + ballState.bId, ball)
    quadTree.insert(ball)
    (plankState, ballState)
  }

  override protected def handleUserEnterRoomNow(): Unit = {
    justJoinUser.foreach { player =>
      playerIdMap.put(player._3, (player._1, player._2))
      //再生成玩家元素, 发送UserEnterRoom事件给前端
      //再生成环境元素，发送GenerateBrick事件给前端
      println(s"handle ${player._1} enter room.")
      val (plankState, ballState) = generatePlayerElem(player._3, player._1)
      player._4 ! UserActor.JoinRoomSuccess(player._1, player._3, roomActorRef, config.getOrbsConfigImpl, playerIdMap.toList)
      val playerEvent = UserEnterRoom(player._1, player._3, player._2, plankState, ballState, systemFrame + Constants.preExecuteFrameOffset)
      dispatch(playerEvent)

      val bricks = generateBricks(player._3)
      val envEvent = GenerateBrick(systemFrame + Constants.preExecuteFrameOffset, player._3, bricks)
      addGameEvent(envEvent)
      dispatch(envEvent)
      latestUserJoinFrame = systemFrame
    }
    justJoinUser = Nil
  }


  /*需要重写的函数*/
  override protected def attackBrickCallBack(ball: Ball)(brick: Brick): Unit = {
    super.attackBrickCallBack(ball)(brick)
    val event = BrickBeAttacked(brick.pId, brick.rId, systemFrame)
    addGameEvent(event)
    dispatch(event)
  }

  override protected def attackPlankCallBack(ball: Ball)(plank: Plank): Unit = {
    super.attackPlankCallBack(ball)(plank)
    dispatch(ReachPlank)
  }

  override protected def plankMissBallCallBack(ball: Ball)(plank: Plank): Unit = {
    super.plankMissBallCallBack(ball)(plank)
    val random = new Random()
    if (playerIdMap.contains(plank.pId)) {
      val playerId = playerIdMap(plank.pId)._1
      val plankPosition = plankMap(playerId).getPlankState.position
      val ballPosition = Point(plankPosition.x, plankPosition.y - config.getBallRadius - (config.getPlankHeight / 2.0).toFloat)
      val ballDirection = (random.nextFloat() * math.Pi * 0.5 - 3 / 4.0 * math.Pi).toFloat
      val ballState = BallState(ball.pId, ballIdGenerator.getAndIncrement(), 1, ballPosition, ballDirection, 0, 0, 0)
      val event = PlankMissBall(ball.pId, ball.bId, ballState, systemFrame)
      addGameEvent(event)
      dispatch(event)
    } else {
      log.debug(s"playerId ${plank.pId} is missing in playerIdMap.")
    }
  }

  override protected def handleBricksDownEventNow(): Unit = {
    super.handleBricksDownEventNow()
    if (systemFrame - latestBricksDownFrame > brickDownInterval) {
      if (plankMap.size == AppSettings.personLimit) {
        brickMap.values.foreach { brick =>
          brick.brickDown(quadTree)
//          println(s"player ${brick.pId} brick down")
//          val data = getOrbsSchemaState
          dispatch(BrickDown(brickMap.values.map(_.getBrickState).toList))
        }
        latestBricksDownFrame = systemFrame
      }
    }
  }

  override protected def handleEpisodeEndNow(): Unit = {
    //    println(s"handleEpisodeEndNow, frame: $systemFrame")
    // DEBUG 砖块要3帧的时候才生成，用户第1帧加入的，中间2帧无砖块
    super.handleEpisodeEndNow()
    if (systemFrame - latestUserJoinFrame > Constants.preExecuteFrameOffset && episodeWinner.isEmpty) {
      var brickWinner: Option[Plank] = None
      var downLoser: Option[Plank] = None
      var lifeLoser: Option[Plank] = None
      var isEqual: Boolean = false
      plankMap.foreach { plank =>
        val playerBricks = brickMap.filter(_._1.startsWith(plank._1))
        //玩家胜利条件检测
        if (playerBricks.isEmpty) {
          if (brickWinner.isEmpty) {
            isEqual = false
            brickWinner = Some(plank._2)
          } else if (plank._2.ballAvailable > brickWinner.get.ballAvailable) {
            isEqual = false
            brickWinner = Some(plank._2)
          } else if (plank._2.ballAvailable == brickWinner.get.ballAvailable) {
            isEqual = true
            brickWinner = None
          }

        } else {
          //玩家失败条件检测 自己砖块没打完，且触底
          val bottomBrickY = playerBricks.values.map(_.getBrickState.position.y).max
          if (bottomBrickY >= plank._2.getPlankState.position.y - plank._2.getHeight / 2 - 2 * config.getBallRadius) {

            if (downLoser.isEmpty) {
              isEqual = false
              downLoser = Some(plank._2)
            } else if (plank._2.ballAvailable < downLoser.get.ballAvailable) {
              isEqual = false
              downLoser = Some(plank._2)
            } else if (plank._2.ballAvailable == downLoser.get.ballAvailable) {
              isEqual = true
              downLoser = None
            }
          }
        }
        //玩家失败条件检测
        if (plank._2.ballAvailable <= 0) {
          if (lifeLoser.isEmpty) {
            isEqual = false
            lifeLoser = Some(plank._2)
          } else {
            val opBricks = brickMap.filterNot(_._1.startsWith(plank._1))
            if (playerBricks.size > opBricks.size) {
              isEqual = false
              lifeLoser = Some(plank._2)
            } else if (playerBricks.size == opBricks.size) {
              isEqual = true
              lifeLoser = None
            }
          }

        }
      }

      if (systemFrame - lastEpisodeEndFrame > Constants.preExecuteFrameOffset) {
        if (brickWinner.nonEmpty) {
          println(s"byteId ${brickWinner.get.pId} 砖块消光")
          val event = PlayerWin(brickWinner.get.pId, systemFrame + Constants.preExecuteFrameOffset)
          addGameEvent(event)
          dispatch(event)
        } else if (downLoser.nonEmpty) {
          println(s"byteId ${downLoser.get.pId} 砖块触底")

          val winner = playerIdMap.filterNot(_._1 == downLoser.get.pId).headOption
          if (winner.nonEmpty) {
            val event = PlayerWin(winner.get._1, systemFrame + Constants.preExecuteFrameOffset)
            addGameEvent(event)
            dispatch(event)
          }
        } else if (lifeLoser.nonEmpty) {
          println(s"byteId ${lifeLoser.get.pId} 没命")

          val winner = playerIdMap.filterNot(_._1 == lifeLoser.get.pId).headOption
//          println(s"winner: $winner")
//          playerIdMap.foreach(a => s"player: $a")
          if (winner.nonEmpty) {
            val event = PlayerWin(winner.get._1, systemFrame + Constants.preExecuteFrameOffset)
            addGameEvent(event)
            dispatch(event)
          } else {
            println(s"找不到胜利玩家")
          }
        } else if (isEqual) {
          val event = PlayerWin(-1, systemFrame + Constants.preExecuteFrameOffset)
          addGameEvent(event)
          dispatch(event)
        }
        lastEpisodeEndFrame = systemFrame
      }

    }
  }

  override protected def restartCallBack(playerId: Byte): Unit = {
    log.info(s"player-$playerId Restart...")
    super.restartCallBack(playerId)
//    println(s"restartCallBack-$playerId")
    //    val bricks = generateBricks(playerId)
    //    val envEvent = GenerateBrick(systemFrame + Constants.preExecuteFrameOffset, playerId, bricks)
    //    addGameEvent(envEvent)
    //    dispatch(envEvent)
  }

  override protected def episodeEndInit(): Unit = {
    super.episodeEndInit()
    plankMap.foreach { player =>
//      println(s"episodeEndInit-${player._1}")
      plankMap.get(player._1).foreach { p =>
        plankMap.remove(player._1)
        quadTree.remove(p)
      }
      ballMap.filter(_._1.startsWith(player._1)).foreach { b =>
        ballMap.remove(b._1)
        quadTree.remove(b._2)
      }
      val playerInIdMap = playerIdMap.get(player._2.pId)
      if (playerInIdMap.nonEmpty) {
        val (plankState, ballState) = generatePlayerElem(player._2.pId, player._1)
        val playerEvent = UserEnterRoom(player._1, player._2.pId, playerInIdMap.get._2, plankState, ballState, systemFrame + Constants.preExecuteFrameOffset)
        dispatch(playerEvent)
      } else {
        debug(s"episodeEndInit error: player-${player._1} not in playerIdMap.")
      }

      brickMap.foreach { brick =>
        brickMap.remove(brick._1)
        quadTree.remove(brick._2)
      }
//      println(s"end brick size: ${brickMap.size}")
      val bricks = generateBricks(player._2.pId)
      val envEvent = GenerateBrick(systemFrame + Constants.preExecuteFrameOffset, player._2.pId, bricks, Some(1))
      addGameEvent(envEvent)
      dispatch(envEvent)
    }
  }

  override def clearEventWhenUpdate(): Unit = {
    super.clearEventWhenUpdate()
    gameEventMap -= systemFrame
    actionEventMap -= systemFrame
    systemFrame += 1
  }

  private def init(): Unit = {
    clearEventWhenUpdate()
  }


}
