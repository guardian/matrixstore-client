package commands
import akka.actor.ActorSystem
import akka.stream.Materializer
import com.om.mxs.client.japi.{MatrixStore, Vault}
import helpers.MatrixStoreHelper
import interpreter.Session
import models.ObjectMatrixEntry
import org.jline.reader.LineReader
import org.jline.terminal.Terminal

import scala.util.{Success, Try}

class Meta extends BaseCommand {
  def lookupEntry(oid:String)(implicit vault:Vault) = Try {
    ObjectMatrixEntry(oid, None, None)
      .getMetadataSync
  }

  def showHelp(implicit terminal:Terminal) = {
    terminal.writer().println("`meta` returns the object metadata for the given OID")
    terminal.writer().println("Usage: meta {oid}")
    terminal.writer().println("\t{oid} is the Objectmatrix Identifier, the value that looks like a UUID with some numbers on the end")
    terminal.writer().flush()
  }

  override def run(params: Seq[String], session: Session)(implicit terminal: Terminal, lineReader: LineReader, actorSystem: ActorSystem, mat: Materializer): Try[Session] = {
    if(params.length!=2) {
      showHelp
      return Success(session)
    }

    (session.activeConnection, session.activeVaultId) match {
      case (Some(mxs), Some(vaultId))=>
        val maybeVault = Try { mxs.openVault(vaultId) }
        val maybeEntry = maybeVault.flatMap(implicit vault=>lookupEntry(params(1)))
        maybeVault.flatMap(v=>Try { v.dispose() })

        maybeEntry.map(entry=>{
          printMeta(entry)
          session
        })
      case _=>
        terminal.writer().write("You need to establish a connection with `connect` before doing metadata lookups")
        Success(session)
    }
  }
}
