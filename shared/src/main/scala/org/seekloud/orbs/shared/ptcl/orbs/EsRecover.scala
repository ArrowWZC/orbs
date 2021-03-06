package org.seekloud.orbs.shared.ptcl.orbs

import org.seekloud.orbs.shared.ptcl.protocol.OrbsProtocol.{GameEvent, UserActionEvent}

import scala.collection.mutable

/**
  * User: TangYaruo
  * Date: 2018/11/12
  * Time: 16:04
  */
trait EsRecover {
  this: OrbsSchemaImpl =>

  private val gameEventHistoryMap = mutable.HashMap[Int, List[GameEvent]]()
  private val actionEventHistoryMap = mutable.HashMap[Int, List[UserActionEvent]]()
  val gameSnapshotMap = mutable.HashMap[Int, OrbsSchemaState]()
  var rollBackFrame: List[Int] = Nil

  def addEventHistory(frame: Int, gameEvents: List[GameEvent], actionEvents: List[UserActionEvent]): Unit = {
    gameEventHistoryMap.put(frame, gameEvents)
    actionEventHistoryMap.put(frame, actionEvents)
  }

  def addGameSnapshot(frame: Int, gameState: OrbsSchemaState) = {
    gameSnapshotMap.put(frame, gameState)
  }

  def clearEsRecoverData(): Unit = {
    gameEventHistoryMap.clear()
    actionEventHistoryMap.clear()
    gameSnapshotMap.clear()
  }

  def rollback(frame: Int) = {
    try {
      require(frame < this.systemFrame)
      removeRollBackFrame(frame)

      gameSnapshotMap.get(frame) match {
        case Some(thorSchemaState) =>
          val startTime = System.currentTimeMillis()
          val curFrame = this.systemFrame
          handleOrbsSchemaState(thorSchemaState, isRollBack = true)
          //同步所有数据
//          removeKillInfoByRollback(frame)
          (frame until curFrame).foreach { f =>
            this.addGameEvents(f, gameEventHistoryMap.getOrElse(f, Nil), actionEventHistoryMap.getOrElse(f, Nil))
            this.rollbackUpdate()
          }
          val endTime = System.currentTimeMillis()
          println(s"roll back to frame=$frame, nowFrame=$curFrame use Time:${endTime - startTime}")
        case None =>
          println(s"there are not snapshot frame=$frame")
          this.rollbackUpdate()
      }
    } catch {
      case _: Exception =>
        println(s"rollback exception frame: $frame, curFrame: ${this.systemFrame}")
    }

  }

  def rollback4GameEvent(e: GameEvent) = {
    println(s"roll back to frame=${e.frame},nowFrame=$systemFrame because GameEvent:$e")
    gameEventHistoryMap.put(e.frame, e :: gameEventHistoryMap.getOrElse(e.frame, Nil))
//    rollback(e.frame)
    addRollBackFrame(e.frame)

  }

  def rollback4UserActionEvent(e: UserActionEvent) = {
    println(s"roll back to frame=${e.frame},nowFrame=$systemFrame because UserActionEvent:$e")
    actionEventHistoryMap.put(e.frame, e :: actionEventHistoryMap.getOrElse(e.frame, Nil))
//    rollback(e.frame)
    addRollBackFrame(e.frame)
  }

  def addRollBackFrame(frame: Int) = {
    if (!rollBackFrame.contains(frame)) rollBackFrame = frame :: rollBackFrame
  }

  def removeRollBackFrame(frame: Int) = {
    if (rollBackFrame.contains(frame)) rollBackFrame = rollBackFrame.filterNot(_ == frame)
  }

  def removePreEventHistory(frame: Int, playerId: Short, serialNum: Byte): Unit = {
    actionEventHistoryMap.get(frame).foreach { actions =>
      actionEventHistoryMap.put(frame, actions.filterNot(t => t.playerId == playerId && t.serialNum == serialNum))
    }
  }

  def addUserActionHistory(e: UserActionEvent) = {
    actionEventHistoryMap.put(e.frame, e :: actionEventHistoryMap.getOrElse(e.frame, Nil))
  }


}
