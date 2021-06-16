package commands

import akka.actor.ActorSystem
import akka.stream.Materializer
import com.om.mxs.client.japi.{MatrixStore, Vault}
import interpreter.Session
import models.ObjectMatrixEntry
import org.jline.reader.LineReader
import org.jline.terminal.Terminal

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

trait BaseCommand {
  def run(params:Seq[String], session:Session)(implicit terminal:Terminal, lineReader:LineReader, actorSystem: ActorSystem, mat:Materializer):Try[Session]

  /**
   * Asynchronous version of withVault.  Callback wrapper that opens a vault connection to the appliance and ensures that it is closed afterwards.
   * The callback should NOT throw exceptions, but pass them back as a failed Future.
   * @param mxs initialised MatrixStore object. Get this from the session.
   * @param vaultId vaultID to connect to. Get this from the session
   * @param cb callback that is called if the vault connect is successful. It must take a single parameter of type Vault
   *           and return a Future of any type
   * @param ec implicitly provided execution context on which to run the future
   * @tparam A data type of the future
   * @return the value of the callback, or a failred Future indicating why the vault connection could not be achieved.
   */
  def withVaultAsync[A](mxs:MatrixStore, vaultId:String)(cb:(Vault)=>Future[A])(implicit ec:ExecutionContext):Future[A] = {
    val maybeVault = Try { mxs.openVault(vaultId) }
    val resultFut = Future.fromTry(maybeVault.map(cb)).flatten
    resultFut.andThen({
      case _=>
        if(maybeVault.isSuccess) maybeVault.get.dispose()
    })
    resultFut
  }

  /**
   * Callback wrapper that opens a vault connection to the appliance and ensures that it is closed afterwards.
   * This relies on the callback NOT throwing exceptions - instead it should pass them back as a failed Try.
   * @param mxs initialised MatrixStore object. Get this from the session.
   * @param vaultId vaultID to connect to. Get this from the session
   * @param cb callback that is called if the vault connect is successful. It must take in a single parameter of type Vault
   *           and return a Try of any data type.  Any exceptions must be contained within the Try
   * @tparam A data type of the Try to be returned
   * @return the value of the callback, or a failure indicating why the vault connection could not be achieved
   */
  def withVault[A](mxs:MatrixStore, vaultId:String)(cb:Vault=>Try[A]):Try[A] = {
    val maybeVault = Try {mxs.openVault(vaultId)}
    val result = maybeVault.flatMap(cb)
    if(maybeVault.isSuccess) maybeVault.get.dispose()
    result
  }

  def printMeta(entry:ObjectMatrixEntry)(implicit terminal: Terminal) = {
    terminal.writer().println(s"${entry.oid}\t${entry.getFileSize.map(_.toString).getOrElse("(no size)")}\t\t${entry.pathOrFilename.getOrElse("(no name)")}")
    entry.attributes.foreach(meta=>{
      meta.stringValues.foreach(kv=>terminal.writer().println(s"\t${kv._1}: ${kv._2}"))
      meta.intValues.foreach(kv=>terminal.writer().println(s"\t${kv._1}: ${kv._2}"))
      meta.longValues.foreach(kv=>terminal.writer().println(s"\t${kv._1}: ${kv._2}"))
      meta.boolValues.foreach(kv=>terminal.writer().println(s"\t${kv._1}: ${kv._2}"))
      meta.floatValues.foreach(kv=>terminal.writer().println(s"\t${kv._1}: ${kv._2}"))
    })
  }
}
