package net.ardfard.mergetrain

package net.ardfard.mergetrain

import zio.macros.accessible
import zio._

@accessible
object PRValidation {
  trait Service {
    def validate(pr: PullRequest): Task[Unit] = ???
  }
}
