package influxdb.json

import io.circe.Json
import org.typelevel.jawn.{ AsyncParser, ParseException, SupportParser }
import zio._
import zio.stream._

/**
  * This is a partial port of circe-fs32 to ZSTream: again, we only support the functionality 
  * that is relevant to parsing chunked JSON results from InfluxDB.
  *
  * @param supportParser
  */
private[json] abstract class ParsingPipe[S](supportParser: SupportParser[Json]) extends Pipe[S, Chunk[Json]] {
  protected[this] def parsingMode: AsyncParser.Mode

  protected[this] def parseWith(parser: AsyncParser[Json])(in: S): Either[ParseException, Chunk[Json]]

  private[this] final def makeParser: UIO[AsyncParser[Json]] =
    ZIO.succeed(supportParser.async(mode = parsingMode))

  private[this] final def runParse(s: Stream[Throwable, S])(p: AsyncParser[Json]): Stream[Throwable, Chunk[Json]] =
    s.aggregate(
      ZSink.pull1(ZIO.succeed[Chunk[Json]](Chunk.empty)) { x: S =>
        ZSink.fromEffect(
          ZIO.fromEither(parseWith(p)(x))
        )
      }      
    )

  final def apply(s: Stream[Throwable, S]): Stream[Throwable, Chunk[Json]] =
    Stream.fromEffect(makeParser)
      .flatMap(runParse(s))
}