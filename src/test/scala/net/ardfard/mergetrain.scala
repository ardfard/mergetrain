import zio.test._
import zio.test.mock._
import Assertion._
import zio.{ULayer}
import net.ardfard.mergetrain._
import ci._
import queue._

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
        val activePipelines = Seq(
          (
            Pipeline("1", "test1", Pipeline.Completed(Pipeline.Success)),
            PullRequest("1", "test1")
          ),
          (
            Pipeline("2", "test2", Pipeline.Completed(Pipeline.Success)),
            PullRequest("2", "test2")
          ),
          (
            Pipeline("3", "test3", Pipeline.Completed(Pipeline.Failed)),
            PullRequest("3", "test3")
          ),
          (Pipeline("4", "test4", Pipeline.Running), PullRequest("4", "test4"))
        )
        assert(WorldView(activePipelines).isSkipAct)(equalTo(false))
      },
      testM("remove from queue and add new pr to pipeline") {
        val env: ULayer[CI with RepoOperation] =
          (CIMock.CancelPipeline(equalTo("1")) ++
            CIMock.CancelPipeline(equalTo("2")) ++
            RepoOpsMock
              .MergeBranch(
                equalTo(
                  "master",
                  "test1"
                )
              ))

        val activePipelines = Seq(
          (
            Pipeline("1", "test1", Pipeline.Running),
            PullRequest("1", "test1")
          ),
          (
            Pipeline("2", "test2", Pipeline.Completed(Pipeline.Success)),
            PullRequest("2", "test2")
          )
          // (
          //   Pipeline("3", "test3", Pipeline.Completed(Pipeline.Failed)),
          //   PullRequest("3", "test3")
          // ),
          // (Pipeline("4", "test4", Pipeline.Running), PullRequest("4", "test4"))
        )

        val expectedWorld = WorldView(
          Seq(
            (
              Pipeline("4", "test4", Pipeline.Running),
              PullRequest("4", "test4")
            )
          )
        )
        for (
          newWorld <-
            act(WorldView(activePipelines)).provideLayer(env ++ inMemory)
        )
          yield (assert(newWorld)(equalTo(expectedWorld)))

      }
    )
}
