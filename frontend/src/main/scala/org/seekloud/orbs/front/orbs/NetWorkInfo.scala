package org.seekloud.orbs.front.orbs

import org.seekloud.orbs.front.utils.Shortcut
import org.seekloud.orbs.shared.ptcl.model.Constants.GameState
import org.seekloud.orbs.shared.ptcl.protocol.OrbsProtocol

/**
  * User: TangYaruo
  * Date: 2018/11/25
  * Time: 17:52
  */
final case class NetworkLatency(latency: Long)

trait NetworkInfo {
  this: GameHolder =>

  private var lastPingTime = System.currentTimeMillis()
  private val PingTimes = 10
  private var latency: Long = 0L
  private var receiveNetworkLatencyList: List[NetworkLatency] = Nil

  def ping(): Unit = {
    val curTime = System.currentTimeMillis()
    if (curTime - lastPingTime > 2000) {
      startPing()
      lastPingTime = curTime
    }
  }

  private def startPing(): Unit = {
    if(gameState == GameState.play) this.sendMsg2Server(OrbsProtocol.PingPackage(System.currentTimeMillis()))
  }

  protected def receivePingPackage(p: OrbsProtocol.PingPackage): Unit = {
    receiveNetworkLatencyList = NetworkLatency(System.currentTimeMillis() - p.sendTime) :: receiveNetworkLatencyList
    if (receiveNetworkLatencyList.size < PingTimes) {
      Shortcut.scheduleOnce(() => startPing(), 10)
    } else {
      latency = receiveNetworkLatencyList.map(_.latency).sum / receiveNetworkLatencyList.size
      receiveNetworkLatencyList = Nil
    }
  }

  protected def getNetworkLatency = latency



}
