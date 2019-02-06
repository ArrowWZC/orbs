package org.seekloud.orbs.front.pages

import org.seekloud.orbs.front.common.Page
import org.seekloud.orbs.front.utils.Shortcut
import org.seekloud.orbs.shared.ptcl.model.Point
import mhtml.Var
import org.scalajs.dom
import org.scalajs.dom.ext.Color
import org.scalajs.dom.html.Canvas
import mhtml._
import scala.xml.Elem

/**
  * Created by Jingyi on 2018/11/9
  */
object OrbsRender extends Page{

  private val canvas = <canvas id ="GameView" tabindex="1"></canvas>


  private val modal = Var(emptyHTML)

  def init() = {

//    val gameHolder = new GameHolder
//    gameHolder.start("test")
  }



  override def render: Elem ={
    Shortcut.scheduleOnce(() =>init(),0)
    <div>
      <div >{modal}</div>
      {canvas}
    </div>
  }



}
