package org.ergoplatform.nodeView.validation

import org.ergoplatform.ErgoBox.BoxId
import org.ergoplatform.modifiers.mempool.ErgoTransaction
import org.ergoplatform.settings.ErgoSettings
import scorex.util.ScorexLogging

import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.{ExecutionContext, Future}

/**
  * Remote backend placeholder. It is intentionally conservative: in case of any issue with remote
  * validation core it fails closed and reports the backend as unavailable.
  */
class RemoteValidationBackend(
  endpoint: String,
  ergoSettings: ErgoSettings,
  client: Option[ValidationCoreClient] = None
)(implicit ec: ExecutionContext)
  extends ValidationBackend
    with ScorexLogging {

  override val backendId: String = s"remote@$endpoint"

  private val statusRef = new AtomicReference[ValidationStatus](
    ValidationStatus(backendId, ValidationHealth.Unavailable, lastError = Some("Remote validation core not initialized"))
  )

  private def unavailable[A](operation: String): Future[A] =
    client match {
      case Some(_) =>
        val message = s"Validation core client not initialized for $operation (endpoint=$endpoint)"
        log.warn(message)
        statusRef.set(
          statusRef
            .get()
            .copy(health = ValidationHealth.Unavailable, lastError = Some(message), lastChecked = Some(System.currentTimeMillis()))
        )
        Future.failed(new IllegalStateException(message))
      case None =>
        val message = s"Validation core unavailable for $operation (endpoint=$endpoint)"
        log.warn(message)
        statusRef.set(
          statusRef
            .get()
            .copy(health = ValidationHealth.Unavailable, lastError = Some(message), lastChecked = Some(System.currentTimeMillis()))
        )
        Future.failed(new IllegalStateException(message))
    }

  override def validateTransaction(tx: ErgoTransaction): Future[ValidationResult] = {
    val started = System.currentTimeMillis()
    client
      .map(_.validateTransaction(ValidateTransactionRequest(ergoSettings.chainSettings.protocolVersion.toShort, tx)))
      .getOrElse(unavailable("validateTransaction"))
      .map { res =>
        statusRef.set(
          statusRef
            .get()
            .copy(health = ValidationHealth.Healthy, lastLatencyMs = Some(System.currentTimeMillis() - started), lastSuccessAt = Some(System.currentTimeMillis()))
        )
        res
      }
      .recover { case e =>
        val msg = s"Remote validation failed: ${e.getMessage}"
        log.warn(msg, e)
        statusRef.set(statusRef.get().copy(health = ValidationHealth.Unavailable, lastError = Some(msg)))
        ValidationFailure(msg, Some(e))
      }
  }

  override def getInputContext(boxIds: Seq[BoxId], height: Int): Future[InputContext] =
    client
      .map(_.getInputContext(GetInputContextRequest(ergoSettings.chainSettings.protocolVersion.toShort, boxIds, height)))
      .getOrElse(unavailable("getInputContext"))
      .recover { case e =>
        val msg = s"Remote input context failed: ${e.getMessage}"
        statusRef.set(statusRef.get().copy(health = ValidationHealth.Unavailable, lastError = Some(msg)))
        InputContext(Map.empty, height)
      }

  override def submitTransaction(tx: ErgoTransaction): Future[SubmitResult] =
    client
      .map(_.submitTransaction(SubmitTransactionRequest(ergoSettings.chainSettings.protocolVersion.toShort, tx)))
      .getOrElse(unavailable("submitTransaction"))
      .recover { case e =>
        val msg = s"Remote submit failed: ${e.getMessage}"
        statusRef.set(statusRef.get().copy(health = ValidationHealth.Unavailable, lastError = Some(msg)))
        SubmitRejected(msg)
      }

  override def buildBlockTemplate(params: MiningParams): Future[BlockTemplate] =
    client
      .map(_.buildBlockTemplate(BuildBlockTemplateRequest(ergoSettings.chainSettings.protocolVersion.toShort, params)))
      .getOrElse(unavailable("buildBlockTemplate"))
      .recover { case e =>
        val msg = s"Remote block template failed: ${e.getMessage}"
        statusRef.set(statusRef.get().copy(health = ValidationHealth.Unavailable, lastError = Some(msg)))
        BlockTemplate(None, Seq.empty, height = 0, backendId)
      }

  override def status: ValidationStatus = statusRef.get()
}
