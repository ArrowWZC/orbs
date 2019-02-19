package org.seekloud.orbs.front.pages

import org.seekloud.orbs.front.common.Page
import org.seekloud.orbs.front.utils.Shortcut
import org.seekloud.orbs.shared.ptcl.model.Point
import mhtml.Var
import org.scalajs.dom
import org.scalajs.dom.ext.Color
import org.scalajs.dom.html.Canvas
import mhtml._
import org.seekloud.orbs.front.orbs.{GameHolder, GameHolder4Play}

import scala.xml.Elem

/**
  * Created by Jingyi on 2018/11/9
  */
class OrbsRender(playerName: String) extends Page {

  private val myCanvasName = "myView"
  private val opCanvasName = "opView"


  private val canvas = <canvas id={myCanvasName} tabindex="1"></canvas>
  private val opCanvas = <canvas id={opCanvasName} tabindex="1"></canvas>

  def init(): Unit = {
    println(s"OrbsRender init...")

    val gameHolder = new GameHolder4Play(myCanvasName, opCanvasName)
    gameHolder.start(playerName)
  }


  override def render: Elem = {
    Shortcut.scheduleOnce(() => init(), 0)
    <div>
      {canvas}
      {opCanvas}
    </div>
  }


}
