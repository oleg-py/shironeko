package com.olegpy.shironeko.internals

import scala.scalajs.LinkingInfo

import slinky.core._
import slinky.readwrite._


object SlinkyHotLoadingWorkaround {
  implicit val srp: StateReaderProvider =
    if (LinkingInfo.developmentMode) Reader.fallback[Any].asInstanceOf[StateReaderProvider] else null
  implicit val swp: StateWriterProvider =
    if (LinkingInfo.developmentMode) Writer.fallback[Any].asInstanceOf[StateWriterProvider] else null
  implicit val prp: PropsReaderProvider =
    if (LinkingInfo.developmentMode) Reader.fallback[Any].asInstanceOf[PropsReaderProvider] else null
  implicit val pwp: PropsWriterProvider =
    if (LinkingInfo.developmentMode) Writer.fallback[Any].asInstanceOf[PropsWriterProvider] else null
}
