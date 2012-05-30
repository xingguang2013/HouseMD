/*
 * Copyright 2012 zhongl
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.github.zhongl

import jline.console.ConsoleReader
import java.io.PrintStream
import java.io.InputStream
import annotation.tailrec
import java.util.List
import Convertors._
import jline.console.completer.{NullCompleter, Completer}

/**
 * @author <a href="mailto:zhong.lunfu@gmail.com">zhongl<a>
 */
abstract class CommandSuite(name: String, version: String, description: String, commands: Command*)
  extends Application(name, version, description) {

  protected val prompt   : String      = name + "> "
  protected val out      : PrintStream = System.out
  protected val in       : InputStream = System.in
  protected val completer: Completer   = DefaultCompleter

  private val command   = parameter[String]("command", "sub command name.")
  private val arguments = parameter[Array[String]]("arguments", "sub command arguments.", Some(Array()))

  private val _commands = commands :+ Help :+ Quit

  override def main(arguments: Array[String]) { if (arguments.isEmpty) interact() else super.main(arguments) }

  override def run() {
    run(command(), arguments()) { n => throw new IllegalArgumentException("Unknown command: " + n) }
  }

  private def interact() {
    val reader = new ConsoleReader(in, out)
    reader.setPrompt(prompt)
    reader.addCompleter(completer)

    @tailrec
    def parse(line: String) {
      if (line == null) return
      val array = line.trim.split("\\s+")
      try {
        run(array.head, array.tail) { n => out.println("Unknown command: " + n) }
      } catch {
        case e: QuitException => return
      }
      parse(reader.readLine())
    }

    parse(reader.readLine())
  }

  private def run(name: String, arguments: Array[String])(handleUnknownCommand: String => Unit) {
    _commands find {_.name == name} match {
      case Some(c) => c.parse(arguments); c.run()
      case None    => handleUnknownCommand(name)
    }
  }

  object Help extends Command("help", "display this infomation.") with CommandCompleter {
    private val command = parameter[String]("command", "sub command name.", Some("*"))

    private lazy val pattern = "%1$-" + _commands.map(_.name.length).max + "s\t%2$s\n"

    def run() {
      try {
        val info = command() match {
          case "*"       => _commands.foldLeft[String]("") { (a, c) => a + pattern.format(c.name, c.description) }
          case n: String => _commands find (_.name == n) match {
            case Some(c) => c.help + "\n"
            case None    => throw new IllegalArgumentException("Unknown command: " + n)
          }
        }
        out.print("\n"+info+"\n")
      } catch {
        case e: IllegalArgumentException => out.println(e.getMessage)
      }
    }

    protected def argumentComplete(name: String, prefix: String, cursor: Int, candidates: List[CharSequence]) = -1
  }

  object Quit extends Command("quit", "terminate the process.") {
    def run() { throw new QuitException }
  }

  class QuitException extends Exception

  trait CommandCompleter extends Completer {

    import collection.JavaConversions._

    private val RE0 = """\s+""".r
    private val RE1 = """\s*(\w+)""".r
    private val RE2 = """\s*(\w+)(.+)""".r

    def complete(buffer: String, cursor: Int, candidates: List[CharSequence]) = buffer match {
      case null | RE0() => candidates.addAll(commandNames); cursor
      case RE1(p)       => candidates.addAll(commandNamesStartsWith(p)); cursor - p.length
      case RE2(n, p)    => argumentComplete(n, p, cursor, candidates)
    }

    protected def argumentComplete(name: String, prefix: String, cursor: Int, candidates: List[CharSequence]): Int

    private def commandNamesStartsWith(prefix: String): List[_ <: CharSequence] =
      _commands.collect { case cl if cl.name.startsWith(prefix) => cl.name }.sorted

    private def commandNames: List[_ <: CharSequence] = _commands.map(_.name).sorted
  }

  object DefaultCompleter extends CommandCompleter {

    protected def argumentComplete(name: String, prefix: String, cursor: Int, candidates: List[CharSequence]) =
      completerOfCommand(name).complete(prefix, cursor, candidates)

    private def completerOfCommand(name: String): Completer = _commands find {_.name == name} match {
      case Some(cl) if cl.isInstanceOf[Completer] => cl.asInstanceOf[Completer]
      case _                                      => NullCompleter.INSTANCE
    }
  }

}
