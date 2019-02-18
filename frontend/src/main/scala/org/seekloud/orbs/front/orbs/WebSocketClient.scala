package org.seekloud.orbs.front.orbs

import org.seekloud.orbs.front.common.Routes
import org.seekloud.orbs.shared.ptcl.protocol.OrbsProtocol
//import org.seekloud.orbs.front.utils.byteObject.MiddleBufferInJs
import org.seekloud.orbs.shared.ptcl.protocol.OrbsProtocol.{WsMsgFront, WsMsgServer}
import org.scalajs.dom
import org.scalajs.dom.raw._

import scala.scalajs.js.typedarray.ArrayBuffer

import org.seekloud.byteobject.MiddleBufferInJs

class WebSocketClient(
  connectSuccessCallback: Event => Unit,
  connectErrorCallback: Event => Unit,
  messageHandler: WsMsgServer => Unit,
  closeCallback: Event => Unit
) {

  println("WebSocketClient...")

  private var wsSetup = false

  private var websocketStreamOpt: Option[WebSocket] = None

  def getWsState = wsSetup


  def getWebSocketUri(url: String): String = {
    val wsProtocol = if (dom.document.location.protocol == "https:") "wss" else "ws"
    s"$wsProtocol://${dom.document.location.host}$url"
  }

  private val sendBuffer: MiddleBufferInJs = new MiddleBufferInJs(2048)
  import org.seekloud.byteobject.ByteObject._
  import org.seekloud.byteobject.MiddleBufferInJs
  import scala.scalajs.js.typedarray.ArrayBuffer

  def sendMsg(msg: WsMsgFront) = {
    //    import org.seekloud.orbs.front.utils.byteObject.ByteObject._
    websocketStreamOpt.foreach { s =>
      s.send(msg.fillMiddleBuffer(sendBuffer).result())
    }
  }


  def setup(url: String): Unit = {
    println(s"set up ${System.currentTimeMillis()}")

    val webSocketStream = new WebSocket(getWebSocketUri(url))
    websocketStreamOpt = Some(webSocketStream)
    webSocketStream.onopen = { event: Event =>
      wsSetup = true
      connectSuccessCallback(event)
    }
    webSocketStream.onerror = { event: Event =>
      wsSetup = false
      websocketStreamOpt = None
      connectErrorCallback(event)
    }

    webSocketStream.onmessage = { event: MessageEvent =>
      //        println(s"receive msg:${event.data.toString}")
      event.data match {
        case blobMsg: Blob =>
          val fr = new FileReader()
          fr.readAsArrayBuffer(blobMsg)
          fr.onloadend = { _: Event =>
            val buf = fr.result.asInstanceOf[ArrayBuffer]
            messageHandler(wsByteDecode(buf))
          }
        case jsonStringMsg: String =>
          import io.circe.generic.auto._
          import io.circe.parser._
          decode[WsMsgServer](jsonStringMsg) match {
            case Right(data) =>
              messageHandler(data)
            case Left(e) =>
              println(s"ws msg decode error: $e")
          }
        case unknown => println(s"receive unknown msg:$unknown")
      }
    }

    webSocketStream.onclose = { event: Event =>
      wsSetup = false
      websocketStreamOpt = None
      closeCallback(event)
    }

  }

  def closeWs = {
    wsSetup = false
    websocketStreamOpt.foreach(_.close())
    websocketStreamOpt = None
  }

  import org.seekloud.byteobject.ByteObject._

  private def wsByteDecode(a: ArrayBuffer): OrbsProtocol.WsMsgServer = {
    val middleDataInJs = new MiddleBufferInJs(a)
    bytesDecode[OrbsProtocol.WsMsgServer](middleDataInJs) match {
      case Right(r) =>
        r
      case Left(e) =>
        println(e.message)
        OrbsProtocol.DecodeError()
    }
  }


}
