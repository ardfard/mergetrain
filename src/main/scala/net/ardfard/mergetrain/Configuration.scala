package net.ardfard.mergetrain

import zio.UIO
import zio.macros.accessible
import zio.{ULayer, ZLayer, Has}

@accessible
object Configuration {
  trait Service {
    val config: UIO[Config]
  }

  def apply(maxRunning: Int): ULayer[Has[Service]] =
    ZLayer.succeed(new Service {
      val config = UIO.succeed(Config(maxRunning))
    })

}
