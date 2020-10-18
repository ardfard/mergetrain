import zio.test._
import zio.test.mock._
import Assertion._
import Expectation.{value}
import zio.{ULayer}
import net.ardfard.mergetrain._
import ci._
import queue._
import zio.ZIO
import zio.test.TestAspect.eventually

@mockable[CI.Service]
object CIMock

@mockable[RepoOperation.Service]
object RepoOpsMock

object MergeTrainSpec extends DefaultRunnableSpec {

  def spec =
    suite("MergeTrainSpec")(
      test(
        "skip act when there are failures but previous pipeline is still running"
      ) {
        val activePipelines = Seq(
          (Pipeline("1", "test1", Pipeline.Running), PullRequest("1", "test1")),
          (Pipeline("2", "test2", Pipeline.Running), PullRequest("2", "test2")),
          (
            Pipeline("3", "test3", Pipeline.Completed(Pipeline.Failed)),
            PullRequest("3", "test3")
          ),
          (Pipeline("4", "test4", Pipeline.Running), PullRequest("4", "test4"))
        )
        val testWorld = WorldView(activePipelines)
        assert(testWorld.isSkipAct)(equalTo(true))
      },
      test("Don't skip when failure exist after previous is completed") {
        import Pipeline._
        val activePipelines = Seq(
          (
            Pipeline("1", "test1", Completed(Success)),
            PullRequest("1", "test1")
          ),
          (
            Pipeline("2", "test2", Completed(Success)),
            PullRequest("2", "test2")
          ),
          (
            Pipeline("3", "test3", Completed(Failed)),
            PullRequest("3", "test3")
          ),
          (Pipeline("4", "test4", Pipeline.Running), PullRequest("4", "test4"))
        )
        assert(WorldView(activePipelines).isSkipAct)(equalTo(false))
      },
      testM("act on world update") {
        import Pipeline._

        val activePipelines = Seq(
          (
            Pipeline("1", "mtest1", Running),
            PullRequest("1", "mtest1")
          ),
          (
            Pipeline("2", "mtest2", Completed(Success)),
            PullRequest("2", "mtest2")
          ),
          (
            Pipeline("3", "mtest3", Completed(Failed)),
            PullRequest("3", "mtest3")
          ),
          (
            Pipeline("4", "mtest4", Pipeline.Running),
            PullRequest("4", "test4")
          ),
          (
            Pipeline("5", "mtest5", Pipeline.Running),
            PullRequest("5", "test5")
          )
        )

        val expectedWorld = WorldView(
          Seq(
            (
              Pipeline("new4", "test4&master", Pipeline.Running),
              PullRequest("4", "test4")
            ),
            (
              Pipeline("new5", "test5&test4&master", Pipeline.Running),
              PullRequest("5", "test5")
            ),
            (
              Pipeline("new6", "test6&test5&test4&master", Pipeline.Running),
              PullRequest("6", "test6")
            )
          )
        )

        val step1 = CIMock.CancelPipeline(equalTo("1")) ||
          RepoOpsMock.MergeBranch(equalTo(("master", "mtest1")))
        val step2 = CIMock.CancelPipeline(equalTo("1")) ||
          RepoOpsMock.MergeBranch(equalTo(("master", "mtest1"))) ||
          RepoOpsMock.MergeBranch(equalTo(("master", "mtest2")))
        val step3 = CIMock.CancelPipeline(equalTo("1")) ||
          RepoOpsMock.MergeBranch(equalTo(("master", "mtest2")))
        val step4 = (CIMock.CancelPipeline(equalTo("4")) ||
          CIMock.CancelPipeline(equalTo("5")) ||
          RepoOpsMock.CreateStagingBranch(
            equalTo(("test4", "master")),
            value("test4&master")
          ) ||
          CIMock.CreatePipeline(
            equalTo("test4&master"),
            value(expectedWorld.runningPipelines(0)._1)
          ) ||
          RepoOpsMock.CreateStagingBranch(
            equalTo(("test5", "test4&master")),
            value("test5&test4&master")
          ) ||
          CIMock.CreatePipeline(
            equalTo("test5&test4&master"),
            value(expectedWorld.runningPipelines(1)._1)
          )).repeats(5 to 5)
        val step5 = RepoOpsMock.CreateStagingBranch(
          equalTo(("test6", "test5&test4&master")),
          value("test6&test5&test4&master")
        ) ++ CIMock.CreatePipeline(
          equalTo("test6&test5&test4&master"),
          value(expectedWorld.runningPipelines(2)._1)
        )
        val mock: ULayer[CI with RepoOperation] =
          step1 ++ step2 ++ step3 ++ step4 ++ step5

        val queuePRS =
          ZIO.foreach(activePipelines)(p => Queue.push(p._2, 0)) *> Queue.push(
            expectedWorld.runningPipelines(2)._2,
            0
          )
        val eff = queuePRS *> act(WorldView(activePipelines))
        val config = Config(5)
        val queueLayer = zio.console.Console.live >+> inMemory

        assertM(
          eff
            .provideLayer(
              (mock ++ queueLayer ++ Configuration(
                5
              ))
            )
        )(
          equalTo(expectedWorld)
        )
      } @@ eventually,
      testM("Test create pipeline") {
        val p = Pipeline("1", "testmaster", Pipeline.Running)
        val env: ULayer[RepoOperation with CI] = (RepoOpsMock
          .CreateStagingBranch(
            equalTo(("test", "master")),
            value("testmaster")
          ) ++
          CIMock.CreatePipeline(equalTo("testmaster"), value(p)))
        val testeff =
          RepoOperation.createStagingBranch("test", "master") >>= (b =>
            CI.createPipeline(b)
          )
        assertM(testeff.provideLayer(env))(equalTo(p))
      },
      testM("Test paralel cancel") {
        val p1 = Pipeline("1", "testmaster", Pipeline.Running)
        val p2 = Pipeline("2", "testmaster2", Pipeline.Running)
        val env: ULayer[CI] = (
          CIMock.CancelPipeline(equalTo(p1.id)) ||
            CIMock.CancelPipeline(equalTo(p2.id))
        ).repeats(0 to 1)

        import zio.console._

        val eff = zio.ZIO.collectAllPar(
          Seq(
            CI.cancelPipeline(p1.id) *> zio.ZIO
              .succeed(p1.id),
            CI.cancelPipeline(p2.id) *> zio.ZIO
              .succeed(p2.id)
          )
        )
        assertM(eff.provideLayer(env))(equalTo(List("1", "2")))
      }
    )
}
