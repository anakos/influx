package influxdb

package object http {
  def get[A](path: String, params: Map[String, String])
            (implicit has: Has[A]): RIO[A, HttpResponse.Text] =
    has.using(_.get(path, params))

  def post[A](path: String, params: Map[String, String], content: String)
             (implicit has: Has[A]): RIO[A, HttpResponse.Text] =
    has.using(_.post(path, params, content))
}