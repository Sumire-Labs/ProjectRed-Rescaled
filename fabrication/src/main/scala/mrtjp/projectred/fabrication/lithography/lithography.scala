/*
 * Copyright (c) 2015.
 * Created by MrTJP.
 * All rights reserved.
 */
package mrtjp.projectred.fabrication.lithography

import mrtjp.core.vec.Size

/** Basic-process constants from the pinned 1.21.1 implementation. */
object Lithography:
    val TILE_LENGTH_NM = 64
    val STANDARD_DIE_TILES = 16
    val MAX_DIE_OUTPUT = 64

/** Smallest feature size the basic lithography process can resolve. */
enum ProcessNode(val tileLen:Int):
    case PROCESS_64NM extends ProcessNode(Lithography.TILE_LENGTH_NM)
    def getTileWidth:Int = tileLen
    def getTileHeight:Int = tileLen
    def getTileArea:Int = tileLen * tileLen
    def getDisplayName:String = s"${tileLen}nm"
/**
  * Silicon wafer grade. A rougher wafer is cheaper but carries a higher
  * defect rate.
  *
  * @param waferLen               wafer edge length in nanometers
  * @param standardDieDefectRate  defect rate of one standard die (see
  *                               [[Lithography.STANDARD_DIE_TILES]]) at the
  *                               64nm node
  */
enum WaferType(val waferLen:Int, val standardDieDefectRate:Double, val unlocalizedName:String):

    case ROUGH_WAFER extends WaferType(4096, 0.5, "roughWafer")

    def getWaferWidth:Int = waferLen
    def getWaferHeight:Int = waferLen
    def getWaferArea:Int = waferLen * waferLen

    /**
      * Defect rate per square nanometer. Back-calculated from
      * [[standardDieDefectRate]] so that a standard die comes out with exactly
      * that rate.
      */
    def getDefectRatePerUnitArea:Double =
        standardDieDefectRate / math.pow(Lithography.STANDARD_DIE_TILES * Lithography.TILE_LENGTH_NM, 2)

/**
  * Die size, die count and yield for one design cut from one wafer.
  *
  * Immutable: the high version keeps these as mutable setters to drive its
  * GUI, but nothing here needs that yet, and an immutable value is easier to
  * reason about from the machine.
  */
class YieldCalculator(val designSize:Size, val processNode:ProcessNode, val waferType:WaferType):

    /** Physical footprint of one die, in nanometers. */
    def getDieSize:Size =
        Size(designSize.width * processNode.getTileWidth, designSize.height * processNode.getTileHeight)

    def getWaferSize:Size =
        Size(waferType.getWaferWidth, waferType.getWaferHeight)

    /** How many dies the wafer is cut into, before any defect roll. */
    def getDieCount:Size =
        val die = getDieSize
        if die.width <= 0 || die.height <= 0 then Size.zeroSize
        else getWaferSize.divide(die.width, die.height)

    /**
      * Probability that any single die comes out defective. Grows with die
      * area, so a denser design yields fewer usable parts.
      */
    def getDefectChancePerDie:Double =
        val die = getDieSize
        die.width.toDouble * die.height * waferType.getDefectRatePerUnitArea

    /** Upstream yield estimate; large designs can produce a negative estimate. */
    def getSingleLayerYield:Double =
        1.0 - getDefectChancePerDie

    /** Total dies a run may actually output, see [[Lithography.MAX_DIE_OUTPUT]]. */
    def getCappedDieCount:Int =
        val count = getDieCount
        math.min(count.width * count.height, Lithography.MAX_DIE_OUTPUT)
