package interpreter
import com.om.mxs.client.japi.{Attribute, Constants, MatrixStore, MxsObject, SearchTerm, Vault}
import org.slf4j.LoggerFactory

import scala.concurrent.duration._

/**
 * the "session" class holds the data for a given interactive session - i.e. any active connections,
 * settings, etc.
 * @param activeConnection
 * @param activeVaultId
 * @param lastException
 * @param asyncTimeout
 * @param itemsPerPage
 * @param showHeaders
 */
case class Session(
                    activeConnection: Option[MatrixStore],
                    activeVaultId: Option[String],
                    lastException: Option[Throwable],
                    asyncTimeout: FiniteDuration,
                    itemsPerPage: Int,
                    showHeaders: Boolean,
                    fields: Seq[String]
                  ) {
  private val logger = LoggerFactory.getLogger(getClass)

  def cleanup() = {
    if(activeConnection.isDefined) {
      try {
        logger.info("Terminating MatrixStore connection...")
        activeConnection.get.closeConnection()
        activeConnection.get.dispose()
      } catch {
        case err:Throwable=>
          logger.error(s"Could not clean up MatrixStore connection from vault: ${err.getMessage}", err)
      }
    } else {
      logger.info("No active matrixstore connection")
    }
  }
}

object Session {
  def empty = new Session(None, None, None, 1.minute, 10, true, Seq())
}