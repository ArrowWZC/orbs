package org.seekloud.orbs.shared.ptcl.config

import org.seekloud.orbs.shared.ptcl.model.Point


/**
  * User: TangYaruo
  * Date: 2019/2/6
  * Time: 15:12
  */
final case class GridBoundary(width: Int, height: Int) {
  def getBoundary: Point = Point(width, height)
}

final case class BallParams(
  radius: Float,
  speedList: List[Float]
) {

  def getBallSpeedByLevel(l: Byte): Point = {
    Point(speedList(l - 1), 0)
  }
}

final case class BrickParams(
  max: Int,
  xMax: Int,
  yMax: Int,
  collisionWidthOffset: Float,
  width: Float,
  height: Float,
  colorList: List[String]
) {
  def getBrickShape = Point(width, height)

  def getColorSize: Int = colorList.size

  def getColor(r: Int) = colorList(r - 1)
}

final case class PlankParams(
  collisionWidthOffset: Float,
  height: Float,
  widthList: List[Float],
  speed: Float,
) {
  def getWidthByLevel(l: Byte) = widthList(l - 1)
}


trait OrbsConfig {
  def frameDuration: Long

  def boundary: Point

  def getBallRadius: Float

  def getBallSpeedByLevel(l: Byte): Point

  def getBrickMax: Int

  def getBrickXMax: Int

  def getBrickYMax: Int

  def getBrickCW: Float

  def getBrickShape: Point

  def getBrickColorSize: Int

  def getBrickColor(r: Int): String

  def getPlankCW: Float

  def getPlankHeight: Float

  def getPlankWidthByLevel(l: Byte): Float

  def getPlankSpeed: Point

  def getBallMoveDistanceByFrame(l: Byte): Point

  def getPlankMoveDistanceByFrame: Point

  def getOrbsConfigImpl: OrbsConfigImpl
}

case class OrbsConfigImpl (
  gridBoundary: GridBoundary,
  frameDuration: Long,
  ballParams: BallParams,
  brickParams: BrickParams,
  plankParams: PlankParams
) extends OrbsConfig {

  def getOrbsConfigImpl: OrbsConfigImpl = this

  def boundary: Point = gridBoundary.getBoundary

  override def getBallRadius: Float = ballParams.radius

  override def getBallSpeedByLevel(l: Byte): Point = ballParams.getBallSpeedByLevel(l)

  override def getBrickMax: Int = brickParams.max

  override def getBrickXMax: Int = brickParams.xMax

  override def getBrickYMax: Int = brickParams.yMax

  override def getBrickCW: Float = brickParams.collisionWidthOffset

  override def getBrickShape: Point = brickParams.getBrickShape

  override def getBrickColorSize: Int = brickParams.getColorSize

  override def getBrickColor(r: Int): String = brickParams.getColor(r)

  override def getPlankCW: Float = plankParams.collisionWidthOffset

  override def getPlankHeight: Float = plankParams.height

  override def getPlankWidthByLevel(l: Byte): Float = plankParams.getWidthByLevel(l)

  override def getPlankSpeed: Point = Point(plankParams.speed, 0)

  /*球与板的运动*/
  override def getBallMoveDistanceByFrame(l: Byte): Point = getBallSpeedByLevel(l) * frameDuration / 1000

  override def getPlankMoveDistanceByFrame: Point = getPlankSpeed * frameDuration / 1000

}
