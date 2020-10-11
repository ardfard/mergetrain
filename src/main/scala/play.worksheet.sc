import zio.blocking.Blocking
import sttp.model.Header
import sttp.client._
import sttp.client.asynchttpclient.zio._
import sttp.client.SttpBackend
import zio._

val req = basicRequest
  .header(Header("x-auth", "somerandomstring"))
  .get(uri"https://httpbin.org/get")

val send: ZIO[SttpClient, Throwable, Response[
  Either[String, String]
]] =
  SttpClient.send(req)

Runtime.default.unsafeRun {
  for {
    resp <- send.provideLayer(AsyncHttpClientZioBackend.layer())
    _ <- console.putStrLn(resp.body.toString())

  } yield (resp)
}

import zio.console._
import zio.clock._
import zio.duration._
