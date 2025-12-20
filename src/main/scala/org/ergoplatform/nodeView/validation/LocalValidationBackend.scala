package org.ergoplatform.nodeView.validation

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import org.ergoplatform.ErgoBox
import org.ergoplatform.ErgoBox.BoxId
import org.ergoplatform.modifiers.mempool.{ErgoTransaction, UnconfirmedTransaction}
import org.ergoplatform.nodeView.ErgoReadersHolder.{GetReaders, Readers}
import org.ergoplatform.nodeView.mempool.ErgoMemPoolReader
import org.ergoplatform.nodeView.state.{ErgoStateReader, UtxoStateReader}
import org.ergoplatform.settings.ErgoSettings
import scorex.util.ScorexLogging

import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

class LocalValidationBackend(
  readersHolder: ActorRef,
  ergoSettings: ErgoSettings
)(implicit ec: ExecutionContext, timeout: Timeout)
    extends ValidationBackend
    with ScorexLogging {

  override val backendId: String = "local"

  private val statusRef = new AtomicReference[ValidationStatus](
    ValidationStatus(backendId, ValidationHealth.Healthy)
  )

  private def now(): Long = System.currentTimeMillis()

  private def updateStatusSuccess(latencyMs: Long): Unit = {
    statusRef.set(
      statusRef
        .get()
        .copy(
          health = ValidationHealth.Healthy,
          lastChecked = Some(now()),
          lastSuccessAt = Some(now()),
          lastError = None,
          lastLatencyMs = Some(latencyMs)
        )
    )
  }

  private def updateStatusFailure(reason: String): Unit = {
    statusRef.set(
      statusRef
        .get()
        .copy(
          health = ValidationHealth.Unavailable,
          lastChecked = Some(now()),
          lastError = Some(reason)
        )
    )
  }

  private def getReaders: Future[Readers] = (readersHolder ? GetReaders).mapTo[Readers]

  private def resolveBoxes(
    utxo: UtxoStateReader,
    mempool: ErgoMemPoolReader,
    boxIds: Seq[BoxId]
  ): Map[BoxId, ErgoBox] = {
    val reader = utxo.withMempool(mempool)
    boxIds.flatMap(id => reader.boxById(id).map(id -> _)).toMap
  }

  override def validateTransaction(tx: ErgoTransaction): Future[ValidationResult] = {
    val started = now()
    getReaders
      .map {
        case Readers(_, utxo: UtxoStateReader, mempool, _) =>
          val maxTxCost          = ergoSettings.nodeSettings.maxTransactionCost
          val validationContext  = utxo.stateContext.simplifiedUpcoming()
          val validationAttempt  = utxo.withMempool(mempool).validateWithCost(tx, validationContext, maxTxCost, None)
          val timestamp          = now()
          val txBytes            = Some(tx.bytes)
          validationAttempt match {
            case Success(cost) =>
              val utx = new UnconfirmedTransaction(tx, Some(cost), timestamp, timestamp, txBytes, source = None)
              ValidationSuccess(utx, Some(cost))
            case Failure(err) =>
              ValidationFailure(err.getMessage, Some(err))
          }
        case Readers(_, _: ErgoStateReader, _, _) =>
          tx.statelessValidity() match {
            case Success(_) =>
              val timestamp = now()
              ValidationSuccess(new UnconfirmedTransaction(tx, None, timestamp, timestamp, Some(tx.bytes), source = None), None)
            case Failure(err) =>
              ValidationFailure(err.getMessage, Some(err))
          }
      }
      .map { result =>
        updateStatusSuccess(now() - started)
        result
      }
      .recover { case e =>
        val msg = s"Local validation failed: ${e.getMessage}"
        log.warn(msg, e)
        updateStatusFailure(msg)
        ValidationFailure(msg, Some(e))
      }
  }

  override def getInputContext(boxIds: Seq[BoxId], height: Int): Future[InputContext] = {
    val started = now()
    getReaders
      .map {
        case Readers(_, utxo: UtxoStateReader, mempool, _) =>
          val resolved = resolveBoxes(utxo, mempool, boxIds)
          InputContext(resolved, utxo.stateContext.currentHeight)
        case Readers(_, _, _, _) =>
          InputContext(Map.empty, height)
      }
      .map { ctx =>
        updateStatusSuccess(now() - started)
        ctx
      }
      .recover { case e =>
        val msg = s"Unable to resolve input context: ${e.getMessage}"
        log.warn(msg, e)
        updateStatusFailure(msg)
        InputContext(Map.empty, height)
      }
  }

  override def submitTransaction(tx: ErgoTransaction): Future[SubmitResult] = {
    val started = now()
    validateTransaction(tx).map {
      case _: ValidationSuccess =>
        updateStatusSuccess(now() - started)
        SubmitAccepted
      case ValidationFailure(reason, _) =>
        updateStatusFailure(reason)
        SubmitRejected(reason)
    }
  }

  override def buildBlockTemplate(params: MiningParams): Future[BlockTemplate] = {
    val started = now()
    getReaders
      .map { readers =>
        val txs = readers.m.getAll.map(_.transaction).take(params.maxTransactions)
        val height = readers.h.bestHeaderOpt.map(_.height).getOrElse(readers.s.stateContext.currentHeight)
        BlockTemplate(None, txs, height, backendId, workMessage = None)
      }
      .map { tpl =>
        updateStatusSuccess(now() - started)
        tpl
      }
      .recover { case e =>
        val msg = s"Unable to build local block template: ${e.getMessage}"
        log.warn(msg, e)
        updateStatusFailure(msg)
        BlockTemplate(None, Seq.empty, height = 0, backendId)
      }
  }

  override def status: ValidationStatus = statusRef.get()
}
