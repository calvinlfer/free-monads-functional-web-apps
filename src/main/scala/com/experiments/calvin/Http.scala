package com.experiments.calvin

import org.http4s._
import org.http4s.dsl._
import org.http4s.circe._
import io.circe._
import io.circe.syntax._
import io.circe.generic.auto._
import domain._

import scalaz.concurrent.Task
import scalaz.~>

trait Http {
  val interpreter: AppInstruction ~> Task

  val helloWorldService = HttpService {
    case GET -> Root / "hello" =>
      Ok("Hello there")

    case GET -> Root / "async-hello" =>
      Ok(Task("Hello there"))
  }

  val personService = HttpService {
    case body@POST -> Root / "persons" =>
      body.as(jsonOf[Person])
        .flatMap(person => {
          val savePerson = for {
            _ <- log(s"Saving Person: $person")
            _ <- save(person)
          } yield ()
          savePerson.foldMap(interpreter)
        })
        .flatMap(_ => Ok())

    case GET -> Root / "persons" / personId =>
      val optPerson = for {
        _ <- log(s"Obtaining person with id: $personId")
        optPerson <- getById(personId)
      } yield optPerson

      val tskOptPerson: Task[Option[Person]] = optPerson.foldMap(interpreter)
      // Note that we flatMap on the Task, there is an implicit conversion
      // for Http Status Codes which are Tasks so this is the appropriate use
      // of flatMap since the mapper function is nested in that it produced a Task
      // using an implicit conversion
      tskOptPerson
        .map(optPerson => optPerson.map(_.asJson))
        .flatMap(optJson =>
          optJson
            .map(json => Ok(json))
            .getOrElse(NotFound())
        )
  }
}
