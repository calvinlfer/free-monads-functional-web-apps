package com.experiments.calvin

import scalaz.Free


package object domain {

  case class Person(username: String, firstName: String, lastName: String)

  // general application instruction
  sealed trait AppInstruction[Result] {
    def lift: Free[AppInstruction, Result] = Free.liftF(this)
  }

  // more specific types of instructions
  sealed trait LogInstruction[Result]
  sealed trait DatabaseInstruction[Result]

  // AST
  case class GetById(id: String) extends AppInstruction[Option[Person]] with DatabaseInstruction[Option[Person]]
  case class Save(person: Person) extends AppInstruction[Unit] with DatabaseInstruction[Unit]
  case class Log(info: String) extends AppInstruction[Unit] with LogInstruction[Unit]

  // Smart constructors
  def getById(id: String): Free[AppInstruction, Option[Person]] = GetById(id).lift
  def save(person: Person): Free[AppInstruction, Unit] = Save(person).lift
  def log(info: String): Free[AppInstruction, Unit] = Log(info).lift
}
