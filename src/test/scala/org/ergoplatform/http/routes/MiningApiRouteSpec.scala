package org.ergoplatform.http.routes

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.ScalatestRouteTest
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.Json
import io.circe.syntax._
import org.ergoplatform.http.api.MiningApiRoute
import org.ergoplatform.mining.AutolykosSolution
import org.ergoplatform.nodeView.validation._
import org.ergoplatform.settings.ErgoSettings
import org.ergoplatform.utils.Stubs
import org.ergoplatform.utils.generators.ErgoCoreGenerators.genECPoint
import org.ergoplatform.{ErgoTreePredef, Pay2SAddress}
import org.ergoplatform.mining.WorkMessage
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.util.Try

class MiningApiRouteSpec
  extends AnyFlatSpec
    with Matchers
    with ScalatestRouteTest
    with Stubs
    with FailFastCirceSupport {

  import org.ergoplatform.utils.ErgoNodeTestConstants._
  import org.ergoplatform.utils.generators.ErgoCoreGenerators._

  private implicit val ec = system.dispatcher

  private val testBackend: ValidationBackend = new ValidationBackend {
    override val backendId: String = "test"
    override def validateTransaction(tx: org.ergoplatform.modifiers.mempool.ErgoTransaction) =
      scala.concurrent.Future.successful(ValidationSuccess(org.ergoplatform.modifiers.mempool.UnconfirmedTransaction(tx, None), None))
    override def getInputContext(boxIds: Seq[org.ergoplatform.ErgoBox.BoxId], height: Int) =
      scala.concurrent.Future.successful(InputContext(Map.empty, height))
    override def submitTransaction(tx: org.ergoplatform.modifiers.mempool.ErgoTransaction) =
      scala.concurrent.Future.successful(SubmitAccepted)
    override def buildBlockTemplate(params: MiningParams) =
      scala.concurrent.Future.successful(BlockTemplate(None, Seq.empty, 0, backendId, Some(WorkMessage(Array.emptyByteArray, BigInt(1), Some(0), pk, None))))
    override def status: ValidationStatus = ValidationStatus(backendId, ValidationHealth.Healthy)
  }

  val prefix = "/mining"

  val localSetting: ErgoSettings = settings.copy(nodeSettings = settings.nodeSettings.copy(useExternalMiner = true))
  val route: Route = MiningApiRoute(minerRef, localSetting, testBackend).route

  val solution = AutolykosSolution(genECPoint.sample.get, genECPoint.sample.get, Array.fill(32)(9: Byte), BigInt(0))

  it should "return requested candidate" in {
    Get(prefix + "/candidate") ~> route ~> check {
      status shouldBe StatusCodes.OK
      Try(responseAs[Json]) shouldBe 'success
    }
  }

  it should "process external solution" in {
    Post(prefix + "/solution", solution.asJson) ~> route ~> check {
      status shouldBe StatusCodes.OK
    }
  }

  it should "display miner pk" in {
    Get(prefix + "/rewardAddress") ~> route ~> check {
      status shouldBe StatusCodes.OK
      val script = ErgoTreePredef.rewardOutputScript(settings.chainSettings.monetary.minerRewardDelay, pk)
      val addressStr = Pay2SAddress(script)(settings.addressEncoder).toString()
      responseAs[Json].hcursor.downField("rewardAddress").as[String] shouldEqual Right(addressStr)
    }
  }

}
