package net.ardfard.mergetrain.ci

import zio._
import zio.macros.accessible
import zio.console._
import zio.clock._
import sttp.client.asynchttpclient.zio.SttpClient
import net.ardfard.mergetrain.Pipeline

@accessible
object CI {
  trait Service {
    def getPipeline(id: String): Task[Pipeline]
    val getRunningPipelines: Task[Set[Pipeline]]
    def cancelPipeline(id: String): Task[Unit]
    def createPipeline(branch: String): Task[Pipeline]
  }
}
