package org.ergoplatform.nodeView.validation

import org.ergoplatform.ErgoBox
import org.ergoplatform.ErgoBox.BoxId
import org.ergoplatform.modifiers.history.header.Header
import org.ergoplatform.modifiers.mempool.{ErgoTransaction, UnconfirmedTransaction}
import org.ergoplatform.mining.WorkMessage

sealed trait ValidationResult
case class ValidationSuccess(unconfirmed: UnconfirmedTransaction, cost: Option[Long]) extends ValidationResult
case class ValidationFailure(reason: String, throwable: Option[Throwable] = None) extends ValidationResult

case class InputContext(boxes: Map[BoxId, ErgoBox], height: Int)

sealed trait SubmitResult
case object SubmitAccepted extends SubmitResult
case class SubmitRejected(reason: String) extends SubmitResult

case class MiningParams(maxTransactions: Int = Int.MaxValue, txsToInclude: Int = 0)

case class BlockTemplate(
  header: Option[Header],
  transactions: Seq[ErgoTransaction],
  height: Int,
  backend: String,
  workMessage: Option[WorkMessage] = None
)

sealed trait ValidationHealth
object ValidationHealth {
  case object Healthy extends ValidationHealth
  case object Degraded extends ValidationHealth
  case object Unavailable extends ValidationHealth
}

case class ValidationStatus(
  backend: String,
  health: ValidationHealth,
  lastChecked: Option[Long] = None,
  lastSuccessAt: Option[Long] = None,
  lastError: Option[String] = None,
  lastLatencyMs: Option[Long] = None
)
