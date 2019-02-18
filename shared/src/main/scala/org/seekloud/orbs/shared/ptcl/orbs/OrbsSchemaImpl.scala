package org.seekloud.orbs.shared.ptcl.orbs

import org.seekloud.orbs.shared.ptcl.component.{Ball, Brick, Plank}
import org.seekloud.orbs.shared.ptcl.config.OrbsConfig
import org.seekloud.orbs.shared.ptcl.protocol.OrbsProtocol.{GameEvent, UserActionEvent}

import scala.collection.mutable


/**
  * User: TangYaruo
  * Date: 2019/2/15
  * Time: 12:29
  */
class OrbsSchemaImpl(
  override val config: OrbsConfig,
  myId: String,
  myName: String,
  opId: Option[String],
  opName: Option[String]
) extends OrbsSchema with EsRecover {

  import scala.language.implicitConversions

  override def debug(msg: String): Unit = println(msg)

  override def info(msg: String): Unit = println(msg)

  private val esRecoverSupport: Boolean = true

  private val uncheckedActionMap = mutable.HashMap[Byte, Int]() //serinum -> frame

  protected var orbsSchemaStateOpt: Option[OrbsSchemaState] = None

  protected var waitSyncData: Boolean = true

  private val preExecuteFrameOffset = org.seekloud.orbs.shared.ptcl.model.Constants.preExecuteFrameOffset

  private var justSyncFrame = 0

  def receiveGameEvent(e: GameEvent) = {
    if (e.frame >= systemFrame) {
      addGameEvent(e)
    } else if (esRecoverSupport) {
      //      println(s"rollback-frame=${e.frame}, curFrame=${this.systemFrame},e=$e")
      rollback4GameEvent(e)
    }
  }

  //接受服务器的用户事件
  def receiveUserEvent(e: UserActionEvent) = {
    val pIdStr = byteId2PlayerId(e.playerId)
    pIdStr match {
      case Right(pId) =>
        if (pId == myId) {
          uncheckedActionMap.get(e.serialNum) match {
            case Some(preFrame) =>
              if (e.frame != preFrame) {
                //            println(s"preFrame=$preFrame eventFrame=${e.frame} curFrame=$systemFrame")
                if (preFrame < e.frame && esRecoverSupport) {
                  if (preFrame >= systemFrame) {
                    removePreEvent(preFrame, e.playerId, e.serialNum)
                    addUserAction(e)
                  } else if (e.frame >= systemFrame) {
                    //preFrame 比 systemFrame小，但事件frame比systemFrame大，删除preFrame历史数据，回滚后加入事件
                    removePreEventHistory(preFrame, e.playerId, e.serialNum)
                    println(s"roll back to $preFrame curFrame $systemFrame because of UserActionEvent $e")
                    addRollBackFrame(preFrame)
                    addUserAction(e)
                  } else {
                    //preFrame 比 systemFrame小，事件frame比systemFrame小，删除preFrame历史数据，加入事件e为历史，回滚
                    removePreEventHistory(preFrame, e.playerId, e.serialNum)
                    addUserActionHistory(e)
                    println(s"roll back to $preFrame curFrame $systemFrame because of UserActionEvent $e")
                    addRollBackFrame(preFrame)
                  }
                }
              }
            case None =>
              if (e.frame >= systemFrame) {
                addUserAction(e)
              } else if (esRecoverSupport) {
                rollback4UserActionEvent(e)
              }
          }
        } else {
          if (e.frame >= systemFrame) {
            addUserAction(e)
          } else if (esRecoverSupport) {
            //        println(s"rollback-frame=${e.frame},curFrame=${this.systemFrame},e=$e")
            rollback4GameEvent(e)
          }
        }
      case Left(_) => // do nothing
    }


  }

  def preExecuteUserEvent(action: UserActionEvent): Option[Int] = {
    addUserAction(action)
    uncheckedActionMap.put(action.serialNum, action.frame)
  }

  protected def handleOrbsSchemaState(orbsSchemaState: OrbsSchemaState, isRollBack: Boolean = false): Unit = {
    val curFrame = systemFrame
    val startTime = System.currentTimeMillis()
    (math.max(curFrame, orbsSchemaState.f - 100) until orbsSchemaState.f).foreach { f =>
      if (systemFrame != f) {
        systemFrame = f
      }
      super.update()
      if (esRecoverSupport) addGameSnapshot(systemFrame, getOrbsSchemaState)
    }
    val endTime = System.currentTimeMillis()
    if (curFrame <= orbsSchemaState.f) {
      println(s"handleOrbsSchemaState update from $curFrame to ${orbsSchemaState.f} use time=${endTime - startTime}")
      justSyncFrame = orbsSchemaState.f
    } else if (!isRollBack) {
      println(s"handleOrbsSchemaState from $curFrame roll back to ${orbsSchemaState.f}.")
      justSyncFrame = orbsSchemaState.f
    }

    systemFrame = orbsSchemaState.f

    quadTree.clear()
    plankMap.clear()
    ballMap.clear()
    brickMap.clear()
    //    println(s"update time: ${System.currentTimeMillis()}")

    orbsSchemaState.plank.foreach { p =>
      if (playerIdMap.contains(p.pId)) {
        val plank = new Plank(config, p)
        quadTree.insert(plank)
        plankMap.put(playerIdMap(p.pId)._1, plank)
      } else {
        debug(s"player [${p.pId}] is missing.")
      }
    }
    orbsSchemaState.ball.foreach { b =>
      if (playerIdMap.contains(b.pId)) {
        val ball = new Ball(config, b)
        quadTree.insert(ball)
        ballMap.put(playerIdMap(b.pId)._1 + "&" + b.bId, ball)
      } else {
        debug(s"player [${b.pId}] is missing.")
      }
    }
    orbsSchemaState.brick.foreach { r =>
      if (playerIdMap.contains(r.pId)) {
        val brick  = new Brick(config, r)
        quadTree.insert(brick)
        brickMap.put(playerIdMap(r.pId)._1 + "&" + r.rId, brick)
      } else {
        debug(s"player [${r.pId}] is missing.")
      }
    }
    waitSyncData = false
  }

  def receiveOrbsSchemaState(orbsSchemaState: OrbsSchemaState): Unit = {
    if (orbsSchemaState.f > systemFrame) {
      orbsSchemaStateOpt = Some(orbsSchemaState)
    } else if (orbsSchemaState.f == systemFrame) {
      info(s"收到同步数据，立即同步，curSystemFrame=$systemFrame, sync orbs schema state frame=${orbsSchemaState.f}")
      orbsSchemaStateOpt = None
      handleOrbsSchemaState(orbsSchemaState)
    } else {
      if (systemFrame - orbsSchemaState.f < 10) {
        info(s"收到滞后数据，立即同步，curSystemFrame=$systemFrame, sync orbs schema state frame=${orbsSchemaState.f}")
        handleOrbsSchemaState(orbsSchemaState)
      } else {
        info(s"收到滞后数据，不同步，curSystemFrame=$systemFrame, sync orbs schema state frame=${orbsSchemaState.f}")
      }
    }

  }


  override def update(): Unit = {
    if (orbsSchemaStateOpt.nonEmpty) {
      val orbsSchemaState = orbsSchemaStateOpt.get
      info(s"逻辑帧同步，curSystemFrame=$systemFrame, sync orbs schema state frame=${orbsSchemaState.f}")
      handleOrbsSchemaState(orbsSchemaState)
      orbsSchemaStateOpt = None
      if (esRecoverSupport) {
        clearEsRecoverData()
        addGameSnapshot(systemFrame, this.getOrbsSchemaState)
      }
    } else {
      //      super.update()
      if (esRecoverSupport) {
        if (rollBackFrame.nonEmpty) {
          rollBackFrame = rollBackFrame.distinct.filterNot(r => r < justSyncFrame || r >= systemFrame).sortWith(_< _)
          rollBackFrame.headOption.foreach(rollback)
          super.update()
        } else {
          super.update()
          addGameSnapshot(systemFrame, this.getOrbsSchemaState)
        }
      } else {
        super.update()
      }
    }
  }

  override protected def clearEventWhenUpdate(): Unit = {
    super.clearEventWhenUpdate()
    if (esRecoverSupport) {
      addEventHistory(systemFrame, gameEventMap.getOrElse(systemFrame, Nil), actionEventMap.getOrElse(systemFrame, Nil))
    }
    gameEventMap -= systemFrame
    actionEventMap -= systemFrame
    systemFrame += 1
  }

  protected def rollbackUpdate(): Unit = {
    super.update()
    if (esRecoverSupport) addGameSnapshot(systemFrame, getOrbsSchemaState)
  }



}
