import net.ardfard.mergetrain._
import zio.test._
import zio.test.Assertion._
import zio.ZIO
import zio.stream.ZStream.Pull
import net.ardfard.mergetrain

object QueueLiveTest {
  import queue._
  val prs = Seq(
    PullRequest("1", "test"),
    PullRequest("2", "test2"),
    PullRequest("3", "test3")
  )
  val live = suite("")(
    testM("When push PRs in sequence, get back PRs the in the correct order") {
      val prs = Seq(PullRequest("1", "test"), PullRequest("2", "test2"))
      val insertPRs = ZIO.foreach(prs)(pr => Queue.push(pr, 0))
      val getPRs = ZIO.foreach(0 to (prs.length - 1))(pos => Queue.getAt(pos))

      assertM(insertPRs *> getPRs)(equalTo(prs))
    },
    testM("After inserted, PR can be removed") {
      val insertPRs = ZIO.foreach(prs)(pr => Queue.push(pr, 0))
      val removePR = Queue.remove(prs(1))
      assertM(insertPRs *> Queue.getAt(1))(equalTo(prs(1))) *>
        assertM(removePR *> Queue.getAt(1))(equalTo(prs(2)))
    }
  )
}
