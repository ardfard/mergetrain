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
    def getPipeline(id: String): Task[Option[Pipeline]]
    def cancelPipeline(id: String): Task[Unit]
    def createPipeline(branch: String): Task[Pipeline]
  }

  val printed: RLayer[Console, Has[Service]] =
    ZLayer.fromFunction { console =>
      new Service {
        def getPipeline(id: String): zio.Task[Option[Pipeline]] =
          console.get.putStrLn(s"get ${id}") *> ZIO.succeed(
            Some(Pipeline(id, "rand", Pipeline.Running))
          )
        def cancelPipeline(id: String): zio.Task[Unit] =
          console.get.putStrLn(s"cancel pipeline $id")
        def createPipeline(branch: String): zio.Task[Pipeline] =
          console.get.putStrLn(s"create pipeline $branch") *> ZIO.succeed(
            Pipeline("rand", branch, Pipeline.Running)
          )
      }
    }
}
