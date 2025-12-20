package org.ergoplatform.settings

import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ValueReader

sealed trait WalletMode

object WalletMode {
  case object Delegated extends WalletMode
  case object Filtered  extends WalletMode
  case object Full      extends WalletMode

  def fromString(value: String): Option[WalletMode] = value.toLowerCase match {
    case "delegated" => Some(Delegated)
    case "filtered"  => Some(Filtered)
    case "full"      => Some(Full)
    case _           => None
  }

  implicit val walletModeReader: ValueReader[WalletMode] = { (cfg, path) =>
    val raw = cfg.as[String](path)
    fromString(raw).getOrElse(
      throw new IllegalArgumentException(s"Unsupported wallet mode '$raw', expected one of: delegated, filtered, full")
    )
  }

  implicit val walletModeOptionReader: ValueReader[Option[WalletMode]] = { (cfg, path) =>
    cfg.getAs[String](path).flatMap(fromString)
  }
}
