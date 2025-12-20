package org.ergoplatform.nodeView.validation

import org.ergoplatform.ErgoBox.BoxId
import org.ergoplatform.modifiers.mempool.ErgoTransaction

import scala.concurrent.Future

trait ValidationBackend {
  def backendId: String

  def validateTransaction(tx: ErgoTransaction): Future[ValidationResult]

  def getInputContext(boxIds: Seq[BoxId], height: Int): Future[InputContext]

  def submitTransaction(tx: ErgoTransaction): Future[SubmitResult]

  def buildBlockTemplate(params: MiningParams): Future[BlockTemplate]

  def status: ValidationStatus
}
