package influxdb
package query

import cats._
import cats.data._
import cats.instances.all._
import cats.syntax.applicative._
import cats.syntax.either._
import cats.syntax.list._
import cats.syntax.reducible._
import cats.syntax.semigroup._
import cats.syntax.traverse._

import influxdb.http.api.QueryResult

import io.circe._

final case class Result[A](series: List[A]) { self =>
  def map[B](f: A => B): Result[B] =
    Monad[Result].map(self)(f)
  def flatMap[B](f: A => Result[B]): Result[B] =
    Monad[Result].flatMap(self)(f)
}
object Result {
  implicit def monoid[A]: Monoid[Result[A]] =
    new Monoid[Result[A]] {
      def empty: Result[A] =
        Result(List.empty)
      def combine(x: Result[A], y: Result[A]): Result[A] =
        Result(x.series |+| y.series)
    }

  implicit def eq[A : Eq]: Eq[Result[A]] =
    Eq.by(_.series)

  implicit val monad: Monad[Result] =
    new Monad[Result] {
      def pure[A](x: A): Result[A] =
        Result(List(x))
      
      def flatMap[A, B](fa: Result[A])(f: A => Result[B]): Result[B] =
        fa.copy(series = fa.series.flatMap(a => f(a).series))

      def tailRecM[A, B](a: A)(f: A => Result[Either[A,B]]): Result[B] =
        Result(Monad[List].tailRecM(a)(f.andThen(_.series)))
    }

  def extract[A : Decoder](content: String): Either[Throwable, List[Result[A]]] =
    for {
      qr  <- jawn.decode[QueryResult[A]](content)
      res <- extract(qr)
    } yield res

  def extract[A](js: influxdb.http.api.QueryResult[A]): Either[Throwable, List[Result[A]]] =
    for {
      results <- js.unwrap.leftMap(_.toInfluxException())
      series  <- results.traverse { _.failOnError().map(Result(_)) }
    } yield series

  def extractSingle[A : Decoder](content: String): Either[Throwable, Result[A]] =
    for {
      qr     <- jawn.decode[QueryResult[A]](content)
      series <- extractSingle(qr)
    } yield series

  def extractSingle[A](js: influxdb.http.api.QueryResult[A]): Either[Throwable, Result[A]] =
    for {
      results <- js.unwrap.leftMap(_.toInfluxException())
      series  <- results.headOption.fold(Monoid[Result[A]].empty.asRight[InfluxException]) {
        _.failOnError().map(Result(_))
      }
    } yield series

  def extractPartial[A](js: influxdb.http.api.QueryResult[A]): IorNec[Throwable, List[Result[A]]] =
    for {
      results <- js.unwrap.leftMap(_.toInfluxException())
        .toEitherNec
        .toIor

      series  <- results.toNel
        .fold[IorNec[InfluxException, List[Result[A]]]](Ior.right(List.empty)) {
          _.reduceMap(
            _.failOnError()
              .map(Result(_).pure[List])
              .toEitherNec
              .toIor
          )
        }
    } yield series
}