package org.ergoplatform.nodeView.validation

import akka.actor.ActorRef
import akka.util.Timeout
import org.ergoplatform.settings.{ErgoSettings, ExecutionMode}

import scala.concurrent.ExecutionContext

object ValidationBackendProvider {

  def apply(
    settings: ErgoSettings,
    readersHolder: ActorRef,
    client: Option[ValidationCoreClient] = None
  )(implicit ec: ExecutionContext, timeout: Timeout): ValidationBackend = {
    settings.nodeSettings.executionMode match {
      case ExecutionMode.Thin =>
        new RemoteValidationBackend(settings.nodeSettings.validationEndpoint, settings, client)
      case ExecutionMode.Full =>
        new LocalValidationBackend(readersHolder, settings)
    }
  }
}
