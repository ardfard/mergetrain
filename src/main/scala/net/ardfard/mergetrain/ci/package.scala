package net.ardfard.mergetrain

import zio.Has

package object ci {
  type CI = Has[CI.Service]
}
