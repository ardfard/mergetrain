package net.ardfard.mergetrain

import zio.test._
import zio.test.Assertion._
import zio.ZIO
import zio.stream.ZStream.Pull
import net.ardfard.mergetrain

object QueueLiveSpec extends DefaultRunnableSpec {
  import queue._
  val prs = Seq(
    PullRequest("1", "test"),
    PullRequest("2", "test2"),
    PullRequest("3", "test3")
  )

  override def spec =
    suite("Live redis")(
      testM(
        "When push PRs in sequence, get back PRs the in the correct order"
      ) {
        val insertPRs = ZIO.foreach(prs)(pr => Queue.push(pr, 0))
        val getPRs = ZIO.foreach(0 to (prs.length - 1))(pos => Queue.getAt(pos))
        val testEff = (insertPRs *> getPRs).provideLayer(queue.Queue.live)

        assertM(testEff)(equalTo(prs))
      },
      testM("After inserted, PR can be removed") {
        val insertPRs = ZIO.foreach(prs)(pr => Queue.push(pr, 0))
        val removePR = Queue.remove(prs(1))
        val testEff1 =
          (insertPRs *> Queue.getAt(1)).provideLayer(queue.Queue.live)
        val testEff2 =
          (removePR *> Queue.getAt(1)) provideLayer (queue.Queue.live)
        assertM(testEff1)(equalTo(prs(1))) *>
          assertM(testEff2)(equalTo(prs(2)))
      }
    )
}
