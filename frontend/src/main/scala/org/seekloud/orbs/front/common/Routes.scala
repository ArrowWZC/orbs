package org.seekloud.orbs.front.common

/**
  * User: Taoz
  * Date: 2/24/2017
  * Time: 10:59 AM
  */
object Routes {


  val base = "/orbs"

  def wsJoinGameUrl(name:String) = base + s"/game/join?name=$name"

  def imgPath(fileName: String) = base +"/static/img/" + fileName











}
