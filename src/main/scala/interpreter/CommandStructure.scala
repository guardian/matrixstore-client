package interpreter

import commands.BaseCommand
import org.jline.reader.ParsedLine
import scala.jdk.CollectionConverters._

trait Token {
  val branch:Boolean
  val name:String
}

case class BranchToken(name:String, children:Seq[Token]) extends Token {
  val branch = true
}

/**
 * represents a leaf node, i.e. one on the end of the tree that will actually "do" something.
 * That "something" is contained within the callback function
 * @param name the token name to respond to
 */
case class LeafToken(name:String, cmd:BaseCommand) extends Token {
  val branch = false
}