package org.seekloud.orbs.shared.ptcl.protocol

import org.seekloud.orbs.shared.ptcl.component.{BallState, BrickState, PlankState}
import org.seekloud.orbs.shared.ptcl.config.OrbsConfigImpl
import org.seekloud.orbs.shared.ptcl.model.Score
import org.seekloud.orbs.shared.ptcl.orbs.OrbsSchemaState


object OrbsProtocol {

  sealed trait GameEvent {
    val frame: Int
  }

  sealed trait UserEvent extends GameEvent

  trait EnvironmentEvent extends GameEvent

  sealed trait UserActionEvent extends UserEvent {
    val playerId: Byte
    val serialNum: Byte
  }

  /*前端*/
  sealed trait WsMsgFrontSource

  case object CompleteMsgFrontServer extends WsMsgFrontSource

  case class FailMsgFrontServer(ex: Exception) extends WsMsgFrontSource

  sealed trait WsMsgFront extends WsMsgFrontSource


  /*后台*/
  sealed trait WsMsgBackSource

  case object CompleteMsgServer extends WsMsgBackSource

  case class FailMsgServer(ex: Exception) extends WsMsgBackSource

  final case class Wrap(ws: Array[Byte]) extends WsMsgBackSource

  sealed trait WsMsgServer extends WsMsgBackSource

  final case class DecodeError() extends WsMsgServer


  /*收发包*/
  final case class PingPackage(sendTime:Long) extends WsMsgServer with WsMsgFront

  /*游戏消息*/

  //用户信息
  final case class UserInfo(playerId: String, name: String) extends WsMsgServer

  final case class YourInfo(config: OrbsConfigImpl, id: String, name: String, byteId: Byte = 0, playerIdMap: List[(Byte, (String, String))] = Nil) extends WsMsgServer

  final case object RestartYourInfo extends WsMsgServer

  final case object UserMapReq extends WsMsgFront

  final case class UserMap(playerIdMap: List[(Byte, (String, String))] = Nil) extends WsMsgServer

  //生成环境元素
  final case class GenerateBrick(override val frame: Int, playerId: Byte, brick: List[BrickState]) extends EnvironmentEvent with WsMsgServer


  /*快照*/
  sealed trait GameSnapshot

  final case class OrbsSnapshot(
    state: OrbsSchemaState
  ) extends GameSnapshot






  final case object RestartGame extends WsMsgFront

  final case class SchemaSyncState(s: OrbsSchemaState) extends WsMsgServer




  //用户事件
  final case class UserEnterRoom(playerId: String, byteId: Byte, name: String, plankState: PlankState, ballState: BallState, override val frame: Int = 0) extends UserEvent with WsMsgServer

  final case class UserLeftRoom(playerId: String, byteId: Byte, name: String, override val frame: Int = 0) extends UserEvent with WsMsgServer

//  final case class BallAttackBrick(playerId: Byte, bId: Int, rId: Int, override val frame: Int = 0) extends UserEvent with WsMsgServer

//  final case class BallLandPlank(playerId: Byte, bId: Int, override val frame: Int = 0) extends UserEvent with WsMsgServer
//
//  final case class BallReachWall(playerId: Byte, bId: Int, override val frame: Int = 0) extends UserEvent with WsMsgServer
//
//  final case class BallReachBottom(playerId: Byte, bid: Int, override val frame: Int = 0) extends UserEvent with WsMsgServer

  final case class BrickBeAttacked(playerId: Byte, rId: Int, override val frame: Int = 0) extends UserEvent with WsMsgServer

  final case class PlankMissBall(playerId: Byte, bId: Int, override val frame: Int = 0) extends UserEvent with WsMsgServer

  final case class PlankLeftKeyDown(playerId: Byte, override val frame: Int, override val serialNum: Byte) extends UserActionEvent with WsMsgFront with WsMsgServer

  final case class PlankLeftKeyUp(playerId: Byte,  override val frame: Int, override val serialNum: Byte) extends UserActionEvent with WsMsgFront with WsMsgServer

  final case class PlankRightKeyDown(playerId: Byte, override val frame: Int, override val serialNum: Byte) extends UserActionEvent with WsMsgFront with WsMsgServer

  final case class PlankRightKeyUp(playerId: Byte, override val frame: Int, override val serialNum: Byte) extends UserActionEvent with WsMsgFront with WsMsgServer

  final case class MouseClickLeft(playerId: Byte, override val frame: Int, override val serialNum: Byte) extends UserActionEvent with WsMsgFront with WsMsgServer


  final case class JoinGameInfo(
    name: String,
    playerId: Option[String] = None
  )

  //TODO 完善消息 游戏胜利，游戏失败，使用道具等



}
