import akka.actor.ActorSystem
import akka.stream.Materializer
import interpreter.Interpreter
import org.jline.reader.{Candidate, Completer, LineReader, LineReaderBuilder, MaskingCallback, ParsedLine}
import org.jline.terminal.{Terminal, TerminalBuilder}
import org.jline.reader.impl.DefaultParser

import java.util.function.Consumer
import scala.concurrent.Await
import scala.jdk.CollectionConverters._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

object Main {
  lazy implicit val actorSystem:ActorSystem = ActorSystem("matrixstore-client")
  lazy implicit val mat:Materializer = Materializer(actorSystem)

  val defaultPrompt = "> "

  private val interpreter = new Interpreter

  def terminalInputLoop(callbacks:Seq[Consumer[LineReader]]=Seq())(implicit terminal: Terminal):Unit = {
    val parser = new DefaultParser()
    implicit val lineReader = LineReaderBuilder.builder()
      .terminal(terminal)
      //.completer(completer)
      .parser(parser)
      .variable(LineReader.SECONDARY_PROMPT_PATTERN, "%M%P > ")
      .variable(LineReader.INDENTATION, 2)
      .option(LineReader.Option.INSERT_BRACKET, true)
      .build()


    callbacks.foreach(_.accept(lineReader))
    while(true) {
      val prompt = interpreter.session.activeVaultId match {
        case None=>
          defaultPrompt
        case Some(vaultId)=>
          s"$vaultId$defaultPrompt"
      }

      //we must read in the line here, this stores it in the LineReader's state so we can ignore the return value
      //and rely on the LineReader to send it directly to the parser
      lineReader.readLine(prompt, "", null.asInstanceOf[MaskingCallback], null)
      val request = lineReader.getParsedLine
      val tokens = request.words().asScala
      terminal.writer().println(s"You input these tokens: ${tokens.mkString(" | ")}")
      terminal.writer().flush()
      if(tokens.headOption.contains("exit")) {
        terminal.writer().println("Goodbye!")
        terminal.writer().flush()
        interpreter.session.cleanup()
        return
      }

      interpreter.handle(lineReader.getParsedLine)
    }
  }

  def main(args: Array[String]):Unit = {
    //get system default terminal
    implicit val terminal:Terminal = TerminalBuilder.terminal()

    terminalInputLoop()
    Await.result(actorSystem.terminate(), 1.minute)
  }
}
