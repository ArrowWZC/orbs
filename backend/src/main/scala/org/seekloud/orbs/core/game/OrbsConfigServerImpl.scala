package org.seekloud.orbs.core.game


import akka.util.Helpers
import org.seekloud.orbs.shared.ptcl.config._
import org.seekloud.orbs.shared.ptcl.model.Point
import com.typesafe.config.Config
/**
  * User: TangYaruo
  * Date: 2019/2/6
  * Time: 15:16
  */
case class OrbsConfigServerImpl(config: Config) extends OrbsConfig {

  import collection.JavaConverters._
  import Helpers.Requiring

  private[this] val gridBoundaryWidth = config.getInt("orbsGame.gridBoundary.width")
    .requiring(_ > 50,"minimum supported grid boundary width is 100")
  private[this] val gridBoundaryHeight = config.getInt("orbsGame.gridBoundary.height")
    .requiring(_ > 100,"minimum supported grid boundary height is 50")
  private[this] val gridBoundary = GridBoundary(gridBoundaryWidth,gridBoundaryHeight)

  private[this] val gameFameDuration = config.getLong("orbsGame.frameDuration")
    .requiring(t => t >= 1l,"minimum supported game frame duration is 1 ms")


  private[this] val ballRadius = config.getDouble("orbsGame.ball.radius")
    .requiring(_ > 1, "minimum supported ball radius is 1").toFloat
  private[this] val ballSpeedList = config.getDoubleList("orbsGame.ball.speed")
    .requiring(_.size() >= 1, "minimum supported ball speed list size is 1").asScala.map(_.toFloat).toList

  private [this] val ballParams = BallParams(ballRadius, ballSpeedList)

  private[this] val brickMax = config.getInt("orbsGame.brick.max")
    .requiring(_ >= 0, "minimum supported brick max number is 0")
  private[this] val brickXMax = config.getInt("orbsGame.brick.xMax")
    .requiring(_ >= 0, "minimum supported brick x max number is 0")
  private[this] val brickYMax = config.getInt("orbsGame.brick.yMax")
    .requiring(_ >= 0, "minimum supported brick y max number is 0")
  private[this] val brickCW = config.getDouble("orbsGame.brick.collisionWidthOffset")
      .requiring(_ >= 0, "minimum supported brick collisionWidthOffset is 0").toFloat
  private[this] val brickWidth = config.getDouble("orbsGame.brick.width")
    .requiring(_ >= 1, "minimum supported brick width is 1").toFloat
  private[this] val brickHeight = config.getDouble("orbsGame.brick.height")
    .requiring(_ >= 1, "minimum supported brick height is 1").toFloat
  private[this] val brickColorList = config.getStringList("orbsGame.brick.color")
    .requiring(_.size() >= 1, "minimum supported ball speed list size is 1").asScala.toList

  private[this] val brickParams = BrickParams(brickMax, brickXMax, brickYMax, brickCW, brickWidth, brickHeight, brickColorList)

  private[this] val plankHeight = config.getDouble("orbsGame.plank.height")
    .requiring(_ >= 0.5, "minimum supported plank height is 0.5").toFloat
  private[this] val plankWidthList = config.getDoubleList("orbsGame.plank.width")
    .requiring(_.size() >= 1, "minimum supported ball speed list size is 1").asScala.map(_.toFloat).toList
  private[this] val plankSpeed = config.getDouble("orbsGame.plank.speed")
    .requiring(_ >= 10, "minimum supported plank speed is 10").toFloat
  private[this] val plankCW = config.getDouble("orbsGame.plank.collisionWidthOffset")
    .requiring(_ >= 0, "minimum supported plank collisionWidthOffset is 0").toFloat
  private[this] val plankParams = PlankParams(plankCW, plankHeight, plankWidthList, plankSpeed)



  private val orbsGameConfig = OrbsConfigImpl(gridBoundary, gameFameDuration, ballParams, brickParams, plankParams)


  override def getOrbsConfigImpl: OrbsConfigImpl = orbsGameConfig

  override def boundary: Point = orbsGameConfig.boundary

  override def frameDuration: Long = orbsGameConfig.frameDuration

  override def getBallRadius: Float = orbsGameConfig.getBallRadius

  override def getBallSpeedByLevel(l: Byte): Point = orbsGameConfig.getBallSpeedByLevel(l)

  override def getBallMoveDistanceByFrame(l: Byte): Point = orbsGameConfig.getBallMoveDistanceByFrame(l)

  override def getBrickMax: Int = orbsGameConfig.getBrickMax

  override def getBrickXMax: Int = orbsGameConfig.getBrickXMax

  override def getBrickYMax: Int = orbsGameConfig.getBrickYMax

  override def getBrickCW: Float = orbsGameConfig.getBrickCW

  override def getBrickShape: Point = orbsGameConfig.getBrickShape

  override def getBrickColorSize: Int = orbsGameConfig.getBrickColorSize

  override def getBrickColor(r: Int): String = orbsGameConfig.getBrickColor(r)

  override def getPlankCW: Float = orbsGameConfig.getPlankCW

  override def getPlankHeight: Float = orbsGameConfig.getPlankHeight

  override def getPlankWidthByLevel(l: Byte): Float = orbsGameConfig.getPlankWidthByLevel(l)

  override def getPlankSpeed: Point = orbsGameConfig.getPlankSpeed

  override def getPlankMoveDistanceByFrame: Point = orbsGameConfig.getPlankMoveDistanceByFrame




}
