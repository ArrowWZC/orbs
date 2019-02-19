package org.seekloud.orbs.core.game

import java.util.concurrent.atomic.AtomicInteger

import akka.actor.typed.ActorRef
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

  private def generateBricks(byteId: Byte, playerId: String) = {

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
        val xIndex = if (index % config.getBrickXMax != 0) index % config.getBrickXMax else config.getBrickXMax
        val yIndex = if (index % config.getBrickYMax != 0) index / config.getBrickYMax else index / config.getBrickYMax - 1
        val brickPosition = genPosition(xIndex, yIndex)
//        val random = new Random()
//        val brickColor = (random.nextInt(5) + 1).toByte
        val brickColor = (yIndex + 1).toByte
        val brickState = BrickState(byteId, brickIdGenerator.getAndIncrement(), 0, brickPosition, brickColor)
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
    val plankState = PlankState(byteId, 1, plankPosition, 0, 0)
    val plank = new Plank(config, plankState)
    plankMap.put(playerId, plank)
    quadTree.insert(plank)

    val ballPosition = Point(plankPosition.x, plankPosition.y - config.getBallRadius - (config.getPlankHeight / 2.0).toFloat)
    val ballDirection = (random.nextFloat() * math.Pi * 0.5 - 3 / 4.0 * math.Pi).toFloat
    val ballState = BallState(byteId, ballIdGenerator.getAndIncrement(), 1, ballPosition, ballDirection, 0, 0, 0)
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
      val (plankState, ballState) = generatePlayerElem(player._3, player._1)
      player._4 ! UserActor.JoinRoomSuccess(player._1, player._3, roomActorRef, config.getOrbsConfigImpl, playerIdMap.toList)
      val playerEvent = UserEnterRoom(player._1, player._3, player._2, plankState, ballState, systemFrame + Constants.preExecuteFrameOffset)
      dispatch(playerEvent)

      val bricks = generateBricks(player._3, player._1)
      val envEvent = GenerateBrick(systemFrame + Constants.preExecuteFrameOffset, player._3, bricks)
      addGameEvent(envEvent)
      dispatch(envEvent)
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

  override protected def plankMissBallCallBack(ball: Ball)(plank: Plank): Unit = {
    super.plankMissBallCallBack(ball)(plank)
    val event = PlankMissBall(ball.pId, ball.bId, systemFrame)
    addGameEvent(event)
    dispatch(event)
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
