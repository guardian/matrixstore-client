package commands

import akka.actor.ActorSystem
import akka.stream.Materializer
import com.om.mxs.client.japi.{MatrixStore, MatrixStoreConnection}
import com.om.mxs.client.japi.cred.Credentials
import interpreter.Session
import org.jline.reader.LineReader
import org.jline.terminal.Terminal

import scala.util.{Failure, Success, Try}

/**
 * the connect command establishes a connection to the matrixstore appliance
 */
class Connect extends BaseCommand {
  def showHelp(implicit terminal: Terminal) = {
    terminal.writer().println("`connect` connects to the given objectmatrix cluster.")
    terminal.writer().println("Usage: connect {ip-address} {vault-id} {user-id}")
    terminal.writer().println("  {ip-address} is a comma-separated list of IP addresses")
    terminal.writer().println("  {vault-id} is the ID of the vault to connect to")
    terminal.writer().println("  {user-id} is a user that is valid on the given ObjectMatrix appliance")
    terminal.writer().println("You are then prompted for a password")
    terminal.flush()
  }

  def getPassword(implicit terminal:Terminal, lineReader:LineReader):String = {
    lineReader.readLine("Password > ", '*')
  }

  def run(params:Seq[String], session:interpreter.Session)(implicit terminal:Terminal, lineReader:LineReader, actorSystem: ActorSystem, mat:Materializer):Try[Session] = {
    if(params.length != 4 || params.contains("help")) {
      showHelp
      Success(session)
    } else {
      Try {
        terminal.writer().println(s"Connecting to ${params(2)} on ${params(1)} as ${params(3)}...")
        terminal.writer().flush()
        val credentials = Credentials.newUsernamePasswordCredentials(params(3),getPassword)
        val hosts = params(1).split(",")
        val conn = MatrixStoreConnection.builder().withHosts(hosts).build()
        val mxs = MatrixStore.builder().withConnection(conn).withCredentials(credentials).build()

        //check if the vault ID is valid by getting a vault ptr and then disposing it. According to OM this is a lightweight operation.
        //if this fails then we will trip on an exception that is caught by the interpreter.
        val tempVault = mxs.openVault(params(2))
        tempVault.dispose()

        if(session.activeConnection.isDefined) {
          session.activeConnection.get.closeConnection()
          session.activeConnection.get.dispose()
        }
        session.copy(activeConnection = Some(mxs), activeVaultId = Some(params(2)))
      }
    }
  }
}
