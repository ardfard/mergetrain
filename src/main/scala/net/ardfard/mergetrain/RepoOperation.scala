package net.ardfard.mergetrain

import zio.macros.accessible
import zio._

@accessible
object RepoOperation {
  trait Service {
    def getPullRequest(id: String): Task[Unit]
    def mergeBranch(base: String, branch: String): Task[Unit]
    def createStagingBranch(current: String, target: String): Task[String]
    def deleteBranch(name: String): Task[Unit]
  }
}
