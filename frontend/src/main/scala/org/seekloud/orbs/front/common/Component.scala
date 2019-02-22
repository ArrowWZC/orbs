package org.seekloud.orbs.front.common

import scala.xml.Elem

/**
  * User: TangYaruo
  * Date: 2019/2/22
  * Time: 21:33
  */
trait Component {

  def render: Elem



}

object Component {
  implicit def component2Element(comp: Component): Elem = comp.render

  val loadingDiv: Elem =
    <div>
      <img src="/esheep/static/img/loading.gif" style="width: 100px"></img>
      <h3>加载中</h3>
    </div>

  val noDataDiv: Elem =
    <div>
      <img src="/esheep/static/img/warn.png"></img>
      <h3>无数据</h3>
    </div>

}
