# Http4s + Circe + Free Monads
*In the pursuit of pure functional web applications*

Libraries:
- [Http Server: Http4S](http://http4s.org/)
- [JSON Parser: Circe](https://github.com/circe/circe)
- [ScalaZ](https://github.com/scalaz/scalaz)

Credits: 
- [David Hoyt: Drinking the Free Kool-Aid](https://www.youtube.com/watch?v=T4956GI-6Lw)
- [Chris Myers: A year living freely](https://www.youtube.com/watch?v=rK53C-xyPWw)
- [Functional Programming in Scala](https://www.manning.com/books/functional-programming-in-scala)
- [John De Goes: A Modern Architecture for FP](http://degoes.net/articles/modern-fp)

## Purpose
The purpose of this project was to apply the theory behind the Free Monad in a semi-practical way by trying to craft a
pure functional web application in terms of having a functional core and an imperative shell. The edges where 
side-effects can occur is when you have completed processing the request and you are sending a response. This is where 
we interpret the pure Free instructions using an interpreter which causes side-effects to occur in the system.

### A very quick and dirty introduction to Free Monads
The idea behind the Free Monad is to separate the description of the computation from the execution of the computation.
Doing this allows us to keep a pure embedded DSL where we describe what we would like to do but NOT how to do it. You
can see that these instructions are *free to be interpreted* since they describe what they would like to do. The 'how' 
part is the responsibility of the interpreter which interprets these free instructions and gives them meaning actually
performing the execution of the instruction which most likely causes side effects (reading from a database, logging, 
etc.)

I have not used Monad Coproducts that Runar suggests to combine the different types of instructions together. Instead,
I have gone for a very simple approach similar to the one showed by Chris Myers although the way I have done it does
suffer from type erasure.

The free instructions are defined like this:
```scala
  import scalaz.Free
  
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

  // data that represents a person
  case class Person(username: String, firstName: String, lastName: String)
```

I have two types of instructions, `LogInstruction` for log related concerns and `DatabaseInstruction` for persistence
related concerns. The descriptions of the computations are:
- `GetById` - I want to get a person by their id from the database
- `Save` - I want to save a new Person to the database
- `Log` - I want to log information

Notice that their results are encoded in the `AppInstruction` for example for `GetById`, we want to get back a Person
provided a Person with that id is present so the result is a `Option[Person]` encoded in the `AppInstruction`

We provide smart constructors to lift these instructions into `Free` so we get a Monad for free. This gives us the 
ability to sequence our instructions (log and then save or get-by-id and then log and then save)

So far, we have covered the description of the instructions. We haven't covered how to give meaning/interpret these 
instructions/descriptions. This is where the interpreter comes into play. The interpreter is responsible for giving
meaning to the instructions and quite possible performing side-effects as a result of doing so.

#### What does an Interpreter for Free instructions look like?
The interpreter is a [Natural Transformation typeclass](http://eed3si9n.com/learning-scalaz/Natural-Transformation.html) 
that abstracts over higher-kinded-types/functions-at-the-type-level/type-constructors (think List, Future, Option). A 
Natural transformation provides a conversion from one higher kinded type to another higher-kinded-type whilst 
preserving the internal structure. 

The Natural Transformation typeclass more or less looks like this:
```scala
trait NaturalTransformation[F[_], G[_]] {
  def apply[A](given: F[A]): G[A]
}

// type alias for ~> sugar
type ~>[F[_], G[_]] = NaturalTransformation[F, G]
```

I said that it provides a conversion from one higher-kinded-type to another. A simple example of this would be to 
convert an Option to a List. 

```scala
import scalaz.~>
object optionToList extends (Option ~> List) {
 // pretending toList is not present
 def apply[A](given: Option[A]): List[A] = given match {
  case Some(a) => a :: Nil
  case None => Nil
 } 
}
```

So now you have seen an example implementation of a Natural Transformation from one Higher Kinded Type (Option) to 
another (List). We use the Natural Transformation in Free Monads to convert from our Instruction that is a 
higher-kinded-type to a higher-kinded-type that must have an implementation of the Monad typeclass. So the definition of
an interpreter looks like this:

```scala
  /**
   * Relevant snippet taken from ScalaZ
   * 
   * Catamorphism for `Free`.
   * Runs to completion, mapping the suspension with the given transformation at each step and
   * accumulating into the monad `M`.
   */
  final def foldMap[M[_]](f: S ~> M)(implicit M: Monad[M]): M[A] =
    step match {
      case Return(a) => M.pure(a)
      case Suspend(s) => f(s)
      // This is stack safe because `step` ensures right-associativity of Gosub
      case a@Gosub(_, _) => M.bind(a.a foldMap f)(c => a.f(c) foldMap f)
    }
```

As you can see the result higher-kinded-type `M` in this example, must have a Monad typeclass implementation. 

##### Why is all this needed?
Coyoneda says that we can turn any Abstract Data Type into a Functor (for free) given you provide a conversion later.
The Free Functor doesn't know how to `map`. So whenever you `map` over a Free Functor, it will accumulate your `map`s so
that it will be able to run it later. 

Likewise, a similar situation exists to get a Monad for free. You can provide any Functor and get a Monad (for free)
given you provide a conversion to a real Monad later. Similar to the Free Functor, the Free Monad doesn't know how to
`join` or `flatMap`. So whenever you `flatMap`, it will accumulate your `flatMap`s so that it will be run later when the 
conversion is given. You end up with a recursive data structure when you perform those flatMaps (`F[F[F[F[F[F]]]]]`) and
when you provide a conversion to a real monad, you are able to shed the layers (hence the `foldMap`). One more thing to 
note is that a Monad's `map` is a derived combinator meaning you get `map` for free if you implement `flatMap` and `pure`
so you meet the requirement for having a conversion both for the Free Monad and Free Functor if you provide a conversion
to a real Monad. This pays the cost of getting a Free Functor and a Free Monad.

######So how do I do the conversion from the Free Monad to a real Monad?
A Natural Transformation and the imposition that the result data type of the Natural Transformation has a Monad 
implementation.

Note that all of this happens behind the scenes so we get to do very minimal work to use this tool. 
Our `AppInstruction` data-type isn't a Functor so Coyoneda is used to get a Free Functor and then a Free Monad is used
to get a Monad from a Functor (buy-now, pay-later).

When we write the interpreter (Natural Transformation + Monad imposition), we pay the cost. Here's what an example 
interpreter looks like along with the description of the computation. This works if you bring in ScalaZ:

```scala
import scalaz.{Free, ~>}

object Example extends App {

  case class Person(id: String)

  sealed trait AppInstruction[Result]
  case class GetById(id: String) extends AppInstruction[Option[Person]]
  case class Save(person: Person) extends AppInstruction[Unit]
  case class Log(info: String) extends AppInstruction[Unit]

  // smart constructors to lift the instruction data-types into Free
  def getById(id: String): Free[AppInstruction, Option[Person]] = Free.liftF(GetById(id))
  def save(person: Person): Free[AppInstruction, Unit] = Free.liftF(Save(person))
  def log(info: String): Free[AppInstruction, Unit] = Free.liftF(Log(info))

  // an example sequence of instructions (haven't executed anything yet)
  val result: Free[AppInstruction, Option[Person]] = for {
    _ <- log("about to save a Person")
    _ <- save(Person("1"))
    _ <- log("about to get a Person")
    optPerson <- getById("1")
  } yield optPerson

  type Id[A] = A
  val exampleInterpreter = new (AppInstruction ~> Id) {
    override def apply[A](fa: AppInstruction[A]): Id[A] = fa match {
      case GetById(_) =>
        Some(Person("1"))

      case Save(_) =>
        ()

      case Log(info) =>
        println(info)
        ()
    }
  }

  // run the instructions using the example interpreter
  println(result.foldMap(exampleInterpreter))
}
```

This is what the use of a Free Monad looks like. Notice the instructions are pure and we only side-effect when we run
the instructions against the `exampleInterpreter`

In the application, a big interpreter is used which is composed of little interpreters which know how to handle a 
certain concern. The big interpreter dispatches specific instructions to the specific little interpreter

The `Http` trait is what I would consider the 'end-of-the-program' if you consider each web request-response is a 
program. The `Http` trait demands for the general 'big' interpreter so it is unaware of the little interpreters so it
can run the free instructions and give meaning to them which forms the response to return to the user. 

The main concepts revolve around the Free Monad which is also known as the Interpreter pattern. I do use Circe to do the
JSON encoding and decoding so you will see some imports scattered around. I have placed the instructions in the `domain` 
package and the interpreters in the `interpreter` package. 

## TO-DOs
Implement some 'real' interpreters as opposed to using toy interpreters
- Interpreter for SLF4J
- Interpreter for DynamoDB


## Contributing
Feel free to send in PRs or tell me if I've missed something (which I most certainly have). I do admit that I have 
glossed over some concepts.