package com.experiments.calvin

import com.experiments.calvin.domain._

import scalaz.concurrent.Task
import scalaz.~>

package object interpreters {
  trait LogInterpreter {
    def run[A](logInstruction: LogInstruction[A]): Task[A]
  }

  class PrintlnLogInterpreter extends LogInterpreter {
    def run[A](logInstruction: LogInstruction[A]): Task[A] = logInstruction match {
      case Log(info) => Task(println(info))
    }
  }

  trait DatabaseInterpreter {
    def run[A](dbInstruction: DatabaseInstruction[A]): Task[A]
  }

  class InMemoryDbInterpreter extends DatabaseInterpreter {
    var map = Map.empty[String, Person]

    override def run[A](dbInstruction: DatabaseInstruction[A]): Task[A] = dbInstruction match {
      case GetById(id) =>
        Task(map.get(id))

      case Save(person) =>
        map = map + (person.username -> person)
        Task(())
    }
  }

  class AppInstructionInterpreter(logInterpreter: LogInterpreter, dbInterpreter: DatabaseInterpreter) {
    val interpreter: AppInstruction ~> Task = new (AppInstruction ~> Task) {
      override def apply[A](fa: AppInstruction[A]): Task[A] = fa match {
        case logInstruction: LogInstruction[A] =>
          logInterpreter.run(logInstruction)

        case dbInstruction: DatabaseInstruction[A] =>
          dbInterpreter.run(dbInstruction)
      }
    }
  }
}
