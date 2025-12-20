package org.ergoplatform.settings

import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ValueReader

sealed trait ExecutionMode {
  def isThin: Boolean = this == ExecutionMode.Thin
}

object ExecutionMode {
  case object Full extends ExecutionMode
  case object Thin extends ExecutionMode

  def fromString(value: String): Option[ExecutionMode] = value.toLowerCase match {
    case "full" => Some(Full)
    case "thin" => Some(Thin)
    case _      => None
  }

  implicit val executionModeReader: ValueReader[ExecutionMode] = { (cfg, path) =>
    val raw = cfg.as[String](path)
    fromString(raw).getOrElse(
      throw new IllegalArgumentException(s"Unsupported execution mode '$raw', expected one of: full, thin")
    )
  }
}
