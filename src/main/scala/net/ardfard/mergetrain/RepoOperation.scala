package net.ardfard.mergetrain

import zio.macros.accessible
import zio._
import zio.console.Console

@accessible
object RepoOperation {
  trait Service {
    def getPullRequest(id: String): Task[Unit]
    def mergeBranch(base: String, branch: String): Task[Unit]
    def createStagingBranch(current: String, target: String): Task[String]
    def deleteBranch(name: String): Task[Unit]
  }

  val printed: RLayer[Console, Has[Service]] =
    ZLayer.fromFunction(hasConsole => {
      val console = hasConsole.get
      new Service {
        def getPullRequest(id: String): Task[Unit] =
          console.putStrLn(s"get $id")
        def mergeBranch(base: String, branch: String): Task[Unit] =
          console.putStrLn(s"merge $base $branch")
        def createStagingBranch(current: String, target: String): Task[String] =
          console.putStrLn(
            s"create staging branch from $current to $target"
          ) *> Task.succeed(current ++ target)
        def deleteBranch(name: String): Task[Unit] =
          console.putStrLn(s"delete $name")
      }
    })
}
