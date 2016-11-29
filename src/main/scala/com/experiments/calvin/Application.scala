package com.experiments.calvin

import com.experiments.calvin.domain.AppInstruction
import com.experiments.calvin.interpreters.{AppInstructionInterpreter, InMemoryDbInterpreter, PrintlnLogInterpreter}
import org.http4s.server.{Server, ServerApp}
import org.http4s.server.blaze._

import scalaz.concurrent.Task
import scalaz.~>

object Application extends ServerApp with Http {

  override def server(args: List[String]): Task[Server] = {
    BlazeBuilder
      .bindHttp(9000, "localhost")
      .mountService(helloWorldService, "/")
      .mountService(personService, "/")
      .start
  }

  override val interpreter: ~>[AppInstruction, Task] =
    new AppInstructionInterpreter(new PrintlnLogInterpreter, new InMemoryDbInterpreter).interpreter
}
