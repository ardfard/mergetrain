import java.io.ObjectInputStream
import java.io.ByteArrayInputStream
import java.io.ObjectOutputStream
import java.io.ByteArrayOutputStream
import net.ardfard.mergetrain.PullRequest
import zio.blocking.Blocking
import sttp.model.Header
import sttp.client._
import sttp.client.asynchttpclient.zio._
import sttp.client.SttpBackend
import zio._
import zio.console._
import com.redis._
import net.ardfard.mergetrain.queue
import zio.clock._

val pushQueue = for {
  _ <- queue.Queue.push(PullRequest("6", "test"), 0)
  _ <- queue.Queue.push(PullRequest("2", "test"), 0)
  _ <- queue.Queue.push(PullRequest("1", "test"), 0)
  _ <- queue.Queue.push(PullRequest("bsaasdf", "test"), 0)
  _ <- queue.Queue.push(PullRequest("asdfas", "test"), 0)
  pr <- queue.Queue.getAt(3)
  _ <- queue.Queue.remove(pr)
  newPr <- queue.Queue.getAt(3)

} yield ((pr, newPr))

Runtime.default.unsafeRun(pushQueue.provideCustomLayer(queue.Queue.live))
