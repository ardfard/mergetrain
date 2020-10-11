package net.ardfard.mergetrain.ci

import sttp.client.asynchttpclient.zio.SttpClient
import zio._
import sttp.client._
import sttp.client.circe._
import io.circe._
import io.circe.generic.semiauto._
import shapeless.ops.zipper.Get
import net.ardfard.mergetrain.Pipeline

final case class GetWorkFlowsResponse() {
  def toCIPipelines(): Set[Pipeline] =
    Set(Pipeline("dummy", "branch", Pipeline.Pending))
}

final case class GitHubAction(sttpClient: SttpClient.Service)
    extends CI.Service {

  implicit val GetWorkFlowResponse: Decoder[GetWorkFlowsResponse] =
    deriveDecoder[GetWorkFlowsResponse]

  val getRunningPipelines = {
    val pipelineRequest =
      basicRequest.get(uri"dummy").response(asJson[GetWorkFlowsResponse])
    for {
      resp <- sttpClient.send(pipelineRequest)
      workFlowResp <- ZIO.fromEither(resp.body)
    } yield (workFlowResp.toCIPipelines())
  }

  def cancelPipeline(id: String) = ???
  def createPipeline(branch: String) = ???
  def getPipeline(id: String) = ???
}
