package org.ergoplatform.nodeView.validation

import org.ergoplatform.ErgoBox.BoxId
import org.ergoplatform.modifiers.mempool.ErgoTransaction

case class ValidateTransactionRequest(protocolVersion: Short, transaction: ErgoTransaction)
case class GetInputContextRequest(protocolVersion: Short, boxIds: Seq[BoxId], height: Int)
case class SubmitTransactionRequest(protocolVersion: Short, transaction: ErgoTransaction)
case class GetMempoolInfoRequest(protocolVersion: Short)
case class BuildBlockTemplateRequest(protocolVersion: Short, params: MiningParams)

trait ValidationCoreClient {
  def validateTransaction(request: ValidateTransactionRequest): scala.concurrent.Future[ValidationResult]
  def getInputContext(request: GetInputContextRequest): scala.concurrent.Future[InputContext]
  def submitTransaction(request: SubmitTransactionRequest): scala.concurrent.Future[SubmitResult]
  def getMempoolInfo(request: GetMempoolInfoRequest): scala.concurrent.Future[ValidationStatus]
  def buildBlockTemplate(request: BuildBlockTemplateRequest): scala.concurrent.Future[BlockTemplate]
}
