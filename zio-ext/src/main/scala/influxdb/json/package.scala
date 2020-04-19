package influxdb

/**
  * This is a partial port of of circe-fs2 to ZStream: we only support the methods provided there
  * that are relevant to parsing chunked JSON results from InfluxDB.
  */
package object json {
  import io.circe.Json
  import org.typelevel.jawn.{ AsyncParser, ParseException }
  import zio.Chunk
  import zio.stream.Stream

  type Pipe[A, B] = Stream[Throwable, A] => Stream[Throwable, B]

  private[this] val supportParser = new io.circe.jawn.CirceSupportParser(None, true)

  final def byteStreamParserC: Pipe[Chunk[Byte], Chunk[Json]] =
    byteParserC(AsyncParser.ValueStream)

  final def byteParserC(mode: AsyncParser.Mode): Pipe[Chunk[Byte], Chunk[Json]] =
    new ParsingPipe[Chunk[Byte]](supportParser) {
      protected[this] final def parseWith(p: AsyncParser[Json])(in: Chunk[Byte]): Either[ParseException, Chunk[Json]] =
        p.absorb(in.toArray)(supportParser.facade)
          .map(Chunk.fromIterable(_))

      protected[this] val parsingMode: AsyncParser.Mode = mode
    }
}
