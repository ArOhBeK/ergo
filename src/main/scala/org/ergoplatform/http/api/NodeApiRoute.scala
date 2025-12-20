package org.ergoplatform.http.api

import akka.actor.{ActorRefFactory, ActorSystem}
import akka.http.scaladsl.server.Route
import io.circe.Json
import io.circe.syntax._
import org.ergoplatform.ErgoApp
import org.ergoplatform.ErgoApp.RemoteShutdown
import org.ergoplatform.nodeView.validation.ValidationBackend
import org.ergoplatform.settings.{ErgoSettings, RESTApiSettings}
import scorex.core.api.http.ApiResponse

import scala.concurrent.duration._

case class NodeApiRoute(ergoSettings: ErgoSettings, validationBackend: ValidationBackend)(implicit system: ActorSystem, val context: ActorRefFactory) extends ErgoBaseApiRoute {

  val settings: RESTApiSettings = ergoSettings.scorexSettings.restApi

  override val route: Route = (pathPrefix("node") & withAuth) {
      shutdown ~ modeInfo ~ validationStatus
    }

  private val shutdownDelay = 5.seconds

  private def shutdown: Route = (pathPrefix("shutdown") & post) {
    system.scheduler.scheduleOnce(shutdownDelay)(ErgoApp.shutdownSystem(RemoteShutdown))
    ApiResponse(s"The node will be shut down in $shutdownDelay")
  }

  private def modeInfo: Route = (path("mode") & get) {
    val nodeSettings   = ergoSettings.nodeSettings
    val walletSettings = ergoSettings.walletSettings
    val enforced = Json.obj(
      "verifyTransactions" -> nodeSettings.verifyTransactions.asJson,
      "verifyScripts"      -> nodeSettings.verifyScripts.asJson,
      "stateType"          -> nodeSettings.stateType.toString.asJson
    )

    val payload = Json.obj(
      "executionMode"      -> nodeSettings.executionMode.toString.asJson,
      "walletMode"         -> walletSettings.walletMode.toString.asJson,
      "validationBackend"  -> validationBackend.backendId.asJson,
      "validationEndpoint" -> nodeSettings.validationEndpoint.asJson,
      "enforcedSettings"   -> enforced
    )
    ApiResponse(payload)
  }

  private def validationStatus: Route = (pathPrefix("validation" / "status") & get) {
    val status = validationBackend.status
    val payload = Json.obj(
      "backend"       -> status.backend.asJson,
      "health"        -> status.health.toString.asJson,
      "lastChecked"   -> status.lastChecked.asJson,
      "lastSuccessAt" -> status.lastSuccessAt.asJson,
      "lastError"     -> status.lastError.asJson,
      "lastLatencyMs" -> status.lastLatencyMs.asJson
    )
    ApiResponse(payload)
  }
}
